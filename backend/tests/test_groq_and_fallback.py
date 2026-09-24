"""The Groq translation provider (tested against a fake HTTP server, no network and no key) and the Groq-then-Mistral
fallback provider (tested with the SpyProvider test double from conftest)."""

import json

import httpx
import pytest

from app.core.config import Settings
from app.core.errors import (
    ConfigurationError,
    ProviderError,
    ProviderTimeoutError,
    ProviderUnavailableError,
    SourceLanguageUndetectedError,
)
from app.core.groq import GroqClient
from app.translation.fallback_provider import FallbackTranslationProvider
from app.translation.groq_provider import GroqTranslationProvider
from app.translation.provider import ProviderRequest, ProviderResult
from tests.conftest import SpyProvider

pytestmark = pytest.mark.anyio


@pytest.fixture
def anyio_backend():
    return "asyncio"


KEY = "test-groq-key-not-real"


def settings(**overrides) -> Settings:
    return Settings(_env_file=None, groq_api_key=KEY, **overrides)


def client_with(handler) -> GroqClient:
    return GroqClient(settings(), transport=httpx.MockTransport(handler))


def chat_answer(translation="Tu viens demain ?", source="en"):
    return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({"translation": translation, "source_language": source})}}]})


# ---- the client -------------------------------------------------------------------------------------------------

def test_the_provider_refuses_to_start_without_a_key():
    with pytest.raises(ConfigurationError):
        GroqClient(Settings(_env_file=None))


@pytest.mark.parametrize(
    ("status", "error"),
    [(401, ProviderUnavailableError), (403, ProviderUnavailableError), (429, ProviderUnavailableError), (400, ProviderError), (500, ProviderError)],
)
async def test_http_failures_become_controlled_errors(status, error):
    client = client_with(lambda request: httpx.Response(status, text="secret body"))
    with pytest.raises(error):
        await client.post("/v1/chat/completions", timeout=5, json={"x": 1})


async def test_the_key_is_sent_only_as_a_bearer_header():
    seen = {}

    def handler(request):
        seen["auth"] = request.headers["authorization"]
        return httpx.Response(200, json={})

    await client_with(handler).post("/v1/x", timeout=1, json={"a": 1})
    assert seen["auth"] == f"Bearer {KEY}"


# ---- translation --------------------------------------------------------------------------------------------------

async def test_translation_request_shape_and_result():
    sent = {}

    def handler(request):
        sent.update(json.loads(request.content))
        sent["path"] = request.url.path
        return chat_answer("¿Vienes mañana?", "en")

    provider = GroqTranslationProvider(settings(), client=client_with(handler))
    result = await provider.translate(ProviderRequest("Are you coming tomorrow?", "en", "es", "messaging", "natural"))
    assert (result.translation, result.detected_source) == ("¿Vienes mañana?", "en")
    assert sent["path"] == "/openai/v1/chat/completions"
    assert sent["model"] == "openai/gpt-oss-120b"
    assert sent["response_format"] == {"type": "json_object"}


async def test_auto_detect_uses_the_detected_language():
    provider = GroqTranslationProvider(settings(), client=client_with(lambda r: chat_answer("Are you coming?", "FR-fr")))
    result = await provider.translate(ProviderRequest("Tu viens ?", None, "en", "messaging", "natural"))
    assert result.detected_source == "fr"


async def test_an_unsupported_or_missing_detected_language_is_a_controlled_error():
    provider = GroqTranslationProvider(settings(), client=client_with(lambda r: chat_answer("hi", "xx")))
    with pytest.raises(SourceLanguageUndetectedError):
        await provider.translate(ProviderRequest("hello", None, "fr", "messaging", "natural"))


def test_it_covers_the_eight_catalogue_languages_and_detection():
    caps = GroqTranslationProvider(settings(), client=client_with(lambda r: httpx.Response(200, json={}))).capabilities()
    assert caps.languages == frozenset({"en", "fr", "es", "de", "it", "nl", "zh", "ja"}) and caps.auto_detect


