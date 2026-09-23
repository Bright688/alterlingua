"""DEVELOPMENT-ONLY speech provider. It does NOT recognise speech.

It lets the whole pipeline (upload, validation, temporary files, translation, Android integration) be exercised without
an API key. It ignores the audio and "hears" one sample sentence: in the language the caller named, or English when the
caller asked for automatic detection. Replace it with a real provider through ``ALTERLINGUA_STT_PROVIDER``.
"""

from app.speech.provider import SpeechCapabilities, SpeechRequest, SpeechResult, SpeechToTextProvider
from app.translation.fake_provider import SAMPLE_PHRASES
from app.translation.languages import LANGUAGES


class FakeSpeechToTextProvider(SpeechToTextProvider):
    name = "fake"

    def capabilities(self) -> SpeechCapabilities:
        return SpeechCapabilities(languages=frozenset(LANGUAGES), auto_detect=True)

    async def transcribe(self, request: SpeechRequest) -> SpeechResult:
        language = request.language or "en"
        return SpeechResult(transcript=SAMPLE_PHRASES[language], detected_language=language)
