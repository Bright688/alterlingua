"""Composes speech-to-text providers in order: if the first one fails, or hears nothing, the next one is tried.

Unclear or noisy audio is exactly where one engine can return an empty transcript while another still makes out the words,
so "heard nothing" moves on to the next engine just like an outage does. If every engine that answered heard nothing, that
empty answer is returned (and reported to the user as "no speech recognised"); if every engine failed, the last failure is
raised. Each engine has its own share of the overall timeout (see the providers), so a slow first engine leaves time for the
second. A provider with no credentials configured is left out by the registry before this class sees it.
"""

import logging

from app.core.errors import ConfigurationError, ProviderError, ProviderTimeoutError, ProviderUnavailableError
from app.core.logging import kv
from app.speech.provider import SpeechCapabilities, SpeechRequest, SpeechResult, SpeechToTextProvider

logger = logging.getLogger(__name__)

# Runtime failures worth trying the next engine for.
_SKIPPABLE = (ProviderError, ProviderUnavailableError, ProviderTimeoutError)


class FallbackSpeechToTextProvider(SpeechToTextProvider):
    name = "fallback"

    def __init__(self, providers: list[SpeechToTextProvider]) -> None:
        if not providers:
            raise ConfigurationError("The fallback speech provider needs at least one configured provider to fall back through.")
        self._providers = providers
        self.name = f"fallback({'>'.join(p.name for p in providers)})"

    def capabilities(self) -> SpeechCapabilities:
        all_caps = [p.capabilities() for p in self._providers]
        return SpeechCapabilities(
            languages=frozenset().union(*(c.languages for c in all_caps)),
            auto_detect=any(c.auto_detect for c in all_caps),
        )

    async def transcribe(self, request: SpeechRequest) -> SpeechResult:
        last_error: Exception | None = None
        heard_nothing: SpeechResult | None = None
        for provider in self._providers:
            if not provider.capabilities().supports(request.language):
                continue
            try:
                result = await provider.transcribe(request)
            except _SKIPPABLE as exc:
                last_error = exc
                logger.warning("speech_provider_fallback %s", kv(failed_provider=provider.name, error=type(exc).__name__))
                continue
            if result.transcript.strip():
                return result
            heard_nothing = result
            logger.warning("speech_provider_fallback %s", kv(failed_provider=provider.name, error="heard_nothing"))
        if heard_nothing is not None:
            return heard_nothing
        if last_error is not None:
            raise last_error
        raise ProviderError("No configured speech provider can recognise this language.")
