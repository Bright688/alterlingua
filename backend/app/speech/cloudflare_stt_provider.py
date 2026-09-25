"""Speech-to-text with Whisper large-v3-turbo on Cloudflare Workers AI (https://developers.cloudflare.com/workers-ai/models/whisper-large-v3-turbo/).

The same family of open model as the Groq engine, served from a different company's infrastructure, so it is a genuine
fallback when Groq is down or out of quota. The audio is sent once, as base64 in the request body, and is not kept by this
code. Neither the audio nor the transcript is logged.
"""

import base64

from app.core.cloudflare import CloudflareClient
from app.core.config import Settings
from app.core.errors import ProviderError
from app.speech.provider import SpeechCapabilities, SpeechRequest, SpeechResult, SpeechToTextProvider
from app.speech.whisper_languages import to_code
from app.translation.languages import LANGUAGES


class CloudflareSpeechToTextProvider(SpeechToTextProvider):
    name = "cloudflare"

    def __init__(self, settings: Settings, *, client: CloudflareClient | None = None) -> None:
        self._client = client or CloudflareClient(settings)
        self._model = settings.cloudflare_stt_model
        self._timeout = min(settings.stt_timeout_seconds, 25.0)

    def capabilities(self) -> SpeechCapabilities:
        return SpeechCapabilities(languages=frozenset(LANGUAGES), auto_detect=True)

    async def transcribe(self, request: SpeechRequest) -> SpeechResult:
        payload: dict = {"audio": base64.b64encode(request.audio_path.read_bytes()).decode("ascii"), "task": "transcribe"}
        if request.language:
            payload["language"] = request.language
        result = await self._client.run(self._model, timeout=self._timeout, json=payload)
        text = result.get("text")
        if not isinstance(text, str):
            raise ProviderError("The provider returned an unusable transcript.", provider=self.name)
        info = result.get("transcription_info")
        return SpeechResult(text, to_code(info.get("language")) if isinstance(info, dict) else None)
