import logging
from pathlib import Path

import pytest

from app.core import errors
from app.core.errors import ConfigurationError
from app.core.config import Settings
from app.main import create_app
from app.speech.registry import create_stt_provider
from tests.audio_helpers import StubSpeech, make_wav
from tests.conftest import SpyProvider, make_client

SECRET = "please wire 4200 euros for invoice XYZ-9917"
SECRET_JA = "極秘の会議は午後三時です"


def post(client, source="en", target="es", **kw):
    return client.post(
        "/v1/audio/translate", files={"audio": ("clip.wav", make_wav(), "audio/wav")}, data={"target": target, "source": source, **kw}
    )


def logged(caplog) -> str:
    return "\n".join(record.getMessage() for record in caplog.records)


def temp_files(directory: Path):
    return list(directory.iterdir())


# ---- temporary audio is always deleted ----

def test_the_audio_exists_while_it_is_processed_and_is_gone_afterwards(tmp_path):
    stt = StubSpeech()
    response = post(make_client(stt=stt, temp_dir=str(tmp_path)))
    assert response.status_code == 200
    assert stt.file_existed_during_call == [True]
    assert not stt.paths[0].exists()
    assert temp_files(tmp_path) == []


def test_the_temporary_file_is_private_to_the_server(tmp_path):
    modes = []

    class Checking(StubSpeech):
        async def transcribe(self, request):
            modes.append(request.audio_path.stat().st_mode & 0o777)
            return await super().transcribe(request)

    post(make_client(stt=Checking(), temp_dir=str(tmp_path)))
    assert modes == [0o600]


@pytest.mark.parametrize(
    "stt",
    [
        StubSpeech(error=errors.ProviderError("x")),
        StubSpeech(error=errors.ProviderUnavailableError("x")),
        StubSpeech(error=RuntimeError(SECRET)),
        StubSpeech(transcripts={"en": ""}),
        StubSpeech(delay=0.5),
    ],
    ids=["provider_error", "unavailable", "crash", "nothing_recognised", "timeout"],
)
def test_the_temporary_file_is_deleted_even_when_processing_fails(tmp_path, stt):
    client = make_client(stt=stt, temp_dir=str(tmp_path), stt_timeout_seconds=0.05)
    response = post(client)
    assert response.status_code >= 400
    assert stt.paths and not stt.paths[0].exists()
    assert temp_files(tmp_path) == []


def test_the_temporary_file_is_deleted_when_translation_fails(tmp_path):
    stt = StubSpeech()
    client = make_client(SpyProvider(error=errors.ProviderError("x")), stt, temp_dir=str(tmp_path))
    # The words were understood, so the transcript is returned with an empty translation; either way nothing is left behind.
    assert post(client, target="fr").status_code == 200
    assert temp_files(tmp_path) == []
    # And when the translator is needed to detect the language, the request fails outright.
    detecting = make_client(SpyProvider(error=errors.ProviderError("x")), StubSpeech(detected=""), temp_dir=str(tmp_path))
    assert post(detecting, source="auto", target="fr").status_code == 502
    assert temp_files(tmp_path) == []


def test_files_refused_before_processing_leave_nothing_behind(tmp_path):
    client = make_client(stt=StubSpeech(), temp_dir=str(tmp_path))
    client.post("/v1/audio/translate", files={"audio": ("c", b"not audio", "audio/wav")}, data={"target": "es"})
    client.post("/v1/audio/translate", files={"audio": ("c", make_wav(), "text/plain")}, data={"target": "es"})
    post(client, target="pt")
    assert temp_files(tmp_path) == []


def test_many_requests_leave_nothing_behind(tmp_path):
    client = make_client(stt=StubSpeech(), temp_dir=str(tmp_path))
    for target in ("es", "fr", "ja", "de", "pt"):
        post(client, target=target)
    assert temp_files(tmp_path) == []


def test_nothing_is_written_to_the_working_folder(tmp_path, monkeypatch):
    work = tmp_path / "work"
    work.mkdir()
    monkeypatch.chdir(work)
    post(make_client(stt=StubSpeech(), temp_dir=str(tmp_path)))
    assert list(work.iterdir()) == []


# ---- nothing private in the logs or the error bodies ----

def test_success_logs_no_transcript_or_translation(caplog):
    caplog.set_level(logging.DEBUG)
    stt = StubSpeech(transcripts={"en": SECRET, "ja": SECRET_JA})
    assert post(make_client(stt=stt), source="en", target="ja").status_code == 200
    assert post(make_client(stt=stt), source="ja", target="en").status_code == 200
    text = logged(caplog)
    assert "audio_translation_completed" in text
    assert "source_language=en" in text and "target_language=ja" in text and "audio_bytes=" in text and "transcript_chars=" in text
    for private in ("invoice", "XYZ", "4200", "極秘", "会議", "[ja]", "[en]"):
        assert private not in text, private


@pytest.mark.parametrize("error", [errors.ProviderError("failed"), RuntimeError(SECRET)])
def test_failures_log_no_transcript(caplog, error):
    caplog.set_level(logging.DEBUG)
    post(make_client(stt=StubSpeech(transcripts={"en": SECRET}, error=error)))
    text = logged(caplog)
    assert "invoice" not in text and "4200" not in text


def test_error_bodies_never_echo_the_transcript():
    # (A translation outage after the words were understood is a success that carries the transcript on purpose; an error
    # is when the translator is needed to detect the language and is down.)
    stt = StubSpeech(detected="", transcripts={"en": SECRET})
    response = post(make_client(SpyProvider(error=errors.ProviderError("failed")), stt), source="auto", target="fr")
    assert response.status_code == 502 and "invoice" not in response.text
    response = post(make_client(stt=StubSpeech(error=RuntimeError(SECRET))))
    assert response.status_code == 500 and "invoice" not in response.text


def test_the_fake_speech_provider_says_it_is_fake(caplog):
    caplog.set_level(logging.WARNING)
    create_app(Settings(_env_file=None, environment="test"))
    assert "speech_provider_is_fake" in logged(caplog)


# ---- configuration ----

def test_speech_settings_come_from_environment_variables(monkeypatch):
    monkeypatch.setenv("ALTERLINGUA_MAX_AUDIO_BYTES", "2048")
    monkeypatch.setenv("ALTERLINGUA_STT_PROVIDER", "fake")
    monkeypatch.setenv("ALTERLINGUA_STT_TIMEOUT_SECONDS", "7")
    settings = Settings(_env_file=None)
    assert (settings.max_audio_bytes, settings.stt_provider, settings.stt_timeout_seconds) == (2048, "fake", 7)


def test_an_unknown_speech_provider_fails_at_startup_with_a_clear_message():
    settings = Settings(_env_file=None, stt_provider="nonexistent")
    with pytest.raises(ConfigurationError, match="nonexistent"):
        create_stt_provider(settings)
    with pytest.raises(ConfigurationError):
        create_app(settings)
