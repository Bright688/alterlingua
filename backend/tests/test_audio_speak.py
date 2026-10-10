"""POST /v1/audio/speak and GET /v1/audio/voices: voice in, translated speech out."""

import base64
import io
import logging
import wave

import pytest

from app.core.errors import ProviderError, ProviderTimeoutError
from app.speech.tts_provider import TextToSpeechProvider, TtsCapabilities, TtsRequest, TtsResult, Voice
from app.translation.languages import LANGUAGES
from tests.audio_helpers import StubSpeech, make_wav
from tests.conftest import SpyProvider, make_client

SECRET = "please wire 4200 euros for invoice XYZ-9917"
TARGETS = sorted(LANGUAGES)


class StubTts(TextToSpeechProvider):
    """A configurable text-to-speech stand-in that records what it is asked to say."""

    name = "stub-tts"

    def __init__(self, languages=tuple(LANGUAGES), error=None, delay=0.0, voices_per_language=1):
        self._voices = tuple(
            Voice(f"{code}-v{n}", code, LANGUAGES[code].default_locale) for code in languages for n in range(1, voices_per_language + 1)
        )
        self.error = error
        self.delay = delay
        self.requests: list[TtsRequest] = []

    def capabilities(self):
        return TtsCapabilities(self._voices)

    async def synthesize(self, request):
        import asyncio

        self.requests.append(request)
        if self.delay:
            await asyncio.sleep(self.delay)
        if self.error:
            raise self.error
        return TtsResult(make_wav(0.2), "audio/wav")


def post(client, source="en", target="es", data=None, **kw):
    return client.post(
        "/v1/audio/speak",
        files={"audio": ("clip.wav", make_wav(), "audio/wav")},
        data={"target": target, "source": source, **(data or {})},
        **kw,
    )


def wav_of(response) -> wave.Wave_read:
    audio = response.json()["audio"]
    return wave.open(io.BytesIO(base64.b64decode(audio["data"])), "rb")


def logged(caplog) -> str:
    return "\n".join(record.getMessage() for record in caplog.records)


# ---- the flow: voice, text, translation, speech ----

def test_english_voice_becomes_spanish_speech():
    client = make_client(stt=StubSpeech(transcripts={"en": "Are you coming tomorrow?"}), provider=SpyProvider(languages=tuple(LANGUAGES)))
    response = post(client, source="en", target="es")
    assert response.status_code == 200
    body = response.json()
    assert (body["source_language"], body["target_language"]) == ("en", "es")
    assert body["transcript"] == "Are you coming tomorrow?"
    assert body["translation"] == "<es>"
    assert body["audio"]["content_type"] == "audio/wav"


@pytest.mark.parametrize("target", TARGETS)
def test_every_supported_target_language_is_translated_and_spoken(target):
    tts = StubTts()
    client = make_client(stt=StubSpeech(languages=tuple(LANGUAGES)), provider=SpyProvider(languages=tuple(LANGUAGES)), tts=tts)
    response = post(client, source="en" if target != "en" else "fr", target=target)
    assert response.status_code == 200, response.text
    assert response.json()["target_language"] == target
    # The voice that speaks is one for the TARGET language, and what it says is the translation, not the transcript.
    assert tts.requests[-1].voice.language == target
    assert tts.requests[-1].text == response.json()["translation"]
    with wav_of(response) as wav:
        assert wav.getnframes() > 0


@pytest.mark.parametrize("source,target", [("es", "en"), ("ja", "en"), ("en", "fr"), ("en", "ja"), ("fr", "de"), ("zh", "it"), ("nl", "es")])
def test_other_directions_work_the_same_way(source, target):
    tts = StubTts()
    stt = StubSpeech(languages=tuple(LANGUAGES))
    client = make_client(stt=stt, provider=SpyProvider(languages=tuple(LANGUAGES)), tts=tts)
    response = post(client, source=source, target=target)
    assert response.status_code == 200
    assert response.json()["source_language"] == source
    assert tts.requests[-1].voice.language == target


