"""Who may call the API, and how often.

The app sends a secret token as ``Authorization: Bearer <token>``; the server compares it in constant time with the configured
list. This keeps strangers from spending the paid provider keys. It is a shared-secret guard for the pilot, not user accounts
(those come with the authentication milestone), and a token inside an app can be extracted, so the rate limit is a second line.
Neither the token nor any request content is ever logged.
"""

import hashlib
import hmac
import time
from collections import defaultdict, deque

from fastapi import Request

from app.core.config import Settings
from app.core.errors import ConfigurationError, RateLimitedError, UnauthorizedError


def configured_tokens(settings: Settings) -> tuple[str, ...]:
    raw = settings.api_tokens.get_secret_value() if settings.api_tokens else ""
    return tuple(token.strip() for token in raw.split(",") if token.strip())


def check_startup(settings: Settings) -> None:
    """Refuses to start in production without tokens, so a public server is never left open."""
    if settings.environment == "production" and not configured_tokens(settings):
        raise ConfigurationError("Production needs ALTERLINGUA_API_TOKENS (a comma-separated list of secret tokens).")


class RateLimiter:
    """At most ``limit`` requests per client in any 60 seconds (in memory, per process). 0 means no limit."""

    def __init__(self, limit: int, *, clock=time.monotonic) -> None:
        self._limit = limit
        self._clock = clock
        self._hits: dict[str, deque[float]] = defaultdict(deque)

    def check(self, client: str) -> None:
        if self._limit <= 0:
            return
        now = self._clock()
        hits = self._hits[client]
        while hits and now - hits[0] > 60:
            hits.popleft()
        if len(hits) >= self._limit:
            raise RateLimitedError("Too many requests. Wait a moment and try again.", retry_after_seconds=max(1, int(60 - (now - hits[0]))))
        hits.append(now)


def make_guard(settings: Settings):
    """A FastAPI dependency that checks the token (when tokens are configured) and the rate limit."""
    tokens = configured_tokens(settings)
    limiter = RateLimiter(settings.rate_limit_per_minute)

    async def guard(request: Request) -> None:
        client = request.client.host if request.client else "unknown"
        if tokens:
            header = request.headers.get("authorization", "")
            presented = header[7:].strip() if header.lower().startswith("bearer ") else ""
            # Compare against every configured token, without stopping early, so timing does not reveal which matched.
            matched = False
            for token in tokens:
                matched |= hmac.compare_digest(presented.encode(), token.encode())
            if not matched:
                raise UnauthorizedError("A valid API token is required.")
            # Count requests per token (hashed), so clients behind one proxy address do not share a limit.
            client = "token:" + hashlib.sha256(presented.encode()).hexdigest()[:16]
        limiter.check(client)

    return guard
