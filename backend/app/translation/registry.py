"""Chooses the provider named in configuration. Add a real provider by registering its factory here."""

from collections.abc import Callable

from app.core.config import Settings
from app.core.errors import ConfigurationError
from app.translation.cloudflare_provider import CloudflareTranslationProvider
from app.translation.fake_provider import FakeTranslationProvider
from app.translation.fallback_provider import FallbackTranslationProvider
from app.translation.groq_provider import GroqTranslationProvider
from app.translation.mistral_provider import MistralTranslationProvider
from app.translation.provider import TranslationProvider


def _fallback(settings: Settings) -> TranslationProvider:
    """Groq first, then a second, smaller Groq model (Groq's free daily allowance is per model), then Cloudflare Workers AI.
    A leg with no key (or account id) configured is left out rather than failing the whole chain. Mistral was the fallback leg here until 2026-09-24, when its account's translation
    models turned out to be stuck at a 0 requests/minute limit on Mistral's side (not a key or code problem — see
    docs/build-log.md); "mistral" is still available as a standalone provider, just not in this chain."""
    legs: list[TranslationProvider] = []
    factories: list[Callable[[], TranslationProvider]] = [lambda: GroqTranslationProvider(settings)]
    second_model = settings.groq_translation_fallback_model.strip()
    if second_model and second_model != settings.groq_translation_model:
        # Groq's free allowance is per model, so a second, smaller model keeps translation going when the first has used its own.
        factories.append(lambda: GroqTranslationProvider(settings, model=second_model))
    factories.append(lambda: CloudflareTranslationProvider(settings))
    for factory in factories:
        try:
            legs.append(factory())
        except ConfigurationError:
            continue
    return FallbackTranslationProvider(legs)


# A real provider reads its own credentials from environment variables in its factory (never from code).
PROVIDERS: dict[str, Callable[[Settings], TranslationProvider]] = {
    "fake": lambda settings: FakeTranslationProvider(),
    "mistral": lambda settings: MistralTranslationProvider(settings),
    "groq": lambda settings: GroqTranslationProvider(settings),
    "cloudflare": lambda settings: CloudflareTranslationProvider(settings),
    "fallback": _fallback,
}


def create_provider(settings: Settings) -> TranslationProvider:
    try:
        return PROVIDERS[settings.translation_provider.lower()](settings)
    except KeyError:
        raise ConfigurationError(
            f"Unknown translation provider {settings.translation_provider!r}. Available: {', '.join(sorted(PROVIDERS))}."
        ) from None
