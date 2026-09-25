"""Levels a voice note for the speech recogniser: decode it, cut the low rumble, bring the volume to a steady level, and write
16 kHz mono WAV.

Quiet or rumbly recordings are a common reason for misheard words. On synthetic French degraded like a poor voice note, sending
the levelled audio instead of the raw file cut the word errors on the worst clip from 100% to 29% (see docs/build-log.md).

It runs as its own short-lived process, ``python -m app.speech.normalize IN OUT`` (see ``preprocess.py``), so a malformed or
malicious file that upsets the audio decoder can crash only this process, never the server, and it is limited in time and memory.
Exit code 0 means OUT was written; anything else means it was not and the caller uses the original file. It never prints
anything about the audio.
"""

import sys
import wave
from pathlib import Path

RATE = 16_000
MAX_SECONDS = 300  # a longer recording is left alone (the original is sent as it is)
_TARGET_RMS_DB = -20.0
_PEAK_LIMIT = 0.97
_HIGHPASS_HZ = 80.0
_MEMORY_LIMIT_BYTES = 2 * 1024**3


def _decode(path: Path):
    import av
    import numpy as np

    container = av.open(str(path))
    try:
        resampler = av.AudioResampler(format="flt", layout="mono", rate=RATE)
        chunks = []
        samples = 0
        for frame in container.decode(audio=0):
            for resampled in resampler.resample(frame):
                block = resampled.to_ndarray().reshape(-1)
                samples += len(block)
                if samples > MAX_SECONDS * RATE:
                    raise ValueError("recording too long to level")
                chunks.append(block)
        for resampled in resampler.resample(None) or []:
            chunks.append(resampled.to_ndarray().reshape(-1))
    finally:
        container.close()
    if not chunks:
        raise ValueError("no audio")
    return np.concatenate(chunks).astype(np.float32)


def level(samples):
    """Cuts everything below 80 Hz (traffic, handling noise) and sets the loudness to a steady level without clipping."""
    import numpy as np

    spectrum = np.fft.rfft(samples)
    spectrum[np.fft.rfftfreq(len(samples), 1 / RATE) < _HIGHPASS_HZ] = 0
    filtered = np.fft.irfft(spectrum, n=len(samples)).astype(np.float32)
    rms = float(np.sqrt(np.mean(filtered**2)))
    if rms < 1e-6:
        raise ValueError("silent")
    scaled = filtered * (10 ** (_TARGET_RMS_DB / 20) / rms)
    peak = float(np.max(np.abs(scaled)))
    if peak > _PEAK_LIMIT:
        scaled = scaled * (_PEAK_LIMIT / peak)
    return scaled.astype(np.float32)


def _write_wav(samples, out_path: Path) -> None:
    import numpy as np

    pcm = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
    with wave.open(str(out_path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(pcm.tobytes())


def normalise(in_path: Path, out_path: Path) -> None:
    _write_wav(level(_decode(in_path)), out_path)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        return 2
    try:
        import resource

        resource.setrlimit(resource.RLIMIT_AS, (_MEMORY_LIMIT_BYTES, _MEMORY_LIMIT_BYTES))
    except (ImportError, ValueError, OSError):
        pass  # not available on this platform; the time limit in the caller still applies
    try:
        normalise(Path(argv[1]), Path(argv[2]))
    except Exception:  # noqa: BLE001 - any failure just means "use the original file"
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
