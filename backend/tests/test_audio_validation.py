import pytest

from tests.audio_helpers import SAMPLES, StubSpeech, make_wav
from tests.conftest import make_client


def post(client, audio, content_type, **form):
    form.setdefault("target", "es")
    return client.post("/v1/audio/translate", files={"audio": ("clip", audio, content_type)}, data=form)


def error_code(response):
    return response.json()["error"]["code"]


# ---- audio type ----

@pytest.mark.parametrize("name", list(SAMPLES))
def test_every_accepted_format_is_accepted(client, name):
    data, content_type = SAMPLES[name]
    assert post(client, data, content_type).status_code == 200


def test_content_type_parameters_and_case_are_ignored(client):
    data, _ = SAMPLES["ogg"]
    assert post(client, data, "Audio/OGG; codecs=opus").status_code == 200


@pytest.mark.parametrize("content_type", ["text/plain", "application/json", "image/png", "video/mp4", "application/octet-stream", ""])
def test_types_that_are_not_audio_are_refused(client, content_type):
    response = post(client, make_wav(), content_type)
    assert response.status_code == 415
    assert error_code(response) == "unsupported_audio_type"


def test_a_file_that_is_not_what_it_claims_is_refused(client):
    assert post(client, b"this is plain text, not audio at all", "audio/wav").status_code == 415
    mp3, _ = SAMPLES["mp3"]
    assert post(client, mp3, "audio/wav").status_code == 415  # mp3 bytes declared as wav
    assert post(client, make_wav(), "audio/mpeg").status_code == 415  # wav bytes declared as mp3
    assert post(client, b"\x00\x01", "audio/wav").status_code == 415  # too small to be audio


def test_the_refusal_lists_what_is_supported(client):
    error = post(client, make_wav(), "text/plain").json()["error"]
    assert "audio/wav" in error["supported"] and "audio/ogg" in error["supported"]
    assert error["received"] == "text/plain"


def test_a_refused_file_never_reaches_speech_recognition():
    stt = StubSpeech()
    client = make_client(stt=stt)
    post(client, b"not audio", "audio/wav")
    post(client, make_wav(), "text/plain")
    assert stt.calls == []


# ---- size ----

def test_an_empty_file_is_refused(client):
    response = post(client, b"", "audio/wav")
    assert response.status_code == 422
    assert error_code(response) == "invalid_audio"


def test_audio_over_the_limit_is_refused(tmp_path):
    stt = StubSpeech()
    client = make_client(stt=stt, max_audio_bytes=1024, temp_dir=str(tmp_path))
    ok = post(client, make_wav(seconds=0.01), "audio/wav")  # 364 bytes
    assert ok.status_code == 200

    response = post(client, make_wav(seconds=0.05), "audio/wav")  # about 1.6 KB
    assert response.status_code == 413
    assert error_code(response) == "audio_too_large"
    assert response.json()["error"]["max_bytes"] == 1024
    assert len(stt.calls) == 1
    assert list(tmp_path.iterdir()) == []  # the partly written file is gone


def test_an_obviously_huge_upload_is_turned_away_from_its_declared_size(tmp_path):
    stt = StubSpeech()
    client = make_client(stt=stt, max_audio_bytes=1024, temp_dir=str(tmp_path))
    response = post(client, b"RIFF\x00\x00\x00\x00WAVE" + b"\x00" * 300_000, "audio/wav")
    assert response.status_code == 413
    assert error_code(response) == "audio_too_large"
    assert stt.calls == []
    assert list(tmp_path.iterdir()) == []


def test_a_file_exactly_at_the_limit_is_accepted(tmp_path):
    wav = make_wav(seconds=0.05)  # 1644 bytes
    client = make_client(stt=StubSpeech(), max_audio_bytes=len(wav), temp_dir=str(tmp_path))
    assert post(client, wav, "audio/wav").status_code == 200
    assert post(make_client(max_audio_bytes=1024), wav + b"\x00" * 1000, "audio/wav").status_code == 413


# ---- the form fields ----

def test_missing_audio_or_target_is_reported_by_field(client):
    no_audio = client.post("/v1/audio/translate", data={"target": "es"})
    assert no_audio.status_code == 422
    assert error_code(no_audio) == "invalid_request"
    assert {d["field"] for d in no_audio.json()["error"]["details"]} == {"audio"}

    no_target = client.post("/v1/audio/translate", files={"audio": ("c.wav", make_wav(), "audio/wav")})
    assert no_target.status_code == 422
    assert {d["field"] for d in no_target.json()["error"]["details"]} == {"target"}


@pytest.mark.parametrize("target", ["", "f", "french", "12", "auto", "fr--FR"])
def test_malformed_target_codes(client, target):
    response = post(client, make_wav(), "audio/wav", target=target)
    assert response.status_code == 422
    assert error_code(response) == "invalid_request"
    assert "target" in {d["field"] for d in response.json()["error"]["details"]}


def test_unknown_options_are_rejected(client):
    for field, value in (("context", "shouting"), ("tone", "rude")):
        response = post(client, make_wav(), "audio/wav", **{field: value})
        assert response.status_code == 422
        assert field in {d["field"] for d in response.json()["error"]["details"]}


def test_an_unsupported_spoken_language_hint_is_controlled(client):
    response = post(client, make_wav(), "audio/wav", source="pt")
    assert response.status_code == 422
    error = response.json()["error"]
    assert (error["code"], error["language"], error["role"]) == ("unsupported_language", "pt", "source")


def test_wrong_method(client):
    assert client.get("/v1/audio/translate").status_code == 405
