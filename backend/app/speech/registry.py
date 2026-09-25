"""Chooses the speech provider named in configuration. Add a real provider by registering its factory here."""

from collections.abc import Callable

from app.core.config import Settings
from app.core.errors import ConfigurationError
from app.speech.cloudflare_stt_provider import CloudflareSpeechToTextProvider
from app.speech.fake_provider import FakeSpeechToTextProvider
from app.speech.fallback_provider import FallbackSpeechToTextProvider
from app.speech.groq_stt_provider import GroqSpeechToTextProvider
from app.speech.fake_tts_provider import FakeTextToSpeechProvider
from app.speech.mistral_stt_provider import MistralSpeechToTextProvider
from app.speech.mistral_tts_provider import MistralTextToSpeechProvider
from app.speech.provider import SpeechToTextProvider
from app.speech.tts_provider import TextToSpeechProvider

def _fallback(settings: Settings) -> SpeechToTextProvider:
    """Groq's Whisper large-v3 first, then Cloudflare's Whisper large-v3-turbo. An engine with no credentials configured is
    left out rather than failing the whole chain. Mistral's Voxtral is still available as a standalone engine ("mistral")."""
    legs: list[SpeechToTextProvider] = []
    for factory in (GroqSpeechToTextProvider, CloudflareSpeechToTextProvider):
        try:
            legs.append(factory(settings))
        except ConfigurationError:
            continue
    return FallbackSpeechToTextProvider(legs)


# A real provider reads its own credentials from environment variables in its factory (never from code).
PROVIDERS: dict[str, Callable[[Settings], SpeechToTextProvider]] = {
    "fake": lambda settings: FakeSpeechToTextProvider(),
    "mistral": lambda settings: MistralSpeechToTextProvider(settings),
    "groq": lambda settings: GroqSpeechToTextProvider(settings),
    "cloudflare": lambda settings: CloudflareSpeechToTextProvider(settings),
    "fallback": _fallback,
}


def create_stt_provider(settings: Settings) -> SpeechToTextProvider:
    try:
        return PROVIDERS[settings.stt_provider.lower()](settings)
    except KeyError:
        raise ConfigurationError(
            f"Unknown speech provider {settings.stt_provider!r}. Available: {', '.join(sorted(PROVIDERS))}."
        ) from None


TTS_PROVIDERS: dict[str, Callable[[Settings], TextToSpeechProvider]] = {
    "fake": lambda settings: FakeTextToSpeechProvider(),
    "mistral": lambda settings: MistralTextToSpeechProvider(settings),
}


def create_tts_provider(settings: Settings) -> TextToSpeechProvider:
    try:
        return TTS_PROVIDERS[settings.tts_provider.lower()](settings)
    except KeyError:
        raise ConfigurationError(
            f"Unknown text-to-speech provider {settings.tts_provider!r}. Available: {', '.join(sorted(TTS_PROVIDERS))}."
        ) from None
