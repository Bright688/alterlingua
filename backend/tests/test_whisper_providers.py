"""The Whisper speech engines (Groq large-v3, Cloudflare large-v3-turbo) and their fallback chain, tested against fake HTTP
servers (no network and no real keys)."""

import base64
import json

import httpx
import pytest

from app.core.cloudflare import CloudflareClient
from app.core.config import Settings
from app.core.errors import ConfigurationError, ProviderError, ProviderTimeoutError, ProviderUnavailableError
from app.core.groq import GroqClient
from app.speech.cloudflare_stt_provider import CloudflareSpeechToTextProvider
from app.speech.fallback_provider import FallbackSpeechToTextProvider
from app.speech.groq_stt_provider import GroqSpeechToTextProvider
from app.speech.provider import SpeechCapabilities, SpeechRequest, SpeechResult, SpeechToTextProvider
from app.speech.registry import create_stt_provider
from app.speech.whisper_languages import to_code
from tests.audio_helpers import StubSpeech

pytestmark = pytest.mark.anyio


@pytest.fixture
def anyio_backend():
    return "asyncio"


GROQ_KEY = "test-groq-key-not-real"
CF_TOKEN = "test-cloudflare-key-not-real"
MISTRAL_KEY = "test-mistral-key-not-real"
ACCOUNT = "test-account-id"


def settings(**overrides) -> Settings:
    return Settings(_env_file=None, groq_api_key=GROQ_KEY, cloudflare_api_token=CF_TOKEN, cloudflare_account_id=ACCOUNT, **overrides)


def audio_file(tmp_path, name="clip.ogg", content=b"OggSfakeopusaudio"):
    path = tmp_path / name
    path.write_bytes(content)
    return path


# ---- language names -----------------------------------------------------------------------------------------------

@pytest.mark.parametrize(
    ("reported", "code"),
    [("french", "fr"), ("French", "fr"), (" japanese ", "ja"), ("fr", "fr"), ("fr-FR", "fr"), ("zh_CN", "zh"),
     ("portuguese", "pt"), ("cantonese", "zh"), ("haitian creole", "ht")],
)
def test_reported_languages_become_codes(reported, code):
    assert to_code(reported) == code


@pytest.mark.parametrize("reported", [None, "", "  ", "klingon", 42])
def test_an_unusable_reported_language_is_none(reported):
    assert to_code(reported) is None


# ---- Groq Whisper large-v3 ---------------------------------------------------------------------------------------

def groq_with(handler) -> GroqClient:
    return GroqClient(settings(), transport=httpx.MockTransport(handler))


async def test_groq_sends_an_ogg_upload_with_the_model_language_and_temperature_zero(tmp_path):
    seen = {}

    def handler(request):
        seen["path"] = request.url.path
        seen["body"] = request.content
        seen["auth"] = request.headers["authorization"]
        return httpx.Response(200, json={"text": "Bonjour tout le monde", "language": "french"})

    provider = GroqSpeechToTextProvider(settings(), client=groq_with(handler))
    result = await provider.transcribe(SpeechRequest(audio_file(tmp_path), "audio/ogg", "fr"))
    assert (result.transcript, result.detected_language) == ("Bonjour tout le monde", "fr")
    assert seen["path"] == "/openai/v1/audio/transcriptions" or seen["path"].endswith("/v1/audio/transcriptions")
    body = seen["body"]
    assert b"whisper-large-v3" in body and b'name="language"' in body and b"verbose_json" in body
    assert b'name="temperature"' in body and b"OggSfakeopusaudio" in body
    assert b'filename="audio.ogg"' in body, "Groq decodes by file name, so an Ogg voice note must be named .ogg"
    assert seen["auth"] == f"Bearer {GROQ_KEY}"


