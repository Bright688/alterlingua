"""The API token check and the rate limit."""

import pytest
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.core.errors import ConfigurationError, RateLimitedError
from app.core.security import RateLimiter
from app.main import create_app
from app.translation.fake_provider import FakeTranslationProvider

BODY = {"text": "Are you coming tomorrow?", "target": "es"}


def client(**settings) -> TestClient:
    return TestClient(create_app(settings=Settings(_env_file=None, **settings), provider=FakeTranslationProvider()))


def test_with_no_tokens_configured_outside_production_the_api_is_open():
    assert client().post("/v1/translate", json=BODY).status_code == 200


def test_a_configured_token_is_required_and_health_stays_public():
    c = client(api_tokens="first-secret,second-secret")
    assert c.get("/health").status_code == 200
    assert c.post("/v1/translate", json=BODY).status_code == 401
    body = c.post("/v1/translate", json=BODY).json()
    assert body["error"]["code"] == "unauthorized"


@pytest.mark.parametrize("header", ["", "Bearer", "Bearer wrong", "Basic first-secret", "first-secret", "Bearer first-secre"])
def test_wrong_or_malformed_credentials_are_refused(header):
    c = client(api_tokens="first-secret")
    headers = {"Authorization": header} if header else {}
    assert c.post("/v1/translate", json=BODY, headers=headers).status_code == 401


def test_any_configured_token_works():
    c = client(api_tokens="first-secret, second-secret")
    for token in ("first-secret", "second-secret"):
        assert c.post("/v1/translate", json=BODY, headers={"Authorization": f"Bearer {token}"}).status_code == 200


def test_the_error_never_echoes_the_presented_token_or_the_text():
    c = client(api_tokens="real-secret")
    response = c.post("/v1/translate", json=BODY, headers={"Authorization": "Bearer guess-me"})
    assert "guess-me" not in response.text and "real-secret" not in response.text and BODY["text"] not in response.text


def test_audio_routes_are_guarded_too():
    c = client(api_tokens="s")
    assert c.post("/v1/audio/voices", headers={}).status_code in (401, 404, 405)
    assert c.get("/v1/audio/voices").status_code == 401


def test_production_refuses_to_start_without_tokens():
    with pytest.raises(ConfigurationError):
        create_app(settings=Settings(_env_file=None, environment="production"), provider=FakeTranslationProvider())
    create_app(settings=Settings(_env_file=None, environment="production", api_tokens="x"), provider=FakeTranslationProvider())


def test_the_rate_limit_stops_a_burst_then_recovers():
    now = [0.0]
    limiter = RateLimiter(3, clock=lambda: now[0])
    for _ in range(3):
        limiter.check("a")
    with pytest.raises(RateLimitedError):
        limiter.check("a")
    limiter.check("b")  # another client is not affected
    now[0] = 61.0
    limiter.check("a")


def test_the_route_answers_429_over_the_limit_per_token():
    c = client(api_tokens="one,two", rate_limit_per_minute=2)
    h1, h2 = {"Authorization": "Bearer one"}, {"Authorization": "Bearer two"}
    assert [c.post("/v1/translate", json=BODY, headers=h1).status_code for _ in range(3)] == [200, 200, 429]
    assert c.post("/v1/translate", json=BODY, headers=h2).status_code == 200


def test_a_zero_limit_means_no_limit():
    c = client(rate_limit_per_minute=0)
    assert all(c.post("/v1/translate", json=BODY).status_code == 200 for _ in range(10))
