"""Text-to-speech with Mistral's Voxtral TTS (https://docs.mistral.ai/api/endpoint/audio/speech).

Mistral's API always needs an explicit voice (or a reference audio clip for cloning) — there is no server-side
default voice per language, confirmed against the live API (a request with neither fails with
"Either ref_audio or voice must be provided"). So a language is only ever advertised as supported here when a real
voice id for it is configured in ALTERLINGUA_MISTRAL_TTS_VOICES; there is no invented placeholder id. `GET
/v1/audio/voices` on Mistral lists the preset voices actually available to the account (at the time this was
written, only English presets existed on this account — no French/Spanish/German/Italian/Dutch preset voice, despite
Voxtral TTS documentation describing broader language support; a custom cloned voice via ref_audio could add one
later). Text is never logged.
"""

import base64
import binascii
import json

from app.core.config import Settings
from app.core.errors import ConfigurationError, ProviderError
from app.core.mistral import MistralClient
from app.speech.tts_provider import TextToSpeechProvider, TtsCapabilities, TtsRequest, TtsResult, Voice
from app.translation.languages import LANGUAGES

_SPOKEN = ("en", "fr", "es", "de", "it", "nl")


class MistralTextToSpeechProvider(TextToSpeechProvider):
    name = "mistral"

    def __init__(self, settings: Settings, *, client: MistralClient | None = None) -> None:
        self._client = client or MistralClient(settings)
        self._model = settings.mistral_tts_model
        self._timeout = settings.tts_timeout_seconds
        self._voice_ids = _parse_voices(settings.mistral_tts_voices)

    def capabilities(self) -> TtsCapabilities:
        return TtsCapabilities(
            tuple(
                Voice(voice_id, code, LANGUAGES[code].default_locale)
                for code, voice_id in self._voice_ids.items()
                if code in _SPOKEN and code in LANGUAGES
            )
        )

    async def synthesize(self, request: TtsRequest) -> TtsResult:
        configured = self._voice_ids.get(request.voice.language)
        if not configured:
            # capabilities() only advertises languages with a configured voice, so this should be unreachable in
            # normal use; refuse rather than send Mistral a request it will reject anyway.
            raise ProviderError("No voice is configured for this language.", provider=self.name)
        payload = {"model": self._model, "input": request.text, "response_format": "wav", "voice_id": configured}
        body = await self._client.post("/v1/audio/speech", timeout=self._timeout, json=payload)
        encoded = body.get("audio_data")
        if not isinstance(encoded, str) or not encoded:
            raise ProviderError("The provider returned no audio.", provider=self.name)
        try:
            audio = base64.b64decode(encoded, validate=True)
        except (binascii.Error, ValueError):
            raise ProviderError("The provider returned unreadable audio.", provider=self.name) from None
        return TtsResult(audio, "audio/wav")


def _parse_voices(raw: str) -> dict[str, str]:
    if not raw.strip():
        return {}
    try:
        voices = json.loads(raw)
    except ValueError:
        raise ConfigurationError("ALTERLINGUA_MISTRAL_TTS_VOICES must be JSON such as {\"fr\": \"voice-id\"}.") from None
    if not isinstance(voices, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in voices.items()):
        raise ConfigurationError("ALTERLINGUA_MISTRAL_TTS_VOICES must map language codes to voice id strings.")
    return voices
