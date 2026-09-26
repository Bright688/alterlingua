# AlterLingua — Progress

Status values: `NOT STARTED`, `IN PROGRESS`, `IMPLEMENTED`, `MANUALLY VERIFIED`, `BLOCKED`.
`IMPLEMENTED` means the code exists and passed automated checks. It is **not** the same as
`MANUALLY VERIFIED`, which means a person confirmed it on a real device.

Last updated: 2026-09-20 (Personal Language Map implemented)

## Numbering note

The project owner counts from the app shell: their "Milestone 1" = app shell (row 2b), their "Milestone 2" = onboarding
(CLAUDE.md milestone 3). `CLAUDE.md` section 47 numbers the work differently (1 = environment, 2 = Android foundation,
3 = onboarding). This file uses the CLAUDE.md numbers and adds the owner's numbers in brackets.
Please confirm which numbering to use going forward.

## Milestones

| # | Milestone | Status | Notes |
|---|---|---|---|
| 1 | Environment and repository | IMPLEMENTED | Toolchain checked; repo on `main`; no commits yet. |
| 2 | Native Android foundation | MANUALLY VERIFIED | Builds, tests pass, and the app runs on the phone (via 2b). |
| **2b** | **App shell: theme, navigation, Home, Learn, Words, Progress, Settings** (owner's "Milestone 1") | **MANUALLY VERIFIED** | Builds, 20 unit tests pass, lint has 0 errors. Owner confirmed it runs and works on the phone. |
| 3 | Onboarding (owner's "Milestone 2") | IMPLEMENTED | Now eight steps (see row 4), answers saved with DataStore, 8 languages shown by their own names, language switching in Settings. Purpose, level and Adaptive text name the selected target language; the level is kept per language. 91 unit tests pass. Not yet tried on the phone. |
| 4 | Android system setup and permissions | IMPLEMENTED | Keyboard enable and select, notification-listener access and microphone permission: explanation screens, live status, Android settings navigation. 84 unit tests pass, lint 0 errors. Not yet tried on the phone. |
| 5 | FastAPI backend | IMPLEMENTED | Foundation only: `GET /health` and `POST /v1/translate` with a provider abstraction and a development-only fake provider (no real translation yet). 66 backend tests pass; server started and both routes called with curl on this machine. Not connected to the Android app. |
| 6 | Basic AlterLingua IME | IMPLEMENTED | Real Android input method: QWERTY, shift and caps lock, backspace (holds to repeat), space, enter, punctuation, two number/symbol pages, globe key. 123 unit tests pass, lint 0 errors. **Not yet tried on the phone: Gate A needs you.** |
| 7 | Translation toolbar | IMPLEMENTED | Toolbar on the keyboard: `AUTO → XX` chip (XX = saved target language), language chooser with native names, Translate and microphone buttons (shown, not working yet), Settings button that opens the app. 144 unit tests pass, lint 0 errors. **Not yet tried on the phone.** |
| 8 | Outgoing WhatsApp text translation | IMPLEMENTED | Translate reads the composer text, calls `POST /v1/translate` with the selected target, replaces the text, offers Undo; every failure keeps the original. 199 unit tests pass; the real client was also run against the real backend. The backend only has a **development stand-in translator** (a few sample phrases). **Not yet tried on the phone: Gate B needs you.** |
| 9 | Backend voice translation | IMPLEMENTED | `POST /v1/audio/translate`: audio, speech-to-text, source-language detection, translation, with a speech-provider abstraction and capability checks. 162 backend tests pass; the server was started and the route called with curl on this machine. Only a **development stand-in** recogniser exists (it does not recognise speech). Not connected to the Android app. |
| 10 | Keyboard microphone | IMPLEMENTED | Microphone opens a voice panel: records, uploads to `POST /v1/audio/translate`, shows Original and Translated, and inserts the translation as text (never sends). 240 unit tests pass; the real client was also run against the real backend. The backend recogniser is a **development stand-in** (it does not recognise speech). **Not yet tried on the phone: Gate C needs you.** |
| 11 | Incoming text translation (WhatsApp, Telegram, Messenger, Signal) | IMPLEMENTED | Prototype: a notification listener reads notifications from a small whitelist of known chat apps (`IncomingSources`: WhatsApp, WhatsApp Business, Telegram, Messenger, Signal — widened from WhatsApp-only on 2026-09-23), translates a message into the user's selected native language through the backend, and posts its own AlterLingua notification ("Marie / Are you coming tomorrow? / Translated from Français"). 303 unit tests pass; the real client was run against the real backend. The backend translator is a **development stand-in** (a few sample phrases). **Not yet tried on the phone with a real message from Telegram, Messenger or Signal (WhatsApp was manually verified earlier).** |
| 12 | Learning event extraction | IMPLEMENTED | Foundation: a language-aware linguistic-analysis abstraction, candidate extraction (words, phrases, expressions) for all eight languages including 中文 and 日本語 with dictionary segmentation, a `LearningEvent` pipeline hooked into outgoing text, voice and incoming translations, and an in-memory exposure store. Only short units are kept, never messages. 343 unit tests pass. **No screen shows the units yet (Words UI is later), and nothing is saved to disk yet (Personal Language Map is next).** |
| 13 | Personal Language Map | IMPLEMENTED | A Room database with one map per language, a `LanguageMapService`, and a simple explainable mastery calculation (UNKNOWN, LEARNING, FAMILIAR, MASTERED). The learning pipeline now saves units into it. 382 unit tests pass. **No screen shows it yet (Words UI is next) and nothing feeds it help requests, lessons or recognitions yet.** Not yet tried on the phone. |
| 14 | Words UI | NOT STARTED | The Words tab exists with sample data (see 2b). |
| 15 | Daily micro-lessons | IMPLEMENTED | An explainable lesson-selection engine picks about three high-value items a day from the Personal Language Map (not the most frequent ones). The Learn tab shows word and phrase cards with meaning, context, disabled Listen and Repeat placeholders, Next, and a completion screen. Finishing a card records a lesson-encounter mastery signal. 433 unit tests pass. **Not yet tried on the phone.** |
| 16 | Full Support | IMPLEMENTED | Incoming messages get complete translation under Full Support and still feed the Personal Language Map. A central `AssistancePolicy` decides this; `IncomingTranslator` reads the user's mode for each notification. 441 unit tests pass. Adaptive and On-demand are not built. **Not yet tried on the phone.** |
| 17 | Adaptive | IN PROGRESS | The deterministic decision engine is IMPLEMENTED and unit-tested (`AdaptiveEngine`, 466 unit tests pass): from the Personal Language Map of the current learning language it decides which words can stay in that language. **It is not yet connected to notifications, the keyboard or any screen, so choosing Adaptive mode still behaves like Full Support.** Not tried on a phone. |
| 18 | On-demand | IMPLEMENTED (AlterLingua screens only) | On the Learn tab, "Read with help": pasted text stays in the language being learned, and a word's meaning appears only when the user taps that word. Asking is recorded as a mastery signal. 497 unit tests pass. **Not applied to WhatsApp notifications or the keyboard, and not tried on the phone.** |
| 19 | Incoming voice-note Share flow | IMPLEMENTED | AlterLingua appears in Android's Share sheet for audio. A shared WhatsApp voice note is read through its temporary `content://` permission, copied to a private temp file, sent to `/v1/audio/translate` (source auto-detected, target = your language), and shown on a Voice Translation screen: original language, transcript, your language, translation, useful words and phrases, Listen and Review actions. Useful words feed the Personal Language Map. The audio copy is deleted once processed. 545 unit tests pass. **Not tried on a phone, and not tried with a real speech provider (the development backend returns a fixed sample).** |
| 20 | Pronunciation | IMPLEMENTED (first version) | Daily lesson cards now have working Listen and Repeat: Listen reads the word or phrase aloud, Repeat records you, speech recognition transcribes it, and you see Good, Nearly, Try again or "couldn't hear that" plus what was heard. No percentages: a speech recognizer cannot give a trustworthy pronunciation score. Practice is recorded in the Personal Language Map. 584 unit tests pass. **Not tried on a phone or with a real speech provider.** |
| 21 | Progress dashboard | IMPLEMENTED | The Progress tab now uses real data: words encountered, Learning, Familiar, Mastered; Translation Dependence (a defined, measured metric) with a weekly trend; mastery trend; learning activity; translation-assistance trend. With too little history each section shows an empty state and no percentage is invented. Home's dependence card no longer shows sample figures. 632 unit tests pass. **Not tried on a phone; no real user history exists yet.** |
| 22 | Translated outgoing voice | IMPLEMENTED | Backend `POST /v1/audio/speak` (speech-to-text, translation, text-to-speech) and `GET /v1/audio/voices`, behind a text-to-speech provider abstraction with voice and language capability checks. Android: a Translated voice message screen (Home tab): record in your language, pick a target language, listen to the spoken translation, then share it through Android's Share sheet. Never sends. 201 backend tests and 670 Android unit tests pass. **Only development stand-in providers exist (no real recognition, no real speech); not tried on a phone or in WhatsApp.** |
| 23 | Privacy and security audit | IMPLEMENTED | Audit of the app, backend, storage, logging, audio, notifications, network and learning data: no CRITICAL findings, 3 HIGH (2 fixed, 1 needs the authentication milestone), 6 MEDIUM, 10 LOW; results in `docs/privacy.md`. Fixed: server logs could contain message text after a crash; no way to delete learning data. 209 backend and 693 Android unit tests pass. **Nothing here was checked on a phone.** |
| 24 | Pilot preparation and metrics | IMPLEMENTED (definitions and counters); pilot NOT READY | The twelve pilot metrics, eight event names, privacy boundaries and reporting requirements are defined in `docs/pilot.md`. The missing counters (translations by kind, word-state advances, lessons completed, assistance-mode actions) and a privacy-checked weekly report (`alterlingua.pilot.v1`) were added; 711 unit tests pass. **Four blockers remain before real users: no way to record correct recognitions (so Translation Dependence cannot fall), no real providers or backend protection, no way to export the report, and no consent text.** Nothing tried on a phone. |
| 25 | Localization and the three language settings | IMPLEMENTED (not run on a phone) | App language, source/default language (with AUTO) and target language are separate settings. The app language applies to every screen, the keyboard and the notification; 453 strings in each of the eight interface languages (the seven translations are AI-written and NOT reviewed by native speakers); a first-launch app-language screen (L1); the onboarding now follows the brief's 11-step order with AUTO on the source step; the keyboard toolbar and translate requests follow AUTO or a fixed source. Placeholder sample data (Words, Home) is not translated. 746 unit tests pass. See `docs/localization.md`. |

Post-MVP (25 Authentication, 26 Subscription infrastructure, 27 Paywalls and quotas,
28 Monetization analytics): NOT STARTED.

## Milestone 2b detail: app shell

| Item | Status |
|---|---|
| Package and application ID `com.alterlingua.app` | IMPLEMENTED |
| Theme (Stitch colours, Plus Jakarta Sans and Inter fonts, shapes, light and dark) | IMPLEMENTED |
| Navigation: bottom bar with five tabs, state kept per tab | IMPLEMENTED |
| Home, Learn, Words, Progress, Settings screens with placeholder data | IMPLEMENTED |
| Privacy: backup and device transfer turned off | IMPLEMENTED |
| `assembleDebug` builds | IMPLEMENTED |
| Unit tests (20) | IMPLEMENTED — all passing |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional, see build log) |
| On-device navigation test (compiles) | IMPLEMENTED — not run on a device |
| App opens and works on the phone (Infinix X6728B, Android 15) | MANUALLY VERIFIED — owner confirmed on 2026-09-20 |

Not part of this milestone (still NOT STARTED): keyboard, translation, backend connection, voice, notifications,
learning engine, onboarding, saving settings.

## Milestone 3 detail: onboarding (owner's "Milestone 2")

| Item | Status |
|---|---|
| Welcome, Languages & Goals, Assistance Mode, Daily Reminder, All Set screens (first five steps; system setup steps added in milestone 4) | IMPLEMENTED |
| Captures native language, target language, purpose, level, assistance mode, reminder on/off, reminder time | IMPLEMENTED |
| Central language catalogue: English, Français, Español, Deutsch, Italiano, Nederlands, 中文, 日本語. Base codes (en, fr, es, de, it, nl, zh, ja) are saved; locale, writing system and translation / speech / learning support flags are separate data | IMPLEMENTED |
| Languages are shown by their own names in every selector, with the English name as a smaller label underneath (Chinese under 中文); onboarding labels are "My language" and "Language I want to learn"; any pair can be chosen and picking the other field's language swaps them | IMPLEMENTED |
| Language switching in Settings (both languages), saved; Home, Learn and Words follow it | IMPLEMENTED |
| Sample content follows the chosen learning language (Home lesson chips, Learn lesson, Words list) for all 8 languages | IMPLEMENTED — meanings are in English for now |
| Saved with Jetpack DataStore; answers saved as you go; app opens onboarding until finished | IMPLEMENTED |
| Home and Settings show the saved values; Settings changes are saved | IMPLEMENTED |
| Light and dark mode (uses the shared theme) | IMPLEMENTED — not looked at on the phone |
| Purpose and level questions name the selected target language ("Why do you need Español?", "How much Deutsch do you already know?"); the Adaptive card names that language's Personal Language Map | IMPLEMENTED |
| Level is stored per language: switching target shows that language's own answer and keeps the others (data model does not prevent several language profiles) | IMPLEMENTED |
| Unit tests (91 in total after milestone 4 and this update) | IMPLEMENTED — all passing |
| On-device onboarding test (compiles) | IMPLEMENTED — not run on a device |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional) |
| Onboarding and language samples tried on the phone (steps, saving, dark mode, Chinese and Japanese text) | NOT STARTED — awaiting manual check |

Multilingual work still to do in later milestones (CLAUDE.md sections 6.1 to 6.23): the translation API and provider abstraction with
controlled unsupported-language states (6.11, 6.12); the keyboard language selector with search (6.8); one Personal Language Map per
learning language, kept when the user switches (6.13, 6.20, 6.21, needs the database); language-aware lessons and Adaptive mode, with proper
Chinese and Japanese segmentation (6.14 to 6.16); speech-to-text, text-to-speech and pronunciation with per-language capabilities (6.17 to 6.19);
sample meanings in the user's own language; regional and script variants such as zh-TW; and end-to-end tests for every pair in 6.22.

Deliberately not in this milestone: sending reminders (milestone 15), the translation and learning engines.

## Milestone 4 detail: Android system setup and permissions

Onboarding is now 8 steps: Welcome, Languages & Goals, Assistance, Reminder, **Keyboard, Incoming Messages, Microphone**, All Set.
Each new step explains first; only its own button opens an Android screen or the permission dialog. Settings → Setup shows the same status and actions.

| Item | Status |
|---|---|
| Keyboard service so Android lists AlterLingua as a keyboard (the placeholder from this milestone was replaced by the real keyboard in milestone 6) | IMPLEMENTED |
| Placeholder notification listener (`AlterLinguaNotificationListener`) so Android lists AlterLingua under notification access. It overrides nothing and reads nothing | IMPLEMENTED |
| Keyboard enabled: detected with `InputMethodManager.enabledInputMethodList`; button opens Android's keyboard list | IMPLEMENTED |
| Keyboard selected: detected from `Settings.Secure.DEFAULT_INPUT_METHOD`; button opens Android's keyboard chooser (only once enabled) | IMPLEMENTED |
| Notification access: detected with `isNotificationListenerAccessGranted` (API 27+, compat fallback on 26); button opens AlterLingua's own access page (Android 11+) with fallback to the list, then app info | IMPLEMENTED |
| "Allow restricted settings" hint and Open app info button (Android 13+, sideloaded builds) | IMPLEMENTED |
| Microphone: NOT_ASKED / DECLINED / BLOCKED / GRANTED told apart; system dialog only after tapping "Allow microphone" on its own screen; blocked state links to app settings | IMPLEMENTED |
| Live status: re-read when the app resumes and when its window regains focus | IMPLEMENTED |
| Every step can be skipped; typing never depends on any of it | IMPLEMENTED |
| Summary screen and Settings → Setup show the real status | IMPLEMENTED |
| Unit tests (84 in total, +15) for status rules, view model and saved flag; instrumented tests updated to 8 steps | IMPLEMENTED — unit tests passing; instrumented tests compile, not run |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional) |
| Everything above tried on the phone | NOT STARTED — awaiting manual check |

