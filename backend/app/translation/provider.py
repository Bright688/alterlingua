"""The translation-provider abstraction (CLAUDE.md 6.12).

The rest of the app talks only to ``TranslationProvider``. A concrete provider (a cloud API, a local model...) is
chosen by configuration and can be replaced without touching the service or the route. Providers must never log text.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass


@dataclass(frozen=True)
class ProviderCapabilities:
    """What a provider can do. Product-level support lives in the language catalogue; this is the provider's side."""

    languages: frozenset[str]
    auto_detect: bool

    def supports_pair(self, source: str | None, target: str) -> bool:
        """``source`` is None when the provider must detect it."""
        if target not in self.languages or source == target:
            return False
        if source is None:
            return self.auto_detect
        return source in self.languages


@dataclass(frozen=True)
class ProviderRequest:
    text: str
    source: str | None  # None means "detect it"
    target: str
    context: str
    tone: str


@dataclass(frozen=True)
class ProviderResult:
    translation: str
    detected_source: str | None = None  # required when the request's source was None


class TranslationProvider(ABC):
    name: str

    @abstractmethod
    def capabilities(self) -> ProviderCapabilities: ...

    @abstractmethod
    async def translate(self, request: ProviderRequest) -> ProviderResult:
        """Translates. May raise ``ProviderError``, ``ProviderUnavailableError`` or ``SourceLanguageUndetectedError``."""