@pytest.mark.parametrize(
    ("content_type", "extension"),
    [("audio/wav", "wav"), ("audio/mpeg", "mp3"), ("audio/mp4", "m4a"), ("audio/opus", "ogg"), ("audio/webm", "webm"), ("audio/flac", "flac")],
)
async def test_groq_names_the_upload_after_its_real_format(tmp_path, content_type, extension):
    seen = {}

    def handler(request):
        seen["body"] = request.content
        return httpx.Response(200, json={"text": "x", "language": "english"})

    provider = GroqSpeechToTextProvider(settings(), client=groq_with(handler))
    await provider.transcribe(SpeechRequest(audio_file(tmp_path), content_type, None))
    assert f'filename="audio.{extension}"'.encode() in seen["body"]


async def test_groq_sends_no_language_for_auto_detection_and_returns_the_detected_one(tmp_path):
    seen = {}

    def handler(request):
        seen["body"] = request.content
        return httpx.Response(200, json={"text": "hola", "language": "spanish"})

    provider = GroqSpeechToTextProvider(settings(), client=groq_with(handler))
    result = await provider.transcribe(SpeechRequest(audio_file(tmp_path), "audio/ogg", None))
    assert b'name="language"' not in seen["body"]
    assert result.detected_language == "es"


async def test_groq_refuses_a_format_it_cannot_decode_so_the_next_engine_can_try(tmp_path):
    provider = GroqSpeechToTextProvider(settings(), client=groq_with(lambda r: httpx.Response(200, json={"text": "x"})))
    with pytest.raises(ProviderError):
        await provider.transcribe(SpeechRequest(audio_file(tmp_path), "audio/amr", None))


async def test_groq_answer_without_text_is_a_provider_error(tmp_path):
    provider = GroqSpeechToTextProvider(settings(), client=groq_with(lambda r: httpx.Response(200, json={"language": "english"})))
    with pytest.raises(ProviderError):
        await provider.transcribe(SpeechRequest(audio_file(tmp_path), "audio/ogg", "en"))


@pytest.mark.parametrize(("status", "error"), [(401, ProviderUnavailableError), (429, ProviderUnavailableError), (500, ProviderError)])
async def test_groq_failures_become_controlled_errors_that_never_echo_the_audio(tmp_path, status, error):
    provider = GroqSpeechToTextProvider(settings(), client=groq_with(lambda r: httpx.Response(status, text="OggSfakeopusaudio secret")))
    with pytest.raises(error) as caught:
        await provider.transcribe(SpeechRequest(audio_file(tmp_path), "audio/ogg", None))
    assert "secret" not in str(caught.value) and "OggS" not in str(caught.value)


def test_groq_covers_the_eight_catalogue_languages_and_detection():
    caps = GroqSpeechToTextProvider(settings(), client=groq_with(lambda r: httpx.Response(200, json={}))).capabilities()
    assert caps.languages >= frozenset({"en", "fr", "es", "de", "it", "nl", "zh", "ja"}) and caps.auto_detect


def test_groq_needs_its_key():
    with pytest.raises(ConfigurationError):
        GroqSpeechToTextProvider(Settings(_env_file=None))


# ---- Cloudflare Whisper large-v3-turbo ---------------------------------------------------------------------------

def cloudflare_with(handler) -> CloudflareClient:
    return CloudflareClient(settings(), transport=httpx.MockTransport(handler))


async def test_cloudflare_runs_the_whisper_model_on_the_native_endpoint_with_base64_audio(tmp_path):
    seen = {}

    def handler(request):
        seen["path"] = request.url.path
        seen["json"] = json.loads(request.content)
        seen["auth"] = request.headers["authorization"]
        return httpx.Response(200, json={"success": True, "result": {"text": "Tu viens demain ?", "transcription_info": {"language": "fr"}}})

    provider = CloudflareSpeechToTextProvider(settings(), client=cloudflare_with(handler))
    audio = audio_file(tmp_path, content=b"OggSfakeopusaudio")
    result = await provider.transcribe(SpeechRequest(audio, "audio/ogg", "fr"))
    assert (result.transcript, result.detected_language) == ("Tu viens demain ?", "fr")
    assert seen["path"].endswith(f"/accounts/{ACCOUNT}/ai/run/@cf/openai/whisper-large-v3-turbo")
    assert base64.b64decode(seen["json"]["audio"]) == b"OggSfakeopusaudio"
    assert seen["json"]["language"] == "fr" and seen["json"]["task"] == "transcribe"
    assert seen["auth"] == f"Bearer {CF_TOKEN}"