Deliberately not implemented: translation, notification translation, voice recording, a typing keyboard.

## Milestone 5 detail: backend foundation

| Item | Status |
|---|---|
| `backend/` modular monolith (Python 3.14, FastAPI): `core`, `api`, `translation` with code; `speech`, `learning`, `vocabulary`, `mastery`, `lessons`, `users`, `database` prepared as empty packages | IMPLEMENTED |
| `GET /health` | IMPLEMENTED |
| `POST /v1/translate`: one route for every pair, dynamic `source` (or `auto`) and `target`; response has `translation`, `source_language`, `target_language` | IMPLEMENTED |
| Central language catalogue (en fr es de it nl zh ja) with per-feature support flags; locale codes such as `fr-FR` reduce to `fr` | IMPLEMENTED |
| `TranslationProvider` abstraction with capability checks (languages, auto-detect, pair); controlled errors for unsupported language, unsupported pair, missing auto-detect, undetected source, same language, too long, provider failure / unavailable / timeout | IMPLEMENTED |
| Development-only `fake` provider (small phrasebook for sample phrases, otherwise output marked `[xx] text`). **It is not a real translator** | IMPLEMENTED |
| A real translation provider | IMPLEMENTED (Mistral; Groq + Groq-then-Mistral fallback added 2026-09-22) — see the dedicated section below |
| Input validation, Unicode-safe processing (NFC, characters not bytes, UTF-8 JSON, CJK and emoji tested), one error shape that never echoes the text | IMPLEMENTED |
| Environment-variable configuration (`ALTERLINGUA_*`), `.env.example`, `.env` git-ignored, no keys in code | IMPLEMENTED |
| No message storage; logs hold languages, character counts and latency only (checked by tests, including a deliberate break of the rule) | IMPLEMENTED |
| Tests: 66 passing, covering English to all seven other languages, non-English sources (es, fr, ja, zh, de, nl), non-English pairs, auto-detection | IMPLEMENTED |
| Multilingual verification with a real provider (CLAUDE.md 6.22) | NOT STARTED — the fake provider only proves the pipeline |
| Started locally and both routes called with curl | IMPLEMENTED — done by Claude on this machine, not by the owner |
| Authentication, quotas, entitlements, speech endpoints, database, Android connection, deployment | NOT STARTED (later milestones) |

## Milestone 6 detail: the AlterLingua keyboard

Android docs consulted first: creating an input method, `InputMethodService`, `InputConnection`, the input-method XML, IME switching and edge-to-edge insets.

| Item | Status |
|---|---|
| `InputMethodService` (`AlterLinguaKeyboardService`), manifest entry (bind permission, `android.view.InputMethod`, meta-data), `res/xml/method.xml`. Name shown by Android: **AlterLingua** | IMPLEMENTED |
| QWERTY letters, shift (tap = next letter; quick double tap = caps lock; tap again = off), backspace (deletes a selection, or one character; hold repeats), space, enter, `,` and `.` | IMPLEMENTED |
| Number/symbol switching: `?123` (digits and common punctuation), `=\<` (more symbols), `ABC` back | IMPLEMENTED |
| Text goes in through `InputConnection.commitText`; capital at the start of a sentence where the field asks for it; number, phone and date fields open on the digits page | IMPLEMENTED |
| Enter: adds a line break in multi-line fields, runs the field's Go / Search / Next / Done action. **Never runs "Send"** (see decisions) | IMPLEMENTED |
| Globe key to switch keyboard (Android requires a way out of every keyboard) | IMPLEMENTED |
| Visual design from Stitch K1 (key colours, key size and shape, lift shadow, indigo action key, space bar named AlterLingua), light and dark mode, screen-reader labels, navigation-bar padding | IMPLEMENTED — not looked at on the phone |
| Unit tests: 32 new (layouts, typing, shift, auto-capitals, pages, backspace, enter policy, globe); instrumented view test compiles, not run | IMPLEMENTED — unit tests passing |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional) |
| **Gate A: enable, select, open WhatsApp, tap the composer, type normal text** | **NOT STARTED — needs a real device** |
| Known limits (not in this milestone): accented letters and other scripts, emoji key, suggestions and autocorrect, key pop-ups, translation toolbar, microphone | NOT STARTED |

### Device test (Gate A), step by step

1. Run the app from Android Studio with the phone connected (Run ▶). Go through onboarding to step 5, or open Settings → Setup.
2. **Enable:** tap "Open keyboard settings", switch **AlterLingua** on, accept Android's warning, go back. The status shows "On".
3. **Select:** tap "Choose keyboard" and pick **AlterLingua**. The status shows "In use".
4. Open **WhatsApp**, open any chat, tap the message box. The AlterLingua keyboard appears; the space bar says "AlterLingua". (If another keyboard appears, tap the keyboard icon at the bottom right of the screen, or the globe key, and pick AlterLingua.)
5. **Type normal text:** in an empty box the first letter is a capital (shift arrow filled). Type `hello there`, then a space, a comma, a full stop. Check every letter appears in the message box.
6. **Shift:** tap shift once, type a letter: capital, then back to lowercase. Double-tap shift quickly: caps lock (arrow with a bar); type a few letters; tap shift: off.
7. **Backspace:** tap deletes one character; hold it: keeps deleting. Long-press words to select them, then Backspace: the selection goes. Move the cursor into the middle of the text and type: text is inserted there.
8. **Symbols:** tap `?123`: digits, `@ # $ - ( ) ! ? " '` appear as typed. Tap `=\<` for the second page (`€ £ % ©`...), then `ABC` to return.
9. **Enter:** type a line, press Enter: a new line starts in the box. **The message must not be sent.** Press it several times; nothing is sent. Send only with WhatsApp's own Send button.
10. **Other fields:** in Chrome's address bar, Enter should go to the page; in a phone-number field the keyboard opens on the digits.
11. **Globe key:** opens Android's keyboard chooser (or switches keyboard). Try portrait and landscape, and dark mode.
12. Note anything that looks wrong: keys too big or small, wrong colours, a key that does nothing, text in the wrong place.

## Milestone 7 detail: the keyboard translation toolbar

Stitch screens used: "D/K1-K2 Keyboard Typing & Translate Active" (toolbar), "D/K6-K10 Keyboard Auxiliary" (language list).

| Item | Status |
|---|---|
| Toolbar above the QWERTY keys: language chip (dot, `AUTO → FR`, arrow), **Translate** pill, microphone button, settings button | IMPLEMENTED — not looked at on the phone |
| The target code is read from the saved settings (DataStore, the same one the app uses) and updates when it changes; until it is read the chip shows `AUTO → …` and cannot be opened | IMPLEMENTED |
| Tapping the chip opens a "Translate to" list in place of the keys (same height, so the keyboard does not jump). Names are native (Français, Español, Deutsch, Italiano, Nederlands, 中文, 日本語, English) with the English name smaller beside them; the current one has a check mark. Choosing one saves it, closes the list and updates the chip (`AUTO → ES`, `DE`, `IT`, `NL`, `ZH`, `JA`...) | IMPLEMENTED |
| The user's own language is not in the list (see decisions) | IMPLEMENTED |
| Settings button opens AlterLingua on its Settings tab (new `AppLinks`, handled by `MainActivity`) | IMPLEMENTED |
| Microphone and Translate are shown; tapping either shows a short "isn't available yet" notice and changes nothing (no recording, no translation, the typed text is untouched) | IMPLEMENTED |
| QWERTY keyboard unchanged and still covered by its 32 tests | IMPLEMENTED |
| Target choice kept separate from typing layout: `ToolbarController` has no access to the text field, and no Chinese or Japanese input method was built | IMPLEMENTED |
| Unit tests: 21 new (toolbar states for all 8 targets, list contents, saving, switching through seven languages, real DataStore round trip incl. 中文 and 日本語, French speaker choosing English, app link); instrumented toolbar tests compile, not run | IMPLEMENTED — unit tests passing |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional) |
| Search box in the language list, the "Full Support" chip from the design | NOT STARTED |
| Translating (milestone 8), voice recording (milestones 9 and 10) | NOT STARTED |

### Device test

1. Run the app from Android Studio (Run ▶). Finish onboarding if needed (native English, target Français). Make sure AlterLingua is your keyboard (Settings → Setup).
2. Open WhatsApp, tap a chat's message box. The AlterLingua keyboard shows a **toolbar** above the keys: `AUTO → FR`, Translate, a microphone, a settings icon.
3. **Typing still works:** type `hello`, shift, backspace, `?123`, Enter (new line, not sent).
4. **Change target:** tap the `AUTO → FR` chip. The keys are replaced by a list: Français (checked), Español, Deutsch, Italiano, Nederlands, 中文, 日本語. Tap **Español**: the list closes and the chip reads `AUTO → ES`. Repeat for Deutsch (`DE`), Italiano (`IT`), Nederlands (`NL`), 中文 (`ZH`), 日本語 (`JA`). The Chinese and Japanese names must display correctly.
5. **It is saved:** close the keyboard, open another chat or app, bring the keyboard back: the chip still shows the language you chose. Open AlterLingua → Settings: "Language I want to learn" shows the same language.
6. **The other direction:** in AlterLingua Settings change the target language, then open the keyboard again: the chip follows it.
7. Tap the chip again while the list is open (or the ✕): the keys come back. The list has the same height as the keys.
8. Tap **Translate**: a short message says translation isn't available yet; the text in the message box is unchanged. Tap the **microphone**: same kind of message; nothing is recorded and no permission dialog appears.
9. Tap the **settings icon**: AlterLingua opens on the Settings tab and the keyboard hides.
10. Try dark mode and landscape. Note anything cut off, too big, or wrong (chip too wide, text clipped, list not matching the height).

## Milestone 8 detail: outgoing translation

Android docs consulted first: `InputConnection` (`getExtractedText`, `setSelection`, `commitText`, batch edits, `replaceText` needs API 34 so not used), network security configuration for cleartext traffic.

| Item | Status |
|---|---|
| Tapping **Translate** reads the whole composer text through `InputConnection` (`getExtractedText`, or text around the cursor if a field cannot give it all) | IMPLEMENTED |
| Sends `POST /v1/translate` with `source=auto`, `target=<the user's current selection, read from DataStore at the moment of the tap>`, `context=messaging`, `tone=natural`. French is never assumed | IMPLEMENTED |
| Replaces the composer text with the answer (select all, then commit) inside one batch edit, cursor at the end | IMPLEMENTED |
| Shows **Translating to Español…** (with Cancel), then **✓ Translated** with **Undo** (about 10 s, or until you type) | IMPLEMENTED |
| **Undo** restores the exact original text and selection, only if the text is still what the translation left; otherwise says it can't undo and touches nothing | IMPLEMENTED |
| Result is checked after writing; if the field refused or garbled it, the original is put back, and if that fails too a **Restore** button keeps the original available | IMPLEMENTED |
| Handled with a clear message and the original untouched: empty text, backend unavailable, timeout, offline, translation failure, unsupported language, unsupported pair, source undetected, text too long, unsupported editor, password field, not configured | IMPLEMENTED |
| Switching app or field during translation cancels the request and writes nothing; text edited while waiting is never overwritten | IMPLEMENTED |
| A translation in a different language than the one requested is refused | IMPLEMENTED |
| Never sends a message, never touches WhatsApp's Send button or storage (the text-field interface has no such capability; a test checks it) | IMPLEMENTED |
| Password fields are never sent | IMPLEMENTED |
| App talks to the backend over the phone's `localhost` through `adb reverse`; plain HTTP is allowed for loopback in debug builds only. Release builds have no address until a real server exists | IMPLEMENTED |
| New permissions: INTERNET, ACCESS_NETWORK_STATE (only to word an offline error) | IMPLEMENTED |
| Unit tests: 55 new (client against a real local HTTP server, flow, composer, traits, messages); optional live test against the real backend (passed here for es, fr, ja, de, it, nl, zh); instrumented tests compile, not run | IMPLEMENTED — unit tests passing |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional) |
| A **real** translation provider | NOT STARTED — the backend's `fake` provider knows three sample phrases only |
| Microphone / voice translation (milestones 9 and 10), incoming translation (11) | NOT STARTED |

### Device test (Gate B)

The backend's development stand-in only knows these phrases (type them exactly, capital letter and punctuation included):
`Are you coming tomorrow?`, `I'll send you the quotation before noon.`, `Thank you very much!`.
Anything else comes back as `[es] your text`, marked as fake on purpose.

