"""Small helpers for the audio tests: tiny valid audio files and a configurable speech provider."""

import asyncio
import io
import wave

from app.speech.provider import SpeechCapabilities, SpeechRequest, SpeechResult, SpeechToTextProvider


def make_wav(seconds: float = 0.1) -> bytes:
    """A real, silent, valid WAV file."""
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16_000)
        wav.writeframes(b"\x00\x00" * int(16_000 * seconds))
    return buffer.getvalue()


_PAD = b"\x00" * 64

# The first bytes that identify each accepted family, with a declared content type that goes with it.
SAMPLES: dict[str, tuple[bytes, str]] = {
    "wav": (make_wav(), "audio/wav"),
    "mp3": (b"ID3\x03\x00\x00\x00\x00\x00\x00" + _PAD, "audio/mpeg"),
    "mp3-frame": (b"\xff\xfb\x90\x00" + _PAD, "audio/mpeg"),
    "m4a": (b"\x00\x00\x00\x18ftypM4A \x00\x00\x00\x00M4A mp42" + _PAD, "audio/mp4"),
    "3gp": (b"\x00\x00\x00\x14ftyp3gp4\x00\x00\x00\x00" + _PAD, "audio/3gpp"),
    "ogg": (b"OggS\x00\x02" + _PAD, "audio/ogg"),
    "webm": (b"\x1a\x45\xdf\xa3" + _PAD, "audio/webm"),
    "flac": (b"fLaC" + _PAD, "audio/flac"),
    "aac": (b"\xff\xf1\x50\x80" + _PAD, "audio/aac"),
    "amr": (b"#!AMR\n" + _PAD, "audio/amr"),
}


class StubSpeech(SpeechToTextProvider):
    """A configurable stand-in that records every call, to prove what reaches a speech provider."""

    name = "stub"

    def __init__(self, languages=("en", "fr", "es", "ja"), auto_detect=True, transcripts=None, detected="en", error=None, delay=0.0):
        self._caps = SpeechCapabilities(frozenset(languages), auto_detect)
        self.transcripts = transcripts or {}
        self.detected = detected
        self.error = error
        self.delay = delay
        self.calls: list[SpeechRequest] = []
        self.file_existed_during_call: list[bool] = []
        self.paths = []

    def capabilities(self):
        return self._caps

    async def transcribe(self, request):
        self.calls.append(request)
        self.paths.append(request.audio_path)
        self.file_existed_during_call.append(request.audio_path.exists() and request.audio_path.stat().st_size > 0)
        if self.delay:
            await asyncio.sleep(self.delay)
        if self.error:
            raise self.error
        language = request.language or self.detected or "en"
        return SpeechResult(self.transcripts.get(language, f"transcript in {language}"), self.detected if request.language is None else request.language)
