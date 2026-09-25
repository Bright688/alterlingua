"""The audio translation pipeline: audio, speech-to-text, source-language detection, translation.

Cheap checks come first (languages and provider capabilities), so nothing costly happens for a request that cannot
succeed. The translation step reuses the same translation service as POST /v1/translate.
"""

import asyncio
import logging
import time
import unicodedata

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
from app.speech.provider import SpeechRequest, SpeechResult, SpeechToTextProvider
from app.speech.schemas import AudioTranslateOptions, AudioTranslateResponse
from app.translation.languages import AUTO, LANGUAGES
from app.translation.schemas import TranslateRequest
from app.translation.service import TranslationService

logger = logging.getLogger(__name__)

# A second attempt forced to a likely language is only trusted if the engine is at least this sure of its words (the mean
# log-probability; Whisper's own default cut-off for "this attempt failed" is the same -1.0). Below it the words are most
# likely nonsense from forcing the wrong language, and the honest answer is that the language is not supported.
_MIN_FORCED_CONFIDENCE = -1.0


def _clean_transcript(text: str) -> str:
    """Normalises recognised text and drops control characters other than line breaks."""
    text = unicodedata.normalize("NFC", text)
    return "".join(ch for ch in text if ch in "\n\t" or unicodedata.category(ch) != "Cc").strip()


class SpeechTranslationService:
    def __init__(self, stt: SpeechToTextProvider, translation: TranslationService, *, timeout_seconds: float) -> None:
        self._stt = stt
        self._translation = translation
        self._timeout = timeout_seconds

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

    async def _transcribe(self, audio: TemporaryAudio, language: str | None) -> SpeechResult:
        try:
            return await asyncio.wait_for(
                self._stt.transcribe(SpeechRequest(audio.path, audio.content_type, language)), timeout=self._timeout
            )
        except TimeoutError:
            raise ProviderTimeoutError("Speech recognition took too long.", provider=self._stt.name, stage="speech_to_text") from None

    async def _second_opinion(self, audio: TemporaryAudio, hints: list[str], first: SpeechResult) -> SpeechResult:
        """Tries the recording again in each of the [hints] languages, for when automatic detection failed.

        Speech engines guess the language of a short or unclear recording from very little, and a wrong guess (a French voice
        note taken for Yoruba, say) makes the whole request fail although the words were perfectly recognisable in French. So
        when detection named a language we cannot use, or heard nothing, the recording is transcribed again forced to each
        likely language, and the attempt the engine is most sure of wins. An attempt it is not sure of is discarded, so speech
        in a language that really is unsupported is still refused rather than turned into a nonsense translation. Returns
        [first] unchanged when no attempt is good enough.
        """
        supported = self._stt.capabilities().languages
        attempts: list[SpeechResult] = []
        for code in hints:
            language = LANGUAGES.get(code)
            if language is None or not language.speech_to_text_supported or code not in supported:
                continue
            try:
                attempt = await self._transcribe(audio, code)
            except (ProviderError, ProviderUnavailableError, ProviderTimeoutError) as error:
                logger.warning("speech_second_opinion_failed %s", kv(language=code, error=type(error).__name__))
                continue
            if not _clean_transcript(attempt.transcript):
                continue
            if attempt.confidence is not None and attempt.confidence < _MIN_FORCED_CONFIDENCE:
                continue
            attempts.append(SpeechResult(attempt.transcript, code, attempt.confidence))
        if not attempts:
            return first
        scored = [a for a in attempts if a.confidence is not None]
        chosen = max(scored, key=lambda a: a.confidence or 0.0) if len(scored) == len(attempts) else attempts[0]
        logger.info(
            "speech_second_opinion %s",
            kv(
                first_language=first.detected_language or "none",
                tried=",".join(a.detected_language or "" for a in attempts),
                chosen=chosen.detected_language,
                confidence=round(chosen.confidence, 2) if chosen.confidence is not None else "unknown",
            ),
        )
        return chosen

    async def translate_audio(self, audio: TemporaryAudio, options: AudioTranslateOptions, target: str, spoken: str | None) -> AudioTranslateResponse:
        started = time.perf_counter()
        result = await self._transcribe(audio, spoken)

        if spoken is None and options.hints:
            detected = (result.detected_language or "").lower()
            if not _clean_transcript(result.transcript) or (detected and detected not in LANGUAGES):
                result = await self._second_opinion(audio, options.hints, result)

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
            translated = await self._translation.translate(
                TranslateRequest(text=transcript, source=source, target=target, context=options.context, tone=options.tone)
            )
            translation = translated.translation

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
        return AudioTranslateResponse(source_language=source, transcript=transcript, target_language=target, translation=translation)
