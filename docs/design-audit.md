# AlterLingua — Stitch Design Audit

Date: 2026-09-19. Audit only: nothing has been implemented from these designs.
Source of truth order (CLAUDE.md §53): owner instruction → CLAUDE.md → product docs → Stitch → existing code.

## What was audited

| Stitch project | Screens | Role |
|---|---|---|
| **AlterLingua UI/UX System** `312001149311948663` | 17 (composite sheets) | Primary, most recent design (updated 2026-09-19 16:56) |
| AlterLingua Multilingual Android Keyboard `7749130267461928574` | 4 (K1–K4) | Earlier keyboard iteration |
| AlterLingua Android UI/UX Design `1764441793147372982` | 5 + logo | Earlier app iteration |
| AlterLingua Design System (asset `f07aceba…`) | 1 | Tokens, typography, components |

Method: every screen's HTML was downloaded and its text, section comments and prototype scripts were read.
Screenshots were not visually reviewed, so purely visual issues (spacing, contrast) are outside this audit.
Most Stitch "screens" are tall composites holding several numbered states (for example A1–A11 in one sheet).
The HTML is web-mockup code that only describes visual intent (CLAUDE.md §10).

## Classification key

`APP` MAIN COMPOSE APP · `IME` ANDROID IME · `NOTIF` ANDROID NOTIFICATION · `SHARE` ANDROID SHARE FLOW ·
`PERM` SYSTEM PERMISSION FLOW · `FUTURE` FUTURE / NOT MVP

## 1. Mapping table

Milestone numbers follow CLAUDE.md §47.

