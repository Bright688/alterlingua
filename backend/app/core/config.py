"""Configuration from environment variables (see .env.example). No secrets live in code."""

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """All settings are read from ``ALTERLINGUA_*`` environment variables or a local ``.env`` file."""

    model_config = SettingsConfigDict(
        env_prefix="ALTERLINGUA_",
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    environment: Literal["development", "test", "production"] = "development"
    log_level: str = "INFO"

    # Which speech-to-text provider to use. "fake" is a development stand-in and never real recognition.
    stt_provider: str = "fake"
    # Largest audio upload accepted, in bytes (default 10 MiB).
    max_audio_bytes: int = Field(default=10 * 1024 * 1024, ge=1024, le=200 * 1024 * 1024)
    # How long to wait for speech recognition before answering 504.
    stt_timeout_seconds: float = Field(default=60.0, gt=0, le=300)
    # Which text-to-speech provider to use. "fake" makes a placeholder tone, never real speech.
    tts_provider: str = "fake"
    # How long to wait for speech synthesis before answering 504.
    tts_timeout_seconds: float = Field(default=60.0, gt=0, le=300)
    # Longest text that is turned into speech, counted in Unicode characters.
    max_tts_chars: int = Field(default=1500, ge=1, le=20_000)
    # Where temporary audio is written while it is processed (deleted afterwards). Empty means the system temp folder.
    temp_dir: str = ""

    # Which translation provider to use: "fake" (a development stand-in, never a real translation), "mistral", "groq",
    # "cloudflare", or "fallback" (tries Groq first, then Cloudflare Workers AI if Groq fails or is not configured).
    translation_provider: str = "fake"
    # Longest text accepted, counted in Unicode characters (not bytes).
    max_text_chars: int = Field(default=5000, ge=1, le=100_000)
    # How long to wait for a provider before answering 504. With "fallback" this budget covers all attempts in the
    # chain, so raise it if a slow, timed-out primary should still leave time for the fallback provider to answer.
    provider_timeout_seconds: float = Field(default=15.0, gt=0, le=120)

    # Who may use the API: a comma-separated list of secret tokens, sent by the app as "Authorization: Bearer <token>".
    # Empty means no check, which is only allowed outside production. Set it only in the environment or .env.
    api_tokens: SecretStr | None = None
    # How many API requests one client may make per minute (protects the paid providers). 0 turns the limit off.
    rate_limit_per_minute: int = Field(default=60, ge=0, le=100_000)

    # Mistral AI (used when a provider is set to "mistral" or as the fallback leg of "fallback"). The key is a
    # secret: set it only in the environment or .env, never in code.
    mistral_api_key: SecretStr | None = None
    mistral_base_url: str = "https://api.mistral.ai"
    mistral_translation_model: str = "mistral-small-latest"
    mistral_stt_model: str = "voxtral-mini-latest"
    mistral_tts_model: str = "voxtral-mini-tts-2603"
    # Optional voices for text-to-speech as JSON, language code to Mistral voice id, for example {"fr": "voice-id"}.
    # Without an entry the provider's default voice is used.
    mistral_tts_voices: str = ""

    # Groq (used when a provider is set to "groq", or as the primary leg of "fallback"). The key is a secret: set it
    # only in the environment or .env, never in code.
    groq_api_key: SecretStr | None = None
    groq_base_url: str = "https://api.groq.com/openai"
    groq_translation_model: str = "openai/gpt-oss-120b"

    # Cloudflare Workers AI (used when a provider is set to "cloudflare", or as the fallback leg of "fallback").
    # Needs both a token and an account id: set them only in the environment or .env, never in code.
    cloudflare_api_token: SecretStr | None = None
    cloudflare_account_id: str = ""
    cloudflare_base_url: str = "https://api.cloudflare.com/client/v4"
    cloudflare_translation_model: str = "@cf/qwen/qwen3-30b-a3b-fp8"


@lru_cache
def get_settings() -> Settings:
    return Settings()