1. **Start the backend** on your computer: `cd backend && .venv/bin/uvicorn app.main:app_factory --factory --port 8000` (first-time setup is in `backend/README.md`).
2. **Connect the phone** by USB and run: `adb reverse tcp:8000 tcp:8000` (this lets the phone's `localhost` reach your computer). Check with `curl localhost:8000/health` on the computer.
3. **Install the new build**: Run ▶ from Android Studio. Make sure AlterLingua is your keyboard and onboarding is finished.
4. **Español:** in the keyboard toolbar set the chip to `AUTO → ES`. Open a WhatsApp chat, type `Are you coming tomorrow?`, tap **Translate**. You see "Translating to Español…", then the message box shows `¿Vienes mañana?` and the banner "✓ Translated  Undo".
5. **Undo:** tap **Undo**: the box shows `Are you coming tomorrow?` again, exactly as typed (including a multi-line or emoji message). Translate again, then type one more letter and look for Undo: the banner is gone.
6. **Français and 日本語:** set the chip to `AUTO → FR`, retype the phrase, Translate: `Tu viens demain ?`. Set it to `AUTO → JA`: `明日来ますか？`. Also try `DE`, `IT`, `NL`, `ZH`. The target must always match the chip.
7. **Nothing is sent:** after translating, the message is only in the box. Send only with WhatsApp's own Send button, yourself. Pressing Enter on the AlterLingua keyboard never sends either.
8. **Empty text:** clear the box, tap Translate: "Type something to translate first."
9. **Backend down:** stop the backend (Ctrl+C), type the phrase, Translate: "Can't reach the translation service. Your text is unchanged." with **Retry**. Restart the backend and tap Retry: it translates.
10. **Offline:** turn on airplane mode and run `adb reverse --remove-all`, Translate: "You're offline. Your text is unchanged." Restore both afterwards (`adb reverse tcp:8000 tcp:8000`).
11. **Timeout:** on the computer pause the backend with `kill -STOP <pid of uvicorn>` (find it with `ss -ltnp | grep 8000`); Translate; after about 20 seconds: "Translation took too long. Your text is unchanged." Resume with `kill -CONT <pid>`.
12. **Switching apps mid-translation:** with the backend paused as above, tap Translate, immediately switch to another app and come back after a few seconds. The text must be unchanged and no banner or result appears. Resume the backend.
13. **Editing while waiting:** with the backend paused, tap Translate, type an extra word, then resume the backend: "The text changed, so the translation wasn't applied." and your text (with the extra word) is kept.
14. **Password field:** in a browser login page, tap a password box and Translate: "Translation is off in password fields."
15. **Unsupported language:** the toolbar only offers supported languages, so this cannot be produced from the keyboard; it is covered by tests.
16. Watch the backend terminal throughout: only languages, character counts and latency are logged, never your text. Note anything that looks wrong.

## Milestone 9 detail: backend speech translation

| Item | Status |
|---|---|
| `POST /v1/audio/translate` (multipart `audio` + `target`, optional `source`, `context`, `tone`) returning `source_language`, `transcript`, `target_language`, `translation`. One route for every language pair | IMPLEMENTED |
| Pipeline: audio, speech-to-text, source-language detection (or the language the caller named), translation through the same translation service as `/v1/translate` | IMPLEMENTED |
| `SpeechToTextProvider` abstraction with capabilities (which languages, whether it can detect the language); catalogue flag `speech_to_text_supported` kept separate from translation support | IMPLEMENTED |
| Capability checks: unsupported spoken language, unsupported target, provider without auto-detection, unsupported pair: controlled 422 errors, all before any audio is processed | IMPLEMENTED |
| Audio validation: accepted types (wav, mp3, m4a/mp4/3gp, aac, ogg/opus, webm, flac, amr) checked by declared type and by the file's own first bytes; empty file, wrong type and mismatched contents refused (415 / 422); size limit (default 10 MiB) enforced while reading (413) and from the declared size before the body is read | IMPLEMENTED |
| Audio is written to a private (0600) temporary file only, and deleted in every case: success, validation error, provider error, timeout, crash (checked by tests, including a deliberate break) | IMPLEMENTED |
| No transcript, translation or audio in logs or error bodies (checked by tests, including a deliberate break); logs hold languages, audio size, character counts, latency | IMPLEMENTED |
| Development-only speech provider (`fake`): ignores the audio, "hears" one sample sentence in the language named, or English for auto. **It is not speech recognition** | IMPLEMENTED |
| A **real** speech-to-text provider | NOT STARTED — needs the owner's choice of provider and a key |
| Multilingual verification with a real recogniser (CLAUDE.md 6.22) | NOT STARTED — tests cover all eight languages through the fake and through a stub that supports only en, fr, es, ja |
| Tests: 96 new (162 in total): pipeline across all eight languages as source and target, non-English pairs, auto-detection, capability limits, validation of type and size, temporary-file deletion, privacy | IMPLEMENTED — all passing |
| Started locally; audio uploaded with curl for es, fr, ja and ja to en; errors and the empty temp folder checked | IMPLEMENTED — done by Claude on this machine, not by the owner |
| Android keyboard microphone, recording, upload (milestone 10) | NOT STARTED |
| Translated speech output (`/v1/audio/speak`, text-to-speech) | NOT STARTED |

### How to try it

1. Start the backend as in `backend/README.md` (`cd backend && .venv/bin/uvicorn app.main:app_factory --factory --port 8000`). After pulling this change run `.venv/bin/pip install -r requirements-dev.txt` once (a multipart library was added).
2. Make any small WAV file, or use any real recording in a supported format.
3. `curl -X POST localhost:8000/v1/audio/translate -F "audio=@clip.wav;type=audio/wav" -F "target=es" -F "source=auto"` returns `Are you coming tomorrow?` and `¿Vienes mañana?` (the fake recogniser always "hears" that sentence for `auto`).
4. Add `-F "source=ja"` to have it "hear" `明日来ますか？`. Try `target=fr`, `de`, `it`, `nl`, `zh`, `ja`.
5. Try errors: upload a text file with `type=audio/wav` (415), `target=pt` (422), a file over the size limit (413).
6. Watch the server terminal: only languages, sizes and timings are logged, never the text.
7. Run the tests: `cd backend && .venv/bin/python -m pytest` (expect 162 passed).

## Milestone 10 detail: keyboard voice input

Android docs consulted first: `MediaRecorder` (recording, foreground-only microphone), runtime permissions (a keyboard cannot show the question; explain first; never nag). Stitch screens used: "D/K11-K16 Keyboard Voice Translation IME" and "D/K12-K16 Voice States".

| Item | Status |
|---|---|
| Tapping the toolbar microphone opens a compact voice panel in place of the toolbar and keys | IMPLEMENTED |
| **Recording:** "Speak in English" (the user's own language, so "Speak in Français" for a French speaker), a live waveform, a timer, **Cancel** and **Stop**. AAC in an MP4 file, mono 16 kHz; stops by itself after 60 s | IMPLEMENTED |
| After Stop: **"Understanding your message…"**, then **"Translating to Español…"** (or Français, 日本語 or the current selection, read when Stop is pressed) while `POST /v1/audio/translate` runs | IMPLEMENTED |
| Result shows **"Original — English"** (or the detected language) with the transcript, and **"Translated — Español"** with the translation | IMPLEMENTED |
| **Insert as text** puts the translation into the composer at the cursor, as typed text; nothing is sent | IMPLEMENTED |
| **Edit** edits the translation with the AlterLingua keys before inserting | IMPLEMENTED |
| **Record again** discards the recording and starts a new one | IMPLEMENTED |
| **Listen** plays back your own recording (translated speech / text-to-speech comes later) | IMPLEMENTED |
| **Share voice** is not shown, because translated speech does not exist yet | IMPLEMENTED (deliberately absent) |
| Handled with a clear message: microphone permission not granted (explanation first, then an invisible screen asks; blocked leads to app settings), unsupported speech language or provider, no speech (silence is not uploaded), unclear speech, too short, partial transcript (offers "Use what I heard"), network failure (offline, unreachable, timeout, with Retry), recording cancelled, keyboard dismissed, app switched, password and key-only fields | IMPLEMENTED |
| Recordings are deleted when the panel closes for any reason (insert, cancel, record again, keyboard hidden, app or field switched, service ends) and leftovers from a crash are swept at start; they live in the app's private cache only | IMPLEMENTED |
| New code path uses only existing permissions (RECORD_AUDIO, INTERNET) | IMPLEMENTED |
| Unit tests: 41 new (recording, waiting, result, target read at Stop across es / fr / ja / de / zh, cancel, permission, each failure, retry, timeout, dismissed, app switch, listen, edit, record again, time limit, cleanup, multipart client against a real local server); optional live tests against the real backend passed; instrumented tests (panel, and a real-microphone recording test for a phone) compile, not run | IMPLEMENTED — unit tests passing |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional) |
| A **real** speech recogniser on the backend | NOT STARTED — the `fake` provider ignores audio and "hears" `Are you coming tomorrow?` (English) or the sample sentence of the language named |
| Translated speech / text-to-speech and Share voice, pronunciation | NOT STARTED |

### Device test (Gate C)

1. **Backend and link:** start the backend (`cd backend && .venv/bin/uvicorn app.main:app_factory --factory --port 8000`), and with the phone connected run `adb reverse tcp:8000 tcp:8000`. Install the new build with Run ▶ from Android Studio (the backend has a new library: run `pip install -r requirements-dev.txt` once in `backend/`).
2. **Microphone permission:** if you have not allowed it (Settings → Setup, or onboarding), skip to step 9 first; otherwise continue.
3. In WhatsApp open a chat, tap the message box, tap the **microphone** in the AlterLingua toolbar (with the chip at `AUTO → ES`). The panel shows **"Speak in English"**, a moving waveform and a timer. Speak a sentence for two or three seconds. Tap **Stop**.
4. You see **"Understanding your message…"**, then **"Translating to Español…"**, then a result: **"Original — English"** and **"Translated — Español"**. Because the recogniser is a stand-in, it always "hears" `Are you coming tomorrow?` whatever you say, and the translation is `¿Vienes mañana?`.
5. Tap **Insert as text**: the translation appears in the message box at the cursor. It is **not sent**; only WhatsApp's Send button sends it.
6. Repeat with the chip at `AUTO → FR` (`Tu viens demain ?`) and `AUTO → JA` (`明日来ますか？`). The labels must name the language you selected each time.
7. **Edit / Listen / Record again:** in a new recording tap **Edit** (the keys appear and the top shows the translation; type or delete, then **Insert as text**); tap **Listen** (plays your recording, tap again to stop); tap **Record again** (a new recording starts).
8. **Cancel:** start recording and tap **Cancel**: the panel closes and the toolbar returns. Start recording and press the phone's Back button to hide the keyboard: recording stops. To check that no recording was left, run `adb shell run-as com.alterlingua.app ls cache/voice` after each: it should list nothing.
9. **Permission denied:** run `adb shell pm revoke com.alterlingua.app android.permission.RECORD_AUDIO`, tap the microphone: the panel explains "AlterLingua needs the microphone…" with **Allow microphone**. Tap it: Android's question appears. Choose **Don't allow**, tap the microphone again and **Allow microphone** again, and **Don't allow** once more: next time the button opens AlterLingua's settings page instead. Typing still works throughout.
10. **No speech:** record two seconds of silence and Stop: "I couldn't hear anything." (nothing is uploaded). Tap the microphone and Stop straight away: "That was too short."
11. **Network:** stop the backend and record: "Can't reach the translation service. Your recording is kept until you close this." with **Retry**; restart the backend and tap Retry: it translates. With airplane mode on and `adb reverse --remove-all`: "You're offline…".
12. **App switched:** pause the backend with `kill -STOP <pid>` (find it with `ss -ltnp | grep 8000`), record and Stop, then switch to another app: nothing is inserted, and `run-as ... ls cache/voice` is empty. Resume with `kill -CONT <pid>`.
13. **Password field:** in a browser login page tap a password box and the microphone: "Voice input isn't available in this field."
14. Watch the backend terminal: only languages, audio size and timings, never the text.

## Milestone 11 detail: incoming message translation (prototype)

Android docs consulted first: `NotificationListenerService` (`onNotificationPosted`, `onNotificationRemoved`), `StatusBarNotification` and `Notification.extras`, `NotificationCompat.MessagingStyle`, group summaries and visibility, and the `POST_NOTIFICATIONS` runtime permission (Android 13+).

Originally WhatsApp-only; widened on 2026-09-23 to a small whitelist of known chat apps (see the 2026-09-23 build-log entry for why an `AccessibilityService`-based "read any app's live chat screen" approach was investigated and **not** used).

| Item | Status |
|---|---|
| `AlterLinguaNotificationListener` (a real `NotificationListenerService`) reads notifications from a small whitelist of known chat apps only (`IncomingSources`: WhatsApp, WhatsApp Business, Telegram, Messenger, Signal); every other app's notification is dropped on its package name before any content is looked at | IMPLEMENTED |
| Reads only what Android exposes to a listener: title, text, big text, messaging-style messages (with sender names, conversation title, group flag), category, flags, visibility. No private storage, no unofficial API, no accessibility service | IMPLEMENTED |
| Translates into the user's **selected native language** (read for every message; English is never assumed): `POST /v1/translate` with `source=auto`, `target=<native>`, `context=messaging`, `tone=natural` | IMPLEMENTED |
| Shows AlterLingua's own notification: title = sender (or group name), text = the translation, small line **"Translated from Français"** (source language in its own name), quiet channel, lock screen shows only "Translated message" | IMPLEMENTED — not looked at on the phone |
| A message already in the user's language is left alone (no notification) | IMPLEMENTED |
| Handled: **grouped** (summary skipped; each conversation gets its own; several messages in one notification are all translated, in order; up to 5 lines kept), **duplicates and re-posts** (translated once; only the new message of an updated conversation), **missing text** (skipped), **hidden content** (secret visibility or the app's own name as a placeholder: reported in Settings, not translated), **media and emoji-only** (photo, voice note, document, location, thumbs-up: skipped), **unsupported language or pair**, **language not detected**, **translation failure**, **offline**, **timeout**, **backend down** (nothing is posted; Settings says what happened; problems that may pass are retried when WhatsApp posts the conversation again) | IMPLEMENTED |
| When WhatsApp's notification goes away (chat opened or dismissed), AlterLingua's translation is removed and its text forgotten. Tapping AlterLingua's notification runs WhatsApp's own "open chat" action, as the notification offers it | IMPLEMENTED |
| The conversation bubble in WhatsApp is never claimed to be, or attempted to be, rewritten | IMPLEMENTED |
| Nothing is saved: duplicates are tracked by a one-way hash in memory (bounded, expires); translated text is held in memory only while its notification exists; nothing is logged; the settings store holds no messages | IMPLEMENTED |
| Settings: **"Translate incoming messages"** switch (on by default, needs notification access, one shared toggle for every supported app) and one line about how the last incoming message ended (no text) | IMPLEMENTED |
| Setup: a new "Show translations" step for the notification permission (Android 13+ asks first; explained before the button), also in Settings → Setup; status detected live | IMPLEMENTED |
| New permission: `POST_NOTIFICATIONS` | IMPLEMENTED |
| Debug builds only: a test aid so it can be tried without a second phone (an `adb` command posts a WhatsApp-shaped test message from AlterLingua itself); the release build contains neither the aid nor the extra source | IMPLEMENTED |
| Unit tests: 63 new (extraction rules, dedup, translator across all eight native languages, every failure, retry, timeout, removal, settings); optional live test against the real backend passed; instrumented tests (real notifications through the reader, real posting) compile, not run | IMPLEMENTED — unit tests passing |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional) |
| Trying it with **real WhatsApp** and with a real notification listener | NOT STARTED — needs your phone |
| A real translation provider on the backend | NOT STARTED — the fake provider only knows a few sample sentences |
| Learning signals from incoming messages, entitlement/quota limits | NOT STARTED |
| Known limits: only what the source app puts in its notification — an app or a user set to "sender name only" (Signal offers this) gives nothing to translate, correctly skipped as hidden content; media-only messages have no text; the group "Name: text" form used by very old WhatsApp versions is not split | — |

### Removed: floating translation bubble (2026-09-23, removed 2026-09-24)

The optional floating bubble (`SYSTEM_ALERT_WINDOW`, "Display over other apps") was removed entirely at the owner's request: `FloatingBubblePresenter`, `CompositeTranslationPresenter`, the `floatingTranslationEnabled` setting and its DataStore key, the Settings switch and Setup row, the onboarding step (back to one fewer step), the `overlayPermission` status/checker/intent, the manifest permission, and every string in all 8 languages. `PrivacyAuditTest` again locks the manifest to `RECORD_AUDIO`, `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`. Incoming *notification* translation is unchanged and still uses AlterLingua's own translated notification.

### Optional add-on: live chat-screen translation via AccessibilityService (2026-09-24)

The owner explicitly asked to use `AccessibilityService` after an earlier decision not to, once the trade-off (Google Play's Accessibility API policy allows non-accessibility use with an in-app disclosure and a Play Console declaration, but reviews narrow-API alternatives more favourably; misuse risks app suspension or developer account termination) was understood. Scoped narrowly per CLAUDE.md section 39: text only (voice notes stay the manual Share flow), restricted at the OS level to exactly the apps `IncomingSources` knows about, off by default, and gated behind its own in-app consent dialog.

**Redesigned 2026-09-24 after owner feedback** ("it didn't read the chat screen well and place translation under each message"): the first version read every text on screen with no positions and showed one translation at a time as a notification. It now reads each message with its position and draws its translation directly under it.

| Item | Status |
|---|---|
| `AlterLinguaAccessibilityService`, scoped via `res/xml/accessibility_service_config.xml`'s `android:packageNames` to exactly WhatsApp, WhatsApp Business, Telegram, Messenger and Signal; events: window state, content changed, scrolled | IMPLEMENTED |
| `AccessibilityTreeReader` reads visible nodes with their screen bounds into a `ScreenSnapshot`; `ChatScreenExtractor` keeps message-like text with positions: never the typing box or anything at/below it, never the title-bar region, never clock times or text with no letter, sorted top to bottom | IMPLEMENTED — 12 unit tests |
| `CaptionPlacer` (pure): a caption is **always directly under its own message**, and its height is set by the room there is. The text size adapts (about 10.5 / 9 / 8 / 7.5 sp): the largest size whose whole translation fits the room before the next text box is used; if none shows it all, the smallest size with an ellipsis; if not even one line fits, no caption. The caption may reach at most 6 dp into the next text box (which has empty padding above its first line); every other message, name or quote and every earlier caption is protected, and captions stay above the text box. Real measurements from WhatsApp on the owner's phone: text boxes are 55 px tall and only 12 px apart in a run from one sender | IMPLEMENTED — 13 unit tests, including a run of messages with those measured positions |
| `CaptionOverlay`: one transparent, touch-through `TYPE_ACCESSIBILITY_OVERLAY` window (needs no "Display over other apps" permission) drawing the captions; hidden while scrolling, when nothing is translated, when the setting is turned off, and when the chat app is no longer in front (checked every 800 ms while captions show) | IMPLEMENTED — not run on a device |
| `LiveChatTranslator`: per-message cache (bounded, in memory only), at most 6 new messages per reading, a rolling 30-requests-per-minute budget, 3 in parallel, 30 s back-off for a failed message, no caption when the translation equals the original or the message is already in the user's language; feeds the learning pipeline as before | IMPLEMENTED — 13 unit tests |
| `ReadScheduler` (replaces `ScreenReadThrottle`): a flood of content-changed events becomes at most one reading per 350 ms with a guaranteed trailing reading; scroll events wait until the screen settles | IMPLEMENTED — 5 unit tests using virtual time |
| Cache is cleared by "Delete all learning data" and when the service stops | IMPLEMENTED |
| Settings switch, consent dialog and onboarding step (copy updated in all 8 languages to say captions are drawn under each message, and that they can cover the time under a message) | IMPLEMENTED |
| Privacy-audit tests: accessibility service in the exported-component list with `BIND_ACCESSIBILITY_SERVICE`; config `packageNames` locked equal to `IncomingSources` | IMPLEMENTED |
| Known limit, by design: an accessibility overlay cannot push another app's layout apart, so a caption is drawn *on top of* the space under the message (the bubble's time stamp and gap). It is limited to the room before the next message, so a long translation ends with an ellipsis rather than covering it | — |
| Known limit: screen text has no agreed structure; a stray non-message label may occasionally be captioned, and caption alignment depends on how each app reports node bounds, expected to need tuning per app on a real device | — |
| Earlier, on the owner's phone: Settings row and consent dialog copy, and the Android Accessibility settings page for AlterLingua | MANUALLY VERIFIED (permission wiring only) |
| Earlier incident (request flood from an unthrottled event handler) and its fix | FIXED — confirmed live at the time; the throttle is now superseded by `ReadScheduler` plus the translator's cache and budget |
| **Captions appearing under real messages in a real chat** | PARTLY VERIFIED by the owner on the phone (2026-09-25): after fixing a reader bug (window bounds overwritten during the tree walk), captions appeared under French and Spanish messages in a WhatsApp chat. The first placement covered the start of following messages; the placement above was built in response and has not yet been seen on the phone |
| Numbers-only status line under "Read chat screens live" in Settings (readings, skipped, items, text boxes, messages, captions), because the app is forbidden from logging | IMPLEMENTED, MANUALLY VERIFIED (read off the phone: 45 readings, 15 items, 1 text box) |

### Debug-only test: capturing a voice note as it plays in a chat app (2026-09-26)

**REMOVED 2026-09-26:** the test screen, its service, meter, explainer and their 20 tests were deleted once the real feature existed (it had put a second AlterLingua icon on debug installs). The rows below are the historical record of what it showed. A feasibility test (own launcher icon, debug builds only) for the question "could AlterLingua capture the sound of a voice note while it plays, for any chat app, without the Share step?". The voice-note Share feature is unchanged. See the 2026-09-26 build-log entry for what Android's documentation allows.

| Item | Status |
|---|---|
| Test screen, foreground service, loudness meter and result explainer (`src/debug/.../capturetest/`); release manifest and Share feature untouched | IMPLEMENTED (debug only) |
| Unit tests for the meter and the explainer (20) | IMPLEMENTED, passing |
| The test screen opens on the owner's phone and lists the chat apps installed on it | MANUALLY VERIFIED (screenshot) |
| **WhatsApp** playback can be captured on the owner's phone (Android 15, Infinix) | OBSERVED 2026-09-26 on the test screen: "HEARD, 18 of 45 seconds had sound, loudest 0 dBFS; playback reported UNKNOWN (capturable); capture policy ALLOW_CAPTURE_BY_ALL". An earlier run with nothing playing correctly said "SILENT, no playback". Not yet confirmed that the sound was a voice note, and the captured audio has not been transcribed |
| **Telegram and Messenger** voice notes | NOT STARTED: the owner still has to run the test for each |
| A real "capture a voice note" feature in the release app | IMPLEMENTED 2026-09-26 (next section); NOT MANUALLY VERIFIED |

### Listening session: capture every voice note from chosen chat apps (2026-09-26)

The owner asked for "approve once, then capture every voice note automatically, chosen in onboarding and changeable in Settings". Android's documentation does not allow "approve once forever" (see the build-log entry), so this is the closest permitted version: one approval starts a listening session that captures every voice note from the chosen apps with no further taps, until Android or the user ends it.

| Item | Status |
|---|---|
| Setting `voiceCaptureApps` (chosen chat apps), saved with DataStore | IMPLEMENTED |
| Onboarding step 13 "Voice notes from other apps": tick the installed chat apps, Start listening (its own Continue always works) | IMPLEMENTED, not seen on the phone |
| Settings section "Voice notes from chat apps": change the apps, see Listening / Not listening, Start or Stop | IMPLEMENTED, not seen on the phone |
| `VoiceCaptureService` listening session: several apps at once (by user id), every voice note one after another, newest 5 kept for at most an hour, a "Voice note captured" notification for each, a Stop action | IMPLEMENTED, not run on a phone |
| When Android ends a session (for example the screen locks), a "Listening stopped: tap to turn it back on" notification; one tap opens the screen and goes straight to Android's approval | IMPLEMENTED, not run on a phone |
| Recordings are uploaded only when the user opens one | SUPERSEDED the same day: now uploaded when the note ends unless the user switches "Translate voice notes when they end" off (next section) |
| `<queries>` for nine known chat apps in the release manifest (no all-apps permission) | IMPLEMENTED |
| 880 unit tests pass (8 new); lint and release compile pass; installed on the phone | VERIFIED (automated) |
| **Approve once, forever, with no prompt later** | **NOT POSSIBLE on Android 14+**: consent is required for every session and the token is single-use; caching it is forbidden. Not implemented, on purpose |
| How often a session actually survives on this phone (screen lock, battery optimisation, background limits) | NOT MANUALLY VERIFIED; decides how often the one-tap restart appears |
| Battery cost of a long session; Google Play policy for a long-running mediaProjection service | NOT VERIFIED / BLOCKED on the owner's Play Console review |

### Whole voice note translated when it ends, shown on the keyboard (2026-09-26)

The owner chose "do it all at once and show it on the keyboard, not piece by piece". When a captured voice note ends, the whole recording is sent in one request, and the transcript and translation appear on the AlterLingua keyboard.

| Item | Status |
|---|---|
| `CapturedNotes` (one processing per note, shared by the keyboard, the notification and the result screen; in memory only; newest 5 kept; closed on demand or when data is erased) reusing `SharedVoiceViewModel` unchanged | IMPLEMENTED, unit-tested |
| Keyboard panel (`CapturedNotePanel`): translating, result with original and translation, unclear notice, Listen, Open, Retry, Close; gives way to typing, dictation, translation and handwriting; a note nobody looked at for 10 minutes is put away | IMPLEMENTED, not seen on the phone |
| Setting "Translate voice notes when they end" (default on; off means a note is sent only when opened) | IMPLEMENTED |
| The notification opens the same note (no second upload) | IMPLEMENTED |
| **Default changed:** audio is now uploaded when a note ends, without opening it (owner's choice); privacy text updated in 8 languages and `docs/privacy.md` | DECISION RECORDED |
| 872 unit tests pass (12 new); lint and release compile pass | VERIFIED (automated) |
| The panel on a real keyboard in WhatsApp: layout, timing, Listen, Open | NOT MANUALLY VERIFIED |
| Cost: every sound of about a second or more from a chosen app (video, shared audio) is also transcribed while the default is on | KNOWN, not measured |

### Capture a voice note from any chat app (2026-09-26)

Keyboard toolbar button, then a screen that explains and asks, then a foreground service that records one voice note the chat app plays, then a notification that opens the same Voice Translation screen as a shared voice note. The Share feature and its files (`share/`, its manifest entry) were not modified.

| Item | Status |
|---|---|
| Keyboard voice-note button (`ToolbarEvent.VoiceNote`); the keyboard reads only the typed-into app's name and Android user id and passes them on | IMPLEMENTED |
| `VoiceCaptureActivity` (explanation, then notifications / microphone / Android's screen-capture approval only after Start) | IMPLEMENTED |
| `VoiceCaptureService` (foreground service type mediaProjection, playback capture limited to that app's UID and to media / game / unknown sound, stops by itself when the voice note ends or on Stop or after 90 s of nothing) | IMPLEMENTED |
| `VoiceNoteRecorder` (16 kHz mono, waits for sound, ends on a 2.5 s pause after at least 1 s of sound, ignores short blips, 4 min limit) and WAV writer | IMPLEMENTED, unit-tested |
| `VoiceCaptureResultActivity` reuses the shared-voice view model and screen through a private address scheme; the recording is deleted when read; `captured_audio` cache folder is swept and erased with the other audio folders | IMPLEMENTED, unit-tested |
| Strings in all 8 languages; permissions FOREGROUND_SERVICE and FOREGROUND_SERVICE_MEDIA_PROJECTION added to the audited allow-list | IMPLEMENTED |
| 872 unit tests pass (13 new); lint and release compile pass; debug build installed on the phone | VERIFIED (automated) |
| Toolbar fits a 360 dp screen with the extra button in every language (buttons made slightly narrower for it) | NOT VERIFIED on a screen: sized by calculation only |
| The whole flow on the phone with a real voice note in WhatsApp | NOT MANUALLY VERIFIED |
| Telegram, Messenger, Signal | NOT MANUALLY VERIFIED: each app decides whether its playback can be captured |
| Transcription quality of captured audio (it may be clipped or quiet) | NOT MANUALLY VERIFIED |
| Google Play: foreground-service type declaration and mediaProjection policy review (Play Console, Policy, App content), and a decision on whether the feature ships | BLOCKED on the owner (CLAUDE.md section 39) |

### Device test, two or more language configurations

The backend's development translator only knows a few sample sentences, and detects their language. Use these exact texts:
`Are you coming tomorrow?` (English), `Tu viens demain ?` (French), `¿Vienes mañana?` (Spanish), `Kommst du morgen?` (German), `Vieni domani?` (Italian), `Kom je morgen?` (Dutch), `你明天来吗？` (Chinese), `明日来ますか？` (Japanese).

**Before you start**
1. Start the backend (`cd backend && .venv/bin/uvicorn app.main:app_factory --factory --port 8000`) and, with the phone connected, run `adb reverse tcp:8000 tcp:8000`. Install the new build with Run ▶ from Android Studio.
2. In AlterLingua open onboarding step 6 "Incoming messages" (or Settings → Setup). **Notification access:** tap "Open notification access", switch AlterLingua on (if the switch is greyed out use "Open app info", ⋮, "Allow restricted settings", then retry). **Show translations:** tap "Allow notifications" and allow the Android 13+ question. Both should read "Access allowed" / "Allowed".
3. In Settings, "Translate incoming messages" is on.

**Two ways to send a message.** (a) *Real WhatsApp:* have another person or phone send you the sentence in a WhatsApp chat, with WhatsApp notification previews on. (b) *Without a second phone (debug build only):* run, with your phone connected,
`adb shell am broadcast -n com.alterlingua.app/.notifications.DebugTestMessageReceiver -a com.alterlingua.app.DEBUG_TEST_MESSAGE --es sender "Marie" --es text "Tu viens demain ?"`
It posts a notification that looks like a WhatsApp message from Marie (a separate "Test messages (debug)" notification), which AlterLingua then treats as a WhatsApp message. Add `--es group "Family"` for a group chat.

**Configuration 1: my language English.** In Settings set "My language" to English (learning language Français). Send `Tu viens demain ?` from Marie. Within a second or two AlterLingua posts a quiet notification: title **Marie**, text **Are you coming tomorrow?**, small line **Translated from Français**. The message notification itself is unchanged.

**Configuration 2: my language Español.** Set "My language" to Español. Send `Are you coming tomorrow?` from Marie: **¿Vienes mañana?**, **Translated from English**. Now send `Tu viens demain ?`: **¿Vienes mañana?**, **Translated from Français**. The destination followed the setting; it is not English.

**Configuration 3: my language 日本語.** Send `Tu viens demain ?`: **明日来ますか？**, **Translated from Français**. Also try Deutsch (`Kommst du morgen?` gives `Are you coming tomorrow?` for an English user, `Tu viens demain ?` for a French user).

**Other behaviours to check (any configuration)**
4. **Already in my language:** with My language Français, send `Tu viens demain ?`: no AlterLingua notification. Settings shows "Last message: already in your language, so nothing was translated."
5. **Group:** `--es group "Family" --es sender "Papa"`: title **Family**, line **Papa: …**. Send a second message: one notification showing both lines.
6. **Update and duplicates:** send two different messages in the same chat: one AlterLingua notification with both lines (not two notifications). (With real WhatsApp, a re-posted notification does not translate old messages again; the backend log shows one request per new message.)
7. **Missing or hidden content:** `--es sender "WhatsApp" --es text "1 new message"` gives no translation; Settings says the text was hidden.
8. **Unclear language:** `--es text "Some unknown latin text"`: no translation; Settings says it couldn't tell which language.
9. **Backend down:** stop the backend, send a message: no AlterLingua notification, Settings says the service can't be reached. Restart the backend and send again: it works.
10. **Switch off:** turn "Translate WhatsApp messages" off in Settings and send a message: nothing happens (and nothing reaches the backend log). Turn it back on.
11. **Cleanup:** swipe the test (or WhatsApp) notification away: AlterLingua's translation for that chat disappears too. With real WhatsApp, tap AlterLingua's notification: WhatsApp's chat opens.
12. **Lock screen:** lock the phone and send a message: AlterLingua's notification shows only "Translated message".
13. **Nothing stored:** run `adb shell run-as com.alterlingua.app ls -R files datastore` (debug build): only the settings file is listed, no messages. The backend terminal shows only languages, lengths and timings.
14. **Other apps ignored:** notifications from any other app (an email, a chat app) cause no translation and nothing in the backend log.

## Milestone 12 detail: learning-engine foundation

Not built here, on purpose: adaptive translation, the Personal Language Map (saving and mastery), lessons, meanings.

| Item | Status |
|---|---|
| **`LinguisticAnalyzer` abstraction** (`supports(language)`, `analyze`, `profile`) with an `AnalyzerRegistry`: a language with no analyzer is reported as unsupported, never analysed with the wrong rules. A fuller NLP component can replace the rule-based one per language | IMPLEMENTED |
| **Language-aware tokenization**: languages with spaces use a letters-and-punctuation word breaker (elisions like `l'envoyer` split, hyphens and apostrophes inside words kept, Unicode normalised before splitting); **中文 and 日本語 use dictionary segmentation** (Android's built-in ICU `BreakIterator`, ICU4J in unit tests). Without a dictionary breaker they are unsupported instead of being split badly | IMPLEMENTED |
| **Language profiles for en, fr, es, de, it, nl, zh, ja** as data: function words by role (article, pronoun, preposition, conjunction, auxiliary, particle, negation, adverb), elisions, fixed expressions, whether capitals mean names (not in Deutsch), and phrase direction (function words before content, or after it in 日本語) | IMPLEMENTED |
| **Candidates**: WORD (content words), PHRASE (a content word with the function words that belong to it: `je vais vous envoyer`, `avant midi`, `antes del mediodía`, `te enviaré`, 日本語 `お送りします`, `来ます`), EXPRESSION (fixed expressions: `au courant`, `por favor`, `ありがとうございます`, `谢谢`...). Compounds ICU splits (`見積書`, `报价单`) are rejoined | IMPLEMENTED |
| Each candidate carries: `surface`, `normalized` (for counting), `type`, `learningLanguage` and `meaningLanguage` (the user's own language), `meaning` and `lemma` (empty until later), `usefulness` (0 to 1 with named signals), `exposure` (count and first and last seen), and a `key` (language + normalized + type) | IMPLEMENTED |
| **`LearningEvent` pipeline**: translation interaction, then "is the text in the learning language?", then analysis, then candidates, then an event of units only, then the exposure store. Wired into outgoing text translation, inserted voice translations and translated incoming messages through a recorder that never delays them | IMPLEMENTED |
| Only text **in the selected learning language** teaches it: outgoing translations (in the target language) and incoming messages written in it. Everything else is skipped with a reason | IMPLEMENTED |
| **Privacy**: the message is used inside the pipeline and dropped; events and the store hold only short units (at most 6 words, 60 characters) with at most 5 words, 4 phrases and 3 expressions per message; names, numbers, acronyms, links and addresses never become units; a unit covering a whole longer message is never kept; the event and candidate classes have no field for message text; nothing is logged; nothing is written to disk | IMPLEMENTED |
| **Exposure tracking**: `ExposureStore` (in memory, bounded, least-recently-seen forgotten first), counts across events, each language kept apart | IMPLEMENTED |
| Settings switch **"Learn from my messages"** (on by default): off means nothing is analysed or kept | IMPLEMENTED |
| Unit tests: 40 new (all eight languages including real segmentation of 中文 and 日本語, examples from the brief for Français and Español, normalization, privacy rules, caps, pipeline rules, exposure counts, language separation, hooks in the three flows, settings). A device test with the real Android ICU compiles, not run | IMPLEMENTED — unit tests passing |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional) |
| Real Android ICU segmentation of 中文 and 日本語 run on a phone | NOT STARTED — needs your phone (one instrumented test) |
| Lemmatization (`envoyer` from `enverrai`), meanings, part-of-speech tagging, frequency lists | NOT STARTED — the structure has empty slots for them |
| Saving units and mastery (Personal Language Map), a Words screen showing what was learned | NOT STARTED (milestones 13 and 14) |
| Known limits: a rule-based analyzer, so it cannot tell a verb from a noun (conjugated verbs are listed as words, not lemmas); names at the start of a sentence can slip through; compound splitting for 中文 depends on the ICU dictionary; German compounds are not split | — |

### What you can check

There is no screen for the units yet, so most of this is proven by the automated tests. On the phone:

1. **Nothing broke.** Run the app from Android Studio (Run ▶). Translate a message from the keyboard, use the microphone, and receive a test notification (see Milestone 8, 10 and 11). All work as before.
2. **The setting.** Open Settings, "Learning" card: **Learn from my messages** is on. Switch it off, force-stop and reopen the app: it stays off. Switch it on again. (When it is off nothing is analysed or kept.)
3. **Real Android ICU for 中文 and 日本語** (the one part the unit tests cannot cover): in Android Studio open `app/src/androidTest/.../learning/engine/AndroidIcuSegmentationTest.kt` and run it on your phone (the green arrow next to the class), or run
   `./gradlew connectedDebugAndroidTest --tests "com.alterlingua.app.learning.engine.AndroidIcuSegmentationTest"`
   with the phone connected. All three tests should pass. If a Chinese or Japanese one fails, tell me which word it reports: Android's dictionary may split a word differently from ICU4J.
4. To see the extraction results themselves, run the unit tests: `cd android && ./gradlew testDebugUnitTest --tests "*CandidateExtractionTest"` (all 8 languages, including the Français and Español examples).

## Milestone 13 detail: Personal Language Map

Docs consulted first: Room and KSP setup with AGP 9's built-in Kotlin (Room 2.8.5 and KSP 2.3.12; the KSP path is the supported one, kapt is not).

| Item | Status |
|---|---|
| **Room database** `language_map.db` (private to the app, not backed up), table `language_map_items`, schema exported to `app/schemas/` and checked in. One row per (language, normalized form, type), so the same word in two languages, or as a word and as a phrase, is separate | IMPLEMENTED |
| **Each item stores**: language, normalized form, display form, meaning (and its language, empty until supplied), type, exposure count, translation-help requests, lesson encounters, correct recognitions, incorrect recognitions, first seen, last seen, mastery score, mastery state. Counts and a result only, never a message | IMPLEMENTED |
| **`LanguageMapService`**: records learning events (each unit is one exposure), help requests, lesson encounters and recognitions (right or wrong); sets meanings; lists items by language and state; summary counts; observe; delete one language or everything. Every change recomputes the item's mastery | IMPLEMENTED |
| `LanguageMapStore` interface with a Room implementation (atomic read-modify-write in a transaction) and an in-memory implementation used by the tests | IMPLEMENTED |
| The learning pipeline from milestone 12 now saves into the map (the in-memory placeholder was replaced), in the selected learning language only | IMPLEMENTED |
| **Mastery states** UNKNOWN, LEARNING, FAMILIAR, MASTERED: not CEFR levels, and nothing estimates a CEFR level | IMPLEMENTED |
| **Mastery calculation** (see the table below): a fixed formula over the counts plus two gates, deterministic, every result comes with an explanation (`explain()`) and a "what is missing for the next state" line | IMPLEMENTED |
| Tests show the whole path UNKNOWN to LEARNING to FAMILIAR to MASTERED (with exact scores at each step, both for the calculator and stored through the service), regression from MASTERED back down after wrong answers, after translation-help requests and after a long time unseen, that exposure alone can never pass LEARNING, and the gates | IMPLEMENTED — unit tests passing |
| Unit tests: 39 new (382 in total). Device test of the map against the real Room database (saved fields, reopening the database, separate rows, the journey, concurrent updates, delete, observe) compiles, not run | IMPLEMENTED — unit tests passing |
| Android lint | IMPLEMENTED — 0 errors, 2 warnings (both intentional) |
| Something that records translation-help requests, lesson encounters and recognitions (Adaptive mode, lessons, pronunciation) | NOT STARTED — the service functions exist and are tested |
| Screens: Words, Progress, and a "Delete all learning data" button (the service can delete; the Settings row still says "Available later") | NOT STARTED |
| Database migrations (version 1 only), encryption of the database | NOT STARTED |
| Known limits: without a lemmatizer, `enviaré` and `enviar` are separate items; mastery is only as good as the evidence recorded, and today only exposures are recorded, so items stay UNKNOWN or LEARNING until lessons and recognition exist | — |

### How mastery is calculated

| Evidence | Points | Cap |
|---|---|---|
| each exposure (met in a message) | +0.5 | +12 in total |
| each lesson encounter | +4 | +16 |
| each correct recognition | +15 | +75 |
| each incorrect recognition | -12 | none |
| each translation-help request | -3 | -15 |
| each 30 days unseen beyond 60 days | -10 | -40 |

Score = the sum, kept between 0 and 100. **State:** below 10 is UNKNOWN; 10 and up LEARNING; 40 and up FAMILIAR; 75 and up MASTERED. **Two gates:** FAMILIAR also needs at least 2 correct recognitions; MASTERED also needs at least 4 correct recognitions and an accuracy of at least 80%. All numbers are in one place (`MasteryRules`).

Why this shape: meeting a word many times gives at most 12 points, so exposure alone can never pass LEARNING (CLAUDE.md 14). Getting it right is worth the most, getting it wrong costs more than help requests, and time away lowers it slowly. Worked example (`devis`): met once 0.5 UNKNOWN; three lesson encounters 12.5 LEARNING; two correct recognitions 42.5 FAMILIAR; four correct and four lessons 76.5 MASTERED; three wrong answers later 40.5 FAMILIAR; a fourth 28.5 LEARNING.

### What you can check

There is no screen for the map yet. Proof is mostly the automated tests. On the phone:

1. **Nothing broke.** Run the app (Run ▶) and use translation as before. It still works.
2. **The database fills.** Set the language you are learning to Français, translate a French sentence with the keyboard (Translate, with the backend running as in Milestone 8), for example `Tu viens demain ?` in an outgoing translation. Then copy the database to your computer and read it (debug build):
   `adb exec-out run-as com.alterlingua.app cat databases/language_map.db > /tmp/lm.db`
   `python3 -c "import sqlite3; [print(r) for r in sqlite3.connect('/tmp/lm.db').execute('select language, normalized, displayForm, type, exposureCount, masteryState from language_map_items')]"`
   You should see short words and phrases (for example `viens`, `demain`), each with a count and UNKNOWN, and **no sentence**. (If the file is empty, translate a sentence in French first; only text in your learning language is analysed, and "Learn from my messages" in Settings must be on.)
3. **The real database.** Run `RoomLanguageMapTest` on the phone from Android Studio (green arrow beside the class) or `./gradlew connectedDebugAndroidTest --tests "com.alterlingua.app.learning.map.RoomLanguageMapTest"`. All tests should pass.
4. To see the progression itself, run the unit tests: `cd android && ./gradlew testDebugUnitTest --tests "*MasteryCalculatorTest" --tests "*LanguageMapServiceTest"`.

## Milestone 15 detail: daily micro-lessons

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** Built, unit-tested (433 of 433 pass, 51 new) and compiled, including the Compose device tests, which have not been run. Not tried on a phone.

### How the three items are chosen
`learning/lessons/LessonSelector.kt`. Every item in the Personal Language Map for the language being learned gets a score made of named signals, each kept with a plain-words reason:

| Signal | Points |
| --- | --- |
| Usefulness (from the learning engine, 0 to 1) | up to 30 |
| Frequency | 4 x log2(1 + times met), at most 10, so a word met 1,000 times gains little over one met 30 times |
| Recency | +12 within 24 hours, +8 within 3 days, +4 within 7 days |
| Current mastery | UNKNOWN +10, LEARNING +14 (only if you have studied it, not merely met it often), FAMILIAR +6, MASTERED excluded |
| Translation-help requests | +8 each, at most 24 |
| Repetition value | never taught +6; review due +10 (LEARNING after 2 days, FAMILIAR after 5) |

Rules around the scores: about 3 items a day; nothing scoring under 20; mastered items are left out (until inactivity has lowered them again); nothing taught in the last 20 hours; at most 2 of one kind (word, phrase, expression); an item inside a chosen phrase is skipped (also for 中文 and 日本語); ties break in a fixed order, so the same map always gives the same lesson. All numbers live in `SelectionConfig`.

### The lesson and the screen
- `LessonService` makes the lesson once per local day per language and saves it (DataStore `daily_lesson`), so reopening the app shows the same lesson and progress. Switching learning language shows that language's lesson.
- Meanings come from the translation service (the unit only, never a message), cached in the map. Offline the card says the meaning is not available.
- Cards follow the Stitch "Learn / Today's Lesson", empty and "Done for today" designs. The context shows counts, kind of message and how recent, never the message or sender (none is stored).
- Listen and Repeat are visible but disabled ("coming soon"); they belong to pronunciation practice.

### Mastery signal
Pressing Next on a card (or Finish lesson on the last) records one lesson encounter (+4 points, at most 16) for that item, once only. Going Back records nothing. Right/wrong recognition is not recorded here: there is no quiz yet.

### Limits
- The lesson is chosen only from what the map holds, so a new install has none until messages are translated.
- With the development backend, meanings of unknown phrases look like `[en] text`; a real provider gives real meanings.
- The map database moved to version 2 (auto-migration, schema checked in); the migration has not run on a device.

### Device test
1. Install the debug build; keep the backend running with `adb reverse tcp:8000 tcp:8000`.
2. With Français as the learning language, translate several French messages (the Milestone 11 debug notifications work), some repeatedly, and use Translate-help on a few.
3. Open the Learn tab: about three cards, mixing words and phrases, each with meaning, context and "Why this item".
4. Listen and Repeat are greyed out. Back is disabled on card 1.
5. Press Next through the cards; after the last, "Done for today" lists the items.
6. Leave and reopen the app the same day: the same state shows. A new lesson is not made until tomorrow.
7. Optional: `adb exec-out run-as com.alterlingua.app cat databases/language_map.db > map.db` and check `lessonEncounters` is 1 for the taught items.
8. With nothing translated yet (or after deleting data), Learn shows "No lesson today yet".

## Milestone 16 detail: Full Support

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** Unit-tested (441 of 441 pass, 8 new); lint, debug build and instrumented-test compile pass. Not tried on a phone.

### What Full Support does
- **Incoming messages:** a foreign-language WhatsApp notification whose language is supported is translated in full into the user's own language (as since Milestone 11). A message already in the user's language is left alone; an unsupported language gives the controlled "unsupported" outcome, never a wrong translation.
- **Learning stays on:** every translated incoming message is still offered to the learning pipeline, so the Personal Language Map keeps receiving exposures. Being helped does not turn learning off. The user's own "learn from my messages" switch still applies, and wins.
- **Not counted as asking for help:** automatic translation is not a help request, so it does not lower mastery. A test checks this.
- **Where the rule lives:** `learning/assistance/AssistancePolicy.kt` returns what a mode means for an incoming message; `IncomingTranslator` asks it (reading the saved mode fresh each time) instead of checking modes itself. Full Support is the default mode.

### What is not done
- **Adaptive and On-demand are not implemented.** Until their milestones the policy gives them the same complete translation rather than hiding help based on rules that do not exist yet. So today all three modes behave alike for incoming messages; the modes will diverge in the next milestones.
- Full Support on the keyboard side (outgoing translation) is unchanged: it already translates the whole message on request.

### Device test
1. Settings: assistance mode is Full Support (the default), incoming translation on, notification access granted.
2. Receive (or send the debug test) a French message with Français as the learning language and English as your own.
3. The translated notification shows the whole message in English.
4. Learn from messages is on (Settings): after a few such messages, open the Learn tab; words from them appear (see Milestone 15).
5. Turn off "learn from my messages", receive another: it is still fully translated, and nothing new is added to the map.

## Milestone 17 detail: Adaptive engine (first version)

**Status: engine IMPLEMENTED, feature IN PROGRESS.** `learning/assistance/AdaptiveEngine.kt`. Unit-tested (466 of 466 pass, 25 new, in `AdaptiveEngineTest`); lint, build and instrumented-test compile pass. Not connected to the app's screens yet (see "Not done").

### How it decides
Deterministic, explainable, conservative. No model, no randomness: the same message and map always give the same answer.
1. Only a message written in the **learning language** is considered, using that language's own map. Anything else gets full translation. Knowing a word in Français says nothing about Español.
2. The message is analysed by the language's analyzer (dictionary segmentation for 中文 and 日本語). Only **content words** are judged; function words, names and numbers are neutral.
3. A word is **kept** in the language only if the map has it and its mastery, evaluated at that moment, is **MASTERED**. Not in the map means not assumed known. Exposure alone, FAMILIAR, or a long absence (which lowers mastery) do not qualify.
4. A **fixed expression** decides for its own words (knowing every word of "au courant" is not knowing the expression). Where expressions overlap, the least-known one decides.
5. Result:
   - all judged words mastered: **KEEP_ORIGINAL** (no help added);
   - at least half mastered, at most 3 to gloss, and every helped word has a meaning saved in the user's language: **PARTIAL**, the original with a short gloss after each unmastered word, e.g. `Je vais vous envoyer le devis (quotation) avant midi.` (中文 and 日本語 use full-width brackets: `昼までに見積もり（quotation）を送ります。`);
   - anything else (nothing known, too few known, too many to gloss, a missing meaning, unsupported language, no judgeable word): **FULL_TRANSLATION**, with the reason recorded.
6. Every word carries a plain-words reason ("mastered", "learning, not mastered yet", "not in your language map yet, so not assumed known", ...). The numbers (`keepAt`, `minKeptShare`, `maxGlosses`) are in `AdaptiveConfig`.

It never swaps in words of another language and never invents a meaning: a gloss is only a meaning the map already holds. The original text is never altered, only glosses are added (tested for all seven languages).

### Tested
Français (progression from nothing known to all mastered), Español, Deutsch (with a fixed expression), Italiano, Nederlands, 中文, 日本語; map states UNKNOWN, LEARNING, FAMILIAR, MASTERED, stale mastery, exposure without recognition, missing or wrong-language meaning, expressions, per-language separation, determinism.

### Not done (on purpose)
- **Not wired in.** Mastery only reaches MASTERED after at least 4 correct recognitions, and nothing records recognitions yet (no quiz or pronunciation check exists). So today no real user's map would let Adaptive keep any French; wiring it in would change nothing visible, and the notification presenter would need a new display for glossed text. Next step: connect it to incoming notifications once recognition signals exist.
- The engine judges single words and expressions; phrases (chunks) are not judged.
- Glosses use the word's meaning saved in the map (from lessons); words never taught have none, so those messages get full translation.
- Adaptive and On-demand still behave like Full Support in `AssistancePolicy`.

### Device test
None yet; there is nothing to see on a phone. The engine can be exercised only through its unit tests (`./gradlew testDebugUnitTest --tests '*AdaptiveEngineTest*'`).

## Milestone 18 detail: On-demand assistance (AlterLingua screens)

**Status: IMPLEMENTED on AlterLingua-owned screens, not MANUALLY VERIFIED.** Unit-tested (497 of 497 pass, 31 new); lint, debug build and instrumented-test compile pass. The Compose tests and the Room migration did not run on a device.

### What you can do
Learn tab, under the lesson: **Read with help**.
1. Paste text in the language you are learning (for example `Le fournisseur exige un acompte de 30%.`) and press Show text.
2. The text is shown exactly as it is, in that language. Nothing is translated.
3. Tap a word (words are underlined). A panel shows the word, its state, its meaning in your language (for `acompte`: `deposit / advance payment`), "Added to your learning", and a disabled **Listen (coming soon)**.
4. Clear text forgets the text (it is never saved).

Words are real tappable links **because this is AlterLingua's own text**. Nothing here works inside WhatsApp's chat bubbles, and AlterLingua does not claim it does.

### The mastery signal
Asking about a word records a **translation-help request** in the Personal Language Map of the current learning language (once per word per reading; tapping it again in the same reading is not counted again). It:
- adds the word to the map if it was not there, so it can be chosen for a daily lesson (this is what "Added to your learning" means; there is no separate button);
- lowers the score by 3 points per request (existing rule);
- **new rule:** a request in the last 14 days holds a MASTERED word at FAMILIAR ("you asked for its translation recently"), so a word you just asked about is no longer treated as fully known. Adaptive therefore helps with it again. After 14 days without asking it can count as mastered again. The map database moved to version 3 (auto-migration, column `lastHelpAt`).

### Privacy
Only the tapped word is sent to the translation service, never the sentence. The pasted text lives in memory only and is capped at 600 characters. The map keeps the word and counts.

### Not done (on purpose)
- **Not applied to WhatsApp notifications or the keyboard.** Incoming notifications still translate in full in every mode. A notification's text cannot be tapped word by word; a later step could add a "Translate" action to the notification. So On-demand is not yet the incoming default.
- Only single words can be asked about, not phrases. Listen is a placeholder (pronunciation milestone). The Words tab does not show these words yet (Words UI not built).
- The reader is available in every mode (asking for help is always allowed); the mode does not change it.
- Meanings come from the translation service; with the development backend unknown words look like `[en] word`.

### Device test
1. Install the debug build, run the backend (`adb reverse tcp:8000 tcp:8000`), learning language Français, own language English.
2. Learn tab, scroll to "Read with help", paste `Le fournisseur exige un acompte de 30%.`, Show text: the sentence stays in French.
3. Tap `acompte`: a panel shows the meaning, "Added to your learning", and a greyed Listen. Tap it again: the same panel, no second count.
4. Clear text, paste again and tap the word: this counts as a new request.
5. Turn on airplane mode and tap a new word: "Couldn't get the meaning right now", the request is still recorded.
6. Optional: `adb exec-out run-as com.alterlingua.app cat databases/language_map.db > map.db`, then check `helpRequests` and `lastHelpAt` for the word.
7. Next day (or after the word shows in a lesson) the Learn tab may choose it, since a help request raises its lesson score.

## Milestone 19 detail: incoming voice-note Share flow

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** Unit-tested (545 of 545 pass, 48 new); lint, debug build and instrumented-test compile pass. The Compose and Share-sheet device tests did not run. Nothing has been tried on a phone, and nothing with a real speech provider.

### The flow
WhatsApp voice note, Share, **AlterLingua Translate**, then: Android hands AlterLingua a `content://` address with a temporary read permission for that one item; the audio is copied into a private temporary file; `POST /v1/audio/translate` (source `auto`, target your own language); the Voice Translation screen.

### What the screen shows (Stitch: "Incoming Voice Note — Share Flow")
- Working: Getting the voice note, Transcribing and translating, Finding useful language, with Cancel.
- Result: **Original language** and **Your language** chips; **Original transcript**; **Translation**; **Listen to translation**; **Useful words and phrases** (up to 3, each with its meaning in your language and, when saved, its state chip); **Review useful language**; Done.
- Problems (clear text, Try again only when retrying can help, Close): unclear audio, offline, service unreachable, unsupported language, too long, not audio, permission ended, and others.
- Footer: "Audio is sent securely to be transcribed and is deleted after processing."

### Security and privacy
- **Only `content://` addresses are read**, through Android's ContentResolver. `file://` and plain paths are refused, so a share cannot point AlterLingua at another app's private files. WhatsApp's storage, notifications and internal databases are not touched, and no storage permission is requested (a test checks the manifest).
- The temporary permission is used once, immediately; nothing is requested persistently. Access is copied into the app's own cache, so nothing depends on the permission afterwards.
- What the file is comes from its **first bytes** (Ogg, WAV, MP3, MP4/M4A, AAC, FLAC, AMR, WebM), not only from the declared type. A file that is not audio is refused, and so is a declared non-audio type. Size is capped at 10 MB (the backend limit), stopped while reading.
- **Deleting the audio:** the copy is deleted the moment the backend answers, on a failure that cannot be retried, on Cancel/Done, and when the screen goes away. It is kept only while Try again can use it. Leftovers older than an hour (after a crash) are removed the next time the screen opens.
- Transcript and translation are held in memory only, never logged. Only the useful words are saved in the map, never the transcript (tested).

### Learning
The transcript is offered to the learning pipeline as an "incoming voice note" (a new interaction kind, shown in lessons as "in a voice note you received"). If the voice note is in the language you are learning (and "learn from messages" is on), its useful words are saved in that language's map and the screen says "N saved to your language map". Otherwise they are still shown, and the screen says they were not saved. A voice note already in your own language is not translated or learned from.

### Actions
- **Listen** reads the translation aloud with Android's on-device voice, in your language. If the phone has no voice for that language the button says so and is disabled.
- **Review useful language** opens the Learn tab (enabled only when words were saved; they can then appear in lessons).

### Not done
- Listening to the original voice note, playback speed, and a review list of words (Words UI) are not built.
- Only one shared item at a time (`SEND`, not multiple).
- No usage limit or entitlement check yet (billing and quotas are later milestones).
- The development backend returns a fixed sample (English, "Are you coming tomorrow?") whatever audio is sent, so a real French voice note can only be tested end to end with a real speech provider configured.

### Device test
1. Install the debug build; run the backend (`adb reverse tcp:8000 tcp:8000`). Set your own language to **Français** and learning to **English** (the development backend "hears" English, so this shows a real translation: `Tu viens demain ?`).
2. In WhatsApp, long-press a voice note, Share (or Forward, Share), choose **AlterLingua Translate**.
3. The screen shows working steps, then the result: original English, your language Français, the transcript, the translation, up to 3 useful units.
4. Tap **Listen**: the translation is spoken (if the phone has a Français voice); tap again to stop.
5. With the learning language equal to the spoken one, the units are saved; **Review useful language** opens Learn.
6. With your own language English, the same note shows "already in your language".
7. Try airplane mode: "You're offline" and Try again; after reconnecting, Try again works with no second share.
8. Share a non-audio file: AlterLingua is not offered. Turn the backend off: "Couldn't reach the translation service".
9. Check the audio is gone: `adb shell run-as com.alterlingua.app ls cache/shared_audio` shows no `voice-*.tmp` files after the result appears or after Close.
10. Never expected: nothing is sent to anyone, and WhatsApp's own message is not changed.

## Milestone 20 detail: pronunciation practice (first version)

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** Unit-tested (584 of 584 pass, 39 new); lint, debug build and instrumented-test compile pass. The Compose tests and the Room migration did not run on a device. Nothing has been tried on a phone or with a real speech provider.

### The flow (Daily Lessons, each card)
word or phrase, **Listen** (the phone's voice reads it, in the language being learned), **Repeat** (records you, up to 8 seconds; Stop ends it), the recording is sent to speech recognition in the learning language, then simple feedback:
- **Good**: the recognizer wrote exactly the word or phrase (ignoring capitals, punctuation and accents).
- **Nearly**: it wrote something one or two letters away, on a unit of 5 or more letters.
- **Try again**: it wrote something else (shown: what it heard).
- **Try again, "I couldn't hear that"**: silence or nothing recognised.
- Each result has a **Try again** button, and the microphone question is asked in place if it has not been answered.

### What the feedback does and does not say
The screen states: "This checks whether speech recognition understood you. It is not a pronunciation score." A recognizer returns text, not sounds, so it says nothing about accent, rhythm, pitch or single sounds. There are **no percentages and no sound-by-sound feedback**.

**Discrepancy with Stitch ("Part G"):** the design shows an "88% Match", pitch match, cadence, nasal-vowel analysis and phonetic transcriptions. AlterLingua has no acoustic pronunciation-assessment provider, so those were deliberately not built (CLAUDE.md 53 and the brief). The design's structure (word card, Listen, mic, feedback, retry) is followed; only the claims we cannot support are left out. A real assessment provider can be added later behind the same screen.

### How the check is made (language-aware, deterministic)
- Latin scripts: capitals, punctuation and accents ignored (a recognizer's spelling is not pronunciation).
- 中文 and 日本語: punctuation and spaces ignored, katakana treated as hiragana; matched by characters. "Nearly" is not used for them.
- A limit worth knowing: if a 日本語 word is spoken correctly but the recognizer writes it in another script (kanji instead of kana), it shows Try again with what was heard. That is a limit of comparing text, not a judgement of the learner.

### Practice as a mastery signal
Each attempt that produced a usable result is saved as a **pronunciation count** on the word in the Personal Language Map (`pronunciationTries`, `pronunciationGood`; database version 4, auto-migration). Only Good attempts add points: 3 each, at most 9. A miss costs nothing (trying is how pronunciation is learned). Silence, offline and service problems are not recorded as practice. **Practice alone never makes a word FAMILIAR or MASTERED**: those states still need correct recognitions (no quiz exists yet), so the points can move a word from UNKNOWN towards LEARNING only. Pronunciation is kept separate from "recognised the word" on purpose: repeating after hearing is imitation, weaker evidence than recall.

### Privacy
The recording is one temporary file in the app's private cache, sent only to the AlterLingua backend for recognition, and deleted as soon as the answer arrives, on cancel, when you move to another card and when you leave the screen. Only counts are saved: no audio and no transcript.

### Not done
- No dedicated transcription endpoint: the practice uses `/v1/audio/translate` (source = learning language) and ignores the translation, which is wasted work for the backend. A `/v1/audio/transcribe` route is a sensible follow-up.
- No syllable, sound or pitch feedback; no IPA or pinyin display.
- The development backend returns a fixed sample transcript ("Are you coming tomorrow?"), so on a development build every attempt shows Try again; a real speech provider is needed to see Good.
- No spaced review of pronunciation and no use of pronunciation history when choosing lesson items.

### Device test
1. Install the debug build and run the backend (`adb reverse tcp:8000 tcp:8000`); have a lesson (see Milestone 15). With a real speech provider configured for the learning language, continue; with the development provider, expect Try again with "Are you coming tomorrow?".
2. Learn tab, on a card: tap **Listen**: the word is read aloud (if the phone has a voice for that language; otherwise the button is greyed with a note).
3. Tap **Repeat**: Android asks for the microphone (first time only). Say the word, tap **Stop**.
4. "Checking..." then **Good** / **Nearly** / **Try again** with what was heard. Tap **Try again** to repeat.
5. Stay silent: "I couldn't hear that". Turn on airplane mode: a connection message, and nothing is counted.
6. Move to the next card: practice resets. Leave the tab while recording: recording stops.
7. Check the recording is gone: `adb shell run-as com.alterlingua.app ls cache/pronunciation`.
8. Optional: copy `databases/language_map.db` and check `pronunciationTries` and `pronunciationGood` for the word.

## Milestone 21 detail: Progress screen and Translation Dependence

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** Unit-tested (632 of 632 pass, 48 new); lint, debug build and instrumented-test compile pass. The Compose tests and the new progress database did not run on a device. There is no real history yet, so nothing here has been seen with real numbers.

### Translation Dependence: exactly how it is calculated
**Translation Dependence of a week = (words met that were not yet mastered) / (all words met) x 100, rounded to a whole percent.** Lower is better; 100 minus it is the share of words you already knew.

- **Words met** are content words met in *real communication* in the language you are learning: messages you translate or dictate, and messages and voice notes you receive. One count per word per message. Lessons, the reading tool and pronunciation practice are not conversations and are not counted. Phrases are not counted (their words already are, so nothing is counted twice).
- **Not yet mastered** is decided at the moment of meeting, from the Personal Language Map *before* that encounter changes it: the word's mastery, with the fall for time unseen and the recent-help rule applied, was below MASTERED, or the word was not in the map at all. This is the same threshold Adaptive uses, so the metric answers "how many of these words would AlterLingua still have had to translate or gloss?".
- **Weeks** run Monday to Sunday in the phone's time zone. "Week 1" is the week of the first recorded activity; later weeks count on from it (so Week 1, Week 4, Week 8 can appear as in the design). The charts cover the latest 8 weeks.
- **Minimums (no percentages from thin data):** a week's figure is shown only if at least **20 words** were met that week. A trend needs at least **2** such weeks. Until then the card shows a starting-point state ("N of 20 words this week") and, if the current week qualifies, that single week's figure, labelled as one week. With no history at all it shows "No history yet". Nothing is estimated, smoothed or filled in.
- **What is recorded:** per language and local day, only numbers: words met, words assisted, new words, translation requests, lesson cards, practice attempts, and a snapshot of how many items were Learning, Familiar and Mastered. No word, message, transcript or audio (a test checks the data types). Kept in a separate private database (`progress.db`).
- **Known limits:** the language engine keeps only the most useful few words of each message (at most 5), so this is a sample of the words met, biased to content words. Only messages AlterLingua processed are counted. **Mastery needs correct recognitions and no quiz records them yet, so no word can reach MASTERED today: the figure will sit near 100% until recall checks exist.** That is the honest state of the evidence, not a fault; the threshold is configurable in one place.

### What the screen shows (Stitch B5 Progress)
- **Counts:** Encountered, Learning, Familiar, Mastered, from the real map for the language being learned, using today's states.
- **Translation dependence:** big current figure, a line chart, a row per measured week ("Week 1 94%"), a plain sentence about the change (down, up or unchanged since the first week), and "How is this calculated?" showing the definition above.
- **Mastery trend:** weekly stacked bars of Mastered, Familiar and Learning (last known state carried through quiet weeks).
- **Learning activity:** this week's new words, lesson cards, practice attempts, translation requests and active days (Monday to Sunday), plus a weekly chart.
- **Translation assistance:** words that were not mastered plus each time you asked what a word means, per week.
- Each chart has an empty state ("Not enough history yet") until two weeks of data exist.

**Discrepancies with the design:** the design's "Spoken Production Resonance 87% Native Cadence Match" and "Register Distribution" (share of work and social conversations) were not built. The first would be a fake acoustic score (see Milestone 20) and the second would need classifying private messages, which the privacy rules rule out. The design's "Calibrating... 14 / 50 inputs" empty state is followed, using the 20-word rule.

### Other changes
- **Home's dependence card** previously showed sample percentages; it now shows the real figure only when a trend exists, otherwise "Not enough history yet". Home's other cards are still sample data.
- The map now reports to the progress log after each change (words met, help requests, lesson cards, practice); a failure to write progress never stops learning.
- Deleting learning data in Settings does not yet clear the progress database (the log has delete methods; hooking them to Settings was not requested).

### Device test
1. Install the debug build. Open the Progress tab on a fresh install: zeros for the four counts and "No history yet" in every section; no percentages anywhere; Home also says "Not enough history yet".
2. Translate real French messages (Milestone 11 debug notifications work) until about 20 words are met: the Translation dependence card shows "N of 20 words this week", then this week's single figure (near 100%).
3. Use the app across two weeks (or advance the phone's date) with 20 or more words each week: the trend, the "Week n" rows and the change sentence appear.
4. Do lessons and pronunciation practice: activity numbers and the active-days circles update; those do not change dependence.
5. Tap "How is this calculated?": the definition shows.
6. Switch the learning language in Settings: Progress shows that language's own history.
7. Optional: `adb exec-out run-as com.alterlingua.app cat databases/progress.db > progress.db` and check the daily rows contain only numbers.

## Milestone 22 detail: translated outgoing voice

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** Backend: 201 of 201 tests pass (39 new); the real server was started and `/v1/audio/speak` and `/v1/audio/voices` were called for es, fr, de, it, nl, zh and ja (valid WAV answers). Android: lint, debug build, instrumented-test compile and 670 of 670 unit tests pass (38 new). The Compose tests did not run on a device. **The only providers are development stand-ins: the speech recogniser "hears" a fixed English sample and the speech synthesiser makes a placeholder tone, not spoken words. Nothing has been tried on a phone, in the Share sheet or in WhatsApp.**

### The flow
your voice, speech-to-text, translation into the language you choose, text-to-speech in that language, **Listen**, **Share** (Android Share sheet), the chat app. AlterLingua never sends anything: you choose the chat in the share sheet and press Send in that app yourself.

### Backend
- `POST /v1/audio/speak`: multipart like `/v1/audio/translate` (`audio`, `target`, optional `source` or `auto`, `context`, `tone`) plus an optional `voice`. Answers JSON: `source_language`, `transcript`, `target_language`, `translation`, and `audio` (`content_type`, `voice`, `size_bytes`, `data` base64). One route for every language pair, no per-language logic.
- `GET /v1/audio/voices`: which languages have a voice, and which voices, so a client can check before offering it.
- **Provider abstractions** (`app/speech/tts_provider.py`): a text-to-speech provider states its voices per language (id, locale, gender), chooses the requested voice or the first for the language, and synthesises. Recognition, translation and speech are three separate providers. Chosen by `ALTERLINGUA_TTS_PROVIDER` (`fake` for now).
- **Capability checks come first:** the target language, the recognition support for the spoken language and a voice for the target are all checked before the audio is read or any provider is called. A target with no voice gives `422 unsupported_language` with `feature: text_to_speech`, `role: target` and the list of supported languages. Speech already in the target language is spoken as it is.
- **Privacy:** the uploaded audio is a private temp file deleted in every case (success, error, timeout); the generated speech is never written to disk (it is returned in the response); logs hold languages, voice id, sizes and timing, never transcript, translation or audio (tested). Text to be spoken is limited (`ALTERLINGUA_MAX_TTS_CHARS`).

### Android (screen: Home tab, "Record a voice message")
- Choose **Translate to** from the native-name language chips (every supported language except your own); your own language is what you speak.
- **Record**, Stop; **Working** ("Translating to X and creating the voice"); **Result**: what you said, the translation, **Listen** (plays the generated speech), **Share voice message**, **Record again**, Done.
- **Share** hands the audio to Android's Share sheet as a temporary `content://` address from a FileProvider that can only reach one private folder (`cache/spoken_audio/`), with a read permission granted for that share. The provider is not exported. The translated text is given to the learning engine only when you tap Share (as with the keyboard microphone).
- **Temporary files:** the recording is deleted the moment the answer arrives (kept only while Try again can use it); the generated speech is deleted when you record again, close the screen or the screen is destroyed, and anything older than an hour is swept when the screen opens. No audio is retained after the share.
- Problems (offline, unreachable, unsupported, **no voice for that language**, unclear recording, too long) show plain messages; nothing is lost and no message is ever sent.
- **Design:** there is no dedicated Stitch screen for outgoing voice, so the screen follows the keyboard voice-translation states (record, understanding, result with Listen) and the shared voice-note result layout.

### Tested with multiple languages
Backend: every one of the eight languages as a target, plus es to en, ja to en, en to fr, en to ja, fr to de, zh to it, nl to es, auto-detection, same-language, a missing voice, an unknown voice, a chosen voice. Android: the client and the flow with targets en, fr, es, de, it, nl, zh and ja and with each language as the spoken one.

### Not done
- No real speech-to-text or text-to-speech provider is connected (only stand-ins), so no real voice has been heard.
- **The keyboard's microphone does not produce speech** (a keyboard cannot open the Share sheet); the feature is on its own screen.
- The screen offers every language and learns of a missing voice from the server's answer; it does not call `/v1/audio/voices` first, and there is no voice picker yet (the backend accepts a voice id).
- No usage limit or entitlement check (billing and quotas are later milestones).
- Sharing goes through the system chooser rather than straight to WhatsApp, because choosing the app is the user's decision.

### Device test
1. Run the backend (`adb reverse tcp:8000 tcp:8000`), install the debug build. Home tab, **Record a voice message**.
2. Choose a target (for example Español), allow the microphone, record a few seconds, Stop. "Translating..." then the result. With the development providers the transcript is always "Are you coming tomorrow?" and the voice is a placeholder tone whose length follows the text.
3. Tap **Listen**: the tone plays; tap again to stop.
4. Repeat with Français, 日本語 and others: each translation appears in that language.
5. Tap **Share voice message**: Android's share sheet opens. Choose WhatsApp and a chat: the audio arrives as an attachment in WhatsApp's send screen; nothing is sent until you press Send there.
6. Back in AlterLingua, **Record again**; then check no generated files linger: `adb shell run-as com.alterlingua.app ls cache/spoken_audio cache/voice` (empty after Record again or Done).
7. Airplane mode: a connection message and Try again; silence: "I couldn't understand that".

## Milestone 23 detail: privacy and security audit

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** Full report: `docs/privacy.md`. Backend 209 of 209 tests pass (8 new); Android lint, debug build, instrumented-test compile and 693 of 693 unit tests pass (23 new). No new product features were added.

### Result
- **CRITICAL: none.** No secret in the code, the Android app or the repository; no message text persisted; no audio retained.
- **HIGH**
  - **H1 (fixed):** the server could log a message's text after an unexpected error (the web server prints the traceback, including the exception message, which providers often fill with the request). Every log record with an exception is now reduced to its type and where it was raised. Proven with a test that runs the real server.
  - **H2 (fixed):** Settings said "Delete all learning data: Available later". It now deletes the Personal Language Map of every language, the progress history, today's lesson, temporary audio and in-memory message state, after a confirmation. Settings are kept.
  - **H3 (not fixed):** the backend has no authentication, rate limiting or TLS of its own. Not a problem for local development; a blocker for any public deployment. It needs the Authentication milestone and deployment work; a key inside the Android app would not be a valid fix. The required pre-launch controls are listed in `docs/privacy.md`.
- **MEDIUM (4 fixed, 2 recorded):** leftover audio after a crash (start-up sweep), private text in printed objects (redacted `toString`), cacheable responses and public API docs in production, and two recorded: phrases from messages stored on the phone (a product decision) and third-party providers and notification listeners seeing content.
- **LOW (10):** no `FLAG_SECURE`, no certificate pinning, no minification, unencrypted local databases, access-log IP addresses, exported share target, in-memory message hashes, the debug-only test receiver, a library receiver, and a repository with no commits yet.

### The ten checks (all in `docs/privacy.md` section 9)
Persistence, logs, temporary voice files, keys in Android, keys in Git, HTTPS assumptions, permissions, notifications, learning signals, and crash logging were each checked from the code and, where possible, made into a test that fails if the property breaks (for example: the exact permission list, the exported components, no system-log calls, no plain-HTTP address outside the debug loopback file, secret-shaped strings, git-ignore rules, private-text `toString`).

### To check on a phone
1. Use each voice feature, then `adb shell run-as com.alterlingua.app ls cache/voice cache/pronunciation cache/shared_audio cache/spoken_audio`: empty (or only files younger than an hour after a force-stop).
2. Settings, Privacy, **Delete all learning data**, Confirm: "Learning data deleted." The Learn and Progress tabs are empty; `databases/language_map.db` and `progress.db` have no rows.
3. Lock the phone and receive a translated notification: the lock screen shows only the generic line.
4. Force a backend error with a real request and read the server log: it shows the error type and file:line, never the text.

## Milestone 24 detail: pilot preparation (30 to 50 users)

**Status: metric definitions and counters IMPLEMENTED; the pilot itself is NOT READY.** The full specification is `docs/pilot.md`. Android lint, debug build, instrumented-test compile and 711 of 711 unit tests pass (18 new). The Room migration (`progress.db` version 1 to 2) did not run on a device. No new product features; no analytics service and no upload were added.

### Primary hypothesis
Can AlterLingua let users communicate immediately across a language barrier while progressively reducing the translation assistance they require? Two measures: **H1** (communicate immediately) by translation interactions and continued use; **H2** (reduce assistance) by **Translation Dependence** over weeks, read beside the absolute assistance so a fall cannot be explained by translating less. A proposed decision rule is in `docs/pilot.md` section 1, to be confirmed by the owner before any data is seen.

### What was defined (all in `docs/pilot.md`)
- **Twelve metrics** with exact definitions, the moment each is counted and its limits: translation interactions (by kind), vocabulary exposures, translation-help requests, UNKNOWN to LEARNING, LEARNING to FAMILIAR, FAMILIAR to MASTERED, lessons completed, pronunciation attempts, Full Support usage, Adaptive usage, On-demand usage, translation dependence over time.
- **Eight event names** (`translation_completed`, `vocabulary_exposure`, `translation_help_requested`, `word_state_advanced`, `lesson_card_completed`, `lesson_completed`, `pronunciation_attempted`, `assistance_mode_action`). Events are **counted into daily totals on the phone and not stored one by one**.
- **Privacy boundaries:** counts only; no message, word, phrase, transcript, sender, chat, audio or device identifier; finest time resolution one day; a pseudonymous participant code chosen by the pilot team; opt-in, transparent, withdrawable; no cohort cell under 5; retention and separation rules.
- **Reporting requirements:** a weekly per-participant JSON report (`alterlingua.pilot.v1`) and what the pilot team must produce for the owner (uptake, use, learning, dependence, assistance, modes, data quality), with controls against misleading results.

### What was built (so the definitions are measurable)
- `daily_progress` gained counters for translations by kind, word-state advances, lessons completed and actions by assistance mode (Room version 2, auto-migration).
- `MeteredLearningRecorder` counts each translation interaction's kind and passes the interaction on unchanged (the text never reaches the counters); voice notes are counted where they are translated.
- `LanguageMapService` counts upward state changes; `LessonService` counts a completed lesson once; `ProgressLog` counts each action under the mode selected.
- `PilotReport` builds the weekly aggregate report from the same totals the Progress screen uses. Tests check the exact key set, that every value is a number, date, code or null, and that no word from the map appears.

### Blockers before real users (details in `docs/pilot.md` section 8)
1. **B1: the primary measure cannot move.** Correct recognitions are recorded nowhere, so no word can become FAMILIAR or MASTERED and Translation Dependence stays near 100%. A recall check (a Stitch design exists) is needed before H2 can be tested; H1 and UNKNOWN to LEARNING can be measured now.
2. **B2: no real providers and no backend protection** (`docs/privacy.md` finding H3).
3. **B3: no way to get the report off a phone.** Recommended: a "Share pilot report" action that shows the JSON and hands it to the Android Share sheet.
4. **B4: no consent and participant information.**

### Also limits interpretation
Translation failures are not counted (no success rate); Adaptive and On-demand counts measure the mode chosen, not a different experience (they do not yet change incoming translation), so modes must not be compared; only WhatsApp on Android; a sample of at most 5 words per message; install date not recorded.

### To check on a phone
1. After using each translation route (composer, keyboard voice, a shared voice note, an incoming notification, a translated voice message), `adb exec-out run-as com.alterlingua.app cat databases/progress.db > progress.db` and confirm the daily row's translation counters went up by kind and hold only numbers.
2. Change the assistance mode, translate something and ask about a word: the mode counters follow the mode selected.
3. Finish a lesson: `lessonsCompleted` is 1; pressing Next again keeps it at 1.
4. Update from the previous build over an existing install: the app opens and earlier progress rows are intact (the migration).

## Milestone 25 detail: localization and the three language settings

**Status: IMPLEMENTED, not manually verified.** All user-facing screens read their text from resources (746 of 746 unit tests pass); lint, debug build and instrumented-test compile pass. **Nothing has been run on a phone, and the translations are unreviewed by native speakers.** Full detail and the remaining work: `docs/localization.md`.

### What was built
- **Model:** `appLanguage`/`appLanguageChosen`, `detectSourceAutomatically` added to `UserSettings` (saved, read back, tested); `UserLanguagePreferences` (app language, source language, `AUTO` or `FIXED` source detection, active target, locale) with the request source and the keyboard direction label. Source, target and app language never change each other.
- **Applying the app language:** `AppLanguage` and `LocalizedActivity` (all four screens), the keyboard, and AlterLingua's notification use the chosen language; a change recreates the open screen and rebuilds the keyboard, and changes nothing else.
- **Strings:** English plus `values-fr`, `-es`, `-de`, `-it`, `-nl`, `-zh`, `-ja`, 453 strings each. A test checks all eight have the same keys and placeholders, none are empty or left in English, and the brief's exact wording (the eight "Choose your language" prompts, Translate, Settings).
- **L1 first launch:** choose the app language from the eight native names, each with its own "Choose your language", phone language preselected, applied on Continue. Existing users are not asked.
- **Settings, Languages:** App language, Source / default language, Detect source automatically, Target language, and the independence note, all localized.
- **Keyboard and translation:** the toolbar shows `AUTO → FR` or, with a fixed source, `ES → FR`; outgoing translations send `auto` or the fixed source.

### What is not done
Placeholder sample content (Words list samples, some Home demo data), engine/diagnostic explanations and the pilot report stay English by design; keyboard preferences; AUTO for keyboard voice; the Stitch L1, L4, L5, L6 (timed out) and L7 (not sent) designs, so L1 follows the written brief. The onboarding order is now: app language, source (AUTO toggle), target, reason, level, mode, reminder, keyboard, incoming, microphone, complete. Chinese and Japanese typing stays with the phone's input methods, as the brief says.

### Test discovery
The unit tests that read resources, manifests and sources were **not re-run when those files changed** (Gradle treated them as up to date), so a broken translation could have passed. The test task now declares those files as inputs; a deliberate deletion of one Japanese string now fails the tests.

### To check on a phone
1. Fresh install, phone in any language: the first screen is "Choose your language" with eight rows; choosing Español and Continue shows the next screen in Spanish.
2. Settings, Languages: change the app language to 日本語: the screen and the Settings labels change; your target language, words and progress are unchanged.
3. Open the keyboard in WhatsApp: the toolbar words (for example 翻訳) follow the app language; the pair shows `AUTO → FR`. Turn "Detect source automatically" off: it shows `ES → FR` (your source code).
4. With detection off, translate a message in the source language; with it on, translate a message in another language: both work.
5. Receive a WhatsApp message: AlterLingua's notification wording ("Translated from ...") follows the app language.
6. Walk the onboarding on a fresh install: it should show 2 of 11 on the source step and follow the order above, all in the chosen language.

## Gates

Nothing below is MANUALLY VERIFIED: no phone was connected, and the translation, recognition and speech providers are development
stand-ins. "Automated" means unit or backend tests, or a call to the real running backend on this computer. A gate is passed only when the
"On a phone" column has been done by a person.

### Gate 1 — Keyboard: AlterLingua selected, WhatsApp, type normally
| Step | Automated evidence | On a phone |
|---|---|---|
| Keyboard installs and can be selected | Manifest test (bound by the system only); keyboard service crash on start fixed and covered | NOT DONE (Milestone 6 steps) |
| Type normally in WhatsApp (letters, shift, backspace, space, enter, symbols) | Key-model and controller unit tests | NOT DONE |
| **Status** | | **IN PROGRESS, waiting for a person on a phone** |

### Gate 2 — Communication
| Step | Automated evidence | On a phone |
|---|---|---|
| Type in a supported source language, select the target, Translate, the target appears | Toolbar and translation-flow tests; every one of the eight languages as a target | NOT DONE |
| Undo works | Undo tests (original restored, expiry) | NOT DONE |
| Manually Send | Tests and code: no path presses Send; the keyboard cannot send | NOT DONE |
| Speak, the target language appears, manually Send | Voice-flow tests; the client was run against the real backend | NOT DONE, and the recogniser is a stand-in that always "hears" one English sentence |
| Receive a foreign-language notification, assistance appears in the user's native language | Incoming-translator tests for every native language; debug test messages via `adb` | NOT DONE |
| **Status** | | **IN PROGRESS, waiting for a person on a phone and a real provider** |

**Multilingual verification, run against the real backend (stand-in translator), 2026-09-21:**

| Direction | Result |
|---|---|
| English to Français | `Tu viens demain ?` |
| English to Español | `¿Vienes mañana?` |
| English to Deutsch | `Kommst du morgen?` |
| English to Italiano | `Vieni domani?` |
| English to Nederlands | `Kom je morgen?` |
| English to 中文 | `你明天来吗？` |
| English to 日本語 | `明日来ますか？` |
| Español to English (auto-detected as `es`) | `Are you coming tomorrow?` |
| 日本語 to English (auto-detected as `ja`) | `Are you coming tomorrow?` (a non-Latin-script source) |

**What this does and does not prove.** It proves the one language-aware route, target selection, source detection and the
non-English and non-Latin sources work end to end through the real server and client code. The stand-in translator only knows one sample
sentence, so it does **not** prove translation quality; a real provider must repeat this table with real sentences. The backend has a
parametrised test for every target and several other directions, and the Android client and flows are tested for each language as
target and as source.

### Gate 3 — Learning: real communication, learning signal, Personal Language Map, daily lesson, mastery changes, assistance decreases
| Step | Automated evidence | Status |
|---|---|---|
| Real communication produces a learning signal | Pipeline tests for all eight languages; incoming, outgoing and voice events feed it | IMPLEMENTED, tested |
| Signal reaches the Personal Language Map | End-to-end tests from a notification and from a voice note into the map | IMPLEMENTED, tested |
| Daily lesson made from the map | Selection engine and lesson-service tests | IMPLEMENTED, tested |
| **Mastery changes** | Lesson encounters, help requests and pronunciation move a word between UNKNOWN and LEARNING. **FAMILIAR and MASTERED need correct recognitions, and nothing records them** | **BLOCKED** (needs a recall check) |
| **Assistance decreases** | The Adaptive engine is built and tested but **not connected** to notifications or the keyboard; Translation Dependence is measured but cannot fall until words can be mastered | **BLOCKED** (needs recall evidence, then Adaptive wired in) |
| **Status** | | **NOT PASSED: the loop is complete up to "daily lesson", and cannot close until mastery can change and Adaptive is connected** |

### What would close the gates
1. A person runs Gate 1 and Gate 2 on a real phone with the debug build (steps under Milestones 6, 8, 10 and 11).
2. A real translation provider and a real speech provider are connected, and the multilingual table is repeated with real sentences.
3. For Gate 3: record correct recognitions (the recall check), then connect the Adaptive engine to incoming translation, then show a word moving to MASTERED and the same message arriving with less help.

### Older gate notes (before this rewrite)
- **A — Real keyboard:** IN PROGRESS, waiting for your test on the phone (steps under "Milestone 6 detail").
- **B — Real translation:** IN PROGRESS. Gate B says "Tu viens demain ?"; that only works with the stand-in translator for the sample phrase.
- **C — Real voice translation:** IN PROGRESS. The recogniser is a stand-in that always "hears" one sample sentence.

## Keyboard layouts by typing language (follow-up to milestone 25)

**Status: IMPLEMENTED, not manually verified.** `KeyboardLayouts.rows(page, language)`: AZERTY (Français), QWERTZ (Deutsch), Español with ñ, QWERTY otherwise; long-press accent strip; language punctuation on symbol page 2; the keyboard rebuilds when the source language changes. Unit tests cover every language's rows, layouts, digits, punctuation, accents and capital ß. See `docs/localization.md` section 6.

To check on a phone: set My language to Français, open the keyboard in any app: the top row reads A Z E R T Y. Hold "e": é è ê ë appear; tap one. Repeat with Deutsch (Y and Z swapped, ü ö ä ß keys) and Español (ñ key; ?123 then =\< shows ¿ ¡).

## Chinese and Japanese input engines

**Status: IN PROGRESS.** 日本語: kana keyboard + Mozc conversion IMPLEMENTED and wired into the keyboard; engine and typing verified by emulator tests only (not on a phone, not the on-screen keyboard). 中文: librime pinyin conversion IMPLEMENTED and wired into the keyboard (emulator-tested, small dictionary). Open source licences screen IMPLEMENTED (not viewed). Neither engine has run on a phone. See `docs/input-engines.md`.

## Home real figures (follow-up)

**Status: IMPLEMENTED; installed on the owner's phone and the empty states seen on Home and Words (populated data not yet seen).** Home's counts, language map and dependence come from the Progress report (zero when there is no activity). The Home lesson card and the Words tab now also use real data (see build-log 2026-09-21); no sample content remains in the app.

## Backend: real providers (Mistral) and deployment

**Status: IMPLEMENTED and tested against a fake HTTP server; NOT tested against the real Mistral API (no key has been used); NOT deployed.**
- Translation (`mistral-small-latest` chat, JSON answer with the translation and detected language), speech-to-text (Voxtral, `voxtral-mini-latest`) and text-to-speech (Voxtral TTS `voxtral-mini-tts-2603`, English/Français/Español/Deutsch/Italiano/Nederlands only) providers, chosen with `ALTERLINGUA_*_PROVIDER=mistral`; the key is `ALTERLINGUA_MISTRAL_API_KEY`, read only from the environment.
- API token guard (`ALTERLINGUA_API_TOKENS`, required in production) and a per-token rate limit; the Android app sends `Authorization: Bearer` from a build property.
- `backend/deploy/`: Dockerfile, compose file with Caddy (automatic HTTPS), production `.env` example and a step-by-step `DEPLOY.md` for a Contabo VPS. The image was built and run locally (read-only, non-root; health, 401 without a token, 200 with one).
- Tests: 262 backend tests pass (34 new: provider requests and answers, error mapping that never echoes text, prompt-injection placement, token and rate limit); the Android unit tests and lint pass.
- **Privacy:** Mistral's free plan may train on inputs unless turned off in its console; see `backend/deploy/DEPLOY.md` and `docs/privacy.md`.

## Backend: Groq translation provider and Groq-then-Mistral fallback

**Status: IMPLEMENTED and MANUALLY VERIFIED against the real Groq API (see build-log 2026-09-22).** `POST /v1/translate` was called live through the running server for English → Français, Español, Deutsch, Italiano, Nederlands, 中文 and 日本語, plus Español → English and 日本語 → English (auto-detected); all nine returned `200` with a correct translation and detected source language. Mistral's real key answered `429` (quota/rate limit) in the same session, which the fallback chain correctly treated as a skippable leg rather than a hard failure.
- New `groq` translation provider (same JSON-answer chat contract as Mistral), chosen with `ALTERLINGUA_TRANSLATION_PROVIDER=groq` and `ALTERLINGUA_GROQ_API_KEY`. Default model `openai/gpt-oss-120b` — Groq's model list was queried live and no longer includes `llama-3.3-70b-versatile` (it 404s), so the default was corrected to a model this account actually has.
- New `fallback` provider (`ALTERLINGUA_TRANSLATION_PROVIDER=fallback`, the owner's chosen configuration, 2026-09-22): tries Groq first, then Mistral, for the translation endpoint only. A leg with no key configured is left out at startup rather than failing the chain; a runtime failure (down, rejected, timed out, unusable answer) moves to the next leg. Speech-to-text and text-to-speech are unchanged (still `mistral`, `fake`, etc. — no fallback wired for them yet, not asked for).
- The two legs share one timeout budget (`ALTERLINGUA_PROVIDER_TIMEOUT_SECONDS`): a slow primary can leave little time for the fallback attempt. Documented in `.env.example`.
- Tests: 21 new tests (`tests/test_groq_and_fallback.py`) covering the Groq client/provider and the fallback provider's ordering, error-skipping, capability intersection and registry wiring; 283 backend tests pass in total (all against a fake HTTP transport — no network, no key — plus the separate manual live check above).

## Voice checked against the real Mistral API (follow-up to the Groq/fallback section above)

**Status: text-to-speech IMPLEMENTED and MANUALLY VERIFIED for English only; speech-to-text with an explicit source language IMPLEMENTED and MANUALLY VERIFIED; speech-to-text auto-detect BLOCKED (real API limitation, not yet worked around).**
- **Bug fixed:** `MistralTextToSpeechProvider` assumed a server-side default voice that does not exist on the real API (`400 "Either ref_audio or voice must be provided."`); `capabilities()` now only advertises a language with a real configured voice id (`ALTERLINGUA_MISTRAL_TTS_VOICES`), so an unconfigured language is refused up front (`422`) instead of failing after a real transcription + translation call. This account's real Mistral voice catalogue is English-only right now (10 preset voices, all `en_us`/`en_gb`) — French/Spanish/German/Italian/Dutch have no real voice yet despite the Voxtral TTS docs describing them.
- **Verified live:** English text-to-speech produced a real playable WAV; `POST /v1/audio/translate` with real synthesized English speech and an explicit source correctly transcribed and translated it into French.
- **Blocked, not fixed:** Mistral's Voxtral transcription returns `"language": null` when no source is given, so any audio translation request with `source=auto` fails with the controlled `422 source_language_undetected` — the auto-detect capability the code claims does not actually work against the real API. Needs a design decision (a separate detection pass, always requiring an explicit source from the app, or trying Groq's Whisper endpoint instead) before it can be called done; not attempted yet.

## Translated outgoing voice: on-device speech instead of a cloud voice (follow-up to milestone 22)

**Status: IMPLEMENTED, unit tested (768 of 768 Android unit tests pass, lint clean), and MANUALLY VERIFIED on the owner's phone (Infinix X6728B, Android 15, arm64).**

**Why:** checking the backend against the real Mistral and Groq APIs (see the Groq/fallback and "Voice checked against the real Mistral API" sections above) found that Mistral's real text-to-speech has no default voice and, on this account, only English preset voices exist at all — French/Spanish/German/Italian/Dutch/Chinese/Japanese had none. The owner chose to stop depending on a cloud voice for this feature and instead use Android's own on-device `TextToSpeech`, which already ships with real voices for the catalogue languages on most phones, needs no API key, and keeps the translated text from leaving the phone a second time just to be spoken.

**Built:**
- `share/Speaker.kt`: the `Speaker` interface gained `synthesizeToFile(text, language, file): Boolean`, alongside the existing `speak`/`canSpeak` (already used for the incoming voice-note "Listen" feature and pronunciation practice). `AndroidSpeaker` implements it with `TextToSpeech.synthesizeToFile`, wrapped as a suspend function via an utterance-progress listener.
- `speak/SpokenTranslationFlow.kt`: now calls the backend's `VoiceApi.translate` (transcribe + translate only, the same endpoint the incoming voice-note screen already uses) instead of `SpeakApi.speak`. Once translated, it checks `speaker.canSpeak(target)` and, if there is a voice, calls `speaker.synthesizeToFile(...)` to produce the shareable WAV locally. `VoiceFailure.NO_VOICE_FOR_TARGET` now means "this phone has no voice for that language" rather than a cloud-provider gap, and is left not retryable, as before.
- A synthesis failure (the on-device engine itself fails) is kept retryable without re-uploading the recording: the already-translated text is kept in memory (`PendingSpeech`) until either it is spoken or the screen is left, and `retry()` speaks it again directly when that is pending, only falling back to re-sending the recording when there is no pending translation.
- `AppViewModelProvider.kt`: `SpokenTranslationFlow` is now given an `AndroidSpeaker` (the same instance pattern already used for the incoming voice-note screen and pronunciation practice).
- **Removed as dead code**, since nothing in the app calls the backend's `/v1/audio/speak` any more: `SpeakApi`, `SpeakResult`, `SpokenTranslation` (`translation/VoiceModels.kt`), `HttpVoiceApi.speak()`/`interpretSpeak`/the `/v1/audio/speak` endpoint URL, and the dedicated `HttpSpeakApiTest.kt`. The backend endpoint itself is untouched (still implemented and tested there; simply unused by this Android flow now).
- `SpokenAudioFiles` simplified: it now only names a fresh `.wav` file for the device to write into, rather than writing bytes of a server-chosen type (`extensionFor` and its test were removed, since on-device synthesis is always WAV).

**Tests:** `SpokenTranslationFlowTest.kt` rewritten around the two-step flow (a `FakeApi: VoiceApi` for transcribe+translate, a `FakeSpeaker: Speaker` for on-device synthesis), including new cases for "no voice on this phone" (not retryable, deletes the recording) and "the device's synthesis itself fails" (retryable, does not call the backend again). The `Speaker` test doubles in `SharedVoiceViewModelTest.kt` and `PronunciationPracticeTest.kt` were given the new interface method. `PrivacyAuditTest.kt` no longer prints a `SpokenTranslation` (the class is gone).

**Manually verified on the phone (2026-09-22):** backend reached over `adb reverse tcp:8000 tcp:8000`, real recordings with real ambient speech ("Hello, how are you? Hope you are fine.", "Hello.") sent through the whole pipeline: Mistral transcribed them, Groq translated them, and this phone's on-device Google TTS engine actually spoke and produced a real, playable WAV for **Français** ("Bonjour, comment ça va ? J'espère que tu vas bien.", listened to and confirmed audibly playing), **日本語** ("こんにちは。") and **中文** ("你好。") — the three languages Mistral's own cloud TTS could not speak at all (English-only preset voices; see the Groq/fallback and "Voice checked against the real Mistral API" sections above). Android's Share sheet correctly offered the generated file to WhatsApp and other apps each time (opened and cancelled without actually sending, to avoid sending a test message to a real contact). Home's real counts (translated messages, new words, a generated micro-lesson) updated from these real interactions. No crash in `logcat` across the whole session.

Also observed live: the "Couldn't reach the translation service" message when the backend wasn't reachable yet, and "Unclear voice recording" when a recording had no clear speech in it — both matched their designed copy and left the screen usable (Try again / Record again), with no lost state.

**Not done / not verified:**
- Español, Deutsch, Italiano and Nederlands were not exercised on-device this session (no real speech happened to be recorded for them in the time available) — only Français, 日本語 and 中文 were confirmed with real playback. Given Android's Google TTS engine ships broad language coverage by default, they are likely fine, but this is an assumption, not an observation.
- Mistral's `/v1/audio/speak` capability (server-side voice cloning per language, discussed in the Groq/fallback section) was not pursued, since on-device speech was chosen instead.

## Keyboard bottom row cut off on a fresh window (follow-up to milestone 6)

**Status: FIXED and MANUALLY VERIFIED on the owner's phone.** A fresh keyboard window could render its first frame without the navigation-bar padding it needs on Android 15 (the reactive inset listener can arrive a frame late), clipping the bottom row (?123, globe, handwriting, comma, spacebar, period, enter) behind the gesture navigation area. `AlterLinguaKeyboardView` now also starts with a safe minimum bottom padding synchronously, corrected to the exact value once the real inset arrives. Verified by reproducing the cut on a genuinely fresh keyboard process before the fix and confirming the same conversation renders correctly after it.

## Top status-bar padding missing on three standalone screens (follow-up)

**Status: IMPLEMENTED and MANUALLY VERIFIED on the phone for `SpeakScreen`** (clear gap between the status bar and the title, confirmed in the same screen the owner's bug screenshot showed). `AppLanguageScreen` and `SharedVoiceScreen` carry the identical fix but were not separately re-checked on screen this session. `AppLanguageScreen` (first-launch language choice), `SpeakScreen` (Translated voice message) and `SharedVoiceScreen` (incoming voice note) all live outside the main tabbed Scaffold and had no status/navigation-bar inset handling, so their titles rendered under the status bar on Android 15's enforced edge-to-edge. Fixed with `.systemBarsPadding()`, matching the pattern `OnboardingScreen` already used correctly.

## Welcome screen before "Choose your language" (follow-up to onboarding)

**Status: IMPLEMENTED and MANUALLY VERIFIED end-to-end on the owner's phone**, including the "Get started" transition into "Choose your language". A new first-launch screen (`WelcomeScreen`) shows the app's core promise ("You write in your language. They receive theirs.", from the approved Stitch copy), a card that cycles through sample greetings in all 7 target languages, a description of the WhatsApp-keyboard product, and a "Get started" button, before the existing "Choose your language" screen. Shown once per install (a persisted `welcomeSeen` flag), never shown again once onboarding is complete. Localized into all 8 languages. 769 of 769 unit tests pass, lint clean.
