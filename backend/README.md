# AlterLingua backend

FastAPI modular monolith. Current routes: `GET /health`, `POST /v1/translate`, `POST /v1/audio/translate`, `POST /v1/audio/speak` and `GET /v1/audio/voices`.

The only providers so far are `fake` ones: **development stand-ins that are not a real translator and not real speech recognition**.

## First-time setup

```bash
cd backend
python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
cp .env.example .env        # optional; the defaults work
```

## Start the server

```bash
cd backend
.venv/bin/uvicorn app.main:app_factory --factory --reload --port 8000
```

Interactive docs: http://localhost:8000/docs

## Try it

```bash
curl http://localhost:8000/health

curl -X POST http://localhost:8000/v1/translate \
  -H 'content-type: application/json' \
  -d '{"text":"Are you coming tomorrow?","source":"auto","target":"es","context":"messaging","tone":"natural"}'
```

Expected: `{"translation":"¿Vienes mañana?","source_language":"en","target_language":"es"}`.
The fake provider knows three sample phrases in every language (for example "Are you coming tomorrow?", "Thank you very much!");
other text comes back marked like `[es] your text`.

## Translate a recording

`POST /v1/audio/translate` takes a multipart upload: the recording (`audio`), the `target` language, and optionally
`source` (the language spoken; default `auto`), `context` and `tone`. It returns the transcript, the detected source
language, and the translation. Accepted audio: wav, mp3, m4a/mp4/3gp, aac, ogg/opus, webm, flac, amr (checked by both the
declared type and the file's own bytes), up to 10 MiB by default.

```bash
curl -X POST http://localhost:8000/v1/audio/translate \
  -F "audio=@clip.wav;type=audio/wav" -F "target=es" -F "source=auto"
```

The fake speech provider ignores the audio and "hears" one sample sentence: in the language you name in `source`, or
English for `auto`. Audio is written to a private temporary file, deleted right after processing (also on errors), and
never logged or kept. The transcript is never logged.

## Run the tests

```bash
cd backend
.venv/bin/python -m pytest
```

## Rules

No message text, audio or transcript is stored or logged; keys come only from environment variables (`.env` is git-ignored).
To add a real provider, implement `TranslationProvider` (or `SpeechToTextProvider`) and register it in `app/translation/registry.py` (or `app/speech/registry.py`).


## Translated outgoing voice: `POST /v1/audio/speak`

Same multipart fields as `/v1/audio/translate` (`audio`, `target`, optional `source`, `context`, `tone`) plus an optional
`voice` (an id from `GET /v1/audio/voices`). It runs speech-to-text, translation into `target`, then text-to-speech in the
target language, and answers JSON: `source_language`, `transcript`, `target_language`, `translation` and
`audio` (`content_type`, `voice`, `size_bytes`, `data` = the spoken file, base64). `GET /v1/audio/voices` lists which languages
have a voice. A target with no voice is refused before any recognition or translation (`422 unsupported_language`,
`feature: text_to_speech`). The uploaded audio is deleted after processing and the generated speech is never written to disk.
Set `ALTERLINGUA_TTS_PROVIDER` (default `fake`, a development stand-in that makes a placeholder tone, not speech),
`ALTERLINGUA_TTS_TIMEOUT_SECONDS` and `ALTERLINGUA_MAX_TTS_CHARS`.
