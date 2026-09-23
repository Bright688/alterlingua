"""Composes translation providers in order: if the first one fails, the next one is tried.

This is how AlterLingua uses Groq as the primary translation provider with Mistral as a fallback (CLAUDE.md 6.12: a
requested language pair that no configured provider supports must fail in a controlled way, never silently in the
wrong language). Each attempt still goes through the outer timeout in ``TranslationService``, so a slow primary that
uses the whole budget can leave no time for the fallback leg; raise ``ALTERLINGUA_PROVIDER_TIMEOUT_SECONDS`` if that
matters more than a fast failure. A leg with no credentials configured is left out by the registry before this class
ever sees it (see ``app/translation/registry.py``), so ``translate`` only ever has to skip real runtime failures.
"""

import logging

from app.core.errors import ConfigurationError, ProviderError, ProviderTimeoutError, ProviderUnavailableError
from app.core.logging import kv
from app.translation.provider import (
    ProviderCapabilities,
    ProviderRequest,
    ProviderResult,
    TranslationProvider,
)

logger = logging.getLogger(__name__)

# Runtime failures worth trying the next provider for: the provider is down, rejected, timed out, or answered with
# something unusable.
_SKIPPABLE = (ProviderError, ProviderUnavailableError, ProviderTimeoutError)


class FallbackTranslationProvider(TranslationProvider):
    name = "fallback"

    def __init__(self, providers: list[TranslationProvider]) -> None:
        if not providers:
            raise ConfigurationError("The fallback translation provider needs at least one configured provider to fall back through.")
        self._providers = providers
        self.name = f"fallback({'>'.join(p.name for p in providers)})"

    def capabilities(self) -> ProviderCapabilities:
        all_caps = [p.capabilities() for p in self._providers]
        languages = frozenset.intersection(*(c.languages for c in all_caps))
        auto_detect = all(c.auto_detect for c in all_caps)
        return ProviderCapabilities(languages=languages, auto_detect=auto_detect)

    async def translate(self, request: ProviderRequest) -> ProviderResult:
        last_error: Exception | None = None
        for provider in self._providers:
            try:
                result = await provider.translate(request)
            except _SKIPPABLE as exc:
                last_error = exc
                logger.warning(
                    "translation_provider_fallback %s",
                    kv(failed_provider=provider.name, error=type(exc).__name__),
                )
                continue
            return result
        assert last_error is not None  # __init__ guarantees at least one provider was tried
        raise last_error
