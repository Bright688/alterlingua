"""The text-to-speech provider abstraction.

Which languages have a voice differs between providers and from translation and recognition coverage (CLAUDE.md 6.6, 6.18),
so a provider states its voices and the service checks that before any costly work. Several voices may exist per language,
so a voice can be chosen later. Providers must never log text or audio.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass(frozen=True)
class Voice:
    id: str
    language: str  # base language code, for example "es"
    locale: str  # for example "es-ES"
    gender: str | None = None


@dataclass(frozen=True)
class TtsCapabilities:
    """The voices a provider offers, by language."""

    voices: tuple[Voice, ...]

    def languages(self) -> frozenset[str]:
        return frozenset(v.language for v in self.voices)

    def supports(self, language: str) -> bool:
        return language in self.languages()

    def voices_for(self, language: str) -> tuple[Voice, ...]:
        return tuple(v for v in self.voices if v.language == language)

    def choose(self, language: str, voice_id: str | None = None) -> Voice | None:
        """The requested voice if it exists for the language, else the first voice for it; None if there is none."""
        options = self.voices_for(language)
        if voice_id is not None:
            return next((v for v in options if v.id == voice_id), None)
        return options[0] if options else None


@dataclass(frozen=True)
class TtsRequest:
    text: str
    voice: Voice


@dataclass(frozen=True)
class TtsResult:
    audio: bytes
    content_type: str  # for example "audio/wav"


class TextToSpeechProvider(ABC):
    name: str

    @abstractmethod
    def capabilities(self) -> TtsCapabilities: ...

    @abstractmethod
    async def synthesize(self, request: TtsRequest) -> TtsResult:
        """Speaks the text. May raise ``ProviderError``, ``ProviderUnavailableError`` or ``ProviderTimeoutError``."""