| Stitch screen / state | Product purpose | Class | Android surface | Proposed Kotlin / Compose component | Dependencies | Milestone |
|---|---|---|---|---|---|---|
| **A1** Welcome hero | State the promise | APP | Activity screen | `OnboardingWelcomeStep` | none | 3 |
| **A2–A3** Language pair | Choose native and target language | APP | Activity screen | `LanguagePairStep`, `Language` model (not hard-coded EN/FR) | DataStore | 3 |
| **A4–A5** Goal and level | Personalise learning | APP | Activity screen | `ChoiceStep` (selectable cards) | DataStore | 3 |
| **A6** Assistance mode | Pick Full / Adaptive / On-demand | APP | Activity screen, reused in Settings | `AssistanceModeSelector` | DataStore; entitlement policy later | 3 (choice), 16–18 (behaviour) |
| **A7** Daily reminder | Evening review time | APP | Activity screen | `ReminderStep` | DataStore; notifications permission (API 33+) | 3 (preference), 15 (delivery) |
| **A8** Keyboard enable | Get AlterLingua enabled and selected | PERM | System settings + input-method picker | `KeyboardSetupStep` (opens `ACTION_INPUT_METHOD_SETTINGS`, `showInputMethodPicker`, detect via `InputMethodManager`) | IME registered in manifest | 4 |
| **A9** Notification access | Allow reading WhatsApp notifications | PERM | System settings | `NotificationAccessStep` (`ACTION_NOTIFICATION_LISTENER_SETTINGS`, detect enabled listeners) | Listener service declared | 4 |
| **A10** Microphone | Allow voice input | PERM | Runtime permission | `MicPermissionStep` (`RECORD_AUDIO` via activity-result launcher) | Manifest permission | 4 |
| **A11** Finish | End onboarding | APP | Activity screen | `SetupCompleteStep` | onboarding-complete flag | 3 |
| **B1** Home | Dashboard: lesson, map summary, independence | APP | Bottom-nav tab | `HomeScreen`, `LessonCard`, `PlmDistributionBar`, `DependenceCard` | Lesson, PLM and progress data | Shell 3; data 13, 15, 21 |
| **B2** Learn | Today's micro-lesson (item 2 of 3) | APP | Bottom-nav tab | `LessonScreen`, `LessonItemCard`, `MasteryChip` | Lesson engine, audio | 15 (pronunciation part 20) |
| **B3** Words list + filters | Browse the Personal Language Map | APP | Bottom-nav tab | `WordsScreen`, `MasteryFilterTabs`, `WordListItem` | Room PLM | 14 |
| **B3** Word bottom sheet | Quick word detail | APP | Modal sheet | `WordDetailSheet` (`ModalBottomSheet`) | Room PLM | 14 |
| **B4** Word dossier | Full word detail and lifecycle | APP | Full screen | `WordDetailScreen`, `MasteryStepper` | PLM; pronunciation from 20 | 14 |
| **B5** Progress + empty state | Translation-dependence trend | APP | Bottom-nav tab | `ProgressScreen`, `DependenceChart` (Canvas) | Dependence events | 21 |
| **B6 §1–3** Languages, mode, voice, learning | Preferences | APP | Bottom-nav tab | `SettingsScreen`, `SettingsSection`, `SettingsToggle` | DataStore | Shell 3; wired per feature |
| **B6 §4** Privacy, export, purge | Privacy controls | APP | Settings section | `PrivacySection`, `DataLifecycleDiagram` | Room export/delete | 23 (basics earlier) |
| **B6 §5** System status | Show permission states | PERM | Settings section | `SystemStatusRow` (live state) | Setup checks | 4 |
| **C1–C3** Full / Adaptive / On-demand | Explain and preview modes | APP | Settings and onboarding preview | `ModePreviewCard`, shared `AdaptiveTextRenderer` | PLM (Adaptive) | 16 / 17 / 18 |
| **E1** Incoming translation | Show translated WhatsApp message | NOTIF | Own notification (not inside WhatsApp's) | `WhatsAppNotificationListener : NotificationListenerService`, `TranslationNotifier` (`BigTextStyle`) | Notification access, backend translate, dedupe cache | 11 |
| **F1–F3** Incoming voice note | Transcribe, translate, extract vocab | SHARE | Share target activity | `ShareReceiverActivity`, `VoiceNoteScreen` | Backend STT + translate, PLM | 19 |
| **G1–G8** Pronunciation | Practise and score speech | APP | Full screen | `PronunciationScreen`, `PitchContourCanvas`, `WaveformBars` | `AudioRecord`, backend analysis | 20 |
| **D/K1** Default keyboard | Type normally | IME | Input method window | `KeyboardService : InputMethodService`, `KeyboardRoot`, `KeyMatrix`, `KeyButton` | Manifest IME, `ComposeView` lifecycle wiring | 6 (Gate A) |
| **D/K2** Text ready | Enable Translate | IME | IME toolbar | `ToolbarRow`, `TranslateButton` | `InputConnection` | 7 |
| **D/K3** Translating | Loading state | IME | IME toolbar | `TranslationState.Loading` | Backend translate | 7. **Only in the older project** |
| **D/K4** Translated | Inserted into composer | IME | IME + `InputConnection` | `TranslationController` (`commitText` + undo buffer) | `InputConnection` | 8 (Gate B) |
| **D/K5** Undo | Restore original | IME | IME toolbar | `UndoBar` | Undo buffer | 8 |
| **D/K6–K7** Language selector | Change target language | IME | IME bottom sheet | `LanguageSheet` | Language list | 7 |
| **D/K8** Preview tray | Review before applying | IME | IME strip | `PreviewTray` | Translate result | 8 (optional per §18) |
| **D/K9–K10** Offline / recovery | Keep typing when offline | IME | IME banner | `ConnectivityBanner` (Retry) | Connectivity | 8 (copy must change) |
| **D/K11–K14** Voice ready / recording / processing / result | Speak → transcribe → translate → insert | IME | IME voice panel | `VoicePanel` state machine, `WaveformBars` | Mic permission, backend STT + translate | 10 (Gate C) |
| **D/K17** Mic permission | Ask for microphone | PERM | Must be an activity, not the IME | Launch `MicPermissionActivity` from the IME | Runtime permission | 4 / 10 |
| **D/K22** Numbers and symbols | Typing completeness | IME | IME key matrix | `SymbolsKeyboard` | none | 6 |
| **K-Dark** Dark keyboard | Dark theme | IME | IME theming | `KeyboardTheme.Dark` (token conflict, see §3) | Design tokens | 6 (nice-to-have) |
| **D/K18** Quick settings | Mode / preview toggles in keyboard | FUTURE | IME sheet | `QuickSettingsSheet` | Ghost preview, edge STT (not planned) | after MVP |
| Candidate strip (suggestions, registers, replies) | Word prediction / tone variants | FUTURE | IME strip | `CandidateStrip` | Prediction model or multi-tone API | after MVP |
| Daily reminder | Nudge to do the lesson | NOTIF | Own notification | `LessonReminderWorker` + notification | Scheduler, notifications permission | 15 |
| **K23A–C, S6–S7, S10, S12** Quota overlays | Limit reached / TTS gate / warning | FUTURE | IME sheet | `QuotaBanner` | Entitlement + quota policy | 27 |
| **S1–S5, S11** Plans, matrix, usage | Compare tiers, show quotas | FUTURE | Settings → Plan | `PlansScreen`, `UsageScreen` | Entitlement service | 26–27 |
| **S8–S10, S13–S15, S19** Gates, checkout, restore, error | Purchase flow | FUTURE | Screens + Play Billing | `PaywallSheet`, `CheckoutScreen` | Play Billing + backend verification | 26–27 |
| **S16–S20** Manage subscription | Active / cancelled / free fallback | FUTURE | Settings → Plan | `ManagePlanScreen` | Backend entitlement | 26–27 |
| AlterLingua Logo (earlier project) | Launcher icon | APP | Resource | Adaptive icon | none | any |

Counts (rows above, 41 in total): APP 17 · IME 11 · PERM 5 · NOTIF 2 · SHARE 1 · FUTURE 5. Rows with a range (for example K23A–C or S1–S5) count once.

## 2. Required-design checklist

| Required design | Present? | Where | Verdict |
|---|---|---|---|
| Onboarding | ⚠️ | Part A (A1–A11) | One overview sheet, not a step flow (see §3) |
| Home | ✅ | B1 (earlier: P3 Home) | |
| Learn | ✅ | B2 (earlier: P3 Learn) | Missing recall check and finish state |
| Words | ✅ | B3, B4 | |
| Progress | ✅ | B5 (with empty state) | |
| Settings | ✅ | B6, S16–S20 | |
| Full Support | ⚠️ | C1, A6, Settings | Description card, not a working state |
| Adaptive | ⚠️ | C2, Settings "decay rate" | Only shown on incoming text |
| On-demand | ⚠️ | C3 | Tap-to-reveal is not possible inside WhatsApp |
| Default keyboard | ✅ | D/K1 (older project shows disabled Translate) | |
| Translating keyboard | ❌ in main project | D/K3 only in older project | Main sheet title lists K3 but content starts at K4 |
| Translated + Undo | ✅ | D/K4–K5 | Undo appears twice |
| Language selector | ✅ | D/K6–K7 | Lists 5 languages, MVP is EN↔FR |
| Keyboard microphone | ✅ | Toolbar mic, D/K11 | |
| Voice recording | ✅ | D/K12 (prototype script) | |
| Voice processing | ✅ | D/K13 (prototype script) | |
| Voice result | ⚠️ | D/K14 | No edit step |
| Incoming translated notification | ⚠️ | E1 | Shown inside WhatsApp's notification, which Android does not allow |
| Incoming voice note | ⚠️ | F1–F3 | No loading or error states |
| Personal Language Map | ✅ | B3, B1 widget, B4 | |
| Daily micro-lesson | ✅ | B2, B1 card | |
| Pronunciation | ✅ | G1–G8 | |
| Privacy | ⚠️ | B6 §4 | A section only, and its claims are inaccurate |

## 3. Contradictions

### 3a. Conflicts with CLAUDE.md (must not be implemented as drawn)

1. **Translation injected into WhatsApp's notification (E1, A9 "in-place overlay").** Android lets AlterLingua read the notification and post its own notification. It cannot alter WhatsApp's (§21, §37).
2. **Annotations on WhatsApp chat bubbles** ("Tap to view phonetic breakdown" in K1, "Auto-translated via AlterLingua" in K23A, an "AI layer active" pill in chat). Third-party bubbles cannot be modified.
3. **On-demand tap-to-reveal on French text (C3).** Only possible on AlterLingua's own surfaces (share transcript, app, notification actions).
4. **Live / ghost translation while typing** (Settings "Inline Translation Preview", K18 "Ghost Preview", K1 live sheet and translation variants before Translate). CLAUDE.md §17 makes manual Translate the trusted behaviour and forbids per-character translation.
5. **Privacy claims that are not true for this architecture.** "Zero-log", "on-device", "Edge Whisper", "audio only in volatile RAM", "zero server storage", "processed in-memory". Translation and speech run on the backend (§23–24) and audio is deleted after processing (§20). The Settings data-lifecycle diagram omits the server hop. This is a trust and legal risk; copy must be rewritten.
6. **Stored conversation snippets and contact names** (B2 "Pierre's WhatsApp message", B3/B4 "observed in real context" with encrypted snippets and a named client, B5 "84% work and formal", earlier "Recent signals: quotation discussion with Marie"). CLAUDE.md §25 prefers storing learning units, not conversations, and §45 rejects contact intelligence. S16 also offers to export "audio logs".
7. **Sources beyond WhatsApp** ("Slack & Mail", "Teams", "Signal", "Notes", "SMS"). Initial scope is WhatsApp (§6).
8. **Keystroke telemetry** ("Active Keystrokes 3.8k/wk", "harvest from IME sessions", "36 more keyboard interactions"). A keyboard sees everything typed, including passwords. Learning signals must come only from translation events, and password fields must be skipped.
9. **Microphone permission modal inside the keyboard (K17).** An IME cannot show a runtime permission prompt, and the options say "While using WhatsApp" when the grant is to AlterLingua. Needs an activity.
10. **"Quick Reply (FR)" on the notification (E1).** Risks sending a message. It must insert or open a draft, never send (§3).
11. **Offline banner says "Edge engine active"** (K9/K10). There is no offline engine. §40 requires: keep text, explain, offer Retry.
12. **Gamification:** streak counters (Home "19d", Pronunciation "5 Day Streak") and "+15 XP". §36 avoids childish gamification.
13. **Prices hard-coded and inconsistent** (all FUTURE). $9.99/mo and $89.99/yr; Standard $7.49; Premium $14.99; Premium $49.99/yr; Standard $47.99/yr; keyboard overlays $4.99, $7.99/mo, $2.99 voice pack, "Booster". §29 says prices are not fixed and must be configurable. Also "Google Play Billing API v7" must be re-checked against current docs (§32), and the design has cloud vocabulary sync (needs accounts, milestone 25).
14. **Provider named in the UI** ("Whisper-v3 Turbo", "Voice Edge Whisper"). Providers must stay behind an abstraction (§23–24).
15. **"Auto-Injecting" label on the voice preview (K11).** The result state does have an explicit "Insert" button, so the label is misleading. The user must review before insert (§20).

### 3b. Inconsistencies inside Stitch

- **Word totals:** 409 (Home, S16), 1,247 (Words, Progress), 664 (earlier project), 1,248 (voice-note card). Category counts also differ (Home 14/38/112/245 vs Words 42/186/431/588).
- **Status names:** UI says New / Learning / Familiar / Mastered; CLAUDE.md says UNKNOWN / LEARNING / FAMILIAR / MASTERED. Decide the mapping (New = UNKNOWN).
- **Onboarding progress:** "Step 1/3" and "Step 4 of 6" on the same sheet.
- **IPA for *acompte*:** `/a.kɔ̃t/` (Words) vs `/a.kɔ̃pt/` (On-demand, Pronunciation).
- **Dark palette:** keyboard uses #121316 / #1E2024 / #3B82F6; the design system uses #0B0F19 / #131B2E / #1E293B / #2563EB.
- **Brand colour:** the earlier logo is indigo #4338CA; the design system primary is #2563EB.
- **Toolbar:** main keyboard has AUTO→FR / Translate / mic / settings; earlier project adds a mode badge and a disabled Translate; dark variant shows "Traduction".
- **Spacebar label** differs across three screens.
- **Reminder time:** 8:00 PM (A7, Settings) vs 18:00 (Word Detail).
- **Free tier:** S1 lists only On-demand; CLAUDE.md §29 gives Free Full Support. S1 also shows "Infinite" daily lessons on paid plans, but §15 targets about 3 a day.
- **Version noise** ("Engine v3.2", "IME v3.4", "v2.4.0", "v2.4"). Remove.
- **`SEND_MULTIPLE`** is shown for a single shared voice note.
- **Numbering gaps:** K15, K16, K19–K21 and the individual S2–S5 pages are named in titles but not found in the HTML.
- **Sample data:** the user and contacts have different names on different screens. The K1 translation preview does not match its own conversation.

### 3c. Missing designs

1. **D/K3 Translating** in the main project (§42 "Translating to French…").
2. **Error states:** "Couldn't translate — Retry" (§41), "I couldn't understand that — Try again", partial-transcript handling, voice-note failure.
3. **Voice result edit** (§20 requires review, edit, listen, record again, insert; there is no edit).
4. **Notification states:** access not granted, hidden previews, missing text, grouped or duplicate messages (§21, §38), plus a plain-language explanation before asking for notification access.
5. **Onboarding as a real step flow:** enable keyboard, select keyboard, grant notification access, microphone rationale, completion, and re-entry when a permission is later revoked.
6. **Voice-note share:** loading, error, and "Listen to translation".
7. **Learn:** a recognition / recall check (§14 mastery evidence), lesson-complete, no-lesson-today, and a phrase item.
8. **Empty and first-run states** for Home, Words and Learn (Progress has one).
9. **A dedicated Privacy surface:** consent, per-feature toggles (notification translation, learning), delete-all confirmation, analytics opt-out (§51–52).
10. **French input:** accent long-press popup (é è à ç), shift and caps states, emoji page. Only the older project hints at accents.
11. **Keyboard mode badge** (older project only).
12. **Language change** outside the keyboard, and an EN↔FR direction picker.
13. Tablet / foldable layouts named in the design system have no screens (not MVP).

### 3d. Roadmap gap

CLAUDE.md §47 has **no milestone for the app shell (bottom navigation), Home, or Settings**. Onboarding (3) and Words (14) exist; Home and Settings have no home. Suggest adding a shell to milestone 3 and wiring Home and Settings incrementally.

## 4. Decisions needed from the owner

1. Are privacy and marketing claims corrected to match the architecture (server processing, audio deleted after use)?
2. Keep or drop stored chat snippets and contact names in Words, Learn and Progress?
3. Should Full Support, Adaptive and On-demand also apply to the notification and share transcript only (not WhatsApp itself)?
4. Remove live/ghost preview, quick settings and candidate strip from MVP, per §17?
5. Confirm New = UNKNOWN as the label mapping.
6. Confirm the shell and Home placement in the milestone plan.
7. Provide the missing designs in §3c, starting with D/K3, error states and onboarding as steps.

## 5. Follow-up: the nine designs added to Stitch (checked 2026-09-19)

The owner added nine screens to **AlterLingua UI/UX System** (now 26 screens) using the prompts in
`docs/stitch-missing-designs-prompts.md`. Each was downloaded and checked. All nine exist and none contains the
banned wording ("zero-log", "on-device", "edge", provider names, prices, streaks/XP, auto-inject, Slack/Mail/SMS).

| Screen | Closes | Result |
|---|---|---|
| D/K3 — Keyboard Translating | Missing D/K3 | ✅ Loading pill, Cancel, original text untouched. Extra candidate strip ("Register") is not MVP. |
| Onboarding Setup Flow — Steps 1 to 6 of 6 | Partial onboarding | ✅ One counter. Steps 5–7 of the prompt are merged into Step 5. See issues. |
| Full Support — In Use | Partial Full Support | ✅ Own screen, "WhatsApp's messages are not changed". |
| Adaptive — In Use | Partial Adaptive | ✅ Blended sentence plus "Why these words?". |
| On-demand — In Use | Partial On-demand | ✅ "Translate Message" reveal on AlterLingua's screen. |
| D/K14 — Voice Result with Edit | Partial voice result | ✅ Both fields editable, "Insert as text", "Nothing is sent". |
| Incoming Translated Notification | Partial notification | ✅ AlterLingua's own notification, separate from WhatsApp's; no reply/send action. |
| Incoming Voice Note — Share Flow | Partial voice note | ✅ Share sheet, processing, result, error, "Listen to translation". |
| Privacy & Data | Partial privacy | ✅ Accurate server hop, keep/never-keep lists, export and delete-all with confirmation. |

### Issues still to correct in these screens

1. **"99.4% confidence"** (Notification, On-demand). The translation API returns no confidence value.
2. **Onboarding Step 3** describes Full Support as "real-time inline suggestions … instant auto-correction before you hit send". Full Support means full translation help; translation stays manual (§17).
3. **Onboarding Step 4** says the lesson is "generated from your daily typing" and shows "1 phrase you typed today". Lessons must come from translation events, not typing (§25).
4. **Onboarding Step 6** shows Notification Translation as "Done" though Step 5 shows it "Skipped".
5. **Wording "overlay" / "Intercept"**: Full Support footer says "secure display overlay"; Privacy says "inline translation overlays". AlterLingua uses its own notification and screen, not overlays.
6. **Privacy** says "Active keystroke inputs". Say "text you choose to translate".
7. **On-demand** shows "Client: Atelier Duval" (contact/organisation intelligence), "AlterLingua Engine v4.2", and a "Business Deck Progress 84%" widget. Remove.
8. **Voice Note** claims "18+ languages" and shows a real-looking filename and sender name. MVP is English ↔ French.
9. **Still not designed:** notification states (hidden preview, access off, grouped), voice recording/processing/speech-error and microphone-off states, and the audit's other gaps (empty states, recall check, accents).

### 5b. Second batch (checked 2026-09-19): the five previously undesigned states

The owner added five more screens (project now 31 screens). All exist; issues below.

| Screen | Closes | Result |
|---|---|---|
| Incoming Translation — Notification States | Hidden preview, access off, grouped, duplicate | ⚠️ Present, but the "access off" card says text is processed "strictly on-device", which is wrong (§25). |
| D/K12–K16 — Voice States | Recording, processing, speech error, microphone off | ⚠️ All four present, but the direction is **French → English** (French dictation, "Dictating reply…"). CLAUDE.md §20: the user speaks their own language (English) and gets French. |
| Empty & Completion States | Empty Home, Words, Learn, lesson complete | ⚠️ Present. Issues below. |
| Learn — Recall Check | Recognition question, correct/incorrect, phrase item | ⚠️ Present. Issues below. |
| Keyboard — Accents & Modifier States | é è ê ë, à â, ç; shift; light and dark | ✅ Present. Toolbar button is labelled "Synthesize" instead of "Translate". |

Issues to correct:
1. Notification States, "access off": remove "strictly on-device"; say text is sent securely to translate and not kept.
2. Voice States: change to English speech → French text (match D/K14): "Listening…" in English, "EN → FR".
3. Empty states: "Typing … in any communication app will automatically populate…" and "Every term you tap … is cataloged with context" suggest keystroke and context capture. Say words come from messages you translate.
4. Lesson complete shows all three items as "Mastered" after one day. Mastery needs evidence over time (§14); use Learning/Familiar.
5. Recall Check: "Recorded Context: Chat with Pierre" with a quoted chat sentence stores conversation context and a contact (§25). Use a generic example sentence.
6. Recall Check: "Level C1 Core" and "Retention 89%" do not fit a beginner; remove or make dynamic.
7. *acompte* IPA is `/a.kɔ̃pt/` again (Words screen uses `/a.kɔ̃t/`).
8. Accents screen: rename "Synthesize" to "Translate"; the suggestion strip (café, cafés…) is a word-prediction feature, not MVP.

All previously flagged gaps from section 3c are now covered by a design, except the language-pair settings screen (not required).

### 5c. Re-check after the owner's edits (2026-09-19)

**Second batch: all eight corrections confirmed** (project is now 33 screens).
- Notification States: "on-device" claim replaced by "Text is sent securely to translate and is not kept."
- Voice States: now English → French ("EN → FR", "Dictating message...", "Transcribing English").
- Empty states: words come "from the messages you translate"; lesson-complete items are Familiar/Learning; *acompte* is `/a.kɔ̃t/`.
- Recall Check: generic example sentence; no "Chat with Pierre", "Level C1 Core" or "Retention 89%".
- Accents: toolbar button is "Translate".

**Duplicates to delete in Stitch:** editing created new copies of *Learn — Recall Check* (`6ff44db7…`) and
*D/K12–K16 — Voice States* (`f04f03a0…`). The older copies (`cf76a3d5…`, `f1623097…`) still have the old, wrong content.

**First-batch corrections: not applied yet.** The nine first-batch screens are unchanged, so section 5's issues 1–8 are still open
(confidence figures, onboarding Steps 3, 4 and 6 wording, "overlay/Intercept" wording, "keystroke inputs", the On-demand client
name / engine version / progress widget, and the voice-note "18+ languages" claim and real-looking filename).

### 5d. Re-check of the first-batch corrections (2026-09-19)

The six first-batch screens were edited in place. Confirmed fixed: Notification confidence figure; Onboarding Step 3 ("Translate everything for me. Best for beginners."),
Step 4 ("generated from words you translate", "1 phrase you translated today") and Step 6 ("Notification Translation: Skipped");
Privacy "Text you choose to translate"; On-demand client name, engine version and "Business Deck Progress" widget removed;
Voice Note "18+ languages", real filename and sender name removed; Full Support "overlay" wording removed from the visible text.

Still open:
1. **On-demand** still shows "99.4% confidence".
2. **Privacy** still says "Provides inline translation overlays for system banners"; use "AlterLingua's own notification".
3. Optional clean-up: On-demand keeps a "Live IME Keyboard Status" panel (keyboard status does not belong on an incoming-message screen).
4. The two older duplicate screens (Recall Check `cf76a3d5…`, Voice States `f1623097…`) are still in the project.

### 5e. Final re-check (2026-09-19)

Confirmed fixed: On-demand no longer shows "99.4% confidence" or the "Live IME Keyboard Status" panel; Privacy now reads
"Shows the translation in AlterLingua's own notification without saving message text." All text corrections from sections 5, 5b and 5c
are now applied.

Only remaining clean-up in Stitch: delete the two older duplicate screens (Recall Check `cf76a3d5…`, Voice States `f1623097…`); the project
still has 33 screens. Cosmetic: On-demand repeats the label "Just now" three times.

**Design status: approved for implementation planning**, subject to the platform limits in section 3a (own notification, no annotations on
WhatsApp bubbles, permission prompts in a full-screen activity, manual Translate only).

### 5f. Language selector (new requirement, 2026-09-20)

`CLAUDE.md` now requires eight languages and a searchable target-language selector in the keyboard. The Stitch keyboard selector (D/K6–K7) lists
French, Spanish, German, Italian and **Portuguese**, and has no Dutch, Chinese or Japanese. Portuguese is not in the required list. The selector,
the toolbar pill ("AUTO → ZH", "AUTO → JA") and the onboarding language cards need a design pass for the eight languages, including longer
names, native-script names (中文, 日本語) and a search state.

### 5g. Onboarding language cards (2026-09-20)

`CLAUDE.md` 6.2 and 6.3 outrank Stitch here. The app shows languages by their own names (Français, 中文, 日本語) and labels the cards
"My language" and "Language I want to learn" instead of Stitch's "YOU TYPE" and "THEY RECEIVE". The Stitch cards and the keyboard selector
(D/K6–K7) should be updated to match. The selector in 6.8 also lists the current target first, includes English, and has "Search languages".
