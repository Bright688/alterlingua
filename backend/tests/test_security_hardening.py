"""Findings of the privacy and security audit, kept as tests so they cannot come back."""

import asyncio
import json
import logging
import os
import re
import stat
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

import pytest
import uvicorn

from app.core.config import Settings
from app.core.logging import install_exception_redaction
from app.main import create_app
from app.translation.provider import ProviderCapabilities, TranslationProvider
from tests.audio_helpers import StubSpeech, make_wav
from tests.conftest import make_client

SECRET = "SECRET-please-wire-4200-euros-to-account-9917"
REPO = Path(__file__).resolve().parents[2]


class Leaky(TranslationProvider):
    """A provider whose error repeats the user's text, as real providers sometimes do."""

    name = "leaky"

    def capabilities(self):
        return ProviderCapabilities(frozenset({"en", "fr"}), True)

    async def translate(self, request):
        raise RuntimeError(f"upstream rejected: {request.text}")


class Capture(logging.Handler):
    def __init__(self):
        super().__init__()
        self.lines: list[str] = []

    def emit(self, record):
        self.lines.append(self.format(record))


# ---- HIGH: exception messages and tracebacks must never reach the logs ----

def test_a_logged_exception_is_reduced_to_its_type_and_place():
    install_exception_redaction()
    capture = Capture()
    capture.setFormatter(logging.Formatter("%(message)s"))
    log = logging.getLogger("audit.probe")
    log.addHandler(capture)
    log.setLevel(logging.DEBUG)
    try:
        try:
            raise ValueError(SECRET)
        except ValueError as error:
            raise RuntimeError(f"wrapped: {error}") from error
    except RuntimeError:
        log.exception("something failed")
    log.error("with args %s", SECRET, exc_info=ValueError(SECRET))
    text = "\n".join(capture.lines)
    assert SECRET not in text
    assert "Traceback" not in text
    assert "error_type=RuntimeError" in text and "error_type=ValueError" in text
    assert "where=test_security_hardening.py:" in text


def test_the_real_web_server_does_not_log_the_text_of_an_unexpected_error():
    """This is the exact path that leaked: uvicorn logs the traceback of an exception that escapes to it."""
    app = create_app(Settings(_env_file=None, environment="test"), Leaky())
    server = uvicorn.Server(uvicorn.Config(app, host="127.0.0.1", port=0, log_level="info"))
    capture = Capture()
    capture.setFormatter(logging.Formatter("%(name)s %(message)s"))
    logger = logging.getLogger("uvicorn.error")
    logger.addHandler(capture)
    thread = threading.Thread(target=lambda: asyncio.run(server.serve()), daemon=True)
    thread.start()
    try:
        deadline = time.time() + 10
        while not server.started and time.time() < deadline:
            time.sleep(0.05)
        assert server.started
        port = server.servers[0].sockets[0].getsockname()[1]
        request = urllib.request.Request(
            f"http://127.0.0.1:{port}/v1/translate",
            json.dumps({"text": SECRET, "target": "fr", "source": "en"}).encode(),
            {"Content-Type": "application/json"},
        )
        with pytest.raises(urllib.error.HTTPError) as caught:
            urllib.request.urlopen(request)
        assert caught.value.code == 500
        assert SECRET not in caught.value.read().decode()
    finally:
        server.should_exit = True
        thread.join(timeout=10)
        logger.removeHandler(capture)
    text = "\n".join(capture.lines)
    assert "Exception in ASGI application" in text  # the event is still logged...
    assert "error_type=RuntimeError" in text  # ...with its type
    assert SECRET not in text  # ...but not its message
    assert "Traceback" not in text


# ---- MEDIUM: responses are private and not cached ----

def test_api_responses_are_marked_no_store():
    client = make_client()
    ok = client.post("/v1/translate", json={"text": "Are you coming tomorrow?", "target": "fr", "source": "en"})
    bad = client.post("/v1/translate", json={"text": "x", "target": "zz"})
    speak = client.post("/v1/audio/speak", files={"audio": ("c.wav", make_wav(), "audio/wav")}, data={"target": "es", "source": "en"})
    for response in (ok, bad, speak):
        assert response.headers["cache-control"] == "no-store"
        assert response.headers["x-content-type-options"] == "nosniff"


def test_the_interactive_docs_are_off_in_production_and_on_otherwise():
    assert make_client(environment="production", api_tokens="test-token").get("/docs").status_code == 404
    assert make_client(environment="production", api_tokens="test-token").get("/openapi.json").status_code == 404
    assert make_client(environment="development").get("/openapi.json").status_code == 200


# ---- temporary audio ----

def test_temporary_audio_is_private_to_the_server_process_and_gone_afterwards(tmp_path):
    seen: list[int] = []

    class Watching(StubSpeech):
        async def transcribe(self, request):
            seen.append(stat.S_IMODE(os.stat(request.audio_path).st_mode))
            return await super().transcribe(request)

    client = make_client(stt=Watching(), temp_dir=str(tmp_path))
    client.post("/v1/audio/translate", files={"audio": ("c.wav", make_wav(), "audio/wav")}, data={"target": "es", "source": "en"})
    assert seen and all(mode & 0o077 == 0 for mode in seen)
    assert list(tmp_path.iterdir()) == []


# ---- secrets never enter the repository ----

_SECRET_SHAPES = [
    re.compile(r"sk-[A-Za-z0-9]{20,}"),
    re.compile(r"AIza[0-9A-Za-z_\-]{30,}"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"(?i)bearer\s+[A-Za-z0-9._\-]{20,}"),
    re.compile(r"(?i)(api[_-]?key|secret|password)\s*[=:]\s*[\"'][^\"'\s]{8,}[\"']"),
]
_SKIP_DIRS = {".venv", "build", ".gradle", ".git", "__pycache__", ".pytest_cache", ".idea", "node_modules"}
_TEXT_SUFFIXES = {".py", ".kt", ".kts", ".xml", ".md", ".json", ".toml", ".txt", ".properties", ".yml", ".yaml", ".example", ".gradle", ""}


def _repo_files():
    for path in REPO.rglob("*"):
        if path.is_file() and not (set(path.relative_to(REPO).parts) & _SKIP_DIRS) and path.suffix in _TEXT_SUFFIXES:
            yield path


def test_no_secret_shaped_strings_are_in_the_repository():
    hits = []
    for path in _repo_files():
        if path.name == "test_security_hardening.py":
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for shape in _SECRET_SHAPES:
            if shape.search(text):
                hits.append(f"{path.relative_to(REPO)} matches {shape.pattern[:30]}")
    assert hits == []


def test_secret_and_local_files_are_git_ignored():
    ignore = (REPO / ".gitignore").read_text() + (REPO / "android" / ".gitignore").read_text()
    for pattern in (".env", "*.pem", "*.key", "*.jks", "*.keystore", "local.properties", "keystore.properties", "*.db", "*.apk", "*.aab"):
        assert pattern in ignore, pattern


def test_the_example_environment_file_holds_no_real_values():
    for line in (REPO / "backend" / ".env.example").read_text().splitlines():
        if line.strip() and not line.startswith("#") and "=" in line:
            name, value = line.split("=", 1)
            assert "KEY" not in name and "SECRET" not in name and "TOKEN" not in name, name
