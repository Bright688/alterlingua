"""Chooses the provider named in configuration. Add a real provider by registering its factory here."""

from collections.abc import Callable

from app.core.config import Settings
from app.core.errors import ConfigurationError
from app.translation.fake_provider import FakeTranslationProvider
from app.translation.fallback_provider import FallbackTranslationProvider
from app.translation.groq_provider import GroqTranslationProvider
from app.translation.mistral_provider import MistralTranslationProvider
from app.translation.provider import TranslationProvider


def _fallback(settings: Settings) -> TranslationProvider:
    """Groq first, then Mistral. A leg with no key configured is left out rather than failing the whole chain."""
    legs: list[TranslationProvider] = []
    for factory in (GroqTranslationProvider, MistralTranslationProvider):
        try:
            legs.append(factory(settings))
        except ConfigurationError:
            continue
    return FallbackTranslationProvider(legs)


# A real provider reads its own credentials from environment variables in its factory (never from code).
PROVIDERS: dict[str, Callable[[Settings], TranslationProvider]] = {
    "fake": lambda settings: FakeTranslationProvider(),
    "mistral": lambda settings: MistralTranslationProvider(settings),
    "groq": lambda settings: GroqTranslationProvider(settings),
    "fallback": _fallback,
}


def create_provider(settings: Settings) -> TranslationProvider:
    try:
        return PROVIDERS[settings.translation_provider.lower()](settings)
    except KeyError:
        raise ConfigurationError(
            f"Unknown translation provider {settings.translation_provider!r}. Available: {', '.join(sorted(PROVIDERS))}."
        ) from None
