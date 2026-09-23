"""Translated outgoing voice: audio, speech-to-text, translation, text-to-speech.

Cheap checks come first (languages, recognition support, and whether a voice exists for the target), so nothing costly
happens for a request that cannot succeed. Recognition and translation reuse SpeechTranslationService, so the two routes
cannot drift apart.
"""

import asyncio
import base64
import logging
import time

from app.core.errors import ProviderTimeoutError, TextTooLongError, UnsupportedLanguageError
from app.core.logging import kv
from app.speech.audio import TemporaryAudio
from app.speech.schemas import AudioSpeakOptions, AudioSpeakResponse, AudioTranslateOptions, LanguageVoices, SpokenAudio, VoiceInfo, VoicesResponse
from app.speech.service import SpeechTranslationService
from app.speech.tts_provider import TextToSpeechProvider, TtsRequest, Voice
from app.translation.languages import LANGUAGES

logger = logging.getLogger(__name__)


class SpeechSynthesisService:
    def __init__(self, translation_speech: SpeechTranslationService, tts: TextToSpeechProvider, *, timeout_seconds: float, max_chars: int) -> None:
        self._speech = translation_speech
        self._tts = tts
        self._timeout = timeout_seconds
        self._max_chars = max_chars

    @property
    def provider_name(self) -> str:
        return self._tts.name

    def voices(self) -> VoicesResponse:
        caps = self._tts.capabilities()
        languages = sorted(code for code in caps.languages() if code in LANGUAGES and LANGUAGES[code].text_to_speech_supported)
        return VoicesResponse(
            provider=self._tts.name,
            languages=[
                LanguageVoices(language=code, voices=[VoiceInfo(id=v.id, locale=v.locale, gender=v.gender) for v in caps.voices_for(code)])
                for code in languages
            ],
        )

    def check_request(self, options: AudioSpeakOptions) -> tuple[str, str | None, Voice]:
        """Validates everything that can be validated without the audio. Returns (target, spoken language or None, voice)."""
        target, spoken = self._speech.check_request(AudioTranslateOptions(target=options.target, source=options.source, context=options.context, tone=options.tone))
        language = LANGUAGES.get(target)
        caps = self._tts.capabilities()
        supported = sorted(c for c, lang in LANGUAGES.items() if lang.text_to_speech_supported and caps.supports(c))
        if language is None or not language.text_to_speech_supported or not caps.supports(target):
            raise UnsupportedLanguageError(
                f"Speech is not available for {target!r}.", language=target, role="target", feature="text_to_speech", provider=self._tts.name, supported=supported
            )
        voice = caps.choose(target, options.voice)
        if voice is None:
            raise UnsupportedLanguageError(
                "That voice does not exist for this language.", language=target, role="target", feature="text_to_speech", provider=self._tts.name, supported=supported
            )
        return target, spoken, voice

    async def speak_audio(self, audio: TemporaryAudio, options: AudioSpeakOptions, target: str, spoken: str | None, voice: Voice) -> AudioSpeakResponse:
        started = time.perf_counter()
        heard = await self._speech.translate_audio(
            audio, AudioTranslateOptions(target=options.target, source=options.source, context=options.context, tone=options.tone), target, spoken
        )
        if len(heard.translation) > self._max_chars:
            raise TextTooLongError("The translation is too long to speak.", max_chars=self._max_chars)
        try:
            result = await asyncio.wait_for(self._tts.synthesize(TtsRequest(heard.translation, voice)), timeout=self._timeout)
        except TimeoutError:
            raise ProviderTimeoutError("Speech synthesis took too long.", provider=self._tts.name, stage="text_to_speech") from None
        logger.info(
            "audio_speech_completed %s",
            kv(
                source_language=heard.source_language,
                target_language=target,
                voice=voice.id,
                tts_provider=self._tts.name,
                audio_bytes=len(result.audio),
                translation_chars=len(heard.translation),
                latency_ms=round((time.perf_counter() - started) * 1000),
            ),
        )
        return AudioSpeakResponse(
            source_language=heard.source_language,
            transcript=heard.transcript,
            target_language=heard.target_language,
            translation=heard.translation,
            audio=SpokenAudio(content_type=result.content_type, voice=voice.id, size_bytes=len(result.audio), data=base64.b64encode(result.audio).decode("ascii")),
        )