async def test_cloudflare_sends_no_language_for_auto_detection(tmp_path):
    seen = {}

    def handler(request):
        seen["json"] = json.loads(request.content)
        return httpx.Response(200, json={"result": {"text": "hola", "transcription_info": {"language": "es"}}})

    provider = CloudflareSpeechToTextProvider(settings(), client=cloudflare_with(handler))
    result = await provider.transcribe(SpeechRequest(audio_file(tmp_path), "audio/ogg", None))
    assert "language" not in seen["json"] and result.detected_language == "es"


async def test_cloudflare_answer_without_a_result_or_text_is_a_provider_error(tmp_path):
    for body in ({"success": True}, {"result": {"transcription_info": {"language": "en"}}}):
        provider = CloudflareSpeechToTextProvider(settings(), client=cloudflare_with(lambda r, b=body: httpx.Response(200, json=b)))
        with pytest.raises(ProviderError):
            await provider.transcribe(SpeechRequest(audio_file(tmp_path), "audio/ogg", None))


async def test_the_existing_cloudflare_openai_endpoint_is_unchanged():
    seen = {}

    def handler(request):
        seen["path"] = request.url.path
        return httpx.Response(200, json={"ok": True})

    await cloudflare_with(handler).post("/chat/completions", timeout=5, json={"x": 1})
    assert seen["path"].endswith(f"/accounts/{ACCOUNT}/ai/v1/chat/completions")


# ---- the fallback chain ---------------------------------------------------------------------------------------------

class Scripted(SpeechToTextProvider):
    def __init__(self, name, outcome, languages=("en", "fr"), auto_detect=True):
        self.name = name
        self._outcome = outcome
        self._caps = SpeechCapabilities(frozenset(languages), auto_detect)
        self.calls = 0

    def capabilities(self):
        return self._caps

    async def transcribe(self, request):
        self.calls += 1
        if isinstance(self._outcome, Exception):
            raise self._outcome
        return self._outcome


def request(tmp_path, language=None):
    return SpeechRequest(audio_file(tmp_path), "audio/ogg", language)


async def test_the_first_engine_that_answers_wins_and_the_second_is_not_asked(tmp_path):
    first, second = Scripted("a", SpeechResult("bonjour", "fr")), Scripted("b", SpeechResult("other", "fr"))
    result = await FallbackSpeechToTextProvider([first, second]).transcribe(request(tmp_path))
    assert result.transcript == "bonjour" and second.calls == 0


@pytest.mark.parametrize("failure", [ProviderError("boom"), ProviderUnavailableError("down"), ProviderTimeoutError("slow")])
async def test_an_engine_that_fails_hands_over_to_the_next(tmp_path, failure):
    second = Scripted("b", SpeechResult("bonjour", "fr"))
    result = await FallbackSpeechToTextProvider([Scripted("a", failure), second]).transcribe(request(tmp_path))
    assert result.transcript == "bonjour" and second.calls == 1


async def test_an_engine_that_hears_nothing_hands_over_to_the_next(tmp_path):
    # Unclear audio: one engine returns an empty transcript while another can still make out the words.
    first, second = Scripted("a", SpeechResult("  ", "fr")), Scripted("b", SpeechResult("Tu viens demain ?", "fr"))
    result = await FallbackSpeechToTextProvider([first, second]).transcribe(request(tmp_path))
    assert result.transcript == "Tu viens demain ?" and first.calls == second.calls == 1


async def test_when_every_engine_hears_nothing_the_empty_answer_is_returned(tmp_path):
    result = await FallbackSpeechToTextProvider([Scripted("a", SpeechResult("", None)), Scripted("b", SpeechResult(" ", None))]).transcribe(request(tmp_path))
    assert result.transcript.strip() == ""