def test_the_registry_knows_the_groq_provider_and_asks_for_a_key():
    from app.translation.registry import create_provider

    with pytest.raises(ConfigurationError):
        create_provider(Settings(_env_file=None, translation_provider="groq"))
    assert create_provider(settings(translation_provider="groq")).name == "groq"


# ---- fallback -----------------------------------------------------------------------------------------------------

REQUEST = ProviderRequest("Are you coming tomorrow?", "en", "fr", "messaging", "natural")


def test_it_needs_at_least_one_provider():
    with pytest.raises(ConfigurationError):
        FallbackTranslationProvider([])


async def test_the_first_provider_is_used_when_it_succeeds():
    primary = SpyProvider(result=ProviderResult("Tu viens demain ?", "en"))
    secondary = SpyProvider()
    provider = FallbackTranslationProvider([primary, secondary])
    result = await provider.translate(REQUEST)
    assert result.translation == "Tu viens demain ?"
    assert len(primary.calls) == 1 and len(secondary.calls) == 0


@pytest.mark.parametrize("error", [ProviderError("x"), ProviderUnavailableError("x"), ProviderTimeoutError("x")])
async def test_it_falls_back_when_the_first_provider_fails(error):
    primary = SpyProvider(error=error)
    secondary = SpyProvider(result=ProviderResult("Tu viens demain ?", "en"))
    provider = FallbackTranslationProvider([primary, secondary])
    result = await provider.translate(REQUEST)
    assert result.translation == "Tu viens demain ?"
    assert len(primary.calls) == 1 and len(secondary.calls) == 1


async def test_it_raises_the_last_error_when_every_provider_fails():
    primary = SpyProvider(error=ProviderUnavailableError("primary down"))
    secondary = SpyProvider(error=ProviderError("secondary broken"))
    provider = FallbackTranslationProvider([primary, secondary])
    with pytest.raises(ProviderError, match="secondary broken"):
        await provider.translate(REQUEST)


async def test_source_language_undetected_is_not_swallowed_by_the_fallback():
    """A provider that could not detect the source is not a provider failure: do not silently retry another provider."""
    primary = SpyProvider(error=SourceLanguageUndetectedError("no idea"))
    secondary = SpyProvider(result=ProviderResult("x", "en"))
    provider = FallbackTranslationProvider([primary, secondary])
    with pytest.raises(SourceLanguageUndetectedError):
        await provider.translate(REQUEST)
    assert len(secondary.calls) == 0


def test_capabilities_are_the_intersection_of_every_leg():
    a = SpyProvider(languages=("en", "fr", "es"), auto_detect=True)
    b = SpyProvider(languages=("en", "fr", "de"), auto_detect=False)
    caps = FallbackTranslationProvider([a, b]).capabilities()
    assert caps.languages == frozenset({"en", "fr"})
    assert not caps.auto_detect


def test_the_registry_wires_groq_then_cloudflare_and_skips_a_leg_with_no_key():
    from app.translation.registry import create_provider

    both = create_provider(
        Settings(_env_file=None, translation_provider="fallback", groq_api_key=KEY, cloudflare_api_token="c", cloudflare_account_id="acct"),
    )
    assert both.name == "fallback(groq>cloudflare)"

    only_cloudflare = create_provider(
        Settings(_env_file=None, translation_provider="fallback", cloudflare_api_token="c", cloudflare_account_id="acct"),
    )
    assert only_cloudflare.name == "fallback(cloudflare)"

    # A key with no account id is the same as not configured: the leg is skipped, same as a missing key.
    with pytest.raises(ConfigurationError):
        create_provider(Settings(_env_file=None, translation_provider="fallback", cloudflare_api_token="c"))

    with pytest.raises(ConfigurationError):
        create_provider(Settings(_env_file=None, translation_provider="fallback"))
