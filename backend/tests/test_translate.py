import pytest

from tests.conftest import SpyProvider, make_client

PHRASE = "Are you coming tomorrow?"
EXPECTED = {
    "fr": "Tu viens demain ?",
    "es": "¿Vienes mañana?",
    "de": "Kommst du morgen?",
    "it": "Vieni domani?",
    "nl": "Kom je morgen?",
    "zh": "你明天来吗？",
    "ja": "明日来ますか？",
}


def post(client, **body):
    body.setdefault("text", PHRASE)
    body.setdefault("target", "es")
    return client.post("/v1/translate", json=body)


def test_the_documented_example(client):
    response = client.post(
        "/v1/translate",
        json={"text": PHRASE, "source": "auto", "target": "es", "context": "messaging", "tone": "natural"},
    )
    assert response.status_code == 200
    assert response.json() == {"translation": "¿Vienes mañana?", "source_language": "en", "target_language": "es"}


@pytest.mark.parametrize("target", EXPECTED)
def test_english_to_every_initial_language_uses_the_same_route(client, target):
    response = post(client, target=target)
    assert response.status_code == 200
    assert response.json() == {
        "translation": EXPECTED[target],
        "source_language": "en",
        "target_language": target,
    }


@pytest.mark.parametrize(
    ("source", "text", "target", "expected"),
    [
        ("es", "¿Vienes mañana?", "en", PHRASE),
        ("fr", "Tu viens demain ?", "en", PHRASE),
        ("ja", "明日来ますか？", "en", PHRASE),
        ("zh", "你明天来吗？", "fr", "Tu viens demain ?"),
        ("de", "Kommst du morgen?", "ja", "明日来ますか？"),
        ("nl", "Kom je morgen?", "it", "Vieni domani?"),
    ],
)
def test_non_english_sources_and_non_english_pairs(client, source, text, target, expected):
    response = post(client, text=text, source=source, target=target)
    assert response.status_code == 200
    assert response.json() == {"translation": expected, "source_language": source, "target_language": target}


@pytest.mark.parametrize("text", ["¿Vienes mañana?", "明日来ますか？", "你明天来吗？", "Kommst du morgen?"])
def test_auto_detection_reports_the_detected_language(client, text):
    body = post(client, text=text, source="auto", target="en").json()
    assert body["translation"] == PHRASE
    assert body["source_language"] == {"¿Vienes mañana?": "es", "明日来ますか？": "ja", "你明天来吗？": "zh", "Kommst du morgen?": "de"}[text]


def test_source_defaults_to_auto_and_context_and_tone_are_optional(client):
    response = client.post("/v1/translate", json={"text": PHRASE, "target": "fr"})
    assert response.status_code == 200
    assert response.json()["source_language"] == "en"


def test_locale_codes_are_reduced_to_the_language(client):
    response = post(client, source="EN-us", target="fr_FR")
    assert response.json() == {"translation": "Tu viens demain ?", "source_language": "en", "target_language": "fr"}


def test_text_already_in_the_target_language_is_returned_unchanged(client):
    response = post(client, text="Tu viens demain ?", source="auto", target="fr")
    assert response.status_code == 200
    assert response.json() == {"translation": "Tu viens demain ?", "source_language": "fr", "target_language": "fr"}


def test_undetectable_source_is_a_controlled_error(client):
    response = post(client, text="Some unknown latin text", source="auto", target="fr")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "source_language_undetected"


# ---- Unicode ----

def test_unicode_text_round_trips_in_utf8(client):
    response = post(client, text="日本語のテキスト 🙂 café", source="ja", target="zh")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/json")
    assert response.json()["translation"] == "[zh] 日本語のテキスト 🙂 café"
    assert "日本語のテキスト 🙂 café".encode("utf-8") in response.content  # real characters, not \u escapes


