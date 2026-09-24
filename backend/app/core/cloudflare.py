"""A small client for Cloudflare Workers AI's OpenAI-compatible endpoint, used by the translation provider.

It sends requests and turns failures into the app's controlled errors. It never logs or returns a request or response body in an
error, because bodies hold private text (CLAUDE.md 25, 50). The API token comes from configuration and is only ever sent in the
Authorization header. Unlike Groq or Mistral, Cloudflare's endpoint also needs the account id baked into the URL path, not just a
base URL.
"""

import httpx

from app.core.config import Settings
from app.core.errors import ConfigurationError, ProviderError, ProviderTimeoutError, ProviderUnavailableError


class CloudflareClient:
    def __init__(self, settings: Settings, *, transport: httpx.AsyncBaseTransport | None = None) -> None:
        if settings.cloudflare_api_token is None or not settings.cloudflare_api_token.get_secret_value().strip():
            raise ConfigurationError(
                "The Cloudflare Workers AI provider needs ALTERLINGUA_CLOUDFLARE_API_TOKEN (set it in the environment or .env)."
            )
        if not settings.cloudflare_account_id.strip():
            raise ConfigurationError(
                "The Cloudflare Workers AI provider needs ALTERLINGUA_CLOUDFLARE_ACCOUNT_ID (set it in the environment or .env)."
            )
        self._key = settings.cloudflare_api_token.get_secret_value().strip()
        account_id = settings.cloudflare_account_id.strip()
        self._base = f"{settings.cloudflare_base_url.rstrip('/')}/accounts/{account_id}/ai/v1"
        self._transport = transport

    def _client(self, timeout: float) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self._base,
            headers={"Authorization": f"Bearer {self._key}"},
            timeout=timeout,
            transport=self._transport,
        )

    async def post(self, path: str, *, timeout: float, json: dict | None = None) -> dict:
        """Sends a POST and returns the JSON answer. Raises the app's controlled errors; their text never holds message content."""
        try:
            async with self._client(timeout) as client:
                response = await client.post(path, json=json)
        except httpx.TimeoutException:
            raise ProviderTimeoutError("The provider took too long to answer.", provider="cloudflare") from None
        except httpx.HTTPError:
            raise ProviderUnavailableError("The provider could not be reached.", provider="cloudflare") from None
        status = response.status_code
        if status in (401, 403):
            # The token is missing, wrong or not allowed: a server problem, not the user's.
            raise ProviderUnavailableError("The provider rejected the server's credentials.", provider="cloudflare")
        if status == 429:
            raise ProviderUnavailableError("The provider is busy or its quota is used up. Try again later.", provider="cloudflare")
        if status >= 400:
            raise ProviderError("The provider could not process the request.", provider="cloudflare", status=status)
        try:
            body = response.json()
        except ValueError:
            raise ProviderError("The provider returned an unreadable answer.", provider="cloudflare") from None
        if not isinstance(body, dict):
            raise ProviderError("The provider returned an unexpected answer.", provider="cloudflare")
        return body
