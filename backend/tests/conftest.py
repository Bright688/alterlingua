import logging

import pytest
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.core.errors import ProviderError
from app.main import create_app
from app.translation.fake_provider import FakeTranslationProvider
from app.translation.provider import (
    ProviderCapabilities,
    ProviderRequest,
    ProviderResult,
    TranslationProvider,
)


class SpyProvider(TranslationProvider):
    """A configurable stand-in that records every call, to prove what reaches a provider."""

    name = "spy"

    def __init__(self, languages=("en", "fr", "es"), auto_detect=True, result=None, error=None, delay=0.0):
        self._caps = ProviderCapabilities(frozenset(languages), auto_detect)
        self._result = result
        self._error = error
        self._delay = delay
        self.calls: list[ProviderRequest] = []

    def capabilities(self):
        return self._caps

    async def translate(self, request):
        import asyncio

        self.calls.append(request)
        if self._delay:
            await asyncio.sleep(self._delay)
        if self._error:
            raise self._error
        return self._result or ProviderResult(f"<{request.target}>", request.source or "en")


def make_client(provider=None, stt=None, tts=None, **settings):
    settings.setdefault("environment", "test")
    app = create_app(Settings(_env_file=None, **settings), provider or FakeTranslationProvider(), stt, tts)
    return TestClient(app, raise_server_exceptions=False)


@pytest.fixture
def client():
    return make_client()


@pytest.fixture
def spy():
    return SpyProvider()


@pytest.fixture
def log_lines(caplog):
    caplog.set_level(logging.DEBUG)
    return caplog