def test_decomposed_accents_are_normalised(client):
    decomposed = "Kommst du morgen?".replace("o", "ö", 1)  # o + combining diaeresis
    response = post(client, text=f"café {decomposed}", source="de", target="fr")
    assert response.json()["translation"].startswith("[fr] café ")


def test_length_is_counted_in_characters_not_bytes():
    client = make_client(max_text_chars=10)
    assert post(client, text="日本語" * 3, source="ja", target="en").status_code == 200  # 9 chars, 27 bytes
    too_long = post(client, text="日本語" * 4, source="ja", target="en")
    assert too_long.status_code == 422
    assert too_long.json()["error"]["code"] == "text_too_long"


# ---- unsupported languages ----

@pytest.mark.parametrize("field", ["target", "source"])
def test_unsupported_language_is_controlled_and_never_reaches_the_provider(spy, field):
    client = make_client(spy)
    response = post(client, **{field: "pt"})
    assert response.status_code == 422
    error = response.json()["error"]
    assert error["code"] == "unsupported_language"
    assert error["language"] == "pt"
    assert error["role"] == field
    assert "es" in error["supported"] and "ja" in error["supported"]
    assert spy.calls == []


def test_same_source_and_target_is_rejected(spy):
    response = post(make_client(spy), source="fr", target="fr", text="Bonjour")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "same_language"
    assert spy.calls == []


# ---- provider capabilities ----

def test_provider_that_lacks_the_target_language(spy):
    response = post(make_client(spy), target="ja")  # catalogue supports ja, the provider does not
    assert response.status_code == 422
    error = response.json()["error"]
    assert error["code"] == "unsupported_language"
    assert error["provider"] == "spy"
    assert spy.calls == []


def test_provider_that_lacks_the_source_language():
    provider = SpyProvider(languages=("en", "fr"))
    response = post(make_client(provider), source="de", target="fr")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "unsupported_language_pair"
    assert provider.calls == []


def test_provider_without_auto_detect_needs_an_explicit_source():
    provider = SpyProvider(auto_detect=False)
    client = make_client(provider)
    response = post(client, source="auto", target="fr")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "auto_detect_unavailable"
    assert post(client, source="en", target="fr").status_code == 200


def test_the_provider_receives_dynamic_codes_and_options():
    provider = SpyProvider(languages=("en", "de"))
    post(make_client(provider), source="auto", target="de", context="general", tone="formal")
    (call,) = provider.calls
    assert (call.source, call.target, call.context, call.tone) == (None, "de", "general", "formal")


# ---- provider failures ----

@pytest.mark.parametrize(
    ("error_name", "status", "code"),
    [("ProviderError", 502, "provider_error"), ("ProviderUnavailableError", 503, "provider_unavailable")],
)
def test_provider_failures_become_controlled_errors(error_name, status, code):
    from app.core import errors

    provider = SpyProvider(error=getattr(errors, error_name)("It broke."))
    response = post(make_client(provider))
    assert response.status_code == status
    assert response.json()["error"]["code"] == code


def test_slow_provider_times_out():
    provider = SpyProvider(delay=0.5)
    response = post(make_client(provider, provider_timeout_seconds=0.05))
    assert response.status_code == 504
    assert response.json()["error"]["code"] == "provider_timeout"


def test_empty_or_unlabelled_provider_answers_are_rejected():
    from app.translation.provider import ProviderResult

    assert post(make_client(SpyProvider(result=ProviderResult("  ", "en")))).status_code == 502
    assert post(make_client(SpyProvider(result=ProviderResult("hola", None)))).status_code == 502
    assert post(make_client(SpyProvider(result=ProviderResult("hola", "xx")))).status_code == 502


def test_unexpected_exception_gives_a_generic_500():
    provider = SpyProvider(error=RuntimeError(f"secret detail about {PHRASE}"))
    response = post(make_client(provider))
    assert response.status_code == 500
    assert response.json() == {"error": {"code": "internal_error", "message": "Something went wrong."}}
