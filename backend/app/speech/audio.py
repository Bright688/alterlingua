"""Receiving an audio upload safely: check what it is, limit its size, keep it only briefly, always delete it.

PRIVACY (CLAUDE.md sections 20 and 25): audio exists only in a private temporary file (readable by this process only)
while it is processed. It is deleted in every case: success, error, timeout or crash.
"""

import os
import tempfile
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from app.core.errors import AudioTooLargeError, InvalidAudioError, UnsupportedAudioTypeError

# Audio families we accept, with the content types a client may declare for each.
_FAMILY_TYPES: dict[str, frozenset[str]] = {
    "wav": frozenset({"audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"}),
    "mp3": frozenset({"audio/mpeg", "audio/mp3"}),
    "mp4": frozenset({"audio/mp4", "audio/m4a", "audio/x-m4a", "audio/3gpp", "audio/3gpp2"}),
    "aac": frozenset({"audio/aac", "audio/x-aac", "audio/aacp"}),
    "ogg": frozenset({"audio/ogg", "audio/opus", "application/ogg"}),
    "webm": frozenset({"audio/webm"}),
    "flac": frozenset({"audio/flac", "audio/x-flac"}),
    "amr": frozenset({"audio/amr", "audio/amr-wb"}),
}
ALLOWED_AUDIO_TYPES: frozenset[str] = frozenset().union(*_FAMILY_TYPES.values())

_SUFFIX = {"wav": ".wav", "mp3": ".mp3", "mp4": ".m4a", "aac": ".aac", "ogg": ".ogg", "webm": ".webm", "flac": ".flac", "amr": ".amr"}
_MIN_BYTES = 12
_CHUNK = 64 * 1024


def base_content_type(declared: str | None) -> str:
    """"Audio/OGG; codecs=opus" -> "audio/ogg"."""
    return (declared or "").split(";", 1)[0].strip().lower()


def sniff_family(head: bytes) -> str | None:
    """Recognises the audio family from the first bytes of the file, whatever the client claimed."""
    if head[:4] == b"RIFF" and head[8:12] == b"WAVE":
        return "wav"
    if head[:4] == b"OggS":
        return "ogg"
    if head[:4] == b"fLaC":
        return "flac"
    if head[:4] == b"\x1a\x45\xdf\xa3":
        return "webm"
    if head[4:8] == b"ftyp":
        return "mp4"
    if head[:5] == b"#!AMR":
        return "amr"
    if head[:3] == b"ID3":
        return "mp3"
    if len(head) >= 2 and head[0] == 0xFF:
        if head[1] & 0xF6 == 0xF0:  # ADTS frame header (layer bits 00)
            return "aac"
        if head[1] & 0xE0 == 0xE0 and head[1] & 0x06 != 0:  # MPEG audio frame sync
            return "mp3"
    return None


def check_declared_type(declared: str | None) -> str:
    """The declared type without parameters, or a 415 if it is not an accepted audio type."""
    content_type = base_content_type(declared)
    if content_type not in ALLOWED_AUDIO_TYPES:
        raise UnsupportedAudioTypeError(
            "The file is not a supported audio type.",
            received=content_type or None,
            supported=sorted(ALLOWED_AUDIO_TYPES),
        )
    return content_type


class Upload(Protocol):
    async def read(self, size: int = ...) -> bytes: ...


@dataclass(frozen=True)
class TemporaryAudio:
    path: Path
    content_type: str
    size: int


@asynccontextmanager
async def temporary_audio(upload: Upload, declared_type: str | None, *, max_bytes: int, directory: str = "") -> AsyncIterator[TemporaryAudio]:
    """Writes the upload to a private temporary file, checking type and size, and deletes it when the block ends.

    Raises 415 for a wrong type (also when the bytes do not match the declared type), 413 for too large, 422 for empty.
    """
    content_type = check_declared_type(declared_type)
    fd, name = tempfile.mkstemp(prefix="alterlingua-", suffix=".audio", dir=directory or None)  # mode 0600
    path = Path(name)
    try:
        size = 0
        head = b""
        with os.fdopen(fd, "wb") as out:
            while True:
                chunk = await upload.read(_CHUNK)
                if not chunk:
                    break
                size += len(chunk)
                if size > max_bytes:
                    raise AudioTooLargeError(f"The audio is larger than {max_bytes} bytes.", max_bytes=max_bytes)
                if len(head) < 16:
                    head += chunk[: 16 - len(head)]
                out.write(chunk)
        if size == 0:
            raise InvalidAudioError("The audio file is empty.")
        family = sniff_family(head) if size >= _MIN_BYTES else None
        if family is None or content_type not in _FAMILY_TYPES[family]:
            raise UnsupportedAudioTypeError(
                "The file's contents do not match a supported audio format of the declared type.", received=content_type
            )
        final = path.with_suffix(_SUFFIX[family])
        path.rename(final)
        path = final
        yield TemporaryAudio(path=path, content_type=content_type, size=size)
    finally:
        for candidate in {path, Path(name)}:
            candidate.unlink(missing_ok=True)
