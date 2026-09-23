"""Controlled errors. Every error reaches the client in one shape and never echoes the user's text."""

from typing import Any


class AppError(Exception):
    """An error with a stable machine-readable ``code`` and an HTTP status."""

    status_code = 500
    code = "internal_error"

    def __init__(self, message: str, **details: Any) -> None:
        super().__init__(message)
        self.message = message
        self.details = details

    def to_body(self) -> dict[str, Any]:
        return {"error": {"code": self.code, "message": self.message, **self.details}}


class UnsupportedLanguageError(AppError):
    status_code = 422
    code = "unsupported_language"


class UnsupportedLanguagePairError(AppError):
    status_code = 422
    code = "unsupported_language_pair"


class AutoDetectUnavailableError(AppError):
    status_code = 422
    code = "auto_detect_unavailable"


class SourceLanguageUndetectedError(AppError):
    status_code = 422
    code = "source_language_undetected"


class SameLanguageError(AppError):
    status_code = 422
    code = "same_language"


class TextTooLongError(AppError):
    status_code = 422
    code = "text_too_long"


class UnsupportedAudioTypeError(AppError):
    """The upload is not an audio format we accept, or its contents do not match what it claims to be."""

    status_code = 415
    code = "unsupported_audio_type"


class AudioTooLargeError(AppError):
    status_code = 413
    code = "audio_too_large"


class InvalidAudioError(AppError):
    """The audio is empty or unreadable."""

    status_code = 422
    code = "invalid_audio"


class SpeechNotRecognizedError(AppError):
    """No words were recognised in the audio."""

    status_code = 422
    code = "speech_not_recognized"


class UnauthorizedError(AppError):
    """The request carries no valid API token."""

    status_code = 401
    code = "unauthorized"


class RateLimitedError(AppError):
    """Too many requests from one client in a short time."""

    status_code = 429
    code = "rate_limited"


class ProviderError(AppError):
    """The translation provider failed or returned something unusable."""

    status_code = 502
    code = "provider_error"


class ProviderUnavailableError(AppError):
    """The provider cannot be reached or is not configured."""

    status_code = 503
    code = "provider_unavailable"


class ProviderTimeoutError(AppError):
    status_code = 504
    code = "provider_timeout"


class ConfigurationError(Exception):
    """The server is configured wrongly (for example an unknown provider name). Raised at startup."""
