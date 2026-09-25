"""The audio translation pipeline: audio, speech-to-text, source-language detection, translation.

Cheap checks come first (languages and provider capabilities), so nothing costly happens for a request that cannot
succeed. The translation step reuses the same translation service as POST /v1/translate.
"""

import asyncio
import logging
import time
import unicodedata
from collections.abc import Awaitable, Callable
from pathlib import Path

from app.core.errors import (
    AutoDetectUnavailableError,
    ProviderError,
    ProviderTimeoutError,
    ProviderUnavailableError,
    SpeechNotRecognizedError,
    UnsupportedLanguageError,
)
from app.core.logging import kv
from app.speech.audio import TemporaryAudio
from app.speech.preprocess import normalised_copy
from app.speech.provider import SpeechRequest, SpeechResult, SpeechToTextProvider
from app.speech.schemas import AudioTranslateOptions, AudioTranslateResponse
from app.translation.languages import AUTO, LANGUAGES
from app.translation.schemas import TranslateRequest
from app.translation.service import TranslationService

logger = logging.getLogger(__name__)

# An attempt forced to a likely language is only trusted if the engine is at least this sure of its words (the mean
# log-probability; Whisper's own default cut-off for "this attempt failed" is the same -1.0). Below it the words are most
# likely nonsense from forcing the wrong language, and the honest answer is that the language is not supported.
_MIN_FORCED_CONFIDENCE = -1.0

# A forced-language attempt replaces automatic detection only when it is clearly more sure of its words (measured on degraded
# French: an attempt that won by 0.02 was actually the worse transcript, while the ones that were really better won by 0.10+).
_FORCED_MARGIN = 0.05

# Below this the best attempt is still unsure of its words, and the caller is told the recording was unclear (clear speech
# measured -0.05 to -0.25; speech buried in noise -0.5 to -0.6).
_UNCLEAR_BELOW = -0.5

# At most this many likely languages are tried besides automatic detection (each is one more request to the speech engine).
_MAX_FORCED = 2


def _clean_transcript(text: str) -> str:
    """Normalises recognised text and drops control characters other than line breaks."""
    text = unicodedata.normalize("NFC", text)
    return "".join(ch for ch in text if ch in "\n\t" or unicodedata.category(ch) != "Cc").strip()


