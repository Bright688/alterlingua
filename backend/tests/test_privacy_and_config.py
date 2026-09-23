import pytest

from app.core.config import Settings
from app.core.errors import ConfigurationError, ProviderError
from app.main import create_app
from app.translation.registry import create_provider
from tests.conftest import SpyProvider, make_client

SECRET = "Meet me at 14:32 about invoice XYZ-9917"
SECRET_JA = "極秘のメッセージ"


def all_log_text(caplog) -> str:
    return "\n".join(record.getMessage() for record in caplog.records)


def test_successful_translation_logs_no_message_content(log_lines):
    client = make_client(SpyProvider(result=None))
    response = client.post("/v1/translate", json={"text": SECRET, "source": "en", "target": "fr"})
    assert response.status_code == 200
    logged = all_log_text(log_lines)
    assert "translation_request_completed" in logged
    assert "source_language=en" in logged and "target_language=fr" in logged and "input_chars=" in logged
    assert "invoice" not in logged and "XYZ" not in logged and "<fr>" not in logged


@pytest.mark.parametrize("error", [ProviderError("failed"), RuntimeError(SECRET)])
def test_failures_log_no_message_content(log_lines, error):
    client = make_client(SpyProvider(error=error))
    client.post("/v1/translate", json={"text": SECRET_JA, "source": "ja", "target": "en"})
    client.post("/v1/translate", json={"text": SECRET, "source": "en", "target": "fr"})
    logged = all_log_text(log_lines)
    assert "invoice" not in logged and "極秘" not in logged


def test_validation_failures_log_no_message_content(log_lines):
    client = make_client()
    client.post("/v1/translate", json={"text": SECRET + "\x00", "target": "fr"})
    assert "invoice" not in all_log_text(log_lines)


def test_nothing_is_written_to_disk(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    client = make_client()
    client.post("/v1/translate", json={"text": SECRET, "source": "en", "target": "fr"})
    assert list(tmp_path.iterdir()) == []


def test_settings_come_from_environment_variables(monkeypatch):
    monkeypatch.setenv("ALTERLINGUA_MAX_TEXT_CHARS", "42")
    monkeypatch.setenv("ALTERLINGUA_TRANSLATION_PROVIDER", "fake")
    settings = Settings(_env_file=None)
    assert settings.max_text_chars == 42
    assert settings.translation_provider == "fake"


def test_unknown_provider_fails_at_startup_with_a_clear_message():
    settings = Settings(_env_file=None, translation_provider="nonexistent")
    with pytest.raises(ConfigurationError, match="nonexistent"):
        create_provider(settings)
    with pytest.raises(ConfigurationError):
        create_app(settings)


def test_no_api_keys_are_hardcoded():
    import pathlib
    import re

    root = pathlib.Path(__file__).resolve().parent.parent
    pattern = re.compile(r"(sk-[A-Za-z0-9]{16,}|AIza[0-9A-Za-z_-]{20,}|api[_-]?key\s*=\s*['\"][^'\"]{8,})", re.I)
    for path in list((root / "app").rglob("*.py")) + [root / ".env.example"]:
        assert not pattern.search(path.read_text(encoding="utf-8")), path


def test_module_packages_are_prepared():
    import importlib

    for name in ["translation", "speech", "learning", "vocabulary", "mastery", "lessons", "users", "database"]:
        importlib.import_module(f"app.{name}")
