"""Speech-to-text with Mistral's Voxtral (https://docs.mistral.ai/api/endpoint/audio/transcriptions).

The audio file is uploaded once and is not kept by this code (the caller deletes its temporary copy right after). Neither the
audio nor the transcript is logged. Voxtral covers 13 languages; those in the catalogue are English, French, Spanish, German,
Italian, Dutch, Chinese and Japanese, which are all covered.
"""

from app.core.config import Settings
from app.core.errors import ProviderError
from app.core.mistral import MistralClient
from app.speech.provider import SpeechCapabilities, SpeechRequest, SpeechResult, SpeechToTextProvider
from app.translation.languages import LANGUAGES

_VOXTRAL_LANGUAGES = frozenset({"en", "fr", "es", "de", "it", "nl", "zh", "ja"}) & frozenset(LANGUAGES)


class MistralSpeechToTextProvider(SpeechToTextProvider):
    name = "mistral"

    def __init__(self, settings: Settings, *, client: MistralClient | None = None) -> None:
        self._client = client or MistralClient(settings)
        self._model = settings.mistral_stt_model
        self._timeout = settings.stt_timeout_seconds

    def capabilities(self) -> SpeechCapabilities:
        return SpeechCapabilities(languages=_VOXTRAL_LANGUAGES, auto_detect=True)

    async def transcribe(self, request: SpeechRequest) -> SpeechResult:
        data = {"model": self._model}
        if request.language:
            data["language"] = request.language
        with request.audio_path.open("rb") as audio:
            body = await self._client.post(
                "/v1/audio/transcriptions",
                timeout=self._timeout,
                data=data,
                files={"file": ("audio", audio, request.content_type)},
            )
        text = body.get("text")
        if not isinstance(text, str):
            raise ProviderError("The provider returned an unusable transcript.", provider=self.name)
        language = body.get("language")
        detected = language.strip().lower()[:2] if isinstance(language, str) and language.strip() else None
        return SpeechResult(text, detected)
