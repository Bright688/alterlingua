"""The levelling step: real audio, the real separate process. Synthetic recordings only."""

import io
import math
import struct
import wave

import pytest

from app.speech import normalize
from app.speech.preprocess import normalised_copy

pytestmark = pytest.mark.anyio

RATE = 16_000


@pytest.fixture
def anyio_backend():
    return "asyncio"


def quiet_rumbly_wav(seconds=1.0, speech_amplitude=0.02, rumble_amplitude=0.08) -> bytes:
    """A quiet 'voice' (440 Hz) buried under a much louder 30 Hz rumble, as 16-bit mono WAV."""
    frames = []
    for i in range(int(RATE * seconds)):
        t = i / RATE
        value = speech_amplitude * math.sin(2 * math.pi * 440 * t) + rumble_amplitude * math.sin(2 * math.pi * 30 * t)
        frames.append(int(value * 32767))
    out = io.BytesIO()
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(struct.pack(f"<{len(frames)}h", *frames))
    return out.getvalue()


def read(path):
    with wave.open(str(path)) as w:
        samples = struct.unpack(f"<{w.getnframes()}h", w.readframes(w.getnframes()))
        return samples, w.getframerate(), w.getnchannels(), w.getsampwidth()


def rms_db(samples):
    return 20 * math.log10(math.sqrt(sum(s * s for s in samples) / len(samples)) / 32768)


def band_power(samples, frequency):
    """How strong one frequency is (a single-bin Fourier sum)."""
    re = sum(s * math.cos(2 * math.pi * frequency * i / RATE) for i, s in enumerate(samples))
    im = sum(s * math.sin(2 * math.pi * frequency * i / RATE) for i, s in enumerate(samples))
    return math.hypot(re, im) / len(samples)


async def test_a_quiet_rumbly_recording_comes_back_as_16khz_mono_wav_at_a_steady_level_without_the_rumble(tmp_path):
    source = tmp_path / "note.wav"
    source.write_bytes(quiet_rumbly_wav())
    out = await normalised_copy(source)
    assert out is not None and out.exists() and out != source
    samples, rate, channels, width = read(out)
    assert (rate, channels, width) == (16_000, 1, 2)
    assert rms_db(samples) == pytest.approx(-20, abs=1.5)  # brought to a steady level
    assert band_power(samples, 30) < 0.05 * band_power(samples, 440)  # the rumble is gone, the 'voice' stays
    assert max(abs(s) for s in samples) < 32767  # no clipping


async def test_the_original_file_is_never_changed(tmp_path):
    source = tmp_path / "note.wav"
    original = quiet_rumbly_wav()
    source.write_bytes(original)
    await normalised_copy(source)
    assert source.read_bytes() == original


async def test_the_copy_is_private_and_next_to_the_original(tmp_path):
    source = tmp_path / "note.wav"
    source.write_bytes(quiet_rumbly_wav())
    out = await normalised_copy(source)
    assert out.parent == tmp_path and (out.stat().st_mode & 0o077) == 0  # mode 0600


async def test_a_file_that_is_not_audio_gives_none_and_leaves_nothing_behind(tmp_path):
    source = tmp_path / "junk.wav"
    source.write_bytes(b"this is not audio at all" * 100)
    assert await normalised_copy(source) is None
    assert [p.name for p in tmp_path.iterdir()] == ["junk.wav"]


async def test_a_silent_recording_gives_none_because_there_is_nothing_to_level(tmp_path):
    silence = io.BytesIO()
    with wave.open(silence, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"\x00\x00" * RATE)
    source = tmp_path / "silence.wav"
    source.write_bytes(silence.getvalue())
    assert await normalised_copy(source) is None
    assert [p.name for p in tmp_path.iterdir()] == ["silence.wav"]


async def test_a_slow_run_is_stopped_and_gives_none_and_leaves_nothing_behind(tmp_path):
    source = tmp_path / "note.wav"
    source.write_bytes(quiet_rumbly_wav())
    assert await normalised_copy(source, timeout=0.001) is None
    assert [p.name for p in tmp_path.iterdir()] == ["note.wav"]


def test_a_recording_longer_than_the_limit_is_left_alone(tmp_path, monkeypatch):
    monkeypatch.setattr(normalize, "MAX_SECONDS", 0.5)
    source = tmp_path / "note.wav"
    source.write_bytes(quiet_rumbly_wav(seconds=1.0))
    assert normalize.main(["x", str(source), str(tmp_path / "out.wav")]) == 1
    assert not (tmp_path / "out.wav").exists()


def test_the_program_says_nothing_about_the_audio_and_refuses_bad_usage(tmp_path, capsys):
    source = tmp_path / "junk.wav"
    source.write_bytes(b"secret spoken words" * 100)
    assert normalize.main(["x", str(source), str(tmp_path / "out.wav")]) == 1
    assert normalize.main(["x"]) == 2
    captured = capsys.readouterr()
    assert captured.out == "" and captured.err == ""


def test_levelling_never_amplifies_past_the_peak_limit():
    samples = normalize.level(__import__("numpy").array([0.0, 0.9, -0.9, 0.0] * 4000, dtype="float32"))
    assert float(abs(samples).max()) <= 0.97 + 1e-6