async def test_when_every_engine_fails_the_last_failure_is_raised(tmp_path):
    chain = FallbackSpeechToTextProvider([Scripted("a", ProviderError("first")), Scripted("b", ProviderUnavailableError("last"))])
    with pytest.raises(ProviderUnavailableError):
        await chain.transcribe(request(tmp_path))


async def test_a_failure_then_silence_reports_the_silence_not_the_failure(tmp_path):
    chain = FallbackSpeechToTextProvider([Scripted("a", ProviderError("down")), Scripted("b", SpeechResult("", None))])
    assert (await chain.transcribe(request(tmp_path))).transcript == ""


async def test_an_engine_that_does_not_support_the_language_is_skipped(tmp_path):
    only_english, both = Scripted("a", SpeechResult("x", "en"), languages=("en",)), Scripted("b", SpeechResult("bonjour", "fr"))
    result = await FallbackSpeechToTextProvider([only_english, both]).transcribe(request(tmp_path, language="fr"))
    assert result.transcript == "bonjour" and only_english.calls == 0


def test_capabilities_are_the_union_of_the_engines():
    chain = FallbackSpeechToTextProvider([Scripted("a", None, languages=("en",), auto_detect=False), Scripted("b", None, languages=("fr",), auto_detect=True)])
    caps = chain.capabilities()
    assert caps.languages == frozenset({"en", "fr"}) and caps.auto_detect


def test_the_chain_is_named_after_its_engines():
    assert FallbackSpeechToTextProvider([Scripted("groq", None), Scripted("cloudflare", None)]).name == "fallback(groq>cloudflare)"


def test_an_empty_chain_is_a_configuration_error():
    with pytest.raises(ConfigurationError):
        FallbackSpeechToTextProvider([])


# ---- the registry -----------------------------------------------------------------------------------------------------

def test_the_registry_builds_groq_then_cloudflare_for_fallback():
    assert create_stt_provider(settings(stt_provider="fallback")).name == "fallback(groq>cloudflare)"


def test_the_registry_leaves_out_an_engine_with_no_credentials():
    only_groq = Settings(_env_file=None, stt_provider="fallback", groq_api_key=GROQ_KEY)
    assert create_stt_provider(only_groq).name == "fallback(groq)"
    only_cloudflare = Settings(_env_file=None, stt_provider="fallback", cloudflare_api_token=CF_TOKEN, cloudflare_account_id=ACCOUNT)
    assert create_stt_provider(only_cloudflare).name == "fallback(cloudflare)"


def test_the_registry_refuses_a_fallback_with_no_credentials_at_all():
    with pytest.raises(ConfigurationError):
        create_stt_provider(Settings(_env_file=None, stt_provider="fallback"))


@pytest.mark.parametrize("name", ["groq", "cloudflare", "mistral"])
def test_the_registry_still_offers_each_engine_alone(name):
    provider = create_stt_provider(Settings(_env_file=None, stt_provider=name, groq_api_key=GROQ_KEY, cloudflare_api_token=CF_TOKEN,
                                            cloudflare_account_id=ACCOUNT, mistral_api_key=MISTRAL_KEY))
    assert provider.name == name


# ---- through the real route -----------------------------------------------------------------------------------------

def test_a_shared_voice_note_through_the_route_detects_the_language_via_the_chain():
    from app.translation.fake_provider import SAMPLE_PHRASES
    from tests.audio_helpers import make_wav
    from tests.conftest import make_client

    chain = FallbackSpeechToTextProvider([StubSpeech(detected="fr", transcripts={"fr": SAMPLE_PHRASES["fr"]})])
    response = make_client(stt=chain).post(
        "/v1/audio/translate", files={"audio": ("clip.wav", make_wav(), "audio/wav")}, data={"source": "auto", "target": "en"}
    )
    assert response.status_code == 200 and response.json()["source_language"] == "fr"