class SpeechTranslationService:
    def __init__(
        self,
        stt: SpeechToTextProvider,
        translation: TranslationService,
        *,
        timeout_seconds: float,
        normaliser: Callable[[Path], Awaitable[Path | None]] = normalised_copy,
    ) -> None:
        self._stt = stt
        self._translation = translation
        self._timeout = timeout_seconds
        self._normaliser = normaliser

    @property
    def provider_name(self) -> str:
        return self._stt.name

    def check_request(self, options: AudioTranslateOptions) -> tuple[str, str | None]:
        """Validates languages and capabilities. Returns (target, spoken language or None for auto-detect)."""
        target = self._translation.ensure_target_supported(options.target)
        if options.source == AUTO:
            if not self._stt.capabilities().auto_detect:
                raise AutoDetectUnavailableError(
                    "The speech provider cannot detect the spoken language. Choose it explicitly.",
                    provider=self._stt.name,
                    stage="speech_to_text",
                )
            return target, None
        spoken = self._translation.require_language(options.source, "source")
        self._require_speech_support(spoken.code)
        return target, spoken.code

    def _require_speech_support(self, code: str) -> None:
        caps = self._stt.capabilities()
        language = LANGUAGES.get(code)
        if language is None or not language.speech_to_text_supported or code not in caps.languages:
            raise UnsupportedLanguageError(
                f"Speech recognition is not available for {code!r}.",
                language=code,
                role="source",
                feature="speech_to_text",
                provider=self._stt.name,
                supported=sorted(c for c, lang in LANGUAGES.items() if lang.speech_to_text_supported and c in caps.languages),
            )

    async def _transcribe(self, path: Path, content_type: str, language: str | None) -> SpeechResult:
        try:
            return await asyncio.wait_for(
                self._stt.transcribe(SpeechRequest(path, content_type, language)), timeout=self._timeout
            )
        except TimeoutError:
            raise ProviderTimeoutError("Speech recognition took too long.", provider=self._stt.name, stage="speech_to_text") from None

    async def _attempt(
        self, path: Path, content_type: str, language: str | None, label: str
    ) -> tuple[str, SpeechResult | None, Exception | None]:
        """One try at the recording. A provider failure is returned, not raised, so the other tries can still succeed."""
        try:
            return label, await self._transcribe(path, content_type, language), None
        except (ProviderError, ProviderUnavailableError, ProviderTimeoutError) as error:
            logger.warning("speech_attempt_failed %s", kv(attempt=label, error=type(error).__name__))
            return label, None, error

    async def _best_of(self, audio: TemporaryAudio, hints: list[str]) -> SpeechResult:
        """Transcribes the recording a few ways at once and keeps the result the engine is most sure of.

        Speech engines guess the language of a short or unclear recording from very little (a French voice note was taken for
        Russian), and quiet or rumbly recordings are heard less well. So besides the plain recording with automatic detection,
        the recording is levelled (see ``normalize.py``) and transcribed forced to each of the [hints] languages, the ones the
        caller says the speaker is likely to use. The engine's own confidence in its words picks the winner:
          - a forced attempt must clear a minimum confidence, so speech in a language that really is unsupported is still
            refused rather than turned into a nonsense translation, and must beat automatic detection by a margin;
          - automatic detection that names a language we do not support is never used.
        If nothing usable came back, the plain attempt is returned as it is so that the caller reports the honest problem
        (unsupported language, or nothing heard); if every attempt failed, the first failure is raised.
        """
        supported = self._stt.capabilities().languages
        forced = [
            code for code in hints
            if (language := LANGUAGES.get(code)) is not None and language.speech_to_text_supported and code in supported
        ][:_MAX_FORCED]
        leveled = await self._normaliser(audio.path) if forced else None
        try:
            jobs = [self._attempt(audio.path, audio.content_type, None, "auto")]
            for code in forced:
                if leveled is not None:
                    jobs.append(self._attempt(leveled, "audio/wav", code, code))
                else:
                    jobs.append(self._attempt(audio.path, audio.content_type, code, code))
            outcomes = await asyncio.gather(*jobs)
        finally:
            if leveled is not None:
                leveled.unlink(missing_ok=True)

        plain = outcomes[0][1]
        automatic: SpeechResult | None = None
        forced_results: list[SpeechResult] = []
        for label, result, _ in outcomes:
            if result is None or not _clean_transcript(result.transcript):
                continue
            if label == "auto":
                detected = (result.detected_language or "").lower()
                if not detected or detected in LANGUAGES:
                    automatic = result
            elif result.confidence is None or result.confidence >= _MIN_FORCED_CONFIDENCE:
                forced_results.append(SpeechResult(result.transcript, label, result.confidence))

        chosen: SpeechResult | None = None
        scored = [r for r in forced_results if r.confidence is not None]
        best_forced = max(scored, key=lambda r: r.confidence or 0.0) if scored else (forced_results[0] if forced_results else None)
        if automatic is None:
            chosen = best_forced
        elif best_forced is not None and best_forced.confidence is not None and automatic.confidence is not None:
            chosen = best_forced if best_forced.confidence > automatic.confidence + _FORCED_MARGIN else automatic
        else:
            chosen = automatic

        if chosen is None:
            if plain is not None:
                return plain
            raise next(error for _, _, error in outcomes if error is not None)
        logger.info(
            "speech_best_of %s",
            kv(
                tried=",".join(label for label, result, _ in outcomes if result is not None),
                chosen=chosen.detected_language or "auto",
                confidence=round(chosen.confidence, 2) if chosen.confidence is not None else "unknown",
                leveled=leveled is not None,
                auto_language=(plain.detected_language or "none") if plain is not None else "failed",
            ),
        )
        return chosen

    async def translate_audio(
        self, audio: TemporaryAudio, options: AudioTranslateOptions, target: str, spoken: str | None, *, allow_partial: bool = False
    ) -> AudioTranslateResponse:
        """Transcribes and translates. With [allow_partial], a translation that cannot be made because the translation provider
        failed is returned as an empty translation next to the transcript rather than as an error (see below)."""
        started = time.perf_counter()
        if spoken is None and options.hints:
            result = await self._best_of(audio, options.hints)
        else:
            result = await self._transcribe(audio.path, audio.content_type, spoken)

        transcript = _clean_transcript(result.transcript)
        if not transcript:
            raise SpeechNotRecognizedError("No speech was recognised in the audio.")

        source = spoken or (result.detected_language or "").lower()
        if source and source not in LANGUAGES:
            raise UnsupportedLanguageError(
                f"The detected language {source!r} is not supported.", language=source, role="source", supported=sorted(LANGUAGES)
            )

        if not source:
            # Some speech engines (Mistral's Voxtral) transcribe correctly but never say which language they heard, even
            # when asked. The translator can tell from the transcript itself, in the same call that translates it. If it
            # cannot either, that is reported as source_language_undetected, exactly as before.
            translated = await self._translation.translate(
                TranslateRequest(text=transcript, source=AUTO, target=target, context=options.context, tone=options.tone)
            )
            source = translated.source_language
            translation = translated.translation
        elif source == target:
            # Speech already in the target language is returned as it is, never re-translated.
            translation = transcript
        else:
            try:
                translated = await self._translation.translate(
                    TranslateRequest(text=transcript, source=source, target=target, context=options.context, tone=options.tone)
                )
                translation = translated.translation
            except (ProviderError, ProviderUnavailableError, ProviderTimeoutError) as error:
                if not allow_partial:
                    raise
                # The words were understood but the translator is unavailable (its free quota used up, an outage). The
                # transcript is worth having on its own, so it is returned with an empty translation; the app shows what was
                # heard and says the translation could not be made, instead of failing the whole request.
                logger.warning("audio_translation_partial %s", kv(source_language=source, target_language=target, error=type(error).__name__))
                translation = ""

        logger.info(
            "audio_translation_completed %s",
            kv(
                source_language=source,
                target_language=target,
                auto_detected=spoken is None,
                audio_bytes=audio.size,
                transcript_chars=len(transcript),
                stt_provider=self._stt.name,
                latency_ms=round((time.perf_counter() - started) * 1000),
            ),
        )
        clarity = None if result.confidence is None else ("unclear" if result.confidence < _UNCLEAR_BELOW else "clear")
        return AudioTranslateResponse(
            source_language=source, transcript=transcript, target_language=target, translation=translation, clarity=clarity
        )
