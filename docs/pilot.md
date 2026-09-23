# AlterLingua pilot (30 to 50 users): hypothesis, metrics, events, privacy, reporting

Status of this document: **defined and partly instrumented, not yet run.** Section 8 lists what must be true before real users are
invited. Everything below is written so it can be checked against the code: each metric names the code that counts it and the test
that pins it.

## 1. Question and hypothesis

> **Can AlterLingua let users communicate immediately across a language barrier while progressively reducing the translation
> assistance they require?**

Two parts, each with its own measure:

- **H1, communicate immediately.** A new user can use AlterLingua to send and read real messages in another language from the first
  session, and keeps doing so. *Measured by:* translation interactions per participant per active week, the share of participants with
  a completed translation interaction in their first week, and weeks of continued use.
- **H2, progressively reduce the assistance needed.** Over weeks, the share of the words in a participant's real conversations that
  still need translation falls. *Measured by:* **Translation Dependence** over time (section 3, metric 12), read together with the
  absolute amount of assistance (words assisted, help requests) so a fall cannot be explained only by translating less or by
  easier conversations.

**Proposed decision rule (to be confirmed by the project owner and fixed before any data is seen):**

- H1 is supported if at least 80% of participants complete one or more translation interactions in their first week, and the median
  participant has at least 5 in each week they are active.
- H2 is supported if, among participants with **at least two measured weeks** (each with at least 20 words met), the **median change in
  Translation Dependence between their first and latest measured week is at most minus 10 percentage points**, and at least 60% of
  those participants show a decrease.
- Not supported / inconclusive: fewer than 20 participants reach two measured weeks (the pilot is too small to say), or the fall
  disappears when material difficulty is taken into account (section 7).

## 2. Privacy boundary (applies to every metric)

1. **No conversation content leaves the phone for analytics, and none is stored for analytics on the phone either.** No message, word,
   phrase, transcript, translation, sender, chat name, contact, audio, notification text, keyboard input, or device identifier is part
   of any metric or report.
2. **Everything is a count.** Events are counted on the phone into totals per language per local calendar day (`daily_progress`, numbers
   only). Individual events are not stored, so there is no timeline finer than a day and no way to reconstruct a conversation.
3. **The report holds only:** a pseudonymous participant code (assigned by the pilot team, never derived from the phone, phone number,
   account or advertising id, and changeable), the app version, the language code, the report date, and weekly counts and percentages.
   A test checks that every value in the report is a number, a date, a language code, a version, the participant code, or null.
4. **Participation is opt-in and informed,** separate from using the app. The participant sees exactly what the report contains
   before handing it over, can decline any report, and can withdraw and ask for their data to be deleted by quoting their code.
   *Delete all learning data* in Settings also clears the counters on the phone.
5. **Small cohorts:** with 30 to 50 people, no cohort table may show a cell with fewer than 5 participants, and no report may
   describe one participant's behaviour to other participants. The owner sees per-participant series only under pseudonymous codes,
   only for the analysis, and only while the pilot runs.
6. **Retention:** pilot data is kept until the analysis is written and then deleted or reduced to cohort-level figures; the
   participant list linking codes to people is kept separately from the data and destroyed with it.
7. **Not collected on purpose:** message counts per contact or chat, message length, the language pair of a specific conversation,
   times of day, locations, app usage outside AlterLingua, and anything from other apps.
8. Analytics and private content stay separate systems (CLAUDE.md 25 and 51). See `docs/privacy.md` for everything else the app stores.

## 3. Metric definitions

