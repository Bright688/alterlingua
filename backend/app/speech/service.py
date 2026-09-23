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
    ProviderTimeoutError,
    SourceLanguageUndetectedError,
    SpeechNotRecognizedError,
    UnsupportedLanguageError,
)
from app.core.logging import kv
from app.speech.audio import TemporaryAudio
from app.speech.provider import SpeechRequest, SpeechToTextProvider
from app.speech.schemas import AudioTranslateOptions, AudioTranslateResponse
from app.translation.languages import AUTO, LANGUAGES
from app.translation.schemas import TranslateRequest
from app.translation.service import TranslationService

logger = logging.getLogger(__name__)


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

    async def translate_audio(self, audio: TemporaryAudio, options: AudioTranslateOptions, target: str, spoken: str | None) -> AudioTranslateResponse:
        started = time.perf_counter()
        try:
            result = await asyncio.wait_for(
                self._stt.transcribe(SpeechRequest(audio.path, audio.content_type, spoken)), timeout=self._timeout
            )
        except TimeoutError:
            raise ProviderTimeoutError("Speech recognition took too long.", provider=self._stt.name, stage="speech_to_text") from None

        transcript = _clean_transcript(result.transcript)
        if not transcript:
            raise SpeechNotRecognizedError("No speech was recognised in the audio.")

        source = spoken or (result.detected_language or "").lower()
        if not source:
            raise SourceLanguageUndetectedError("Could not tell which language was spoken.")
        if source not in LANGUAGES:
            raise UnsupportedLanguageError(
                f"The detected language {source!r} is not supported.", language=source, role="source", supported=sorted(LANGUAGES)
            )

        # Speech already in the target language is returned as it is, never re-translated.
        if source == target:
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
