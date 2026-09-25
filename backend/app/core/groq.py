"""A small client for Groq's HTTP API (OpenAI-compatible), used by the translation provider.

It sends requests and turns failures into the app's controlled errors. It never logs or returns a request or response body in an
error, because bodies hold private text (CLAUDE.md 25, 50). The API key comes from configuration and is only ever sent in the
Authorization header.
"""

import httpx

from app.core.config import Settings
from app.core.errors import ConfigurationError, ProviderError, ProviderTimeoutError, ProviderUnavailableError


class GroqClient:
    def __init__(self, settings: Settings, *, transport: httpx.AsyncBaseTransport | None = None) -> None:
        if settings.groq_api_key is None or not settings.groq_api_key.get_secret_value().strip():
            raise ConfigurationError("The Groq provider needs ALTERLINGUA_GROQ_API_KEY (set it in the environment or .env).")
        self._key = settings.groq_api_key.get_secret_value().strip()
        self._base = settings.groq_base_url.rstrip("/")
        self._transport = transport

    def _client(self, timeout: float) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self._base,
            headers={"Authorization": f"Bearer {self._key}"},
            timeout=timeout,
            transport=self._transport,
        )

    async def post(self, path: str, *, timeout: float, json: dict | None = None, data: dict | None = None, files: dict | None = None) -> dict:
        """Sends a POST (JSON, or a multipart upload for audio) and returns the JSON answer. Raises the app's controlled
        errors; their text never holds message content."""
        try:
            async with self._client(timeout) as client:
                response = await client.post(path, json=json, data=data, files=files)
        except httpx.TimeoutException:
            raise ProviderTimeoutError("The provider took too long to answer.", provider="groq") from None
        except httpx.HTTPError:
            raise ProviderUnavailableError("The provider could not be reached.", provider="groq") from None
        status = response.status_code
        if status in (401, 403):
            # The key is missing, wrong or not allowed: a server problem, not the user's.
            raise ProviderUnavailableError("The provider rejected the server's credentials.", provider="groq")
        if status == 429:
            raise ProviderUnavailableError("The provider is busy or its quota is used up. Try again later.", provider="groq")
        if status >= 400:
            raise ProviderError("The provider could not process the request.", provider="groq", status=status)
        try:
            body = response.json()
        except ValueError:
            raise ProviderError("The provider returned an unreadable answer.", provider="groq") from None
        if not isinstance(body, dict):
            raise ProviderError("The provider returned an unexpected answer.", provider="groq")
        return body
