"""Speech-to-text with Whisper large-v3 on Groq (https://console.groq.com/docs/speech-to-text).

Whisper large-v3 is OpenAI's open model, trained on a lot of noisy and accented speech, so it holds up on unclear voice
notes better than a small model, and it reports the language it heard. The audio file is uploaded once and is not kept by
this code (the caller deletes its temporary copy right after). Neither the audio nor the transcript is logged.
"""

from app.core.config import Settings
from app.core.errors import ProviderError
from app.core.groq import GroqClient
from app.speech.provider import SpeechCapabilities, SpeechRequest, SpeechResult, SpeechToTextProvider
from app.speech.whisper_languages import to_code
from app.translation.languages import LANGUAGES

# Groq decides how to decode an upload from its file name, so the name must carry the right extension.
_EXTENSIONS: dict[str, str] = {
    "audio/wav": "wav", "audio/x-wav": "wav", "audio/wave": "wav", "audio/vnd.wave": "wav",
    "audio/mpeg": "mp3", "audio/mp3": "mp3",
    "audio/mp4": "m4a", "audio/m4a": "m4a", "audio/x-m4a": "m4a", "audio/3gpp": "mp4", "audio/3gpp2": "mp4",
    "audio/ogg": "ogg", "audio/opus": "ogg", "application/ogg": "ogg",  # WhatsApp voice notes are Ogg Opus
    "audio/webm": "webm",
    "audio/flac": "flac", "audio/x-flac": "flac",
}


class GroqSpeechToTextProvider(SpeechToTextProvider):
    name = "groq"

    def __init__(self, settings: Settings, *, client: GroqClient | None = None) -> None:
        self._client = client or GroqClient(settings)
        self._model = settings.groq_stt_model
        # Each engine gets a share of the overall budget so a slow one leaves time for the next in a fallback chain.
        self._timeout = min(settings.stt_timeout_seconds, 25.0)

    def capabilities(self) -> SpeechCapabilities:
        # Whisper covers every language in the catalogue, and detects the spoken language.
        return SpeechCapabilities(languages=frozenset(LANGUAGES), auto_detect=True)

    async def transcribe(self, request: SpeechRequest) -> SpeechResult:
        extension = _EXTENSIONS.get(request.content_type)
        if extension is None:
            raise ProviderError("This audio format is not supported by the speech provider.", provider=self.name)
        data = {"model": self._model, "response_format": "verbose_json", "temperature": "0"}
        if request.language:
            data["language"] = request.language
        with request.audio_path.open("rb") as audio:
            body = await self._client.post(
                "/v1/audio/transcriptions",
                timeout=self._timeout,
                data=data,
                files={"file": (f"audio.{extension}", audio, request.content_type)},
            )
        text = body.get("text")
        if not isinstance(text, str):
            raise ProviderError("The provider returned an unusable transcript.", provider=self.name)
        return SpeechResult(text, to_code(body.get("language")))
