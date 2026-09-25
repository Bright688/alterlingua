"""The speech-to-text provider abstraction.

Speech recognition support differs from translation support and between providers (CLAUDE.md 6.6, 6.17), so a provider
states what it can do and the service checks that before spending any effort. Providers must never log audio or transcripts.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SpeechCapabilities:
    """What a speech provider can do."""

    languages: frozenset[str]
    auto_detect: bool

    def supports(self, language: str | None) -> bool:
        """``language`` is None when the provider must detect it."""
        return self.auto_detect if language is None else language in self.languages


@dataclass(frozen=True)
class SpeechRequest:
    audio_path: Path  # a private temporary file, deleted by the caller right after
    content_type: str  # the validated audio type, for example "audio/wav"
    language: str | None  # None means "detect it"


@dataclass(frozen=True)
class SpeechResult:
    transcript: str
    detected_language: str | None = None  # required when the request's language was None
    # How sure the engine is, as the mean log-probability of the words it chose (0 is certain, more negative is less sure).
    # Only some engines report it; None when unknown. Used to choose between two attempts at the same recording.
    confidence: float | None = None


class SpeechToTextProvider(ABC):
    name: str

    @abstractmethod
    def capabilities(self) -> SpeechCapabilities: ...

    @abstractmethod
    async def transcribe(self, request: SpeechRequest) -> SpeechResult:
        """Recognises speech. May raise ``ProviderError``, ``ProviderUnavailableError`` or ``ProviderTimeoutError``."""