All metrics are per participant, per language (the language being learned when the event happened), per **week** (Monday to Sunday in
the phone's time zone; "Week 1" is the first week with any activity), unless stated. "Counted at" names the exact moment.

| # | Metric | Report key | Definition | Counted at | Known limits |
| --- | --- | --- | --- | --- | --- |
| 1 | **Translation interactions** | `translations.{outgoing_text, outgoing_voice, incoming_text, incoming_voice, total}` | Number of translations the user actually received or used, by kind. Event `translation_completed`. | Outgoing text: the translation is written into the composer (undo does not subtract). Outgoing voice: the user taps Insert (keyboard) or Share (translated voice message). Incoming text: an incoming WhatsApp message is translated and shown. Incoming voice: a shared voice note's translation is shown. | Failures, cancellations, previews not used and messages already in the user's language are not counted. Failures are **not** instrumented, so a success rate cannot be reported (section 8). Incoming counts depend on notification access and WhatsApp showing message text. |
| 2 | **Vocabulary exposures** | `vocabulary_exposures`, `new_words` | Number of content-word encounters (type WORD) in translated conversations in the learning language: one per word per message, at most 5 per message (the most useful). `new_words` = those met for the first time. Event `vocabulary_exposure`. | When the learning engine saves a message's words (only if *Learn from my messages* is on). | A sample of the words in a message, not all. Lessons, reading tool and practice are not exposures. |
| 3 | **Translation-help requests** | `translation_help_requests` | Times the user asked what a word means on an AlterLingua screen (reading tool). Once per word per reading. Event `translation_help_requested`. | The tap on a word. | Only where AlterLingua owns the screen; nothing inside WhatsApp bubbles. |
| 4 | **UNKNOWN to LEARNING** | `word_state_advances.unknown_to_learning` | Words whose Personal Language Map state moved up across that boundary (score reaches 10). Event `word_state_advanced`. | Each map change, comparing the stored state before and after. | Each boundary counted once per upward move; moves down (long absence, recent help) are not subtracted and a word can be counted again after falling back and rising. |
| 5 | **LEARNING to FAMILIAR** | `word_state_advances.learning_to_familiar` | Same, crossing score 40 with at least 2 correct recognitions. | as above | Correct recognitions are **not recorded anywhere yet** (no quiz), so this stays at 0 until recall checks exist. |
| 6 | **FAMILIAR to MASTERED** | `word_state_advances.familiar_to_mastered` | Same, crossing score 75 with at least 4 correct recognitions and 80% accuracy. | as above | Same limit as 5. |
| 7 | **Lessons completed** | `lessons_completed`, `lesson_cards` | Daily lessons whose last card was finished (once per lesson); cards completed. Events `lesson_completed`, `lesson_card_completed`. | Next on the last card. | Cards are lesson encounters, not tests of knowledge. |
| 8 | **Pronunciation attempts** | `pronunciation_attempts`, `pronunciation_understood` | Practice attempts that produced a usable recognition result, and how many the speech recognizer understood as the word. Event `pronunciation_attempted`. | The result of an attempt. | Silence and service errors are not attempts. "Understood" is speech recognition matching the word, **not a pronunciation score** (Milestone 20). |
| 9 | **Full Support usage** | `mode_actions.full_support` | Translation interactions plus help requests made while Full Support was selected. Event `assistance_mode_action`. | With metrics 1 and 3. | Counts the mode **selected**, see the note under 11. |
| 10 | **Adaptive usage** | `mode_actions.adaptive` | Same, while Adaptive was selected. | | |
| 11 | **On-demand usage** | `mode_actions.on_demand` | Same, while On-demand was selected. | | **Adaptive and On-demand do not yet change incoming translation** (they behave like Full Support there; Adaptive has an engine that is not connected, On-demand works only on the reading tool). So modes 10 and 11 measure what people *choose*, not a different experience. Do not compare outcomes between modes in this pilot. |
| 12 | **Translation Dependence over time** | `translation_dependence_percent` per week, `dependence_trend` | Words met that were not yet mastered, divided by all words met, times 100, per week. Shown only for weeks with at least 20 words; trend needs two such weeks. `dependence_trend` gives first and latest measured week and the change in points. | With metric 2: each word's state is read before the encounter changes it. | Falls only when words become MASTERED, which needs correct recognitions (limit of 5 and 6). See section 8, blocker B1. |

Supporting figures reported with them: `words_assisted` (the numerator of 12), `active_days` (days with any activity, 0 to 7), and
`map_snapshot` (how many words and phrases were encountered, LEARNING, FAMILIAR, MASTERED at the end of the week).
Precise formula and rules for metric 12: `docs/progress.md`, "Milestone 21 detail".

## 4. Event names

Stable identifiers used in this document, in the report and in any later upload. They are counters, not stored events.

| Event name | Feeds | Kind or value it carries |
| --- | --- | --- |
| `translation_completed` | metric 1 | `outgoing_text`, `outgoing_voice`, `incoming_text`, `incoming_voice` |
| `vocabulary_exposure` | metric 2 | count of words, count new |
| `translation_help_requested` | metric 3 | none |
| `word_state_advanced` | metrics 4 to 6 | `unknown_to_learning`, `learning_to_familiar`, `familiar_to_mastered` |
| `lesson_card_completed` | metric 7 | none |
| `lesson_completed` | metric 7 | none |
| `pronunciation_attempted` | metric 8 | understood: yes or no |
| `assistance_mode_action` | metrics 9 to 11 | `full_support`, `adaptive`, `on_demand` |

In code: `PilotMetrics.Event` (`learning/progress/PilotReport.kt`). Other product events named in CLAUDE.md section 51 (voice translation
initiated, upgrade screen viewed, quota reached, and so on) are **not** part of this pilot: there is no billing or quota yet.

## 5. Where each count is recorded (implemented)

- Daily counts: `learning/progress/ProgressLog` into `daily_progress` (Room `progress.db`, version 2 adds the pilot columns).
- Translations by kind: `MeteredLearningRecorder` wraps the learning recorder; a voice note is counted where it is translated. Only the
  kind is kept; the text goes on to the learning engine exactly as before.
- State advances: `LanguageMapService` compares a word's stored state before and after each change.
- Lessons: `LessonService` counts a completed lesson once. Mode: read from the saved setting at the moment of each action.
- The weekly report: `PilotReport.build(...)`, from the same daily totals the Progress screen uses.

Tests: `PilotMetricsTest` (counting rules, the exact report keys, values are never free text, no word from the map reaches the report),
`ProgressCalculatorTest` and `ProgressRecordingTest` (weeks and dependence).

## 6. Report format (schema `alterlingua.pilot.v1`)

One JSON document per participant per language, produced from the phone, containing, per week (oldest first, up to 26): `week`,
`start`, `translations`, `vocabulary_exposures`, `new_words`, `words_assisted`, `translation_help_requests`, `word_state_advances`,
`lessons_completed`, `lesson_cards`, `pronunciation_attempts`, `pronunciation_understood`, `mode_actions`,
`translation_dependence_percent` (null when under 20 words), `active_days`, `map_snapshot`; plus `dependence_trend`
(`no_data`, `collecting` or `ready` with first and latest week, percentages and change in points), `schema`, `participant`,
`app_version`, `generated_on`, `language`. An example is produced by the `PilotMetricsTest` "carries the trend" test (week 1 94%,
week 4 71%, week 8 49%).

## 7. Reporting and analysis requirements

**Cadence.** A weekly report per participant for the length of the pilot (suggested 8 weeks, so Week 1 to Week 8 can be compared as in the
design example), plus a final report.

**What the pilot team must produce for the owner:**
1. *Uptake:* participants invited, consented, and with at least one translation interaction in week 1 (H1).
2. *Use:* per week, median and range of translation interactions by kind; active days; weeks active per participant.
3. *Learning:* median new words, vocabulary exposures, word-state advances, lessons completed, pronunciation attempts.
4. *Dependence:* each participant's weekly dependence series (pseudonymous), the median first-to-latest change, how many participants
   qualify (two measured weeks), and how many improved (H2).
5. *Assistance:* words assisted and help requests per week beside dependence, so a fall in dependence is not mistaken for less use.
6. *Modes:* how many actions were made under each mode, with the warning in metric 11.
7. *Data quality:* number of weeks under 20 words, reports missing, app versions.

**Controls for misleading results.** Report new words per week (harder material raises dependence); do not present a week under 20 words;
compare within participants, not between; state the number of participants behind every figure; show cohort cells only at n of 5 or more.

**Qualitative complement (recommended, outside the app):** a short weekly question, sent by the pilot team, on whether they could
communicate when they needed to and whether translation felt less necessary. It is the only view of communication success, because
failures are not instrumented.

## 8. Readiness for real users: what is and is not in place

**Blockers (the pilot cannot answer its main question until these are resolved):**

- **B1. The primary measure cannot move.** Mastery needs correct recognitions and nothing records them yet, so no word can become
  FAMILIAR or MASTERED, and Translation Dependence will read near 100% for everyone (metrics 5, 6 and 12 stay flat). The recall check
  (a Stitch design exists, "Learn: Recall Check") or an equivalent evidence source must be built before H2 can be tested. Without it the
  pilot can still test H1 and can report UNKNOWN to LEARNING.
- **B2. No real providers and no backend protection.** The translation, speech-recognition and speech-synthesis providers are development
  stand-ins, and the backend has no authentication, rate limiting or TLS of its own (`docs/privacy.md`, finding H3). Real providers,
  provider terms reviewed and disclosed to participants, an authenticated and rate-limited backend behind HTTPS, and a release build with
  the server address are needed.
- **B3. There is no way yet to get the report off a phone.** The report is built, but there is no export or upload screen. Two options:
  (a) *recommended for 30 to 50 people:* a "Share pilot report" action in Settings that shows the exact JSON, asks for the participant
  code, and hands it to the Android Share sheet so the participant sends it themselves (no server, no account, full transparency);
  (b) an authenticated upload endpoint, which needs the Authentication milestone and a data-processing arrangement.
- **B4. Consent and participant information** (what is measured, that content is not, who sees data, how to withdraw) must be written and
  agreed before anyone joins; the app has no such screen.

**Known gaps that limit interpretation (not blockers):**
- Translation failures are not counted, so there is no success rate (use the qualitative question).
- Adaptive and On-demand mode metrics measure selection only (metric 11).
- Only WhatsApp incoming messages and only Android are covered; WhatsApp must expose message text in its notifications.
- The count is of a sample of words per message (at most 5), and only while *Learn from my messages* is on.
- Install date and time to first use are not recorded; "first week" is the first week with activity, so add the participant's start
  date to the pilot log.
- Nothing in the metrics has been run on a phone (see `docs/progress.md`).