def test_automatic_source_detection_works():
    tts = StubTts()
    client = make_client(stt=StubSpeech(detected="fr"), provider=SpyProvider(languages=tuple(LANGUAGES)), tts=tts)
    response = post(client, source="auto", target="es")
    assert response.status_code == 200
    assert response.json()["source_language"] == "fr"


def test_speech_already_in_the_target_language_is_spoken_as_it_is():
    provider = SpyProvider(languages=tuple(LANGUAGES))
    tts = StubTts()
    client = make_client(stt=StubSpeech(transcripts={"es": "hola"}), provider=provider, tts=tts)
    response = post(client, source="es", target="es")
    assert response.status_code == 200
    assert provider.calls == []
    assert tts.requests[0].text == "hola"


def test_with_the_development_providers_the_whole_route_gives_a_valid_wav():
    client = make_client()
    response = post(client, source="en", target="fr")
    assert response.status_code == 200
    assert response.json()["translation"] == "Tu viens demain ?"
    with wav_of(response) as wav:
        assert wav.getframerate() == 16_000 and wav.getnframes() > 0


# ---- capability checks come first ----

def test_a_target_with_no_voice_is_refused_before_any_recognition_or_translation():
    stt, provider, tts = StubSpeech(), SpyProvider(languages=tuple(LANGUAGES)), StubTts(languages=("en", "fr"))
    response = post(make_client(stt=stt, provider=provider, tts=tts), source="en", target="ja")
    assert response.status_code == 422
    error = response.json()["error"]
    assert (error["code"], error["feature"], error["role"], error["language"]) == ("unsupported_language", "text_to_speech", "target", "ja")
    assert error["supported"] == ["en", "fr"]
    assert stt.calls == [] and provider.calls == [] and tts.requests == []


def test_a_target_the_translator_cannot_handle_is_refused_first():
    tts = StubTts()
    response = post(make_client(provider=SpyProvider(languages=("en", "fr")), tts=tts), source="en", target="ja")
    assert response.status_code == 422
    assert tts.requests == []


def test_an_unsupported_spoken_language_is_refused_before_synthesis():
    tts = StubTts()
    response = post(make_client(stt=StubSpeech(languages=("en",)), provider=SpyProvider(languages=tuple(LANGUAGES)), tts=tts), source="ja", target="en")
    assert response.status_code == 422
    assert response.json()["error"]["role"] == "source"
    assert tts.requests == []


def test_nothing_is_spoken_when_no_speech_was_recognised():
    from app.core.errors import SpeechNotRecognizedError

    tts = StubTts()
    response = post(make_client(stt=StubSpeech(error=SpeechNotRecognizedError("none")), tts=tts))
    assert response.status_code == 422
    assert tts.requests == []


# ---- voices ----

def test_the_voices_route_lists_what_can_be_spoken():
    response = make_client(tts=StubTts(languages=("en", "es", "ja"), voices_per_language=2)).get("/v1/audio/voices")
    assert response.status_code == 200
    body = response.json()
    assert body["provider"] == "stub-tts"
    assert [item["language"] for item in body["languages"]] == ["en", "es", "ja"]
    assert [v["id"] for v in body["languages"][1]["voices"]] == ["es-v1", "es-v2"]


def test_the_development_provider_offers_a_voice_for_each_initial_language():
    body = make_client().get("/v1/audio/voices").json()
    assert sorted(item["language"] for item in body["languages"]) == TARGETS


def test_a_chosen_voice_is_used_when_it_exists_for_the_language():
    tts = StubTts(voices_per_language=2)
    response = post(make_client(tts=tts), target="fr", data={"voice": "fr-v2"})
    assert response.status_code == 200
    assert response.json()["audio"]["voice"] == "fr-v2"


def test_a_voice_that_does_not_exist_for_the_language_is_refused():
    tts = StubTts(voices_per_language=2)
    response = post(make_client(tts=tts), target="fr", data={"voice": "es-v1"})
    assert response.status_code == 422
    assert tts.requests == []


