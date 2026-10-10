"""Runs the levelling step (``normalize.py``) in a separate process, with a time limit, and hands back a temporary WAV copy.

The copy lives next to the original in the same private temporary folder, is created with mode 0600, and must be deleted by the
caller as soon as it has been used. If levelling is not possible (unreadable or too-long audio, a timeout, the tools missing)
the answer is None and the caller simply uses the original file: levelling is an improvement, never a requirement.
"""

import asyncio
import contextlib
import os
import sys
import tempfile
from pathlib import Path

_TIMEOUT_SECONDS = 20.0


async def normalised_copy(source: Path, *, timeout: float = _TIMEOUT_SECONDS) -> Path | None:
    fd, name = tempfile.mkstemp(prefix="alterlingua-", suffix=".wav", dir=source.parent)  # mode 0600
    os.close(fd)
    out = Path(name)
    try:
        process = await asyncio.create_subprocess_exec(
            sys.executable, "-m", "app.speech.normalize", str(source), str(out),
            stdin=asyncio.subprocess.DEVNULL, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL,
        )
        try:
            code = await asyncio.wait_for(process.wait(), timeout)
        except TimeoutError:
            with contextlib.suppress(ProcessLookupError):
                process.kill()
            await process.wait()
            code = -1
        if code == 0 and out.stat().st_size > 44:  # more than a bare WAV header
            return out
    except Exception:  # noqa: BLE001 - any trouble means "use the original"
        pass
    out.unlink(missing_ok=True)
    return None
