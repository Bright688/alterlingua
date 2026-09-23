"""GET /health."""

from fastapi import APIRouter, Request

from app import __version__

router = APIRouter(tags=["health"])


@router.get("/health")
async def health(request: Request) -> dict[str, str]:
    """Says the server is up. Reveals no configuration secrets and touches no user data."""
    return {
        "status": "ok",
        "version": __version__,
        "environment": request.app.state.settings.environment,
        "translation_provider": request.app.state.translation_service.provider_name,
        "speech_provider": request.app.state.speech_service.provider_name,
    }
