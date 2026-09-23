"""DEVELOPMENT-ONLY text-to-speech provider. It does NOT speak.

It returns a short valid WAV file of soft tones whose length follows the length of the text, so the whole pipeline (voice
selection, temporary files, sharing, playback) can be exercised without an API key. The tones do not depend on the words.
Replace it with a real provider through ``ALTERLINGUA_TTS_PROVIDER``.
"""

import io
import math
import struct
import wave

from app.speech.tts_provider import TextToSpeechProvider, TtsCapabilities, TtsRequest, TtsResult, Voice
from app.translation.languages import LANGUAGES

SAMPLE_RATE = 16_000


class FakeTextToSpeechProvider(TextToSpeechProvider):
    name = "fake"

    def capabilities(self) -> TtsCapabilities:
        return TtsCapabilities(tuple(Voice(f"fake-{lang.code}", lang.code, lang.default_locale) for lang in LANGUAGES.values()))

    async def synthesize(self, request: TtsRequest) -> TtsResult:
        # Roughly 0.06 s per character, between half a second and eight seconds.
        seconds = min(8.0, max(0.5, len(request.text) * 0.06))
        frames = int(SAMPLE_RATE * seconds)
        pitch = 180 + (sum(request.voice.language.encode()) % 80)
        samples = (int(3000 * math.sin(2 * math.pi * pitch * i / SAMPLE_RATE) * (1 - abs((i / frames) * 2 - 1))) for i in range(frames))
        buffer = io.BytesIO()
        with wave.open(buffer, "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(SAMPLE_RATE)
            wav.writeframes(b"".join(struct.pack("<h", s) for s in samples))
        return TtsResult(buffer.getvalue(), "audio/wav")
