# AlterLingua privacy and security

This document records what AlterLingua does with private communication, and the result of the privacy and security audit of the
MVP (2026-09-21). It is written from the code and from tests, not from intentions. Where something was checked by a test, the test
is named. Where something needs a person on a phone, it says so.

## 1. What is kept, where, and for how long

| Data | Where | Kept | Contains |
| --- | --- | --- | --- |
| Settings | DataStore `user_settings` (phone) | Until changed or uninstall | Languages, assistance mode, switches, reminder time |
| Personal Language Map | Room `language_map.db` (phone, private) | Until "Delete all learning data" or uninstall | Words and short phrases (at most 6 words) met in your messages, counts, states, a meaning in your language, dates. **Never a whole message, sender, chat or time of day.** |
| Progress history | Room `progress.db` (phone, private) | Same | Per language and day: numbers only (words met, assisted, lesson cards, practice attempts, state counts) |
| Today's lesson | DataStore `daily_lesson` (phone) | Until the next lesson or erase | Up to three words or phrases, meanings, counts, reasons |
| Translated-notification text | Memory only | Until the WhatsApp notification is dismissed or the process ends | The translated lines of one conversation (at most 5) |
| "Already translated" list | Memory only | 2 hours, at most 300 | One-way hashes, no text |
| Voice recordings and generated speech | App-private cache folders | Deleted when processed; a start-up sweep removes anything older than an hour | Audio (see section 4) |
| Message and audio content on the server | Nowhere | Processed in memory or a private temporary file, deleted at once | See section 3 |

Nothing is backed up (`allowBackup=false`, empty backup and device-transfer rules). Nothing is written to shared or external storage.
The databases are not encrypted by the app; they rely on Android's app sandbox and the phone's own encryption.

**User control.** Settings has *Delete all learning data* (with a confirmation): it removes the Personal Language Map of every
language, the progress history, today's lesson, temporary audio, and the in-memory message state. Settings are kept. *Learn from my
messages* and *Incoming translation* can each be turned off and are honoured everywhere (the audit added tests for the former).

## 2. What leaves the phone

Only what the user chooses to translate, when they choose it, goes to the AlterLingua backend:

- the text in the composer, when Translate is tapped;
- a recording, when the microphone, a shared voice note, pronunciation practice or a translated voice message is used;
- one word (never the surrounding message), when a meaning is looked up for a lesson, the reading tool or a voice note's useful words;
- an incoming WhatsApp message, when incoming translation is on, so it can be translated into the user's language.

The backend sends this text or audio to the configured translation, speech-recognition and speech-synthesis providers. **Those
providers are third parties and see the content.** Before a real provider is connected, its data-handling terms (retention, use
for training, region) must be reviewed and stated to users; this is a launch requirement, not something the code can decide.

No analytics, advertising, crash-reporting or other third-party SDK is included in the Android app.

## 3. Backend

- **Logging.** Only facts are logged: languages, character counts, byte sizes, voice id, provider names, status codes and
  timings. Tests assert that message text, transcripts, translations and audio never appear in the logs, for success, failure and
  validation errors. *Audit fix:* exception messages and tracebacks are removed from every log record in the process (see finding H1).
- **Storage.** Nothing is persisted: no database, no file of text. Uploaded audio is a private temporary file (mode 0600), deleted in
  every case (success, error, timeout). Generated speech is never written to disk.
- **Errors.** One controlled error shape that never repeats the user's text; unexpected errors answer a generic 500.
- **Responses** carry `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. The interactive API pages are disabled when
  `ALTERLINGUA_ENVIRONMENT=production`.
- **Secrets.** Provider keys are read from environment variables on the server only (`ALTERLINGUA_*`). `.env` is git-ignored and
  `.env.example` holds no values. There is no key in the Android app.

## 4. Audio

Every flow deletes what it created: the keyboard recording, the pronunciation recording, the shared voice-note copy, the uploaded
file on the server, and the generated speech (deleted when the user records again, closes the screen or erases data; the file
must outlive the tap on Share because the receiving app reads it afterwards). Recordings are only in the app's private cache
folders (`voice`, `pronunciation`, `shared_audio`, `spoken_audio`). *Audit fix:* a sweep at app start removes audio older than an
hour that a crash or a killed process left behind. Shared audio is read only from `content://` addresses, checked by its first
bytes, size-limited, and copied at once so nothing depends on the temporary permission afterwards. The Share sheet reads generated
speech through a FileProvider that is not exported and reaches only `cache/spoken_audio/`.

## 5. Notifications

Only WhatsApp's notifications are read (the debug build also reads its own test messages; the release build has no such code).
Hidden content (secret visibility, placeholder text), group summaries, ongoing and non-message notifications are skipped. AlterLingua's
own translated notification is private on the lock screen (a generic public version is shown), quiet, and removed when the original
goes away. Its text is held in memory only. **Limit:** any other app the user has given notification access, and Android's optional
*Notification history*, can see notifications on the phone, including AlterLingua's translated one; that is Android's behaviour.

