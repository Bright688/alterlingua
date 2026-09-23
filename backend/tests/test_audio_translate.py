import pytest

from app.translation.fake_provider import SAMPLE_PHRASES
from tests.audio_helpers import SAMPLES, StubSpeech, make_wav
from tests.conftest import SpyProvider, make_client

ALL = ["en", "fr", "es", "de", "it", "nl", "zh", "ja"]
PHRASE_EN = SAMPLE_PHRASES["en"]


def post(client, audio=None, content_type="audio/wav", **form):
    form.setdefault("target", "es")
    data = make_wav() if audio is None else audio
    return client.post("/v1/audio/translate", files={"audio": ("clip", data, content_type)}, data=form)


def test_the_documented_response_shape(client):
    response = post(client, target="es", source="auto")
    assert response.status_code == 200
    assert response.json() == {
        "source_language": "en",
        "transcript": PHRASE_EN,
        "target_language": "es",
        "translation": "¿Vienes mañana?",
    }
    assert list(response.json()) == ["source_language", "transcript", "target_language", "translation"]


def test_source_defaults_to_auto_and_context_and_tone_are_optional(client):
    response = client.post("/v1/audio/translate", files={"audio": ("c.wav", make_wav(), "audio/wav")}, data={"target": "fr"})
    assert response.status_code == 200
    assert response.json()["source_language"] == "en"


@pytest.mark.parametrize("target", [code for code in ALL if code != "en"])
def test_english_speech_to_every_other_language_uses_the_same_route(client, target):
    body = post(client, target=target).json()
    assert body["source_language"] == "en"
    assert body["transcript"] == PHRASE_EN
    assert body["target_language"] == target
    assert body["translation"] == SAMPLE_PHRASES[target]


@pytest.mark.parametrize("spoken", [code for code in ALL if code != "en"])
def test_speech_in_every_language_is_translated_to_english(client, spoken):
    body = post(client, source=spoken, target="en").json()
    assert body["source_language"] == spoken
    assert body["transcript"] == SAMPLE_PHRASES[spoken]  # in its own script (中文, 日本語, ...)
    assert body["translation"] == PHRASE_EN


@pytest.mark.parametrize(("spoken", "target"), [("ja", "fr"), ("zh", "es"), ("de", "ja"), ("nl", "it"), ("fr", "zh")])
def test_non_english_pairs(client, spoken, target):
    body = post(client, source=spoken, target=target).json()
    assert body["transcript"] == SAMPLE_PHRASES[spoken]
    assert body["translation"] == SAMPLE_PHRASES[target]


def test_locale_codes_are_reduced_to_the_language(client):
    body = post(client, source="ja-JP", target="FR_fr").json()
    assert (body["source_language"], body["target_language"]) == ("ja", "fr")


def test_unicode_transcripts_come_back_as_real_characters(client):
    response = post(client, source="ja", target="zh")
    assert "明日来ますか？".encode("utf-8") in response.content


# ---- auto-detection (through a provider that reports what it heard) ----

@pytest.mark.parametrize("heard", ["en", "fr", "es", "ja"])
def test_auto_detection_reports_the_detected_language(heard):
    stt = StubSpeech(detected=heard, transcripts={heard: SAMPLE_PHRASES[heard]})
    body = post(make_client(stt=stt), source="auto", target="de").json()
    assert body["source_language"] == heard
    assert body["transcript"] == SAMPLE_PHRASES[heard]
    assert body["translation"] == SAMPLE_PHRASES["de"]
    assert stt.calls[0].language is None  # the provider was asked to detect


def test_a_detected_language_that_is_not_supported_is_a_controlled_error():
    stt = StubSpeech(detected="pt", transcripts={"pt": "olá"})
    response = post(make_client(stt=stt), source="auto")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "unsupported_language"


def test_no_language_reported_for_auto_is_undetected():
    stt = StubSpeech(detected="")  # the provider reports nothing
    response = post(make_client(stt=stt), source="auto")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "source_language_undetected"


def test_speech_already_in_the_target_language_is_not_translated():
    spy = SpyProvider(languages=("en", "es", "fr", "ja"))
    stt = StubSpeech(transcripts={"fr": "Tu viens demain ?"})
    body = post(make_client(spy, stt), source="fr", target="fr").json()
    assert body["transcript"] == body["translation"] == "Tu viens demain ?"
    assert spy.calls == []


