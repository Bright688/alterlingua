"""The Cloudflare Workers AI translation provider, tested against a fake HTTP server (no network and no real key)."""

import json

import httpx
import pytest

from app.core.cloudflare import CloudflareClient
from app.core.config import Settings
from app.core.errors import (
    ConfigurationError,
    ProviderError,
    ProviderUnavailableError,
    SourceLanguageUndetectedError,
)
from app.translation.cloudflare_provider import CloudflareTranslationProvider
from app.translation.provider import ProviderRequest

pytestmark = pytest.mark.anyio


@pytest.fixture
def anyio_backend():
    return "asyncio"


KEY = "test-cloudflare-key-not-real"
ACCOUNT = "test-account-id"


def settings(**overrides) -> Settings:
    return Settings(_env_file=None, cloudflare_api_key=KEY, cloudflare_account_id=ACCOUNT, **overrides)


def client_with(handler) -> CloudflareClient:
    return CloudflareClient(settings(), transport=httpx.MockTransport(handler))


def chat_answer(translation="Tu viens demain ?", source="en"):
    return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({"translation": translation, "source_language": source})}}]})


# ---- the client -------------------------------------------------------------------------------------------------

def test_the_provider_refuses_to_start_without_a_key():
    with pytest.raises(ConfigurationError):
        CloudflareClient(Settings(_env_file=None, cloudflare_account_id=ACCOUNT))


def test_the_provider_refuses_to_start_without_an_account_id():
    with pytest.raises(ConfigurationError):
        CloudflareClient(Settings(_env_file=None, cloudflare_api_key=KEY))


@pytest.mark.parametrize(
    ("status", "error"),
    [(401, ProviderUnavailableError), (403, ProviderUnavailableError), (429, ProviderUnavailableError), (400, ProviderError), (500, ProviderError)],
)
async def test_http_failures_become_controlled_errors(status, error):
    client = client_with(lambda request: httpx.Response(status, text="secret body"))
    with pytest.raises(error):
        await client.post("/chat/completions", timeout=5, json={"x": 1})


async def test_the_key_is_sent_only_as_a_bearer_header():
    seen = {}

    def handler(request):
        seen["auth"] = request.headers["authorization"]
        return httpx.Response(200, json={})

    await client_with(handler).post("/x", timeout=1, json={"a": 1})
    assert seen["auth"] == f"Bearer {KEY}"


async def test_the_account_id_is_in_the_url_path_not_the_body_or_headers():
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        return httpx.Response(200, json={})

    await client_with(handler).post("/chat/completions", timeout=1, json={"a": 1})
    assert f"/accounts/{ACCOUNT}/ai/v1/chat/completions" in seen["url"]


# ---- translation --------------------------------------------------------------------------------------------------

async def test_translation_request_shape_and_result():
    sent = {}

    def handler(request):
        sent.update(json.loads(request.content))
        sent["path"] = request.url.path
        return chat_answer("¿Vienes mañana?", "en")

    provider = CloudflareTranslationProvider(settings(), client=client_with(handler))
    result = await provider.translate(ProviderRequest("Are you coming tomorrow?", "en", "es", "messaging", "natural"))
    assert (result.translation, result.detected_source) == ("¿Vienes mañana?", "en")
    assert sent["path"].endswith("/chat/completions")
    assert sent["model"] == "@cf/qwen/qwen3-30b-a3b-fp8"
    assert sent["response_format"] == {"type": "json_object"}


async def test_auto_detect_uses_the_detected_language():
    provider = CloudflareTranslationProvider(settings(), client=client_with(lambda r: chat_answer("Are you coming?", "FR-fr")))
    result = await provider.translate(ProviderRequest("Tu viens ?", None, "en", "messaging", "natural"))
    assert result.detected_source == "fr"


async def test_an_unsupported_or_missing_detected_language_is_a_controlled_error():
    provider = CloudflareTranslationProvider(settings(), client=client_with(lambda r: chat_answer("hi", "xx")))
    with pytest.raises(SourceLanguageUndetectedError):
        await provider.translate(ProviderRequest("hello", None, "fr", "messaging", "natural"))


def test_it_covers_the_eight_catalogue_languages_and_detection():
    caps = CloudflareTranslationProvider(settings(), client=client_with(lambda r: httpx.Response(200, json={}))).capabilities()
    assert caps.languages == frozenset({"en", "fr", "es", "de", "it", "nl", "zh", "ja"}) and caps.auto_detect


def test_the_registry_knows_the_cloudflare_provider_and_asks_for_credentials():
    from app.translation.registry import create_provider

    with pytest.raises(ConfigurationError):
        create_provider(Settings(_env_file=None, translation_provider="cloudflare"))
    assert create_provider(settings(translation_provider="cloudflare")).name == "cloudflare"