## 6. Network and HTTPS assumptions

- The Android release build has **no backend address** built in and **allows HTTPS only** (no cleartext exception in the main manifest).
  Debug builds may use plain HTTP to the phone's own loopback address (`127.0.0.1`, `localhost`, `10.0.2.2`) for `adb reverse` development,
  through a network-security file that exists only in debug builds. A test checks both.
- The backend itself speaks plain HTTP. **In production it must run behind a TLS-terminating proxy or load balancer, and no HTTP port
  may be public.** Set HSTS at that proxy. There is no certificate pinning; the app trusts the system certificate store.
- The backend has **no authentication, no rate limiting and no usage accounting yet** (finding H3). It must not be exposed to the internet
  until those exist.

## 7. Android permissions

`RECORD_AUDIO` (only requested when a voice feature is first used, after an explanation), `INTERNET`, `ACCESS_NETWORK_STATE` (tells
"offline" from "server unreachable" after a failure), `POST_NOTIFICATIONS` (Android 13+, for the translated notification). No storage,
contacts, location, phone, accessibility or overlay permission. Notification access is granted by the user in Android settings. A
test asserts this exact list. Exported components: the launcher screen, the audio Share target, and the keyboard and notification-listener
services (each protected by the system-only bind permission). The FileProvider and the voice-message screen are not exported.

## 8. Audit findings

Severity means the harm if it were exploited on a shipped product. **No CRITICAL finding.**

### CRITICAL
None found. No secret is in the code, the Android app or the repository; no message text is persisted; no audio is retained.

### HIGH
- **H1. Message text could reach the server logs through a crash. FIXED.** When an unexpected error escaped the application, the web
  server logged a traceback whose last line is the exception message; a provider error that repeats the request (common) would put the
  user's message in the logs. The old test did not run through the real server, so it passed. *Fix:* every log record carrying an
  exception is reduced to its type and the file and line where it was raised. *Tests:* a unit test of the redaction and a test that runs
  the real server with a provider that raises an error containing a secret, and checks the server's log. Mutation check: switching the
  redaction off makes both fail.
- **H2. No way for the user to delete what the app learned. FIXED.** The Settings row said "Available later" while the Personal Language
  Map (word fragments) and progress history accumulated. *Fix:* *Delete all learning data* with a confirmation, removing the map for every
  language, progress, the lesson, temporary audio and in-memory message state; each step is tried even if another fails and the result is
  reported honestly. Tests: `PrivateDataTest`, `EraseLearningDataTest`, and a compile-checked dialog test.
- **H3. The backend has no authentication or rate limiting. NOT FIXED (needs the Authentication milestone).** Anyone who can reach it can
  send text and audio and spend provider credits, and content is unprotected in transit unless a TLS proxy is in front. It does not affect
  the current development setup (loopback only; the release app has no address). *Required before any public deployment:* authentication
  tied to the account/entitlement system, per-user rate limits and quotas, TLS at the proxy, a request-size cap at the proxy (the app also
  caps audio at 10 MB and text at 5000 characters), and no public HTTP port. A shared key inside the Android app is **not** an acceptable
  substitute (it could be extracted).

### MEDIUM
- **M1. Audio could linger after a crash. FIXED.** Start-up sweep of the four audio cache folders (older than one hour) plus erase-on-request.
- **M2. Printing an object could put a message in a log or crash report. FIXED.** Every class that holds a message, transcript,
  translation or notification text now prints as `Name(redacted)`; a test prints all of them with a secret inside. The app also writes
  nothing to the system log (a test scans the sources) and includes no crash-reporting service.
- **M3. Responses could be cached, and API docs were public. FIXED.** `Cache-Control: no-store`, `nosniff`, and docs off in production.
- **M4. Phrases from private messages are stored on the phone. NOT CHANGED (product decision).** The map keeps up to six-word fragments,
  which can be personal. Mitigations: names, numbers, e-mail addresses and links are never kept; a whole message longer than three words
  is never kept; storage is on the phone only and not backed up; the user can turn learning off or erase everything. *Recommendation:*
  decide whether *Learn from my messages* should start off (opt-in) and whether old rarely-met phrases should expire.
- **M5. Third-party providers will see message content.** Inherent to cloud translation and speech; see section 2. Needs a privacy policy,
  provider terms review and user-facing disclosure before launch.
- **M6. Notification content is visible to other notification listeners and Notification history.** See section 5. Cannot be prevented by
  the app; the translated notification is private on the lock screen.

### LOW
- **L1.** Screens showing transcripts and translations appear in Android's Recents thumbnails and can be screenshotted (no `FLAG_SECURE`).
  Left as-is because screenshots are a legitimate user need; can be added per screen.
