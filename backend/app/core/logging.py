"""Logging setup. PRIVACY RULE: never log message text, transcripts or translations (CLAUDE.md section 50).

Log only facts such as languages, character counts and latency, written as ``key=value``.

Exceptions are the hidden risk: the message of an exception can contain the user's text (a provider error that repeats the
request, a decoding error that quotes the input), and a traceback prints it. So every log record that carries an exception is
reduced to the exception TYPE and the place it was raised, for every logger in the process, including the web server's own
("Exception in ASGI application"). Nothing else of the exception is ever written.
"""

import logging
import traceback
from types import TracebackType

_installed = False


def _where(tb: TracebackType | None) -> str:
    """The innermost frame of a traceback as ``file.py:line`` (no code, no values)."""
    frames = traceback.extract_tb(tb) if tb is not None else []
    if not frames:
        return "unknown"
    last = frames[-1]
    return f"{last.filename.replace(chr(92), '/').rsplit('/', 1)[-1]}:{last.lineno}"


def _redact_exception(record: logging.LogRecord) -> None:
    if not record.exc_info or record.exc_info[0] is None:
        return
    error_type, _, tb = record.exc_info
    first_line = str(record.msg).split("\n", 1)[0] if not record.args else "error"
    record.msg = f"{first_line} error_type={error_type.__name__} where={_where(tb)}"
    record.args = ()
    record.exc_info = None
    record.exc_text = None
    record.stack_info = None


def install_exception_redaction() -> None:
    """Makes every log record in the process drop exception messages and tracebacks. Safe to call more than once."""
    global _installed
    if _installed:
        return
    _installed = True
    original = logging.getLogRecordFactory()

    def factory(*args: object, **kwargs: object) -> logging.LogRecord:
        record = original(*args, **kwargs)
        _redact_exception(record)
        return record

    logging.setLogRecordFactory(factory)


def configure_logging(level: str = "INFO") -> None:
    install_exception_redaction()
    logging.basicConfig(
        level=level.upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )


def kv(**fields: object) -> str:
    """Formats ``key=value`` pairs for a log line."""
    return " ".join(f"{key}={value}" for key, value in fields.items())