def test_control_characters_in_a_transcript_are_removed():
    stt = StubSpeech(transcripts={"en": "hello\x00 wor\x07ld"})
    body = post(make_client(stt=stt), source="en", target="es").json()
    assert body["transcript"] == "hello world"


# ---- speech provider capabilities (support differs by language) ----

def test_languages_the_speech_provider_supports_work_and_others_are_refused():
    stt = StubSpeech(languages=("en", "fr", "es", "ja"), transcripts={c: SAMPLE_PHRASES[c] for c in ("en", "fr", "es", "ja")})
    client = make_client(stt=stt)
    for supported in ("en", "fr", "es", "ja"):
        target = "de" if supported != "de" else "en"
        response = post(client, source=supported, target=target)
        assert response.status_code == 200, supported
        assert response.json()["transcript"] == SAMPLE_PHRASES[supported]

    calls_before = len(stt.calls)
    for unsupported in ("de", "it", "nl", "zh"):
        response = post(client, source=unsupported, target="en")
        assert response.status_code == 422, unsupported
        error = response.json()["error"]
        assert error["code"] == "unsupported_language"
        assert error["feature"] == "speech_to_text"
        assert error["language"] == unsupported
        assert error["role"] == "source"
        assert error["supported"] == ["en", "es", "fr", "ja"]
    assert len(stt.calls) == calls_before  # the provider was never asked


def test_a_provider_without_auto_detection_needs_an_explicit_language():
    stt = StubSpeech(auto_detect=False)
    client = make_client(stt=stt)
    response = post(client, source="auto")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "auto_detect_unavailable"
    assert stt.calls == []
    assert post(client, source="fr", target="en").status_code == 200


def test_the_speech_provider_receives_the_spoken_language_and_the_audio_type():
    stt = StubSpeech()
    post(make_client(stt=stt), audio=SAMPLES["ogg"][0], content_type="audio/ogg; codecs=opus", source="ja", target="en")
    (call,) = stt.calls
    assert (call.language, call.content_type) == ("ja", "audio/ogg")


# ---- target checks happen before any audio work ----

def test_an_unsupported_target_is_refused_before_speech_recognition():
    stt = StubSpeech()
    response = post(make_client(stt=stt), target="pt")
    assert response.status_code == 422
    error = response.json()["error"]
    assert (error["code"], error["language"], error["role"]) == ("unsupported_language", "pt", "target")
    assert stt.calls == []


def test_a_target_the_translation_provider_lacks_is_refused_before_speech_recognition():
    stt = StubSpeech()
    response = post(make_client(SpyProvider(languages=("en", "fr")), stt), target="ja")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "unsupported_language"
    assert stt.calls == []


def test_a_pair_the_translation_provider_cannot_do_is_a_controlled_error():
    stt = StubSpeech(languages=("en", "fr", "de"))
    response = post(make_client(SpyProvider(languages=("en", "fr")), stt), source="de", target="fr")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "unsupported_language_pair"


# ---- failures ----

@pytest.mark.parametrize(
    ("error_name", "status", "code"),
    [("ProviderError", 502, "provider_error"), ("ProviderUnavailableError", 503, "provider_unavailable")],
)
def test_speech_provider_failures_are_controlled(error_name, status, code):
    from app.core import errors

    stt = StubSpeech(error=getattr(errors, error_name)("It broke.", stage="speech_to_text"))
    response = post(make_client(stt=stt))
    assert response.status_code == status
    assert response.json()["error"]["code"] == code


def test_slow_speech_recognition_times_out():
    response = post(make_client(stt=StubSpeech(delay=0.5), stt_timeout_seconds=0.05))
    assert response.status_code == 504
    assert response.json()["error"]["code"] == "provider_timeout"


def test_nothing_recognised_is_a_controlled_error():
    response = post(make_client(stt=StubSpeech(transcripts={"en": "   \n"})), source="en")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "speech_not_recognized"


def test_a_failing_translation_step_is_a_controlled_error():
    from app.core.errors import ProviderError

    response = post(make_client(SpyProvider(error=ProviderError("x"))), source="en", target="fr")
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "provider_error"


def test_health_reports_the_speech_provider(client):
    assert client.get("/health").json()["speech_provider"] == "fake"


def test_there_is_one_route_not_one_per_language_pair(client):
    assert client.post("/v1/audio/translate-english-to-french").status_code == 404