- **L2.** No certificate pinning (system trust store only). Acceptable for the MVP; consider for production.
- **L3.** The release build is not minified or obfuscated. There is nothing secret in it; enable for size and reverse-engineering cost.
- **L4.** The databases are not encrypted by the app (Android's sandbox and device encryption apply).
- **L5.** The server's access log records client IP addresses and request paths (never bodies or query strings). Configure retention or
  anonymisation where it is deployed.
- **L6.** The audio Share target is exported, as a share target must be. It accepts only `content://` addresses, checks the file by its
  first bytes and size, and reads only what the sending app could already read.
- **L7.** The "already translated" list stores unsalted SHA-256 hashes of messages, in memory for two hours; a short common message could
  in principle be guessed from its hash by someone who can read the app's memory, which already exposes far more.
- **L8.** The debug build has an exported test receiver (`DebugTestMessageReceiver`) that lets any app on the phone post a fake message into
  the debug build. It is not in release builds (verified in the merged release manifest).
- **L9.** The library `ProfileInstallReceiver` is exported (Android's standard, protected by the system DUMP permission).
- **L10.** The Git repository has no commits yet. A dry run of adding everything shows no key, database, keystore, `local.properties`, build
  output or `.env`; the ignore files were extended (`*.db`, `*.apk`, `*.aab`, `*.log`, `*.jks`, `*.keystore`, `local.properties`) and a test
  scans the repository for secret-shaped strings and for missing ignore rules.

## 9. The ten checks

1. **Message contents not persisted unnecessarily.** Verified: the only stores are settings, the map (units and counts), progress (numbers),
   today's lesson (units), and audio in private cache deleted after use. A data-type test shows the progress rows hold no text. The map holds
   short fragments (M4). Server: nothing persisted.
2. **Not in production logs.** Verified for the app (no logging at all) and the server (tests for every route; H1 fixed).
3. **Temporary voice files deleted.** Verified per flow by tests (deleted on success, failure, cancel, leaving, next card), plus a crash sweep (M1).
4. **No API secrets in Android.** Verified: no key, token or provider address in the app; the release build has no server address; a source test scans for secret shapes.
5. **No secrets in Git.** Verified: no commits yet, dry-run clean, ignore rules extended, scan test added.
6. **HTTPS assumptions documented.** Section 6.
7. **Permissions minimal.** Section 7, asserted by a test.
8. **Notification content conservative.** Section 5: WhatsApp only, hidden content skipped, private on the lock screen, memory-only, removed with the original.
9. **Learning signals retain only what is necessary.** Units, counts, states and dates; erasable. M4 records the remaining judgement call.
10. **Error and crash logging cannot leak text.** H1 and M2 fixed; no crash-reporting SDK; nothing written to the system log.

## 10. Not verified here

Nothing in this audit was run on a phone. Manual checks worth doing: Android's *Notification history* and lock-screen behaviour of the
translated notification, `adb shell run-as com.alterlingua.app ls cache` after using each voice feature, and that *Delete all learning data*
empties `databases/language_map.db` and `databases/progress.db`. A real provider must be reviewed separately when it is added.

## Handwriting (added September 2026)

The keyboard has a handwriting pad (a pen key next to the space bar) for all eight languages. It uses Google ML Kit Digital Ink Recognition, which is **not open source**.
- **What leaves the phone:** only the request to download the recognition model for a language (about 20 MB, once per language, from Google). This uses the network and, like any download, tells Google's servers that the app wants that language's model. **What is drawn is recognised on the phone and is not sent anywhere by AlterLingua.**
- **What is kept:** nothing. The strokes live in memory until they are turned into text, cleared, or the keyboard is closed; they are not stored, logged or added to the Personal Language Map. The chosen text is typed into the field like any typing.
- **If the model cannot be downloaded** (offline, or no Google Play services) the pad says so and offers Try again; the normal keys keep working.
- **Not verified:** what ML Kit itself does on the phone beyond its published terms (https://developers.google.com/ml-kit/terms). The licence notice is in Settings, Open source licences.

## Backend providers: Mistral (added September 2026)

Translation, speech-to-text and text-to-speech can be sent to Mistral AI (`ALTERLINGUA_*_PROVIDER=mistral`). That means the text of a message (and, for voice, the recording) leaves our server for Mistral's when the user presses Translate, uses the microphone or asks to hear a translation.
- **Training:** Mistral's free "Experiment" plan can use inputs and outputs for training unless it is turned off in its console (checked in Mistral's public documentation in September 2026; terms change, so re-check). Zero data retention is offered only on the paid plan and only for some endpoints. **Before real messages are sent, turn training off and prefer the paid plan with zero retention.** Until then, only sample text must be used.
- **Retention by Mistral:** for standard endpoints inputs and outputs are kept up to 30 days for abuse monitoring unless zero retention applies.
- **What our server keeps:** nothing. No message, transcript or audio is stored or logged; temporary audio is deleted after use.
- **Access:** the API needs a secret token; requests are rate limited. The token in an app can be extracted, so real accounts remain a later milestone.
- **Not verified:** what Mistral does with data beyond its published terms; whether its audio endpoints qualify for zero retention.