def test_without_a_voice_choice_the_first_voice_for_the_language_speaks():
    tts = StubTts(voices_per_language=2)
    post(make_client(tts=tts), target="fr")
    assert tts.requests[0].voice.id == "fr-v1"


# ---- failures ----

def test_a_translation_outage_still_fails_translated_voice_rather_than_speaking_nothing():
    # /v1/audio/translate may return the transcript with an empty translation; speaking an empty translation would be nonsense.
    tts = StubTts()
    response = post(make_client(SpyProvider(error=ProviderError("down")), tts=tts), source="en", target="fr")
    assert response.status_code == 502 and response.json()["error"]["code"] == "provider_error"
    assert tts.requests == []


def test_a_speech_provider_failure_is_a_controlled_error():
    response = post(make_client(tts=StubTts(error=ProviderError("boom"))))
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "provider_error"


def test_slow_synthesis_times_out():
    response = post(make_client(tts=StubTts(delay=1.0), tts_timeout_seconds=0.05))
    assert response.status_code == 504


def test_an_unexpected_error_from_the_provider_does_not_leak_text():
    response = post(make_client(tts=StubTts(error=RuntimeError(SECRET)), stt=StubSpeech(transcripts={"en": SECRET})))
    assert response.status_code == 500
    assert SECRET not in response.text


def test_a_translation_too_long_to_speak_is_refused():
    provider = SpyProvider(languages=tuple(LANGUAGES), result=None)
    tts = StubTts()
    response = post(make_client(stt=StubSpeech(transcripts={"en": "x" * 50}), provider=provider, tts=tts, max_tts_chars=2), source="en", target="en")
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "text_too_long"
    assert tts.requests == []


def test_a_too_large_upload_is_refused():
    client = make_client(max_audio_bytes=1024)
    response = client.post("/v1/audio/speak", files={"audio": ("clip.wav", make_wav(2.0), "audio/wav")}, data={"target": "es"})
    assert response.status_code == 413


def test_a_bad_request_names_the_field_and_not_the_value():
    response = make_client().post("/v1/audio/speak", files={"audio": ("clip.wav", make_wav(), "audio/wav")}, data={"target": SECRET})
    assert response.status_code == 422
    assert SECRET not in response.text


# ---- privacy: audio and text ----

def test_the_uploaded_audio_is_deleted_after_speaking(tmp_path):
    stt = StubSpeech()
    response = post(make_client(stt=stt, tts=StubTts(), temp_dir=str(tmp_path)))
    assert response.status_code == 200
    assert stt.file_existed_during_call == [True]
    assert list(tmp_path.iterdir()) == []


def test_the_uploaded_audio_is_deleted_when_speech_synthesis_fails(tmp_path):
    response = post(make_client(tts=StubTts(error=ProviderTimeoutError("slow")), temp_dir=str(tmp_path)))
    assert response.status_code == 504
    assert list(tmp_path.iterdir()) == []


def test_generated_speech_is_never_written_to_disk(tmp_path):
    post(make_client(tts=StubTts(), temp_dir=str(tmp_path)))
    assert list(tmp_path.iterdir()) == []


def test_logs_hold_no_transcript_translation_or_audio(caplog, tmp_path):
    caplog.set_level(logging.DEBUG)
    tts = StubTts()
    client = make_client(stt=StubSpeech(transcripts={"en": SECRET}), provider=SpyProvider(languages=tuple(LANGUAGES)), tts=tts, temp_dir=str(tmp_path))
    response = post(client)
    assert response.status_code == 200
    text = logged(caplog)
    assert "audio_speech_completed" in text
    assert SECRET not in text
    assert "<es>" not in text
    assert response.json()["audio"]["data"] not in text
    for field in ("target_language=es", "source_language=en", "voice=es-v1"):
        assert field in text


def test_an_unknown_speech_provider_name_is_a_configuration_error():
    from app.core.config import Settings
    from app.core.errors import ConfigurationError
    from app.speech.registry import create_tts_provider

    with pytest.raises(ConfigurationError):
        create_tts_provider(Settings(_env_file=None, tts_provider="nope"))
