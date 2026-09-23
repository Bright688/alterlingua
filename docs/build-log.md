# AlterLingua — Build Log

A running, plain-language record of how AlterLingua is being built. Entries are appended,
never rewritten. Secrets and private message content never go in this file.

`docs/progress.md` holds the status table; this file holds the story and the reasons.

---

## 2026-09-19 — Milestone 1: Environment and repository

**Goal:** Get a machine that can build the project, and a repository with the right rules.

**What was done**
- Read `CLAUDE.md` and summarised the product, architecture, privacy model and milestone rules.
- Checked the toolchain: Git 2.53, JDK 21, Android Studio 2026.1.4, Android SDK (platform 37,
  build-tools 36.0.0, platform-tools 37.0.1, command-line tools 23.0.0), Python 3.14, Node 26.
- Confirmed the two design/documentation sources work:
  - **Google Stitch** (MCP): three AlterLingua projects are readable, plus the
    "AlterLingua Design System". The main project is "AlterLingua UI/UX System" (18 screens).
  - **Android documentation**: the Google Developer Knowledge MCP server, with `WebFetch` on
    developer.android.com as a fallback.
- Connected and authorised a real test phone (Infinix X6728B, Android 15, API 35, WhatsApp
  installed) for the device gates.

**Decisions**
- The default branch is `main`, not `master`. The repo has no commits, so HEAD was pointed at `main`.
  Git's global `init.defaultBranch` was also set to `main`.
- `CLAUDE.md` was saved with a trailing space in its filename, so Claude Code could not
  auto-load it. It was renamed to `CLAUDE.md`.
- A build-log rule was added to `CLAUDE.md` (sections 9, 44 and 46): this file is the result.

**Problems and fixes**
- The `developer-knowledge` MCP server was configured but its tools were not loaded in the
  running session. Restarting the session loaded them.
- An API key was printed in a terminal transcript while inspecting that server's config.
  It should be rotated or restricted in Google Cloud. (The key itself is intentionally not recorded here.)

**Verification:** Implemented only. Tools were checked by running them; nothing here needs a device check.

---

## 2026-09-19 — Milestone 2: Native Android foundation

**Goal:** Create a native Android project (Kotlin + Jetpack Compose) in the existing repository,
with a Gradle Wrapper, using mutually compatible current stable versions, and without needing a
globally installed Gradle.

**Versions chosen** (each checked against the official repository, not memory)

| Component | Version | Why |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | Latest stable on Google Maven. 9.5 is still alpha. |
| Gradle | 9.6.1 | AGP 9.4 requires 9.6.0 or newer. |
| Kotlin | 2.4.20 | Latest stable. AGP 9 accepts Kotlin 2.2.10 or newer. |
| Compose BOM | 2026.09.00 | Compose UI 1.12.1, Material 3 1.4.0. |
| Compose compiler | plugin, same as Kotlin | Kotlin 2.0+ ships the compiler as a Gradle plugin. |
| JDK | 21 (system) | AGP needs 17 or newer. Java/Kotlin output targets 17. |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 | See decisions below. |

**What was built** (all under `android/`)
- Gradle Wrapper (`gradlew`, `gradle/wrapper/*`) pinned to Gradle 9.6.1 with its SHA-256 checksum.
  It was generated from a temporary Gradle download that was verified against Gradle's published
  checksum and then discarded, so no global Gradle is installed.
- `settings.gradle.kts`, root and app `build.gradle.kts`, `gradle.properties`, and a version catalog
  (`gradle/libs.versions.toml`) that keeps every version in one place.
- App module (`com.alterlingua`): `MainActivity`, a placeholder screen showing the app name and
  the core promise, and the AlterLingua theme.
- Theme colours taken from the Stitch "AlterLingua Design System" (light palette as defined; dark palette
  built from its documented dark surfaces).
- Placeholder launcher icon (brand blue, white "A"). To be replaced by the Stitch logo.
- Tests: 2 unit tests guarding the palette, and 1 on-device Compose UI test.
- Repository: `README.md`, root `.gitignore`, `android/.gitignore`, `docs/progress.md`, this file.

**Decisions and why**
- **Project lives in `android/`**, matching the repository layout in `CLAUDE.md` section 9.
- **Kotlin comes from AGP's built-in support** (new in AGP 9). No separate `kotlin-android` plugin.
  The Compose compiler plugin pulls Kotlin up to 2.4.20 from AGP's 2.2.10 default (confirmed in the
  resolved dependency tree).
- **`compileSdk` is 37 but `targetSdk` is 36.** Compose 1.12.1 requires compiling against API 37.
  `targetSdk` controls which new runtime behaviours the app opts into, so it was kept at 36 to avoid
  unreviewed behaviour changes on the keyboard and notification features. Revisit before release.
- **`minSdk` is 26** (Android 8.0): a safe floor for keyboards and notification services, and it lets
  the launcher use adaptive icons without extra image files. The test phone runs API 35.
- **Backup is off** (`allowBackup=false`) and **no permissions are requested**. AlterLingua will hold
  private learning data, so nothing is copied to cloud backup unless a later milestone opts in.
  Each later milestone adds only the permission it needs.
- **Brand palette, not Android dynamic colour**, so the app matches the approved Stitch design.
- **Deliberately not added yet:** Room, DataStore, navigation, ViewModels, and the empty
  `keyboard/`, `voice/` and other folders from the layout. They arrive with the milestones that use them.
- **No Java toolchain resolver.** The official template adds a plugin that downloads a JDK. It was left
  out because JDK 21 is already installed and compiles to Java 17 output.

**Problems and fixes**
1. `gradle wrapper` failed twice: first because the folder had no `settings.gradle.kts`, then because
   the `app/` module folder did not exist yet. Fixed by creating both, then it succeeded.
2. First build failed: Compose 1.12.1 requires `compileSdk` 37 or newer, and Google's own template
   still says 36. Fixed by raising `compileSdk` to 37 (the SDK already had platform 37).
3. A deprecation warning in the UI test (Google replaced the test-rule function). Fixed by using the
   `v2` version.
4. Side effect worth knowing: reading Google's project template with the `android create` command
   installed two packages into `~/Android/Sdk` (Android platform 36 and `build/templates`). They are
   harmless and small.
5. Unrelated files appeared during the work: `.idea/` at the repository root (Android Studio was opened
   on the repo root rather than `android/`) and `docs/mystuff` (the owner's own note). Neither was
   touched. `.idea/` is now git-ignored.

**Verification (automated)**

| Check | Result |
|---|---|
| `./gradlew assembleDebug` | Passed. Debug APK is about 11.6 MB, package `com.alterlingua`. |
| `./gradlew testDebugUnitTest` | Passed, 2 of 2 tests. |
| `./gradlew lintDebug` | Passed, 0 issues. |
| `./gradlew assembleDebugAndroidTest` | Passed (test APK builds, no warnings). |

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** The app has not been run on the phone yet, and the
on-device UI test has not been run.

**Manual test steps**
1. In Android Studio choose **File → Open** and select the **`android/`** folder (not the repository root).
   Wait for the Gradle sync to finish.
2. With the phone connected and USB debugging on, run `adb devices` and confirm the phone shows `device`.
3. From `android/`, run `./gradlew installDebug`. Open **AlterLingua** on the phone.
   Expected: a blank screen with "AlterLingua" in blue and the line
   "You write in your language. They receive theirs." The screen follows the phone's light/dark setting.
4. Optional: run `./gradlew connectedDebugAndroidTest` to run the on-device UI test.

**Not started (next):** Milestone 3, Onboarding. Waiting for the project owner's instruction.

---

## 2026-09-19 — Design audit (no milestone; planning only)

**Goal:** Audit the complete Stitch design before any screen is built, map each design to an Android
surface, and find missing or contradictory designs.

**What was done**
- Read every screen of the main Stitch project ("AlterLingua UI/UX System", 17 composite screens) and the
  two earlier projects, plus the design system, by downloading each screen's HTML and reading its text and
  prototype scripts. Screenshots were not reviewed visually.
- Wrote the full result to `docs/design-audit.md`: a 41-row mapping table (design → purpose → Android
  surface → Compose component → dependencies → milestone), a checklist of the 23 required designs, and the
  contradictions and missing designs.

**Main findings**
- All 23 required designs exist in some form, but several are conceptual: the translating state (D/K3) is only in
  an earlier project, and there are no error, notification-permission or onboarding-step designs.
- Several designs cannot be built as drawn: text injected into WhatsApp's own notification, annotations on
  WhatsApp chat bubbles, tap-to-reveal on WhatsApp text, and a microphone permission prompt inside the keyboard.
- Several designs conflict with `CLAUDE.md`: live/ghost translation, "zero-log / on-device" privacy claims, stored
  chat snippets and contact names, keystroke telemetry, streaks and XP, and hard-coded, inconsistent prices.
- `CLAUDE.md` §47 has no milestone for the app shell, Home or Settings.

**Verification:** Read-only audit. No code was written or changed.

**Status:** Waiting for the owner's decisions listed in section 4 of `docs/design-audit.md`.

---

## 2026-09-19 — Stitch: missing and partial designs added

**Goal:** Close the missing and partial designs found in the audit.

**What was done**
- Attempts to generate the screens from the Stitch tools timed out and created nothing, so the prompts were
  written to `docs/stitch-missing-designs-prompts.md` and the owner pasted them into Stitch directly.
- Nine screens were added to the main project (D/K3 translating, onboarding steps, Full Support, Adaptive,
  On-demand, voice result, incoming notification, voice-note share flow, privacy). Each was downloaded and checked.

**Result:** all nine exist and follow the banned-wording rules. Nine follow-up corrections and the remaining
undesigned states are listed in section 5 of `docs/design-audit.md`.

**Verification:** read-only check of the HTML text. Screenshots were not reviewed visually.

---

## 2026-09-19 — Stitch: second batch of designs checked

Five more screens were added by the owner (notification states, voice states, empty/completion states, recall
check, keyboard accents). All exist. Eight corrections (including one banned "on-device" claim and a reversed
voice direction) are listed in section 5b of `docs/design-audit.md`. Read-only check; screenshots not reviewed.

---

## 2026-09-19 — Stitch: corrections re-checked

The owner applied the second batch of corrections in Stitch. All eight were confirmed by reading the updated HTML.
Two screens were saved as new copies, so the older copies (Recall Check and Voice States) should be deleted.
The eight corrections for the first nine screens have not been applied yet. Details in section 5c of `docs/design-audit.md`.

---

## 2026-09-19 — Stitch: first-batch corrections re-checked

The six first-batch screens were edited. Most corrections are confirmed; two wording items remain (On-demand "99.4% confidence",
Privacy "overlays") plus the two duplicate screens. Details in section 5d of `docs/design-audit.md`.

---

## 2026-09-19 — Stitch: design corrections complete

The last two wording fixes (On-demand confidence figure and status panel, Privacy "overlays") were confirmed. All text corrections found in the
audit are now applied in Stitch. Only clean-up left: delete two older duplicate screens. See section 5e of `docs/design-audit.md`.

---

## 2026-09-20 — App shell (owner's "Milestone 1"): theme, navigation and five tabs

**Goal:** Turn the empty foundation into an app with the AlterLingua theme, a bottom navigation bar, and the
Home, Learn, Words, Progress and Settings screens, using placeholder data only. No keyboard, translation, backend,
voice, notifications or learning engine.

**Numbering:** the owner called this "Milestone 1". In `CLAUDE.md` section 47 it sits between milestones 2 and 3, so
`docs/progress.md` lists it as row 2b and asks the owner to confirm the numbering.

**What was built** (under `android/app/src/main/java/com/alterlingua/app/`)
- **Package change:** `com.alterlingua` became `com.alterlingua.app` (namespace and application ID), as requested.
  The old placeholder screen was removed.
- **Theme** (`ui/theme`): colours, shapes and type scale from the Stitch design system, light and dark.
  Plus Jakarta Sans and Inter are bundled as variable fonts in `res/font` (SIL Open Font License; licence texts in `docs/licenses/`).
- **Navigation** (`navigation/`, `AlterLinguaApp.kt`): a Material 3 `NavigationBar` with five tabs and a `NavHost`.
  Each tab keeps its own state. The Home button "Continue today's lesson" opens the Learn tab.
- **Screens** (`ui/home`, `learn`, `words`, `progress`, `settings`): one screen plus one ViewModel each.
  The ViewModels hold fixed placeholder state; screens are stateless and receive that state.
- **Shared pieces** (`ui/components`): tab frame with logo and title, card, status chip, language-map bar,
  dependence chart, and three navigation icons.
- **Placeholder data** (`data/PlaceholderData.kt`): one source for every number, so screens cannot disagree
  (409 words in total: 42 new, 86 learning, 131 familiar, 150 mastered).
- **Models** (`learning/LearningModels.kt`): the four learning states (UNKNOWN is shown as "New"), the three assistance modes,
  and small data classes. No logic.
- **Privacy:** backup and device transfer are switched off with `data_extraction_rules.xml` and `backup_rules.xml`.
- **Tests:** 20 unit tests (palette matches Stitch, placeholder numbers add up, destinations, Words search and filter,
  Learn stepping, Settings) and an on-device test that taps through all five tabs.

**Dependencies added and why**

| Dependency | Version | Why |
|---|---|---|
| `androidx.navigation:navigation-compose` | 2.10.1 | Moves between the five tabs and keeps each tab's state. Google's documented pattern for a bottom bar. (Navigation 3, 1.1.7, is also stable and can replace it if we later need custom back stacks.) |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | Gives each screen a ViewModel. |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | Reads ViewModel state safely with the screen lifecycle. |
| `androidx.compose.material:material-icons-core` | from the Compose BOM | Home and Settings icons. The other three tab icons are drawn from path data, avoiding the large "extended" icon library. |

Already present from before: Compose BOM 2026.09.00, Material 3, activity-compose, and the test libraries.

**Decisions and why**
- **Navigation Compose, not Navigation 3.** Five flat tabs need no custom back stack, and Navigation Compose has tab state saving built in.
- **Screens are stateless, ViewModels are tiny.** The pattern (state flows down, events go up) is in place, but there is no repository, database or network layer yet.
- **Left out of the Stitch look on purpose:** the microphone and bell icons in the top bar (voice and notifications are out of scope),
  streaks, XP, "keystrokes", "Slack and Mail" sources, chat snippets and contact names, and the "Simulate Empty State" toggle.
  These were flagged in the design audit.
- **Consistent numbers.** Stitch showed 409, 1,247 and 664 words on different screens; the app uses 409 everywhere.
- **Honest labels.** Sample content says so ("Sample entries", "Sample data", "Not set up yet", "Available later"), and the assistance-mode and
  reminder settings state that they take effect in a later milestone. Settings are kept in memory only and are not saved.
- **No time-of-day greeting** ("Good evening"), since a fixed placeholder could be wrong.

**Problems and fixes**
1. The first build compiled with no errors.
2. **Correction to the foundation entry above:** the earlier lint report was described as "0 issues". That came from a miscounted search;
   lint reported 0 errors but also some warnings (very likely the same ones listed below). It is now checked properly.
3. Lint warned that `allowBackup="false"` does not stop phone-to-phone transfer on Android 12+. Fixed by adding the two rules files.
4. Lint now shows 2 warnings, both intentional: `targetSdk` 36 is behind the newest (kept on purpose, see the foundation entry),
   and a newer Gradle (9.7.1) exists. AGP 9.4 is documented against Gradle 9.6, so it stays on 9.6.1.
5. The first Gradle run took about 35 minutes, mostly waiting on a busy background Gradle process and downloads. Later builds take under 3 minutes.

**Verification (automated)**

| Check | Result |
|---|---|
| `./gradlew assembleDebug` | Passed. APK about 12.7 MB (the two fonts are about 1 MB), `com.alterlingua.app`, no permissions requested. |
| `./gradlew testDebugUnitTest` | Passed, 20 of 20. |
| `./gradlew lintDebug` | 0 errors, 2 warnings (intentional). |
| `./gradlew assembleDebugAndroidTest` | Passed. The on-device test builds but has not been run. |

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** The app has not been opened on the phone. The phone was not connected when the build finished.

**Manual test steps:** in the milestone report and `README.md`.

**Not started (next):** onboarding (CLAUDE.md milestone 3). Waiting for the owner's instruction.

---

## 2026-09-20 — App shell manually verified

The owner installed the app from Android Studio on the Infinix X6728B (Android 15) and confirmed that it runs and works.
`docs/progress.md` now marks the app shell (row 2b) and the native foundation (row 2) as MANUALLY VERIFIED.
The on-device navigation test (`connectedDebugAndroidTest`) has still not been run; only the owner's hands-on check counts as verification.
Which parts were checked beyond "it works" was not itemised.

---

## 2026-09-20 — Onboarding (owner's "Milestone 2", CLAUDE.md milestone 3)

**Goal:** Build the complete onboarding flow from the Stitch design in Jetpack Compose. Capture native language, target language,
learning purpose, current level, assistance mode, the daily learning preference and the reminder time. Save them with Jetpack DataStore.
English and French only. No translation or learning engine. Light and dark mode.

**Design source:** Stitch "Onboarding Setup Flow" (the corrected version). Each step was rendered in Chrome from the design's HTML and looked at
before building.

**What was built** (under `android/app/src/main/java/com/alterlingua/app/`)
- **Saved settings:** `learning/UserSettings.kt` (language, purpose, level and settings models; `Languages.supported` is English and French, as data),
  `storage/UserSettingsRepository.kt` (an interface plus the DataStore version). Unknown or damaged saved values fall back to defaults instead of crashing.
- **App start:** `AlterLinguaApplication`, `AppViewModel`, `AlterLinguaRoot` (shows onboarding until it is finished, then the tabs),
  `AppViewModelProvider` (creates the ViewModels that need the saved settings).
- **Onboarding** (`ui/onboarding/`): `OnboardingViewModel`, `OnboardingScreen` (host, back handling, time picker dialog),
  `OnboardingSteps` (the five steps), `OnboardingComponents` (header, selection cards, icons).
- **Existing screens now use the saved values:** Home shows the language being learned; Settings shows the saved languages, mode and reminder,
  and saves changes to the mode and the reminder switch.
- **Tests:** 22 new unit tests (settings rules, real DataStore saving and loading, onboarding steps and saving, app start, Settings) and an
  on-device test that taps through the flow with an in-memory store (it never touches the phone's real settings).

**The five steps:** 1 Welcome · 2 Languages & Goals (you type / they receive, purpose, level) · 3 Assistance Mode ·
4 Daily Reminder (on/off, presets, any time via a clock dialog) · 5 All Set (summary, then "Start using AlterLingua").

**Dependencies added and why**

| Dependency | Version | Why |
|---|---|---|
| `androidx.datastore:datastore-preferences` | 1.2.1 | Saves the answers on the phone. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | Test only. Lets unit tests run the ViewModels. |

**Deviations from the Stitch design, and why**
- **5 steps instead of 6.** Stitch's step 5 (turn on keyboard, notification access, microphone) is a later milestone (CLAUDE.md milestone 4).
  Stitch's last screen also shows fake statuses ("Keyboard: Done", "Voice: While using app") that would be untrue, so the final screen shows the user's
  real answers and a "Coming next" note instead. The counter reads "of 5".
- **Welcome copy.** Dropped the "real-time simulation" and "executive intelligence layer" wording (translation is manual, CLAUDE.md section 17). The example is
  the canonical "Are you coming tomorrow?" → "Tu viens demain ?", with a line that the user presses Send.
- **Reminder on/off switch added.** Stitch has only the time; the request asked for a daily learning preference as well as a time.
  The reminder note says reminders start once notifications are set up later. No notification permission is requested yet.
- **Continue button pinned at the bottom** of each step (thumb-friendly) instead of inline after the content.
- **Left out:** the "Overview" pill (a prototype control) and the step-5 keyboard/permission cards.
- **"More languages" note** no longer names Spanish, German and Japanese, since nothing is promised.
- Assistance mode wording follows the owner's text: Full Support "Translate everything", Adaptive "reduce assistance based on the Personal Language Map",
  On-demand "Translate only when I ask". The modes are saved but have no effect yet.

**Decisions and why**
- **A repository interface, not DataStore everywhere.** Screens and tests use `UserSettingsRepository`; only one class touches DataStore. That is why the tests can use a fake.
- **Answers are saved each time the user continues**, so leaving part-way keeps them; only the final button marks onboarding complete.
  The current step also survives rotation and the system closing the app.
- **Native and target language can never be the same.** Picking one for both swaps the other (with two languages this behaves like a swap).
- **Defaults:** purpose Work, level Beginner, mode Full Support (the recommended one), reminder on at 8:00 PM.
- **Backup:** the settings file is in the app's private storage, which the earlier backup rules already exclude from cloud backup and device transfer.

**Problems and fixes**
1. One compile error (a missing arrow-icon import), fixed at once.
2. Lint flagged a default `SavedStateHandle()` in production code as test-only. Made the parameter required and updated the one test.
3. While writing the screens I left a few unused placeholder lines behind; they were removed before the final build.

**Verification (automated)**

| Check | Result |
|---|---|
| `./gradlew assembleDebug` | Passed. APK 13.3 MB. |
| `./gradlew testDebugUnitTest` | Passed, 42 of 42. |
| `./gradlew lintDebug` | 0 errors, 2 warnings (intentional). |
| `./gradlew assembleDebugAndroidTest` | Passed. The on-device onboarding test builds but has not been run. |

**Status: IMPLEMENTED, not MANUALLY VERIFIED.** Nobody has looked at the onboarding screens on the phone yet, so the look (including dark mode and the three
icons drawn from path data) is unchecked.

**Manual test steps:** in the milestone report.

**Not started (next):** Android system setup and permissions (CLAUDE.md milestone 4). Waiting for the owner's instruction.

---

## 2026-09-20 — CLAUDE.md now requires multilingual support (impact check; no code changed)

The owner added a "Language Support" section to `CLAUDE.md` (inserted as a second "# 6"; the following section numbers were not shifted, so all
other section references stay valid). Key rules: AlterLingua is multilingual from the start (minimum English, French, Spanish, German, Italian,
Dutch, Chinese, Japanese); nothing may assume English as source or French as target; language pairs must not be hard-coded; standard language
codes; Unicode-safe text and non-Latin writing systems; the keyboard shows the chosen pair dynamically with a searchable selector; one Personal
Language Map per learning language; language-aware lessons; assistance modes independent of the pair; voice and provider capabilities per language;
end-to-end tests may start with English ↔ French but must then cover Spanish, German, Italian, Dutch, Chinese, Japanese and one non-English source.

**Where the current app (onboarding milestone) falls short**
1. `Languages.supported` holds only English and French.
2. The `Language` model has a code and names but no locale or script information (needed for Chinese and Japanese).
3. Choosing the same language for both fields swaps to "the first other language", which is wrong once there are 8 languages.
4. Sample content on Home, Learn and Words is French. With another target language chosen it would show French words under the wrong language.
5. Copy: "EN → FR" on the welcome example (acceptable as an example) and "More languages are coming later" (no longer true).
6. Unit test `initialLanguages_areEnglishAndFrench` encodes the old rule.
7. Later work: the Personal Language Map must be keyed by learning-language code, and the settings model holds a single learning language.
8. Stitch: the keyboard language selector lists Portuguese and lacks Dutch, Chinese and Japanese.
9. The bundled fonts (Inter, Plus Jakarta Sans) have no Chinese or Japanese glyphs; Android's system fonts should cover them, but it needs a check on the phone.

The owner's earlier onboarding instruction said "Initial languages: English, French". The newer `CLAUDE.md` text supersedes it for the language list.

---

## 2026-09-20 — Multilingual update to onboarding (follows the new CLAUDE.md "Language Support")

**Goal:** Bring the onboarding milestone in line with the new multilingual rules, with sample content that follows the language the user selects.

**What changed**
- **Languages:** the list is now English, French, Spanish, German, Italian, Dutch, Chinese and Japanese. A language has a two-letter code for
  translation requests (`zh`) and a full tag that also carries the script (`zh-Hans`), which is what gets saved. An earlier saved plain code such as
  `zh` still loads correctly. Chinese is Simplified for now (owner's choice by default); Traditional can be added as `zh-Hant`.
- **Language menus:** show both names ("Spanish · Español", "Japanese · 日本語") with a tick on the current choice.
- **Swap rule:** choosing the other field's language now swaps the two (it used to pick "the first other language", which is wrong with eight).
- **Sample content:** new `SampleContent` holds one lesson (3 items) and one word list (12 words) for each of the 8 languages, with IPA for
  European languages, pinyin for Chinese and romaji for Japanese. French, Spanish and German lessons use the examples from `CLAUDE.md`.
  Home, Learn and Words now show the sample for the chosen learning language. The French-only samples were removed from `PlaceholderData`.
- **Bug found and fixed:** the Learn screen wrapped every example sentence in French quotation marks (« »). It now uses neutral quotes.
- **Copy:** "More languages are coming later" became "More languages can be added over time." The Words and Learn screens say the samples are
  placeholders with meanings in English.
- **Tests:** 61 unit tests in total (19 new or rewritten) covering the eight languages, tags and swapping, real DataStore saving of every language, sample
  content for every language (counts, no blanks, Chinese and Japanese really use their scripts, no corrupted text), and Home, Learn and Words following the
  selected language. One new on-device test picks Spanish and Japanese from the menus.

**Limits, stated plainly**
- Sample meanings and example translations are written in English, so a Spanish speaker learning German still sees English meanings. The real learning
  engine will use the user's own language. The screens say so.
- The sample phrases were written by hand for placeholder purposes and have not been reviewed by native speakers.
- Chinese and Japanese text uses Android's system fonts (our bundled fonts have no such characters). This needs a look on the phone.

**Verification (automated):** build passed (APK 13.3 MB), 61 of 61 unit tests passed, lint 0 errors and the same 2 intentional warnings, and the
on-device test builds but has not been run. **Status: IMPLEMENTED, not MANUALLY VERIFIED.**

---

## 2026-09-20 — Language handling brought in line with the rewritten CLAUDE.md sections 6.1 to 6.23

The owner rewrote the language section in more detail. Checked against the app, five things needed changing (all done, all automated checks pass):

1. **Native names (6.2).** Selectors now show the language's own name (Français, Español, 中文, 日本語) and never "French", "Spanish" and so on as the primary
   name. The earlier "Spanish · Español" display was wrong. Home, Settings and the onboarding summary use the same names.
2. **Labels (6.3).** Onboarding cards say "My language" and "Language I want to learn" (CLAUDE.md outranks Stitch's "YOU TYPE / THEY RECEIVE").
3. **Codes and locales (6.5).** A language is identified by its base code (`zh`), and that is what gets saved. The earlier `zh-Hans` tag mixed a script variant
   into the identity. Locale ("zh-CN") is separate data. Values saved by the earlier version ("zh-Hans", "zh-CN") still load as Chinese.
4. **Central catalogue (6.6).** New `learning/Language.kt`: code, native name, English name, locale, writing system, and separate flags for translation,
   speech-to-text, text-to-speech and learning. Speech flags are false for every language until a speech provider exists, so nothing claims coverage
   that is not there. Selection lists come from the catalogue (`forNativeSelection`, `forLearningSelection`).
5. **Language switching (6.20).** Settings now lets the user change both languages, using the same menu as onboarding. Changes are saved, and Home, Learn and
   Words follow. Nothing is erased when switching. The Personal Language Map does not exist yet, so keeping one map per language is a later requirement.

**Also:** a shared `LanguageMenu` component (used by onboarding and Settings); 7 new tests (68 in total) for names, codes versus locales, capability flags,
legacy saved values, saving the base code, and switching in Settings; the on-device test now expects native names.

**Not changed, on purpose:** Chinese is still shown as 中文 with the base code `zh` (no Simplified/Traditional choice yet; 6.5 lists `zh-CN` and `zh-TW` as later
regional or script cases). The sample lessons and words are unchanged.

**Verification (automated):** build passed (APK 13.3 MB), 68 of 68 unit tests passed, lint 0 errors and the same 2 intentional warnings, on-device test builds but
was not run. **Status: IMPLEMENTED, not MANUALLY VERIFIED.**

---

## 2026-09-20 — Language selectors: native name plus a small English label

The owner asked why Chinese and Japanese show as 中文 and 日本語. The reason is `CLAUDE.md` 6.2 (native names first). The owner chose option B:
the native name stays the primary name, with the English name as a smaller label under it, so people who cannot read the script can still tell the
languages apart. This still follows 6.2 because the native name is the primary one.

**Changed:** `Language.secondaryName` (English label, empty when it equals the native name, so English has none); the shared language menu, the two onboarding
language cards (now equal height) and the Settings language rows show the label. The Home tag, the summary screen and the samples use the native name only.
The old role captions on the onboarding cards ("I write in this language") were replaced by the English label; the card labels "My language" and
"Language I want to learn" already say the role. One new unit test (69 in total). The owner confirmed that Chinese and Japanese characters display correctly on the
phone, so the font question is closed.

**Verification (automated):** build passed, 69 of 69 unit tests passed, lint 0 errors and the same 2 intentional warnings. **Status: IMPLEMENTED, not MANUALLY VERIFIED**
(the owner has seen the characters render, but not this layout change).

---

## 2026-09-20 — Milestone 4: Android system setup and permissions

**Goal (owner):** onboarding/setup UI and status detection for keyboard enablement, keyboard selection, notification-listener access and microphone permission; explain each permission first; never ask before its explanation screen; navigate to the right Android settings; detect state where Android allows. No translation, notification translation or voice recording.

**Docs consulted first:** Android developer documentation via the developer-knowledge MCP (input method manager and IME manifest rules, notification-listener access checks and settings actions, restricted settings on Android 13+, the runtime-permission flow and rationale).

**Built:**
- `setup/` — `SetupStatus` (with pure rules `microphoneStatus`, `isSelectedKeyboard`), `SetupChecker` (+ `AndroidSetupChecker`), `SystemSettings` (intents with fallbacks), `SetupViewModel`.
- `keyboard/AlterLinguaKeyboardService` and `notifications/AlterLinguaNotificationListener` (inert placeholders), manifest entries, `res/xml/method.xml`, `RECORD_AUDIO` permission with an optional microphone feature.
- `ui/setup/SetupUi.kt` (actions, live status, Settings rows) and `ui/onboarding/SetupSteps.kt` (three new steps); onboarding now 8 steps; summary and Settings → Setup show live status.
- `UserSettings.microphonePermissionAsked` (DataStore key `microphone_permission_asked`); dependency `androidx.core:core-ktx` 1.19.0.

**Decisions:**
- Android only lists a keyboard or notification listener that the app declares, so status detection and the settings screens cannot be tried without them. I added placeholders that do nothing: the keyboard shows a message and a "Switch keyboard" button so nobody is stuck on it, and neither service reads typed text or notifications (CLAUDE.md sections 16, 21, 25). The real keyboard and the notification reading come in their own milestones.
- Stitch has one setup step; it was split into three so each explanation comes before its request. Copy is honest that the keyboard cannot type yet.
- Android cannot say "never asked" and "blocked" apart, so the app remembers that it has asked. Onboarding saves whole-settings drafts, which would have erased that flag; the save now keeps it from the stored value (covered by a test).
- Settings has no explanation screen, so each row says what its button does before it is tapped.
- Status is polled on resume and on window focus, because Android has no callback for these settings (the chooser dialog does not trigger resume).

**Problems:** the first full Gradle run was killed by the system for low memory (no result); I re-ran in separate steps. Four `UseKtx` lint warnings from the new keyboard file were fixed.

**Verification (automated):** debug build and instrumented-test compile passed; 84 of 84 unit tests passed; lint 0 errors, 2 intentional warnings (target SDK, Gradle version). **Status: IMPLEMENTED, not MANUALLY VERIFIED.** Nothing was run on the phone; the instrumented tests were compiled only.

**Manual test steps (run from Android Studio ▶ on the phone):**
1. Start clean: `adb shell pm clear com.alterlingua.app`, then Run. Go through Welcome to Reminder; the header reads "STEP n OF 8".
2. **Step 5 Keyboard:** both statuses read Off / Not in use, "Choose keyboard" is disabled. Tap "Open keyboard settings", switch AlterLingua on, accept Android's warning, go back: status changes to On with no tap. Tap "Choose keyboard", pick AlterLingua: "In use". The bottom button changes from "Skip for now" to "Continue".
3. Open any text field: the AlterLingua panel shows a message and "Switch keyboard". Use it to return to your usual keyboard. Switch AlterLingua off in Android and return: status goes back to Off.
4. **Step 6 Incoming messages:** no permission is requested on arrival. Tap "Open notification access": AlterLingua's page opens. If the switch is greyed out (Android 13+, sideloaded), use "Open app info" → ⋮ → "Allow restricted settings", then retry. Switch it on, confirm: status "Access allowed". Switch off: status returns to "Not allowed".
5. **Step 7 Microphone:** arriving shows an explanation and no dialog. Tap "Allow microphone": the system dialog appears. Choose "Don't allow" once: status "Not allowed", button "Ask again". Decline a second time: "Blocked in Android settings" with "Open app settings". Allow it there: status "Allowed". Reset with `adb shell pm revoke com.alterlingua.app android.permission.RECORD_AUDIO` and `pm clear` to repeat; also test "While using the app".
6. **Step 8:** the summary shows the three real statuses. Finish, open Settings → Setup: same statuses, each row explains its button; buttons behave as above.
7. Rotate the phone and go to the background and back on each setup step: nothing is re-requested and status stays correct. Try dark mode.
8. Confirm nothing is recorded, no notification is read, and no message is ever sent.

---

## 2026-09-20 — Milestone 2 (onboarding) updated to the revised brief

**Goal (owner):** re-issue of the onboarding brief. New or sharper points: the purpose and level questions must name the selected target language; the level is for the SELECTED target language; Adaptive refers to the Personal Language Map of the selected target language; explain how to test English → Français and English → Español.

**Changed:**
- Purpose title is now "Why do you need <language>?" and the level title "How much <language> do you already know?", using the native name. The Adaptive card says "based on your <language> Personal Language Map".
- The level is now per language (`UserSettings.otherLevels` plus DataStore keys `language_level_<code>`). Changing the target shows that language's own answer (Beginner if never answered) and keeps the previous language's answer, so switching back restores it. Levels saved by earlier versions are still read for the current target. This follows CLAUDE.md 6.13 and 6.21 without building the Personal Language Map early.
- Everything else in the brief (native names, no hard-coded pair, DataStore, light/dark, no engines) was already in place and is unchanged.

**Problem found:** an instrumented test from milestone 4 still expected the summary at step 5 of 8; fixed it to walk all steps, and added checks for the dynamic questions in Español and 日本語.

**Verification (automated):** build and instrumented-test compile passed; 91 of 91 unit tests passed; lint 0 errors, 2 intentional warnings. **Status: IMPLEMENTED, not MANUALLY VERIFIED** (nothing run on the phone; instrumented tests compiled only).

**Manual test steps (Android Studio ▶ on the phone; start with `adb shell pm clear com.alterlingua.app`):**
1. **English → Français.** Welcome → Get started. Step 2 shows "My language: English" and "Language I want to learn: Français". Below: "Why do you need Français?" and "How much Français do you already know?". Pick Travel and Intermediate. Continue. On step 3 the Adaptive card says "…your Français Personal Language Map." Pick Adaptive, Continue, pick Morning at step 4, Continue. Skip steps 5 to 7 with "Skip for now" / "Continue without microphone". Step 8 summary reads English → Français, Travel, Intermediate, Adaptive. Tap "Start using AlterLingua"; Home shows English → Français.
2. **English → Español.** Clear the app data again. On step 2 tap "Language I want to learn" and choose Español. Both questions change to "Why do you need Español?" and "How much Español do you already know?", and the level resets to Beginner. Choose Some basics, Continue. Step 3 says "…your Español Personal Language Map." Finish; summary and Home show English → Español, level Some basics.
3. **Levels are kept per language.** On step 2 pick Français, Intermediate; switch the target to Español (shows Beginner), pick Some basics; switch back to Français: Intermediate is shown again.
4. **Native names.** Open both selectors: English, Français, Español, Deutsch, Italiano, Nederlands, 中文, 日本語, each with a small English label. Also try Deutsch ("How much Deutsch…") and 日本語.
5. Picking the other field's language swaps the two. Rotate the phone mid-flow and use back: answers stay. Kill and reopen the app on step 3: the earlier answers are still there. Check light and dark mode.
6. Nothing translates and no lessons are generated; this is only onboarding.

---

## 2026-09-20 — Milestone 5: FastAPI backend foundation

**Goal (owner):** create `backend/` (Python, FastAPI, modular monolith) with modules prepared for translation, speech, learning, vocabulary, mastery, lessons, users, database; implement only `GET /health` and `POST /v1/translate`; a `TranslationProvider` abstraction; dynamic language codes (en fr es de it nl zh ja); provider capability checks and a controlled unsupported-language response; env-var config and `.env.example`; validation; Unicode safety; no message storage; no message text in logs; no hardcoded keys; tests over several targets.

**Built:** `backend/app/` with `core` (settings, errors, logging), `api` (health, v1 router), `translation` (language catalogue, schemas, provider abstraction, fake provider, registry, service, route), `main.py` (app factory, error handlers, request logging), and empty packages for the other seven modules. `requirements*.txt`, `pyproject.toml` (pytest only), `.env.example`, `backend/README.md`, 66 tests in `backend/tests/`.

**Decisions:**
- **No real provider yet.** The brief names none, and choosing one means choosing a vendor and a key, which is the owner's call. I built the abstraction and a `fake` development provider that is loudly not real: it knows three sample phrases in every language, and any other text is returned marked `[es] ...`. The server logs a warning at startup when it is in use. Multilingual verification per CLAUDE.md 6.22 therefore only covers the pipeline until a real provider is added.
- One error shape for everything (`{"error": {"code", "message", ...}}`). FastAPI's default validation answer repeats the submitted values, so it is replaced with one that names only the field and the problem.
- Unsupported language, unsupported pair and other client mistakes are HTTP 422 with a specific `code`; provider failure is 502, unavailable 503, timeout 504. The catalogue's supported list is included in the unsupported-language response.
- Text already in the target language is returned unchanged rather than translated again.
- Text is normalised to NFC and length is counted in characters, not bytes. Control characters other than newline, carriage return and tab are rejected. `context` is `messaging` or `general`; `tone` is `natural`, `formal` or `casual`; unknown fields are rejected.
- Logs contain languages, character counts, provider name and latency only. Uvicorn's own access log shows the path and status, never the body.

**Problems:** `.gitignore` had `.env.*`, which would also have ignored `.env.example`; added an exception. My first batch of config files landed in `backend/app/` because of a wrong working directory; moved. The test client warned that `httpx` is deprecated; `httpx2` is used for tests. `pkill` in one command matched my own shell; nothing was lost.

**Verification (automated):** 66 of 66 backend tests pass. A deliberate break (logging the text) made the privacy test fail, then was reverted. I also started the real server and called `/health`, the documented example, Japanese and an unsupported language with curl; the server log had no message text. **Status: IMPLEMENTED, not MANUALLY VERIFIED** by the owner. Not connected to the Android app.

**Manual test steps:**
1. `cd backend && python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt`
2. Run the tests: `.venv/bin/python -m pytest` (expect 66 passed).
3. Start the server: `.venv/bin/uvicorn app.main:app_factory --factory --reload --port 8000`.
4. `curl localhost:8000/health` returns `status: ok` and `translation_provider: fake`.
5. Call the example request from `backend/README.md`; expect `¿Vienes mañana?`. Repeat with `"target"` set to each of fr, de, it, nl, zh, ja.
6. Try `"target":"pt"`: expect HTTP 422 with `unsupported_language` and the supported list. Try an empty `text`, or a `source` equal to `target`.
7. Try non-English input with `"source":"auto"`, for example `明日来ますか？` to `en`.
8. Watch the server terminal: only languages, character counts and latency appear, never the text.
9. Open http://localhost:8000/docs to see the API description.

---

## 2026-09-20 — Text and control sizes reduced (owner feedback)

**Feedback (owner):** the font sizes are too big; use standard sizes for text and other things.

**Findings:** the phone's font scale (1.0) and display density (320 dpi) are normal, so this is the app. Body and label text was already at standard Android sizes (16 / 14 / 12 sp). The oversized parts were the headings, the 56 dp buttons and chips, and the 96 dp welcome icon. These came from the Stitch design; the owner's instruction outranks Stitch (CLAUDE.md 53).

**Changed:** in `ui/theme/Type.kt`: display 36 to 30 sp, headline large 28 to 24 sp, headline medium 22 to 20 sp (line heights adjusted). Onboarding continue button and purpose chips 56 to 48 dp (the standard touch-target size). Welcome hero icon 96 to 80 dp with a 40 dp glyph. Body, label and title sizes were not changed.

**Note:** while looking for the cause I captured one screenshot of the phone. It showed an unrelated app rather than AlterLingua; I deleted it immediately and did not look further.

**Verification (automated):** unit tests and debug build pass. **Status: IMPLEMENTED, not MANUALLY VERIFIED** (not looked at on the phone).
**Manual test:** Run from Android Studio, go through onboarding and open each tab; headings, buttons and chips should look smaller and standard. Tell me any screen or element that still looks too big.

---

## 2026-09-20 — Text size reduced further (owner feedback, second round)

**Feedback (owner):** the font sizes are still too big on every screen.

**Change:** the whole type scale in `ui/theme/Type.kt` is now compact (sp): display 26, headline large 22, headline medium 18, title large 16, title medium 14, body large 14, body medium 13, body small 12, label large 13, label medium 12, label small 11. Previously body was 16 / 14 / 12 and headings 30 / 24 / 20. Every screen uses these styles, so all text follows. Nothing is hardcoded per screen.

**Trade-off:** 14 sp body text is the size many Google apps use. Going below 12 sp for any text would hurt legibility, so the smallest sizes were kept at 11 to 12 sp.

**Verification (automated):** unit tests and debug build pass. **Status: IMPLEMENTED, not MANUALLY VERIFIED.**
**Manual test:** Run from Android Studio and look at each tab and the onboarding steps. If any element is still too large, name it and it will be adjusted individually.

---

## 2026-09-20 — Milestone 6: the AlterLingua keyboard (basic IME)

**Goal (owner):** implement AlterLingua as a real Android input method named **AlterLingua**: `InputMethodService`, manifest, input-method XML, QWERTY, shift, backspace, space, enter, punctuation, number/symbol switching, `InputConnection` text insertion. No translation, no microphone. Success: enable, select, open WhatsApp, tap the composer, type normal text.

**Docs consulted first (Android):** creating an input method (input view, `InputConnection`, `commitText`, `deleteSurroundingText`), `InputMethodService` callbacks (`onStartInput`, `onEvaluateFullscreenMode`), `sendKeyChar` and `sendDownUpKeyEvents` (discouraged except for `TYPE_NULL` fields), input-method XML attributes, IME switching (`supportsSwitchingToNextInputMethod`, `switchToNextInputMethod`), and edge-to-edge enforcement for target SDK 35+.

**Stitch:** K1 "Keyboard / Default" from the keyboard project: key colours, 46 px keys with 4 px gaps and 8 px corners, function keys in a tinted grey-blue, an indigo action key, a space bar labelled with the keyboard name. The toolbar (AUTO to FR, Translate, microphone) and suggestion chips are translation and voice features, so they are left out. The design's emoji key became a globe key (below). The design uses a different indigo (`#3525CD`) from the app's blue (`#004AC6`); I used the app's blue for the action key so the keyboard matches the app.

**Built** (`keyboard/`): `KeyboardModel` (pages, layouts, key specs), `EditorTraits` (what the field asked for, and the Enter policy), `TextTarget` (interface plus the real `InputConnection` implementation), `KeyboardController` (all typing logic, no Android views), `KeyCapView` and `AlterLinguaKeyboardView` (the panel, hand-drawn keys), `KeyboardColors`, `AlterLinguaKeyboardService`. Six vector icons. Manifest and `method.xml` updated (`supportsSwitchingToNextInputMethod`). Setup screens' wording changed: the keyboard is no longer described as a placeholder.

**Decisions:**
- **Classic Android views, not Compose, for the keyboard.** Compose inside an IME needs hand-built lifecycle and saved-state owners, which is fragile in a service. Views are lighter, start faster and are the documented route. The app itself stays Compose.
- **Real views per key rather than one canvas.** Each key gets its own touch stream (fast two-thumb typing works) and is visible to screen readers.
- **Logic separated from views** (`KeyboardController` and `TextTarget`) so typing, shift, auto-capitals, backspace and Enter are unit tested without a phone.
- **Enter never runs "Send".** CLAUDE.md says AlterLingua must never send a message. WhatsApp can be set to "Enter is send"; in that case the field's action is Send, and the keyboard adds a line break instead (in single-line fields with a Send action it does nothing). The owner presses WhatsApp's own Send button. This differs from normal Android keyboards. It is one rule in `enterBehavior()` and is easy to change if the owner prefers the standard behaviour. Go, Search, Next and Done still run.
- **Globe key.** Android's guidance says every keyboard must offer a way to switch; it takes the emoji key's place. It switches to the next keyboard (Android 9+) or opens the chooser.
- **Bottom padding for the navigation bar.** With target SDK 36, Android 15 draws behind the navigation bar, so the panel pads itself by the navigation-bar inset (zero where the system already does it).
- **Landscape stays a compact panel** (`onEvaluateFullscreenMode` returns false), not a full-screen keyboard.
- **Delete** removes a selection, or one whole code point (so emoji are not split); fields that only accept key events get a real Delete key event.
- **Privacy:** the keyboard never stores or logs typed text and asks for no permissions. It reads nothing from the field except a yes/no "is the cursor at a sentence start?" (for capitals) and whether a selection exists (for Delete). Leftover underlined composing text from a previous keyboard is finished when a new field starts, so it is not replaced by the first key.
- **Key height 42 dp** (design 46 px), letters 20 sp, function labels 14 sp, chosen after the owner's feedback that the app's text was too big.

**Problems found:** Kotlin read `$_` in the symbols string as a template (build error), fixed by escaping. Lint flagged the key view's touch handling and constructor; the delete key deliberately acts on touch-down and repeats while held, so those two warnings are suppressed with comments and `performClick` is kept for screen readers. I rendered the six icons in headless Chrome to check their shapes before using them.

**Verification (automated):** debug build and instrumented-test compile pass; 123 of 123 unit tests pass (32 new). A deliberate break of the Send rule made its test fail, then was reverted. Lint 0 errors and the same 2 intentional warnings. **Status: IMPLEMENTED, not MANUALLY VERIFIED.** The keyboard has not been run on a phone by me or the owner, and the instrumented view test was compiled, not run. Gate A stays open until the owner tests it.

**Known limits:** no accented letters or other writing systems (a French or Spanish user cannot yet type é or ñ), no emoji key, no suggestions or autocorrect, no key pop-ups, English (QWERTY) only. These are for later milestones.

**Manual test steps:** in `docs/progress.md` under "Milestone 6 detail".

---

## 2026-09-20 — Milestone 7: keyboard translation toolbar

**Goal (owner):** add the AlterLingua toolbar to the working keyboard, from the Stitch design: `AUTO → XX` (XX changes with the saved target language), Translate, Microphone, Settings. Target from DataStore; choose it by native name (English, Français, Español, Deutsch, Italiano, Nederlands, 中文, 日本語); Settings opens AlterLingua settings; show the microphone but do not record; do not translate; QWERTY must keep working; no Chinese or Japanese input composition; keep target choice separate from layout behaviour.

**Stitch:** "D/K1-K2 Keyboard Typing & Translate Active" gave the toolbar: a chip with a status dot and arrow, a filled Translate pill, a tinted microphone button, a "tune" settings icon. "D/K6-K10 Keyboard Auxiliary" gave the "Translate to" list. That design lists English names with codes and Portuguese; per CLAUDE.md 6.2 and the owner's earlier choice I used native names with the English name smaller, and the catalogue's languages. The design's "Full Support" chip is the assistance mode, which is not part of this request, so it is left out.

**Built:** `ToolbarModel.kt` (state, controller, events; no access to the text field), `ToolbarButtonView`, `LanguagePanelView`, `AlterLinguaKeyboardView` restructured (toolbar on top, keys or language list below), `AlterLinguaKeyboardService` (reads the target from the app's DataStore, writes the choice), `setTargetLanguage` in the settings repository, `AppLinks` plus `MainActivity`/`AlterLinguaApp` handling so the Settings button opens the Settings tab, six vector icons (checked in a browser first), strings, colour additions.

**Decisions:**
- **The user's own language is not offered as a target.** The app's settings require the two languages to differ and swap them if they match; offering "my language" would silently change it. So a native-English user sees seven choices, a native-French user sees English but not Français. This differs from a literal reading of "select from the eight", and is a one-line change in `ToolbarState.selectable` if the owner prefers otherwise.
- **One source of truth.** The toolbar shows what DataStore says and writes there; the service and the app share the app's single DataStore instance (a second one for the same file would crash). The chip shows `AUTO → …` and is disabled until the first read, so it never flashes a wrong language.
- **Translate and the microphone show a short "isn't available yet" message** rather than doing nothing silently. `ToolbarController` has no access to the text field, so they cannot alter what was typed.
- **List panel replaces the keys at the same height** (40 dp header plus four 36 dp rows equals the four key rows) so the keyboard does not resize. It has no search box: eight languages fit without one. It would need scrolling if the catalogue grows past eight.
- **Settings** launches `MainActivity` with an extra naming the Settings tab (single top, so the running app is reused) and hides the keyboard. If onboarding is unfinished the app still shows onboarding.
- **Separation from input layout (CLAUDE.md 6.9):** choosing 中文 or 日本語 as the target only changes the translation target; the keyboard stays QWERTY and no Chinese or Japanese IME was attempted.

**Problems found:** a missing import; a deprecated accessibility call (replaced by a live region so screen readers read the notice); one lint warning for the panel's constructor (suppressed as for the other in-code views). The instrumented keyboard test from milestone 6 assumed four child rows and would have failed on a device; updated and extended before it was ever run. A deliberate break (letting the user's own language into the list) made three tests fail, then was reverted.

**Verification (automated):** debug build and instrumented-test compile pass; 144 of 144 unit tests pass (21 new; the 32 QWERTY tests unchanged and passing); lint 0 errors and the same 2 intentional warnings. **Status: IMPLEMENTED, not MANUALLY VERIFIED.** Not run on a phone; instrumented tests compiled only.

**Manual test steps:** in `docs/progress.md` under "Milestone 7 detail".

---

## 2026-09-20 — Milestone 8: outgoing translation (keyboard to backend)

**Goal (owner):** connect the keyboard to `POST /v1/translate` and translate the composer text using the SELECTED target language (examples: Español gives `¿Vienes mañana?`, Français gives `Tu viens demain ?`; never hard-code French). Read the text through supported `InputConnection` calls, replace it, show "✓ Translated" with Undo restoring the exact original, never send, never touch WhatsApp's Send button or private storage. Handle empty text, backend unavailable, timeout, offline, failure, unsupported language or pair, unsupported editor, and app switching. The original text must never be lost. Test with Français, Español, 日本語.

**Docs consulted first (Android):** `InputConnection` methods, network security configuration (cleartext HTTP is blocked from Android 9 unless opted in).

**Built:**
- `translation/` (app): `TranslationModels` (request, result, `TranslationFailure`), `HttpTranslationApi` (Android's built-in HTTP client, JSON via the built-in `org.json`, cancellable), `AndroidConnectivity`.
- `keyboard/`: `Composer` and `InputConnectionComposer` (read and replace the whole text), `TranslationFlow` (the whole flow: read, request, verify, write, undo, expiry, cancel), `TranslationMessages`, the status strip in `AlterLinguaKeyboardView`, service wiring, `EditorTraits.isPassword`, `KeySpec.changesText`. `ToolbarController.onTranslate` now emits a Translate event instead of the placeholder notice.
- Build and manifest: `BuildConfig.TRANSLATION_BASE_URL` (debug default `http://127.0.0.1:8000`, override with `-Palterlingua.translationBaseUrl=`, empty in release), permissions INTERNET and ACCESS_NETWORK_STATE, a debug-only network security config allowing plain HTTP to loopback (127.0.0.1, localhost, 10.0.2.2) only, test dependency `org.json:json`.

**Decisions:**
- **The target is read at the moment of the tap** from the saved settings, through a function the service supplies, so a stale or fixed language can never be used. Tests change the target between translations and a deliberate hard-coded "fr" makes several tests fail.
- **The text is replaced only after success, and the result is verified.** After writing, the field is read again; if it does not contain the translation, the original is put back (also verified). If even that fails, the original is kept in memory for a Restore button. Fields that ignore writes, garble them or vanish are all tested.
- **Concurrency rules.** One request at a time. Leaving the field or app cancels the request (the HTTP connection is closed) and writes nothing. If the text changed while waiting, the result is dropped rather than overwriting the user's edit. Choosing another language cancels a request for the old one.
- **Undo** compares the field with the translation it left; if the user has typed since, it refuses and changes nothing. Undo and the banner last about 10 seconds or until a text-changing key. The original text lives in memory only, is never saved or logged, and is forgotten when the field is left. Formatting spans, if any, are not restored (plain text is).
- **Password fields are never sent.** The keyboard refuses before reading anything into a request.
- **Never send:** the `Composer` interface offers only `read` and `replaceAll`; a test asserts that, and the composer test lists every `InputConnection` call it makes.
- **The backend translator is still a stand-in.** Only three sample phrases translate; anything else returns `[xx] text` marked as fake. Gate B's "Tu viens demain ?" therefore works for the sample phrase only. A real provider needs the owner's choice of vendor and a key.
- **Reaching the backend from a phone:** `adb reverse tcp:8000 tcp:8000` and the phone's own `localhost`, with plain HTTP allowed for loopback in debug builds only. Release builds allow HTTPS only and have no address yet. A LAN address would need HTTPS or a wider cleartext exception, so it was not added.
- **HTTP client:** Android's built-in client and `org.json`, so no new runtime library. Response bodies are capped at 256 KB, redirects are not followed, error bodies are reduced to a `TranslationFailure`. A response whose `target_language` differs from the request, or with no translation, is treated as a failure.
- **Mixed-up numbering with the owner's list:** "the AlterLingua toolbar" and this milestone are CLAUDE.md milestones 7 and 8.

**Problems found:** `invokeOnCompletion(onCancelling = true)` is an internal coroutine API, replaced by `suspendCancellableCoroutine` with an explicit cancel that closes the connection. A first build was killed for low memory (exit 137) and was simply re-run. The optional live test's server was still running after my first `kill` (it matched my own shell); found and stopped by port. Lint's stale report hid that `org.json` had a newer version; re-ran lint from scratch.

**Verification (automated):** debug build and instrumented-test compile pass; 199 of 199 unit tests pass (55 new): the client against a real local HTTP server (request format, all targets, Unicode, every error mapping, timeout, connection refused, offline, cancellation), the flow with a fake field (examples A, B and C, changing target, undo, expiry, every failure keeps the text, timeout, app switch, edit while waiting, write failures and rollback, restore), the composer against a pretend `InputConnection`. Deliberate breaks (removing the text-changed guard; hard-coding French) made tests fail and were reverted. **The real client was also run against the real FastAPI backend** (optional live test, run with the server up): es, fr, ja, de, it, nl, zh translate as expected, `pt` gives the unsupported-language failure, and the backend's log held no message text. Lint 0 errors, 2 intentional warnings. **Status: IMPLEMENTED, not MANUALLY VERIFIED.** Nothing was run on a phone; instrumented tests (including one against a real `EditText`) compile but were not run.

**Manual test steps:** in `docs/progress.md` under "Milestone 8 detail". They need the backend running and `adb reverse`.

---

## 2026-09-20 — Milestone 9: backend speech-to-text and translated speech

**Goal (owner):** add `POST /v1/audio/translate` (multipart audio and target language) to the FastAPI backend. Pipeline: audio, speech-to-text, source-language detection, translation into the target. Return `source_language`, `transcript`, `target_language`, `translation`. Dynamic target (en fr es de it nl zh ja). Provider abstractions for speech-to-text with capability checks. Validate audio type and size. Never keep audio, delete temporary audio, no transcripts in production logs. Tests across several languages. Stop after backend implementation and testing.

**Built** (`backend/app/speech/`): `provider.py` (`SpeechToTextProvider`, `SpeechCapabilities`, request and result types), `fake_provider.py` (development stand-in), `registry.py`, `audio.py` (type and size validation, private temporary file, guaranteed deletion), `schemas.py`, `service.py` (the pipeline), `router.py` (the route). Changes elsewhere: new error types and settings (`ALTERLINGUA_STT_PROVIDER`, `_MAX_AUDIO_BYTES`, `_STT_TIMEOUT_SECONDS`, `_TEMP_DIR`), a declared-size check middleware, `python-multipart` added to requirements, the health route lists the speech provider, `TranslationService` gained a public `require_language` and `ensure_target_supported` so the audio route reuses the same rules. Tests: `test_audio_translate.py`, `test_audio_validation.py`, `test_audio_privacy.py`, `audio_helpers.py`.

**Decisions:**
- **No real speech provider.** As with translation, choosing a vendor and key is the owner's call. The `fake` provider ignores the audio and returns one sample sentence (in the language named, else English), and the server logs a warning at startup. It exists to exercise everything around recognition. **It does not recognise speech**, so Gate C cannot pass with real speech until a real provider is added.
- **Capability checks first.** Target, spoken-language hint and provider capabilities are checked before the audio is read into a temporary file, so a request that cannot succeed costs nothing. The catalogue's `speech_to_text_supported` (product intent, now true for all eight) is separate from what a provider can actually do (its capabilities), because support differs by language and provider. Tests use a stub that supports only en, fr, es, ja to prove this.
- **Reuse of translation.** The last step calls the same `TranslationService` as `/v1/translate`, so capability checks, unsupported-pair errors, length limits and logging are identical. Speech already in the target language returns the transcript unchanged, without a translation call (and without the "same language" error a text request would give).
- **Audio type is verified twice**: the declared content type must be an accepted audio type, and the file's own first bytes must identify a family that matches it (WAV, MP3, MP4/M4A/3GP, AAC, Ogg/Opus, WebM, FLAC, AMR). A text file labelled `audio/wav` is refused. `application/octet-stream` is refused too, so clients must label the audio (the Android app will control this).
- **Size:** enforced while reading (stops at the limit, deleting the partial file) and, cheaply, from the declared `Content-Length` before the body is read (limit plus 64 KiB of multipart envelope). Uploads without a declared size are still limited by the read check. The web framework may spool a large upload itself before the route runs; it removes that copy when the request ends.
- **Temporary audio** goes in a private (mode 0600) file created with the operating system's secure temp API, in a configurable folder, and is deleted in a `finally` block (success, refusal, provider error, timeout, crash). Tests check the folder is empty after each case.
- **Privacy:** the transcript and translation are never logged; the completion log holds languages, audio bytes, transcript length and latency. Error bodies never echo the transcript. A transcript's control characters are stripped and text normalised before translation.
- **Form fields validated by hand** (a form model cannot be mixed with the file part), reported in the same error shape as other routes, with field names only.

**Problems found:** four tests were wrong rather than the code (a size below the setting's minimum; a stub that reported no detected language by default), fixed. `python-multipart` had to be installed and added to the requirements. My first attempt to stop the test server matched my own shell again; found by port.

**Verification (automated):** 162 of 162 backend tests pass (96 new): all eight languages as target and as spoken language, non-English pairs, auto-detection through a stub, capability boundaries, every accepted audio family, refusals for wrong type or mismatched bytes, empty and oversized files, temporary-file deletion for every outcome, no transcript in logs or errors. Deliberate breaks (skipping deletion; logging the transcript; disabling the content check) each made tests fail and were reverted. I also started the real server and uploaded a WAV with curl for es, fr, ja and ja to en, checked a 415 and a 422, saw an empty temp folder, and found no transcript text in the server log. **Status: IMPLEMENTED, not MANUALLY VERIFIED** by the owner. Not connected to the Android app.

**How to try it:** in `docs/progress.md` under "Milestone 9 detail".

---

## 2026-09-20 — Milestone 10: keyboard voice input

**Goal (owner):** voice input inside the keyboard, from the Stitch voice designs. Microphone opens a compact recording panel ("Speak in English", waveform, duration, Cancel, Stop). After Stop: "Understanding your message…" then "Translating to <selected language>…". Call `POST /v1/audio/translate`. Show "Original — [language]" and "Translated — [selected target]" with Insert as text, Edit, Record again, Listen; Share voice only if appropriate. Never send. Handle permission denied, unsupported speech language or provider, no speech, unclear speech, partial transcript, network failure, cancelled recording, keyboard dismissed, app switched. Delete abandoned recordings. Test across target languages.

**Docs consulted first (Android):** `MediaRecorder` (needs the app in the foreground; the keyboard is), runtime permission guidance (explain first, never nag, keep text input working when denied). The IME documentation confirms a keyboard has no window to show the system permission question.

**Stitch:** "D/K11-K16 Keyboard Voice Translation IME" and "D/K12-K16 Voice States": a panel with a header, a waveform and timer, two panes (source audio transcript and translated text), buttons Cancel, Again, Listen and Insert, and a "Microphone Access Required" sheet. Not copied, because they are untrue for this build: "On-Device Edge Whisper" and "Zero Audio Storage" (audio goes to the backend and is kept temporarily), and "Insert into WhatsApp" (the button says "Insert as text" as the owner specified: the keyboard inserts into whatever field is focused and never touches WhatsApp itself).

**Built:** `translation/`: `VoiceModels` (`VoiceFailure`, results, `VoiceApi`), `HttpVoiceApi` (multipart upload, fixed length, cancellable), `HttpCall` (shared with the text client). `keyboard/`: `VoiceFlow` (the whole flow), `VoiceRecording` (`VoiceRecorder`, `VoicePlayer`, `VoiceFiles`, and the `MediaRecorder` and `MediaPlayer` versions), `VoicePanel` and `WaveformView` (the views), `VoiceMessages`, `MicrophonePermissionActivity` (invisible, asks for the permission on the keyboard's behalf), `SwitchableTextTarget` (typing goes to the edit text while editing). Changes: `AlterLinguaKeyboardView` (voice header and body, taller header while editing), `KeyboardController` (`insertText`, traits override), `ToolbarController` (microphone emits a Voice event, placeholder notice removed), the service, the app class, manifest and themes.

**Decisions:**
- **Spoken language = the user's own (native) language**, sent as the `source` hint; the panel says "Speak in <that language>". Auto-detection is not used because a hint gives better recognition and the fake recogniser needs it; if a recogniser cannot do the user's language the user gets a clear message (tested), not a wrong transcript. The results panel labels use what the backend answers.
- **Target read when Stop is pressed**, so a change made while speaking is honoured; never assumed.
- **"Understanding…" then "Translating to X…" is one request.** The backend does both steps in a single call and does not report progress, so the label moves on after 1.5 seconds while the request runs. It is a faithful description of the work (both happen), not a measurement of it.
- **Listen plays the user's own recording.** Translated speech needs text-to-speech, which is a later milestone, so "Share voice" is not shown. The recording is kept only while the panel is open, so it can be played and, after a network error, sent again.
- **Edit uses the keyboard's own keys** on a text buffer, with the translation shown in the header (the keys stay visible), then Insert as text. Edits append and delete at the end (no cursor movement inside the buffer). A field would not do: a keyboard cannot host an input field.
- **Nothing is sent to the composer until Insert.** Insertion is typed text at the cursor. The flow has no send capability and a test checks the class for one.
- **Silence and clips under 0.7 s are not uploaded.** Peak loudness is measured while recording; a clip that never rose above a low threshold, or is too short, is refused locally ("I couldn't hear anything" / "too short").
- **Cleanup on every exit:** the recording, its upload and its playback end when the panel is closed, the keyboard hides (`onFinishInputView`), the field or app changes, or the service ends; leftovers from a crash are swept when the service starts. The file lives in the app's private cache.
- **Permission:** the panel explains before asking (its own screen and an "Allow microphone" button), then opens an invisible screen that shows Android's question. If Android will not show it again, the button opens AlterLingua's settings page; it never nags. Voice is refused in password fields and key-only fields.
- **Partial transcript** is handled defensively: a reply with words but no translation offers "Use what I heard". The current backend cannot produce that reply, but the client does not depend on it.
- **Recording format:** AAC in MP4 (`audio/mp4`), mono 16 kHz at 48 kbps: a minute is about 0.35 MB, well under the backend's 10 MiB limit, and the backend recognises the format.

**Problems found:** two waveform code warnings (a needless non-null assertion) fixed; the test helper created the same file name twice, fixed; the earlier text client's private call class was moved out so both clients share it.

**Verification (automated):** debug build and instrumented-test compile pass; 240 of 240 unit tests pass (41 new). Deliberate breaks (never deleting the recording; uploading silence; hard-coding French) each made tests fail and were reverted. The real Android voice client was run against the real FastAPI backend: recordings for es, fr, ja, de, zh translate, Japanese speech translates to English, a non-audio file and an unsupported target get the right failures, the server's temp folder was empty afterwards and its log held no message text. Lint 0 errors, 2 intentional warnings. **Status: IMPLEMENTED, not MANUALLY VERIFIED.** Not run on a phone: the real `MediaRecorder`, the permission screen and the panel's look have never been run, and the instrumented tests (which include a real-microphone recording test) compile but were not run. Gate C stays open.

**Manual test steps:** in `docs/progress.md` under "Milestone 10 detail".

---

## 2026-09-20 — Milestone 11: incoming WhatsApp text translation (prototype)

**Goal (owner):** the first incoming-translation prototype. `NotificationListenerService` for `com.whatsapp` text notifications. Extract only what Android legitimately exposes. If the message language differs from the user's selected native language, translate it into the native language and post an AlterLingua notification, for example "Marie / Are you coming tomorrow? / Translated from Français". The destination is never hard-coded English. Never claim to rewrite the WhatsApp bubble, no private storage, no unofficial APIs. Handle grouped and duplicate notifications, missing text, hidden content, unsupported language or pair, translation failure. Do not permanently store the full text. Two-configuration device instructions.

**Docs consulted first (Android):** `NotificationListenerService` callbacks (main thread, removal gives a "light" notification), `StatusBarNotification`, `NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification` (messages, senders, group flag, conversation title), group summary and visibility handling, and the `POST_NOTIFICATIONS` permission and notification channels. The phone was not connected during this milestone, so `adb shell cmd notification` was not checked and is not used.

**Built** (`notifications/`): `IncomingModels` (source list, snapshot, messages, outcomes), `NotificationExtractor` (which messages are worth translating), `SeenMessages` (in-memory hashes for duplicates), `IncomingTranslator` (the pipeline) and `IncomingStatus`, `NotificationSnapshotReader` (real notification to snapshot), `TranslatedNotificationPresenter` (posts and removes the AlterLingua notification), `AlterLinguaNotificationListener` (the real listener, replacing the inert placeholder). Settings: the `incomingTranslationEnabled` flag, a "Translate WhatsApp messages" switch, and a last-outcome line. Setup: the notification-permission step (onboarding step 6 and Settings → Setup). Manifest: `POST_NOTIFICATIONS`. Debug source set: a test receiver and a debug-only extra source. Build config `EXTRA_INCOMING_PACKAGES`.

**Decisions:**
- **Only WhatsApp is read.** The listener drops every other package before opening a notification's extras. Release builds have no extra sources; a test checks the default list and the release build config was checked to be empty.
- **Destination = the user's native language, read for each message** from the saved settings. The source is auto-detected by the backend; a message whose detected source equals the native language is left alone. Tests run the pipeline for all eight native languages.
- **Failures post nothing.** A missing translation is quieter than a notification about a failure, so failures are recorded as an outcome and shown as one sentence in Settings ("Last message: ..."). Failures that may pass (offline, unreachable, timeout, failed) allow a retry the next time WhatsApp posts that conversation; unsupported languages do not.
- **Nothing is stored.** Duplicates are tracked by SHA-256 hashes in memory (max 300, 2 hours). The translated text of a conversation (up to 5 lines) lives in memory while its notification exists, and is dropped when WhatsApp's notification goes away or access is switched off. Outcomes carry only a kind, a language code and a time. No message text is logged (the shared client logs nothing; the backend logs only lengths).
- **Hidden content** is detected as secret visibility, or a plain notification titled with the app's own name (WhatsApp's placeholder form). It cannot see through anything else: if WhatsApp or Android hides the text, there is nothing to translate, and this is reported rather than guessed.
- **Media and emoji-only messages** are skipped by looking for WhatsApp's leading media emoji or for the absence of any letter. This is a best-effort heuristic.
- **The translated notification** is a separate, quiet (low importance) notification in its own channel with a private lock-screen version ("Translated message"). It is not grouped into or attached to WhatsApp's. Its tap action is WhatsApp's own `contentIntent` from the original notification, which the notification exposes to a listener; no reply action or other WhatsApp API is used. Our own notifications carry a marker and are never translated again.
- **Notification permission** (`POST_NOTIFICATIONS`): posting a notification needs it on Android 13+, separate from notification access. It is explained on the setup screen before the button, with a settings shortcut, and detected live. If it is off, nothing is sent to the backend and Settings says why.
- **A debug-only test aid.** Trying this needs a second WhatsApp account otherwise. Debug builds include a receiver that posts a WhatsApp-shaped test message from AlterLingua itself, and the listener's source list includes AlterLingua's own package in debug only, so a message can be simulated with one `adb` command. This is a deviation from "only `com.whatsapp`" and applies to debug builds only (checked in the release build config and manifest). Real WhatsApp still needs to be tried by the owner.
- **On by default** with a Settings switch (CLAUDE.md 52): the user already had to grant notification access in Android settings, and can switch it off any time.

**Problems found:** two translator tests were wrong (an English speaker's already-English message was correctly skipped); the existing setup fake needed the new method; lint flagged the deliberately exported debug receiver (marked intentional).

**Verification (automated):** debug build and instrumented-test compile pass; 303 of 303 unit tests pass (63 new). Deliberate breaks (no duplicate check; English hard-coded as the destination; accepting every app) each made tests fail and were reverted. The real client was run against the real FastAPI backend for a French message translated for English, Spanish, German, Chinese and Japanese users (source detected as French), and for a French user (unchanged); the backend log held no message text. Lint 0 errors, 2 intentional warnings. **Status: IMPLEMENTED, not MANUALLY VERIFIED.** Nothing was run on a phone: the real listener, the real notification contents WhatsApp sends, and how the notifications look are unverified, and the instrumented tests (real notifications, real posting) compile but were not run.

**Manual test steps:** in `docs/progress.md` under "Milestone 11 detail" (three language configurations plus edge cases).

---

## 2026-09-20 — Milestone 12: learning-engine foundation

**Goal (owner):** the foundation of the learning engine, without adaptive translation. A `LearningEvent` pipeline: after a translation interaction, extract useful linguistic units (word, phrase, expression) from the SELECTED learning language, for example `envoyer`, `devis`, `avant midi`, `je vais vous envoyer` for Français and `presupuesto`, `antes del mediodía`, `te enviaré` for Español. Language-aware, not space-based: 中文 and 日本語 need real segmentation. A linguistic-analysis abstraction. Each candidate carries what later steps need (normalization, meaning, type, source and target language, usefulness, exposure). Do not store the full private message; keep minimal signals. Tests for several languages, including a non-Latin one.

**Docs consulted first (Android):** `android.icu.text.BreakIterator` (word instance, ICU dictionary segmentation, the rule status that marks non-words). ICU4J 78.3 was added as a test-only dependency so JVM unit tests run the same algorithm.

**Built** (`learning/engine/`): `EngineModels` (unit types, candidate, event, exposure, interaction, recorder), `LinguisticAnalyzer` (abstraction, token types, registry), `WordBreaker` (Latin breaker, Android ICU breaker), `LanguageProfile` (the data for all eight languages), `RuleBasedAnalyzer`, `CandidateExtractor`, `LearningPipeline` (pipeline, background recorder, exposure store and its in-memory version). Wiring: `TranslationFlow`, `VoiceFlow` and `IncomingTranslator` take an optional recorder (default does nothing); the app supplies the real one; a "Learn from my messages" setting. Tests: `CandidateExtractionTest`, `LearningPipelineTest`, `Icu4jWordBreaker` (test helper), hook tests in the three flow tests, settings tests, a device test with real Android ICU.

**Decisions:**
- **Analysis runs on the phone, not the backend.** The text is already in memory in the translation code, so nothing more leaves the device, and the privacy rule is easiest to keep. The backend's empty `learning` module stays for later.
- **Only text in the selected learning language teaches it.** Outgoing translations are in the target language; an incoming message counts only when it was written in it (learning Français and receiving French). An English message teaches a Français learner nothing. This keeps each language's map separate (CLAUDE.md 6.13).
- **Rule-based analysis behind an abstraction, with profiles as data.** There is no NLP model, and adding a real one later is a new implementation of the same interface. The profile approach means a new language is data, not code. The honest limits: it separates function words from content words but cannot tag a verb, so conjugated forms (`enviaré`) are listed as they appear; `lemma` and `meaning` are empty slots.
- **Phrase building is chunking around function words**, which reproduces the brief's examples: a chunk is the function words that come before a content word plus the content, so `je vais vous envoyer` (pronoun, auxiliary, pronoun, verb) and `avant midi` (preposition, noun). An article inside a chunk starts a noun phrase and cuts off what came before it (`you the quotation` gives `quotation`), unless the phrase starts with a preposition (`vóór de middag`). In 日本語 function words come after the content, so verb endings join it (`来ます`, `お送りします`).
- **中文 and 日本語** use the ICU dictionary. When no dictionary breaker is available they are reported unsupported, never split character by character or by spaces. ICU splits some compounds (`見積` + `書`); a short list of word-ending characters rejoins them. Particles and polite endings are function words in the 日本語 profile, and one- or two-character hiragana tokens are treated as function words.
- **Privacy by shape.** The event type has no text field. Units are short (at most 6 words, 60 characters), few (5 words, 4 phrases, 3 expressions per message), and exclude names, numbers, acronyms, links and addresses. A unit that covers a whole longer message is dropped (fixed expressions are exempt because they come from a public list). Nothing is logged. Units and counts live only in memory until the Personal Language Map defines what is kept and for how long.
- **Exposure tracking now, mastery later.** Candidates carry an exposure of one; the in-memory store adds them up per unit, per language, with a bounded size. It does not decide what is learned: mastery needs more evidence than exposure (CLAUDE.md 14).
- **Usefulness is a transparent heuristic** (content word, longer word, phrase, phrase starting with a preposition, known expression), stored with its reasons so it can be tuned and later combined with what the user already knows.
- **A setting**, on by default, because CLAUDE.md 52 asks that learning behaviour be visible and controllable. Turning it off stops all analysis.

**Problems found by the tests (and fixed):** a decomposed accent (`i` plus a combining mark) split a word in two, so text is now normalized before segmenting and combining marks stay in words; a shouted `DEVIS` was treated as an acronym and then as a proper noun, so acronyms are limited to four capitals and names need lower-case letters after the capital; the "never a whole message" guard hid the one-message expression `ありがとうございます` (ICU splits it into five pieces), so it no longer applies to fixed expressions; the Japanese `し` (from する) was a particle, breaking `お送りします`, and German `vor Mittag` was listed twice (as expression and phrase).

**Verification (automated):** debug build and instrumented-test compile pass; 343 of 343 unit tests pass (40 new). Deliberate breaks (no cap on units per message; learning from any language; treating 中文 and 日本語 like spaced text) each made tests fail and were reverted. Lint 0 errors, 2 intentional warnings. **Status: IMPLEMENTED, not MANUALLY VERIFIED.** The unit tests use ICU4J (the same algorithm as Android's ICU); the real Android ICU on a phone was not run and the device test compiles only. There is no screen to look at the units yet.

**Manual test steps:** in `docs/progress.md` under "Milestone 12 detail".

---

## 2026-09-20 — Bug fix: the keyboard crashed as soon as it was chosen

**Reported by the owner:** after choosing the AlterLingua keyboard, the app closes unexpectedly.

**Cause (from the phone's crash log, read with `adb logcat -b crash`, filtered to AlterLingua):** `AlterLinguaKeyboardService.<init>` failed with `null cannot be cast to non-null type AlterLinguaApplication`. Android builds a service with its plain constructor and attaches the app to it afterwards. In milestone 8 I initialised the translation flow in a property of the service and read `(application as AlterLinguaApplication)` inside it, so `application` was still null during construction and the service died every time the keyboard was selected. Milestone 12 added a second such read in the same initialiser. The voice flow (added later) was already lazy, which is why it was not affected. All seven crashes in the log had this one cause.

**Why it was not caught:** every keyboard test until now exercised the logic classes, never the real service being constructed. I reported the keyboard as "implemented, not manually verified", and this is the kind of fault only a device shows.

**Fix:** the translation flow is now created lazily, after Android has attached the app. **Regression test:** `ServiceConstructionTest` constructs the keyboard service and the notification listener the way Android does; it fails with the old code (checked by putting the bug back) and passes now. Other reads of the app object in the service are lazy or in getters; the notification listener and permission screen read it only after they are attached.

**Verification (automated):** unit tests pass, lint 0 errors, debug build and instrumented-test compile pass. **Not yet verified on the phone**: the owner needs to install the fixed build (Run ▶ from Android Studio) and choose the keyboard again.

---

## 2026-09-20 — Milestone 13: Personal Language Map

**Goal (owner):** the Personal Language Map: database models and services. Each item: language, normalized word or phrase, display form, meaning, type, exposure count, translation-help requests, lesson encounters, correct and incorrect recognition, last seen, mastery score and state (UNKNOWN, LEARNING, FAMILIAR, MASTERED), not equated with CEFR. A simple, explainable, deterministic first mastery calculation, no opaque AI scoring. Tests for the progression UNKNOWN to MASTERED and for regression where justified.

**Docs consulted first:** Room and KSP setup for AGP 9 (built-in Kotlin: kapt is out, KSP is supported, Room's compiler ships a KSP processor). Room 2.8.5, KSP 2.3.12, and the Room Gradle plugin were added; the whole build was checked to still work before writing any map code.

**Built** (`learning/map/`): `MasteryCalculator` (rules, evidence, factors, evaluation, explanation), `LanguageMapItem`, `LanguageMapStore` (interface plus the in-memory store), `LanguageMapService`, `LanguageMapDatabase` (Room entity, DAO, database, the Room store, mappers), `LanguageMapExposureStore` (lets the pipeline write into the map). The exposure-store interface became suspending so the pipeline can save to a database; the app now saves learning events into Room. Tests: `MasteryCalculatorTest`, `LanguageMapServiceTest`, and a device test against the real Room database.

**Decisions:**
- **Room, with KSP.** CLAUDE.md names Room. The toolchain worked, and Room checks every SQL query at compile time. The alternative (plain SQLite) was not needed.
- **Storage behind an interface.** The service and all rules run against an in-memory store in fast unit tests; Room is one implementation. Atomic read-modify-write (`update`) means two updates at once cannot overwrite each other (tested with 50 concurrent updates).
- **One row per (language, normalized, type)** (unique index), so each language has its own map (CLAUDE.md 6.13) and a word and a phrase with the same text stay apart.
- **What is stored:** counts and a result. There is no message, sender, time of day or source. The display form is the unit as first seen, and a test checks the entity has no message-like field and that no stored unit approaches a message's length. The database is private to the app and, with backup off, not copied anywhere.
- **The mastery calculation is a fixed formula plus two gates**, chosen so it can be explained line by line: exposure is worth 0.5 points each and at most 12, lessons 4 each (max 16), correct recognitions 15 (max 75), incorrect recognitions cost 12, translation help costs 3 each (max 15), and 30-day blocks unseen beyond 60 days cost 10 each (max 40). States begin at 10, 40 and 75, and FAMILIAR needs 2 correct recognitions while MASTERED needs 4 with 80% accuracy. Every rule is a named number in `MasteryRules`; every result carries its factors and a "what is missing" sentence. No learned weights, no hidden state, and the same evidence and time always give the same answer.
- **Exposure alone proves nothing** (CLAUDE.md 14): its 12-point cap keeps a word met a thousand times at LEARNING. Getting a word right is worth the most; a wrong answer costs more than asking for help, because asking for help is honest evidence of not knowing but not evidence of forgetting.
- **Regression is justified in three ways only:** wrong recognitions, translation-help requests, and long absence. Absence is applied when the item is evaluated (`evaluate(item, now)`), not stored, so the stored value is always "as of the last change" and the displayed state can be computed for any date.
- **The states are not CEFR levels** and nothing estimates one; they describe one word for one person.
- **Only exposures are recorded for now.** Help requests, lesson encounters and recognitions have service functions and tests, but nothing produces them until Adaptive mode, lessons and pronunciation exist. So today items sit at UNKNOWN or LEARNING, which is the honest state of the evidence.
- **Deletion:** the service can delete one language's map or everything; the Settings "Delete all learning data" row was not changed (no screen work was requested).

**Problems found:** four of my test expectations were wrong rather than the code (the exposure cap arithmetic, a "last seen" before time zero, a duplicated parameter, half-written placeholder tests) and were corrected; an over-strict "less than half the message length" check was replaced with the real guarantee (every unit is a fragment, never the message).

**Verification (automated):** debug build and instrumented-test compile pass, including Room's generated code and compile-time SQL checks; 382 of 382 unit tests pass (39 new). Deliberate breaks (no cap on exposure points; no "2 correct" gate for FAMILIAR; no accuracy gate for MASTERED; no inactivity penalty) each made tests fail and were reverted. Lint 0 errors, 2 intentional warnings. **Status: IMPLEMENTED, not MANUALLY VERIFIED.** The real Room database was never run on a phone: the device test compiles but did not run, and there is no screen to look at the map.

**Manual test steps:** in `docs/progress.md` under "Milestone 13 detail", including how to copy the database off the phone and read it.

## 2026-09-20 — Milestone 15: Daily micro-lessons

**Goal (owner):** a lesson-selection engine choosing about three high-value items from many, ranked by explainable signals (usefulness, frequency, recency, current mastery, help requests, repetition value), not simply the most frequent; a short daily lesson; Stitch-based word card, phrase card, meaning, Listen and Repeat placeholders, context, Next and completion; lesson interaction recorded as a mastery signal.

**Built:** `learning/lessons/` (`LessonSelector`, `LessonModels`, `MeaningProvider`, `DailyLessonStore`, `LessonService`); map items gained `usefulness`, `lastContext`, `lastLessonAt` (Room version 2 with an auto-migration); `ui/learn/` (`LearnViewModel`, `LearnScreen`, `LessonText`) replacing the sample lesson; wiring in `AlterLinguaApplication`.

**Decisions:**
- Score = sum of named signals with a learner-facing reason each; all weights in one config. Frequency is logarithmic and capped, and a test proves the three most frequent words do not simply win.
- LEARNING earns the "still learning" points only once studied; otherwise a word that reached LEARNING purely by being seen often would beat useful new items (found by a test, fixed).
- The lesson is fixed per local day and language and saved, so it does not change on reopening.
- Privacy: a saved card holds unit, meaning, counts, reasons only; a test pins the exact stored keys. Context is shown as counts and recency, so the Stitch sample sender names and message text were not copied.
- Meaning fetched by translating the unit only, with a timeout; failure shows an honest "not available".
- Listen and Repeat disabled placeholders (pronunciation is a later milestone). Only a lesson encounter is recorded; no fake quiz result.

**Problems:** frequency was double-counting through mastery need (above); two of my service tests and a privacy test were too weak or too broad and were rewritten.

**Verification (automated):** lint, debug build, instrumented-test compile and 433 of 433 unit tests pass (selector, service, codec, view model, context wording). No mutation checks were run this time. **Status: IMPLEMENTED, not MANUALLY VERIFIED:** the Compose tests and the Room migration did not run on a device.

**Manual test steps:** `docs/progress.md`, "Milestone 15 detail".

## 2026-09-20 — Milestone 16: Full Support assistance mode

**Goal (owner):** Full Support: incoming supported foreign-language content gets complete translation assistance, the Personal Language Map still receives learning signals, learning is not disabled. No Adaptive yet. Tests and docs.

**Built:** `learning/assistance/AssistancePolicy` (what a mode means for an incoming message); `IncomingTranslator` takes the mode (read fresh per notification) and asks the policy whether to feed learning; wired to the saved setting in `AlterLinguaApplication`. Tests: `FullSupportTest` (policy, full translation plus learning, own-language messages, mode read each time, an end-to-end test from a notification through the real pipeline into the map, the privacy switch still winning, help not being recorded).

**Decisions:**
- Incoming translation already translated everything and fed learning, so this milestone makes the rule explicit and central rather than adding new behaviour: one policy, asked instead of checking modes around the code.
- Automatic translation is not recorded as a help request (that would punish the user for the app doing its job and would distort Adaptive later).
- Adaptive and On-demand deliberately behave like Full Support for now (per the instruction not to build Adaptive); noted in the code and docs so it is not mistaken for finished behaviour.
- The "learn from my messages" privacy switch is unchanged and still overrides.

**Problems:** two tests first failed to compile because a trailing lambda bound to the wrong parameter; fixed by naming it.

**Verification (automated):** lint, build, instrumented compile and 441 of 441 unit tests pass. A deliberate break (Full Support no longer feeding learning) failed four tests and was reverted. **Status: IMPLEMENTED, not MANUALLY VERIFIED.**

**Manual test steps:** `docs/progress.md`, "Milestone 16 detail".

## 2026-09-20 — Milestone 17: Adaptive assistance engine (first version)

**Goal (owner):** the first deterministic, explainable, testable, conservative Adaptive engine using the Personal Language Map of the current learning language; no random replacement; language-aware (中文 and 日本語 need different segmentation and presentation); unknown or insufficiently mastered language still gets assistance; tests across map states and languages; no opaque LLM for the mastery threshold.

**Built:** `learning/assistance/AdaptiveEngine.kt` (`AdaptiveEngine`, `AdaptiveDecision`, `UnitDecision`, `AdaptiveConfig`, `AdaptivePresentation`) and `AdaptiveEngineTest`.

**Decisions:**
- The engine works on the message already written in the learning language and never invents a mixed-language sentence. Aligning words between an original and its machine translation would need a word aligner and could pair the wrong words, which is exactly the "random replacement" the brief forbids. So "keeping more of the language" means: keep the original, and add a short gloss (a meaning already saved in the map) after only the words still needed. Nothing is substituted.
- Threshold: MASTERED only, using the existing deterministic mastery calculator evaluated at the current time (so absence lowers it). FAMILIAR does not remove help by default; it is configurable.
- Conservative fallbacks to full translation whenever unsure (too few known, too many glosses, missing or wrong-language meaning, unsupported language, no judgeable word, placement failure).
- Fixed expressions decide for their words, with the least-known overlapping expression winning.
- Language handling comes from the analyzers and profiles (ICU dictionary segmentation for CJK) and only the gloss brackets differ by script.
- Not wired into notifications or any screen: MASTERED needs 4 correct recognitions and none are recorded yet, so wiring would change nothing today. Recorded as the next step rather than pretending Adaptive works end to end.

**Problems:** my German test map omitted the "vor Mittag" expression, so the engine (correctly) kept helping; the test map was fixed, not the engine. A mutation check showed the overlap rule was not really tested (it only passed in one ordering); a reverse-order test was added and the mutation is now caught.

**Verification (automated):** lint, build, instrumented-test compile and 466 of 466 unit tests pass. Mutations (threshold lowered to FAMILIAR; overlapping expressions no longer take the least-known) each fail tests and were reverted. **Status: engine IMPLEMENTED; Adaptive as a user feature IN PROGRESS, not connected, not tried on a phone.**

**Manual test steps:** none available yet; see "Milestone 17 detail" in `docs/progress.md`.

## 2026-09-20 — Milestone 18: On-demand assistance (AlterLingua-owned screens)

**Goal (owner):** On-demand mode: the default presentation preserves the target language; translation only when explicitly requested, on surfaces where AlterLingua can technically offer selectable assistance. Example: `acompte` gives `deposit / advance payment`, Listen, Add/review in learning. The request itself is a mastery signal that the item is not fully mastered. Do not claim WhatsApp bubble words are tappable. AlterLingua-owned surfaces first.

**Built:** `learning/assistance/OnDemandHelp` (cuts text into askable words; answers a request and records it); `ui/learn/ReaderViewModel` and `ReaderCard` ("Read with help" on the Learn tab, tappable words via Compose link annotations); a new mastery rule and a `lastHelpAt` field (Room version 3 with an auto-migration); wiring in `AlterLinguaApplication`. Tests: `OnDemandHelpTest`, `ReaderViewModelTest`, `RecentHelpRuleTest`, and an instrumented `ReaderCardTest` (compile only).

**Decisions:**
- The only surface where words are tappable is AlterLingua's own text, so the mode is built as a reader there. WhatsApp bubbles and notification text cannot be selected word by word by AlterLingua; this is stated in the docs and the UI, and notifications are left unchanged (still full translation) rather than half-implemented.
- "The request is a signal that the item is not fully mastered" was not true of the existing formula: a well-mastered word lost only 3 points and stayed MASTERED. Added a simple rule: a help request in the last 14 days holds a MASTERED word at FAMILIAR (recency-based, so a single early request does not block mastery for ever). This also makes the Adaptive engine help with that word again (tested).
- "Add/review in learning": the request already puts the word in the map and raises its lesson score, so the panel says so instead of showing a button that would do nothing more. A review list belongs to the Words milestone.
- One reading counts a word once, so tapping repeatedly does not pile up penalties.
- Only the tapped word goes to the translation service; the text is memory-only and capped.
- Listen is a disabled placeholder, as in lessons.

**Problems:** an unnoticed gap between the wording and the code (help could not demote MASTERED), found before writing tests, fixed with the new rule; a test class built its ViewModel before the main-dispatcher rule started (made lazy); two test expectations about the summary and about numbers were wrong and were corrected.

**Verification (automated):** lint, build, instrumented-test compile and 497 of 497 unit tests pass. Mutations (recent-help rule removed; request no longer recorded) each failed tests and were reverted. **Status: IMPLEMENTED on AlterLingua screens, not MANUALLY VERIFIED.**

**Manual test steps:** `docs/progress.md`, "Milestone 18 detail".

## 2026-09-20 — Milestone 19: Incoming voice-note Share flow

**Goal (owner):** AlterLingua as an Android Share target for audio: WhatsApp voice note, Share, AlterLingua, content URI, securely read permitted audio, `POST /v1/audio/translate`, transcription, translation, Voice Translation screen. No NotificationListenerService route, no WhatsApp private storage, respect temporary URI permissions, use the Stitch design, display original language, transcript, user's language, translation, useful words/phrases, actions Listen and Review useful language, feed the Personal Language Map, delete temporary audio.

**Built:** `share/` package: `SharedAudioReader` (+ `AudioSniffer`, `AudioSource`, `ContentResolverAudioSource`), `SharedVoiceViewModel`, `SharedVoiceScreen`, `SharedVoiceMessages`, `Speaker` (Android text-to-speech), `ShareVoiceActivity`; a Share intent filter for audio in the manifest; wiring in `AppViewModelProvider` and `AlterLinguaApplication` (the learning pipeline is now exposed so the screen knows whether anything was saved); a new `INCOMING_VOICE` interaction kind; `AppLinks.learnIntent`. Tests: `SharedAudioReaderTest`, `SharedVoiceViewModelTest`, `ShareManifestTest`, and an instrumented `SharedVoiceScreenTest` (compile only).

**Decisions:**
- **Reading:** only `content://` is accepted, and the bytes are copied at once into the app's cache with a size cap, so the flow does not rely on the temporary permission afterwards. File paths are refused so a share cannot point at other apps' private files. No storage permission.
- **Type checking uses the file's first bytes**, not only the declared type, so a non-audio file cannot be uploaded just by claiming to be audio.
- **Deleting audio:** deleted on success, on a non-retryable failure, on cancel and on screen end; kept only for Try again; an hour-old sweep covers a crash.
- **Source language is auto-detected** and the target is the user's own language, read fresh, never assumed English. A voice note already in the user's language is shown but neither translated nor learned from.
- **Learning:** a new interaction kind (voice) instead of reusing the text one, so lessons can say where a word was met. The pipeline still decides (learning language only, and the user's privacy switch wins). The screen says truthfully whether units were saved and disables Review when they were not.
- **Listen** uses Android's on-device speech (nothing extra is sent), with an availability check because not every language has a voice (CLAUDE.md 18). The Stitch design's playback-speed control was left out.
- The design's harvested-word list is reproduced with real meanings (fetched for the unit only, cached in the map), not copied sample data.
- No entitlement or quota check yet, per the milestone order.

**Problems:** a test folder helper made a second "cache" folder per call (fixed); one test assumed a specific word was among the top three units (now checks every shown unit). Running the backend for a contract check showed the development speech provider always "hears" the same English sample, which is why the device test uses Français as the user's language.

**Verification (automated):** lint, build, instrumented-test compile and 545 of 545 unit tests pass. The real backend was also queried with an Ogg-signature file and `source=auto` (accepted, answered). Deliberate breaks (audio not deleted after success; file addresses accepted) each made tests fail and were reverted. **Status: IMPLEMENTED, not MANUALLY VERIFIED:** not run in a real Share sheet, with a real WhatsApp voice note, or with a real speech provider.

**Manual test steps:** `docs/progress.md`, "Milestone 19 detail".

## 2026-09-20 — Milestone 20: Pronunciation practice (first version)

**Goal (owner):** the first pronunciation-practice experience in Daily Lessons, using Stitch: word or phrase, Listen, Repeat, record the user, speech recognition or pronunciation assessment, simple feedback (Good, Try again). Do not pretend the scoring is more precise than the provider supports. Record practice as a mastery signal.

**Built:** `learning/pronunciation/` (`PronunciationEvaluator`, `PronunciationPractice`); `ui/learn/` (`PracticePanel`, `PracticeViewModel`, and a practice slot in `LearnScreen` that replaces the disabled Listen and Repeat placeholders); a pronunciation counter on Personal Language Map items (Room version 4 with an auto-migration) and a capped scoring factor; wiring in `AppViewModelProvider`. Tests: `PronunciationEvaluatorTest`, `PronunciationPracticeTest`, an instrumented `PracticePanelTest` (compile only).

**Decisions:**
- **Honest assessment:** the only provider is speech recognition, which returns text. So the verdict is a comparison of what was recognised with the target (Good, Nearly, Try again, "couldn't hear that") and the screen says it is not a pronunciation score. The Stitch design's percentages, pitch and vowel analysis were not built; recorded here as a deliberate discrepancy (CLAUDE.md 53).
- **Language-aware comparison:** accents and capitals ignored for Latin scripts; characters and katakana-as-hiragana for 中文 and 日本語; "Nearly" only for spaced scripts and 5 or more letters. The script limit for 日本語 is documented.
- **Mastery signal kept separate from recognition:** repeating after hearing is imitation, so it is a distinct pronunciation count with small capped points, no penalty for misses, and no ability to make a word FAMILIAR or MASTERED alone. Silence and service errors are not recorded as practice.
- **Reuse:** the microphone recorder, temporary file store, voice API and text-to-speech from earlier milestones; no backend change (a dedicated transcribe route is noted as a follow-up).
- **Privacy:** one private temporary file, deleted when the answer arrives, on cancel, on card change and on leaving; only counts are saved.

**Problems:** my first tests assumed "acompte" versus "compte" would be Try again; it is one letter away and correctly shows Nearly, so the tests were corrected rather than the rule bent. A view-model design point (scope owned by the view model) was fixed by passing a factory.

**Verification (automated):** lint, build, instrumented-test compile and 584 of 584 unit tests pass. Three deliberate breaks (counting Nearly as Good; keeping the recording after assessment; treating every answer as a match) each failed tests and were reverted. **Status: IMPLEMENTED, not MANUALLY VERIFIED:** not run on a device or with a real speech provider.

**Manual test steps:** `docs/progress.md`, "Milestone 20 detail".

## 2026-09-21 — Milestone 21: Progress screen and Translation Dependence

**Goal (owner):** the Progress screen from Stitch, on real learning data: words encountered, Learning, Familiar, Mastered; Translation Dependence as a measurable, documented metric with a trend over time (Week 1 94%, Week 4 71%, Week 8 49% as the example); no fake percentages for real users, with an empty state when history is insufficient; mastery trend, learning activity and translation-assistance trend.

**Built:** `learning/progress/` (`DailyActivity`, `ProgressStore` with in-memory and Room versions, `ProgressLog`, `ProgressCalculator` and `ProgressReport`, `ProgressService`); the Personal Language Map now reports to the log after each change; the Progress tab rewritten (`ProgressViewModel`, `ProgressScreen`); Home's dependence card made real; wiring in `AlterLinguaApplication` and `AppViewModelProvider`. Tests: `ProgressCalculatorTest`, `ProgressRecordingTest`, `ProgressViewModelTest`, an updated `HomeViewModelTest`, an instrumented `ProgressScreenTest` (compile only).

**Decisions:**
- **Definition:** dependence is measured in words, not messages: the share of words met in real conversations that were not mastered at that moment. It reuses the Adaptive threshold, so the metric means "what AlterLingua would still have to translate", is measurable now, and falls only when the learner truly knows more (not when they simply translate less).
- **Data needed history the app did not have,** so a small private daily-counts store was added (numbers only, no words or text) rather than deriving trends from the map's single current state.
- **Guards against invented figures:** at least 20 words in a week, at least two such weeks for a trend, otherwise explicit empty and "collecting" states; weeks with too few words are left out, never estimated. Sample data was removed from Progress and from Home's dependence card.
- **Honest consequence recorded:** since mastery needs correct recognitions and nothing records them yet, the metric will read near 100% until recall checks exist. Documented rather than lowering the bar to make a nicer number.
- **Left out of the design:** the acoustic "Native Cadence Match" score and the work versus social register split (a fake precision and a private-message classification, respectively).
- Phrases are excluded from the count so words are not counted twice; the language engine's per-message word cap is stated as a limit.

**Problems:** none in the tests beyond one edit slip (a helper referencing the wrong module). A long verification command was interrupted partway; I confirmed by diffing the saved copies that no mutated source was left behind, then re-ran the full build and tests.

**Verification (automated):** lint, build, instrumented-test compile and 632 of 632 unit tests pass. Three deliberate breaks (counting mastered words as assisted; dropping the 20-word minimum; showing a trend from one week) each failed tests and were reverted. **Status: IMPLEMENTED, not MANUALLY VERIFIED:** never run on a device and no real usage history exists.

**Manual test steps:** `docs/progress.md`, "Milestone 21 detail".

## 2026-09-21 — Milestone 22: Translated outgoing voice

**Goal (owner):** translated outgoing voice as a later-stage feature: `POST /v1/audio/speak`; source-language voice, speech-to-text, chosen target-language translation, target-language text-to-speech, Listen, Android Share, WhatsApp. Provider abstractions for text-to-speech and language/voice capability checks; listen before sharing; never send automatically; secure temporary files and content URIs; delete generated audio; build and test with several target languages.

**Built:** Backend: `app/speech/tts_provider.py` (abstraction with per-language voices), `fake_tts_provider.py` (development tone generator), `speak_service.py`, the `/audio/speak` and `/audio/voices` routes, provider registry, settings, and the language catalogue's text-to-speech flag; `tests/test_audio_speak.py`. Android: `SpeakApi` and the speak route in `HttpVoiceApi`, `speak/` (`SpokenTranslationFlow`, `SpeakViewModel`, `SpeakScreen`, `SpeakActivity`), a FileProvider limited to one private folder, a Home entry, and a `NO_VOICE_FOR_TARGET` failure.

**Decisions:**
- **One route, no pair logic;** speech, translation and recognition reuse the existing services so the two audio routes cannot drift apart.
- **Capability checks before cost:** the voice for the target is checked with the languages, before the audio is read (the brief asked for language and voice capability checks). A missing voice is a distinct error the app can explain rather than a generic failure.
- **JSON with base64 audio** instead of a binary body, so one response carries the transcript, translation and speech together; the generated audio is never written to the server's disk.
- **Optional voice id and a voices route** so future user selection needs no redesign.
- **Sharing through a FileProvider with a single private folder,** temporary per-share grants, not exported; the system chooser is used so the user picks the app, and AlterLingua never touches a Send button.
- **Generated audio lifetime:** it must outlive the tap on Share (the receiving app reads it afterwards), so it is deleted on Record again, on leaving the screen, and by an hour-old sweep, rather than at the moment of sharing.
- **Its own screen, not the keyboard:** a keyboard cannot open the Share sheet. Home gets an entry point.
- **Learning:** the translated text is fed to the learning engine only when the user actually shares, matching the keyboard microphone's "Insert" rule.
- No dedicated Stitch design exists for this flow; the keyboard voice states and the voice-note result layout were reused, and this is recorded above.

**Problems:** the first backend mutation check (skipping the voice check) was not caught because a second guard (choosing a voice that does not exist) enforces the same rule; that redundancy is deliberate defence, so it was not treated as a gap. The other Android mutation checks were caught.

**Verification (automated):** backend 201 of 201 tests; the real server answered `/v1/audio/speak` for seven target languages with valid WAV data and `/v1/audio/voices` for all eight. Android: lint, build, instrumented-test compile and 670 of 670 unit tests. Deliberate breaks (keeping the recording after the answer; learning before the user shares) failed tests and were reverted. **Status: IMPLEMENTED, not MANUALLY VERIFIED:** no real recognition or speech provider, and nothing run on a phone, in the Share sheet or in WhatsApp.

**Manual test steps:** `docs/progress.md`, "Milestone 22 detail".

## 2026-09-21 — Milestone 23: Privacy and security audit

**Goal (owner):** audit the Android app, backend, database, logging, temporary files, audio, notifications, translation requests, learning events, the Personal Language Map, API keys, authentication, network traffic and local storage; verify ten specific properties; categorise findings CRITICAL to LOW; fix CRITICAL and HIGH where it can be done without changing the architecture; run tests; update `docs/privacy.md` and `docs/progress.md`. No new product features.

**Method:** read the code paths that store, log, send or expose data (storage APIs, log calls, manifests, merged release manifest, network-security files, build configuration, notification code, backend logging and error handling); searched the repository for secret shapes and did a dry run of adding everything to Git; and, for the logging question, ran the real web server with a provider that raises an error containing a secret to see what is actually logged, instead of trusting the existing test.

**Findings and fixes:** see `docs/privacy.md` section 8. No CRITICAL. HIGH: H1 message text in server logs after a crash (found by that experiment; the existing test used a test client that never reaches the server's own error logging) fixed by removing exception messages and tracebacks from every log record; H2 no data deletion fixed by wiring Settings to a `LearningDataEraser`; H3 no backend authentication or rate limiting left open and documented as a deployment blocker. MEDIUM fixes: start-up sweep of temporary audio, redacted `toString` on every class holding private text, no-store and nosniff headers and production docs off, and ignore rules plus a repository secret scan.

**Decisions:**
- **H3 not "fixed" on purpose:** the only quick fix would be a shared key in the app, which breaks the rule against secrets in Android and would give false comfort. Authentication is the planned Authentication milestone; the audit makes it a launch gate and lists what it must include.
- **The learning fragments (M4) were not changed:** whether learning from messages should start off, or expire, is a product decision. They are documented, erasable, device-only and filtered.
- **Redaction is at the log-record level, for every logger,** because leaks come from libraries as well as our own code; our own code already logged only facts.
- **Erase keeps settings** and tries every step even when one fails, reporting honestly.
- Audit checks were made into tests (permissions, exported components, no system-log calls, no plain HTTP outside debug loopback, secret shapes, ignore rules, redacted printing) so a later change cannot silently undo them.

**Problems:** the first version of the log-redaction check passed for the wrong reason (the test client hides the path that leaked); the real-server test is what proves the fix.

**Verification (automated):** backend 209 of 209; Android lint, build, instrumented-test compile and 693 of 693. Deliberate breaks (redaction switched off; a `toString` override removed; the erase step for the map removed) each failed tests and were reverted. **Status: IMPLEMENTED, not MANUALLY VERIFIED:** nothing was run on a phone; notification-history and lock-screen behaviour need a device.

**Manual test steps:** `docs/progress.md`, "Milestone 23 detail", and `docs/privacy.md` section 10.

## 2026-09-21 — Milestone 24: Pilot preparation (30 to 50 users)

**Goal (owner):** prepare for a small pilot without unrelated features. Define privacy-conscious aggregate metrics for translation interactions, vocabulary exposures, help requests, the three upward state changes, lessons completed, pronunciation attempts, Full Support, Adaptive and On-demand usage, and translation dependence over time; state the primary hypothesis; document metric definitions, event names, privacy boundaries and reporting requirements; never store full private conversations for analytics; update `docs/progress.md`.

**Built:** `docs/pilot.md` (hypothesis and proposed decision rule, privacy boundary, twelve metric definitions, event names, report schema, reporting and analysis requirements, readiness blockers). Code so the definitions are measurable: counters in `daily_progress` (Room version 2), `MeteredLearningRecorder`, state-advance counting in `LanguageMapService`, lesson completion in `LessonService`, per-mode counting in `ProgressLog`, and `PilotReport` with the `PilotMetrics` vocabulary. Tests: `PilotMetricsTest`.

**Decisions:**
- **Counters, not an event log.** A raw event stream with timestamps would be the beginning of a private timeline; daily counts cannot reconstruct a conversation. The event names are the vocabulary for the counters and for any later upload.
- **Words, not messages, for dependence** (Milestone 21), kept as the primary measure; translation counts, assistance volume and new words are reported beside it so it is not misread.
- **Modes are counted by the mode selected,** with an explicit warning that Adaptive and On-demand do not yet change incoming translation, so a mode comparison would mislead.
- **Transport was deliberately not built.** How data leaves a phone (a participant-sent report versus an authenticated upload) is a consent and security decision; the report builder exists and the recommendation is a transparent "Share pilot report" through the Share sheet, listed as a blocker.
- **The decision rule is a proposal for the owner** and must be fixed before data is seen.
- **Honest readiness:** the main hypothesis cannot be tested yet, because no correct recognitions are recorded and dependence therefore cannot fall. Recording that as blocker B1 rather than presenting a metric that cannot move.

**Problems:** one test used `advanceUntilIdle`, which does not run work in the background scope; it needed `runCurrent`.

**Verification (automated):** lint, build, instrumented-test compile and 711 of 711 unit tests. Deliberate breaks (state-advance counting removed; downward moves counted) failed tests and were reverted. **Status: definitions and counters IMPLEMENTED; the pilot is not ready; nothing was run on a phone and the database migration did not run on a device.**

**Manual test steps:** `docs/progress.md`, "Milestone 24 detail".

## 2026-09-21 — Milestone 25: Localization and the three language settings

**Goal (owner):** "create the one you haven't done even though it's not on Google Stitch". Read as the localization work of sections 18 and 19 of the product brief (L1 to L7 in the design brief): interface language, source language and target language as independent settings; first-launch app-language choice; localized keyboard actions; source selectable (including AUTO); one string-resource architecture for eight languages.

**Built:** `UserSettings` (app language, chosen flag, auto-detect) and its storage; `UserLanguagePreferences`; `localization/AppLanguage` and `LocalizedActivity` (applied to all four screens, the keyboard and the notification presenter); `AppLanguageScreen` (L1) and the `ChooseAppLanguage` start state; a resource-based Languages section in Settings; keyboard toolbar and translate requests that follow AUTO or a fixed source; string files for seven more languages (105 strings each). Tests: `UserLanguagePreferencesTest`, `SourceLanguageToolbarTest`, `LocalizationResourcesTest`, language-settings and start-state tests, translation-flow source tests, an instrumented `AppLanguageTest` (compile only). `docs/localization.md` written.

**Decisions:**
- **Scope.** The whole app has roughly 200 English literals in Kotlin; migrating and translating all of them (about 1,400 strings) is a milestone of its own. This one builds the architecture and everything the brief names concretely, and records the rest as a precise list rather than pretending the interface is localized.
- **No new dependency.** The language is applied by wrapping each screen's context instead of adding an AppCompat dependency and changing every theme, which is lower risk without a device to test on. The cost is a small synchronous read of the local settings file when a screen starts.
- **Existing field kept.** `nativeLanguage` is the source/default language under its older name (renaming it would touch every flow); `UserLanguagePreferences` gives the spec's names on top.
- **Source detection default on**, preserving today's behaviour (AUTO). Incoming messages always detect, because their sender's language is unknown.
- **Brand names stay untranslated;** language names come from the catalogue in their own language in every interface language.
- **Existing users** who already finished onboarding are not interrupted by the language screen.
- **Translations were written by me, not reviewed by native speakers.** They should be checked by a speaker of each language before release.

**Problems:** a deliberate break of a Japanese string did not fail the tests, because Gradle treated the resource-reading tests as up to date. The tests' inputs are now declared, and the break fails as it should. Stitch timed out on four of the five localization screens.

**Verification (automated):** lint, build, instrumented-test compile and 740 of 740 unit tests. Deliberate breaks (the translate request ignoring the fixed source; a Japanese string removed) each failed tests and were reverted. **Status: PARTIAL; not run on a phone.**

**Manual test steps:** `docs/progress.md`, "Milestone 25 detail".

## 2026-09-21 — Milestone 25 finished: screens migrated, onboarding reordered

**Goal:** move the remaining English screen text into resources with seven translations, and put onboarding in the brief's order.

**Built:** all screens (onboarding, setup, settings, home, learn, practice, reader, words, progress, share, speak) now use `strings.xml`; 453 strings per language for en, fr, es, de, it, nl, zh, ja. Non-composable code returns `UiText` so it can be tested. Onboarding is 11 steps: app language, source with AUTO toggle, target, reason, level, mode, reminder, keyboard, incoming, microphone, complete. The saved step is now stored by name.

**Decisions:** sample data, engine explanations and the pilot report stay English by design. Translations are AI-written and need native review.

**Problems:** composables cannot be called inside `joinToString` lambdas (mapped first); a few legitimately identical strings needed an allowlist in the resource test.

**Verification (automated):** lint, build, instrumented-test compile, 746 of 746 unit tests. **Not run on a phone.**

**Manual test steps:** `docs/progress.md`, "Milestone 25 detail".

## 2026-09-21 — Keyboard layouts follow the typing language

**Goal (owner):** the keyboard letters, numbers and symbols should reflect the language chosen in onboarding.

**Built:** `KeyboardLayouts` takes a language: AZERTY, QWERTZ, Español with ñ, QWERTY otherwise; long-press accents (`KeyCapView`); language punctuation on symbol page 2; the keyboard rebuilds on a source-language change. Digits stay 0-9 (same in all eight languages).

**Decisions:** the layout follows the source (my) language, not the app language or target, because that is the language typed (CLAUDE.md 6.9). 中文/日本語 stay QWERTY; the phone's own input method is used for native characters. Capital ß uses ẞ.

**Verification (automated):** lint, build, instrumented compile and unit tests pass; a deliberate break of the French top row failed a test and was reverted. **Not run on a phone.**

## 2026-09-21 — Chinese and Japanese input engines: decision only

**Goal (owner):** 中文 and 日本語 should have their own keyboards; "any open source engine".

**Done:** a decision record, `docs/input-engines.md`: Mozc for 日本語 and librime for 中文 (both BSD-3-Clause engines; data under IPAdic/Okinawa/ICOT notices and LGPL-3.0 respectively), with the work needed and its order. The design board shows both keyboards. No engine code was written.

**Blocker:** this machine has no Android NDK or CMake; both engines are native. **Verification:** licence details read from the projects' pages, not from shipped files; no code run.

## 2026-09-21 — Input engines: NDK installed, Mozc built for Android

**Goal:** first spike step from `docs/input-engines.md`.

**Done:** NDK 28.2 and CMake 3.22.1 installed (owner approved); Mozc's Android library built successfully (arm64-v8a `libmozc.so` 16.2 MB; 15.8 MB zip for four ABIs). **Problem:** a first build was cancelled when a watcher process was killed; it was restarted detached and resumed from Bazel's cache. A wrong Bazel flag (`--local_ram_resources`) was replaced by `--local_resources=memory=`.

**Not done:** Kotlin wrapper, librime, keyboard UI. **Verification:** the build finished; the library has not been loaded or run.

## 2026-09-21 — Mozc runs from Kotlin (emulator)

**Goal:** load the real Japanese engine from the app and prove it converts.

**Built:** `MozcEngine`, `MozcJni`, generated protobuf classes, `CandidateEngine` and `CompositionController` (with unit tests), `scripts/build-mozc.sh`, Gradle source sets for the Mozc sources, libraries and data file (`noCompress` for `.data`). Mozc's data is a separate 19 MB file, copied out of the APK on first use. Incognito mode is set.

**Environment:** installed an Android 36 x86_64 emulator image and an AVD (`al_test`) to run the real library; the owner's phone was not used.

**Verification:** `MozcEngineTest` 4 of 4 pass on the emulator; lint, build and the unit tests pass. **Not run on a phone; not connected to the keyboard UI.**

## 2026-09-21 — 日本語 keyboard wired to Mozc; first device run of the instrumented suite

**Built:** 12-key kana layout, candidate strip, `routeToConversion` in the keyboard service (kana to `CompositionController`; space, enter, backspace while composing; flush before symbols and toolbar actions), background loading of Mozc. `JapaneseTypingTest` drives real Mozc against a real `EditText`.

**First run of the whole instrumented suite on an emulator (69 tests):** 5 failed, all in the tests, none in the app: a test called `runOnMainSync` from the main thread (2); a test expected two child views and the candidate strip made three (1); the notification test read the notification before the system had posted it, so it now waits up to 3 s (1); a Progress test clicked a button that was below the visible area, so it now scrolls first (1). After the fixes the suite passes. **Problems:** the emulator was killed once for lack of memory; restarted with less memory and the Gradle daemons stopped.

**Verification:** unit tests, lint, build; 69 instrumented tests on an x86_64 emulator. Not run on a phone.

## 2026-09-21 — 中文 with librime, licences screen

**Built:** librime and its dependencies cross-compiled (static) for two ABIs; a JNI layer; `RimeEngine`; the pinyin_simp schema; Chinese punctuation on the keyboard; the composition wiring for 中文 in the keyboard service; an "Open source licences" screen in Settings. **Decisions:** the small Apache-2.0 pinyin_simp schema was chosen because its data licence is permissive and it needs no OpenCC data (OpenCC's dictionary builder cannot run when cross-compiling); it is low quality and meant to be replaced. Schema compilation happens on the phone at first start. **Problems:** OpenCC bundles its own marisa, so the system one was used; librime expects `opencc/opencc.h` (installed by hand); the Android toolchain hides headers outside the find path (`CMAKE_CXX_FLAGS -I`).

**Verification:** lint, build, unit tests and 74 instrumented tests on an x86_64 emulator, including real librime (5 tests). **Not run on a phone; arm64 libraries not run.**

## 2026-09-21 — Home shows real figures instead of sample numbers

**Found on the phone (first real run):** Home showed "12 messages translated today" and "18 new words this week", and its language-map card and "1 of 3 done" bar were also sample numbers. **Fixed:** Home now reads the Progress report: translations this week (label changed from "today", translated in all eight languages), new words this week, and the language map's real state counts, all zero until there is activity; the fake "1 of 3 done" bar was removed. **Still sample content:** the lesson chips on Home, and the whole Words tab (its totals and word list). Tests: a Home state test; the sample-data test was trimmed. Lint, build and unit tests pass; the new build has NOT been installed on the phone yet (it was disconnected); not otherwise verified.

## 2026-09-21 — Words tab and Home lesson use real data; sample data removed

**Goal (owner):** connect the Words tab and the Home lesson to real data.

**Built:** the Words tab is now the user's real Personal Language Map for the language being learned (observed live from the Room map): real totals on the filter tabs, real entries (the word as first seen, its meaning when known, mastery state, exposure count), an honest empty state ("No words yet…") that is different from "No matching words". Home's lesson card shows today's real lesson from the same lesson service as the Learn tab, or "No lesson yet" until something worth a lesson has been met. `PlaceholderData` and `SampleContent` (and their tests) were deleted, so no sample figures or words remain anywhere in the app. Strings added (8 languages): `wd_from_your_conversations`, `wd_empty`, `home_no_lesson_yet`.

**Notes:** pronunciation guidance is not stored in the map, so the Words rows no longer show a phonetic line; meanings appear only once a lesson or help request has fetched them. Opening Home asks the lesson service for today's lesson, the same call the Learn tab makes (it creates the day's lesson if there are candidates).

**Verification (automated):** lint, build, instrumented-test compile and unit tests pass (WordsViewModel and HomeViewModel tests were rewritten around real map data). **Not installed on the phone since this change (it was disconnected); not seen on a screen.**

**Seen on the owner's phone (Infinix X6728B, Android 15, arm64) after installing this build:** Home shows 0 and 0 for the two counts and "No lesson yet…" on the lesson card; Words shows "Search 0 words", all filters at 0 and "No words yet…". This is a visual check of two screens on a fresh-looking data set only: it says nothing about a populated map. Still to fix: the round "A" avatar on each screen's header is a fixed letter, and Home's "Continue today's lesson" button is shown even when there is no lesson.

## 2026-09-21 — Brand mark instead of a fixed "A"; no lesson button without a lesson

The round "A" avatar in every screen header is replaced by `AlterLinguaMark`, drawn in code (two overlapping speech bubbles on a blue tile, following the theme colours). Home's "Continue today's lesson" button now appears only when there is a lesson. The navigation instrumented test that clicked that button was rewritten (it now checks the empty lesson summary and the Learn tab); **that instrumented test was compiled but not run** (no emulator was running). Lint, build and unit tests pass; installed on the owner's phone and Home seen once: the mark shows, the button is gone. The launcher icon was not changed.

## 2026-09-21 — Keyboards get a row of each language's own characters
Owner feedback: each language should visibly have its own keys and symbols, not only a different layout. Added `KeyboardLayouts.hasExtras`/extras row for every language but English (see docs/localization.md section 7), slim (34 dp) and permanent across pages; the key area height follows it. Design board updated. Lint, build, unit tests pass; NOT installed on the phone (it disconnected) and not seen.

**Phone check, 中文 keyboard:** installed on the owner's phone; the extra row, the pinyin candidate strip and the 中文 space bar were seen working (arm64, real librime). A last screenshot showed another app's notification banner and a different screen than expected (the owner was probably using the phone at the same time); that image was deleted unread beyond noticing it, and no further taps were sent.

## 2026-09-21 — Keyboard styles for 中文 and 日本語

**Goal (owner, with a reference image of Gboard-style keyboards):** Chinese and Japanese should offer the usual ways of typing, not one layout.

**Built:** `KeyboardStyle` (PINYIN_26, PINYIN_9, KANA, ROMAJI) saved per language and chosen in Settings; a nine-key pinyin layout (number keys with letter hints, 分词 key); kana flick gestures on the 12-key keyboard; a romaji layout for 日本語; a second Rime schema for nine-key input; a JNI change that returns raw input and a display text; the service routes keys by style and swaps the Rime session when the style changes. **Problems:** the nine-key schema first gave no candidates because Rime's `xlit` maps characters one to one (`xlit/abc/222/`, not `xlit/abc/2/`), found with a debug test; the engine's pinyin display ("ni hao") broke "Enter keeps what I typed", so raw and display text are now separate.

**Verification (automated):** lint, build, unit tests, and 75 instrumented tests on the emulator. **Not run on the phone: flick, the style menu, the nine-key keyboard.**

## 2026-09-21 — Style switch key removed

The on-keyboard style switch key (added earlier the same day) was undone at the owner's request: the enum value, the three bottom-row layouts, the service handler and its string are removed. The typing style is chosen in Settings only.

## 2026-09-21 — Typing style step in onboarding

Owner asked for the 中文/日本語 typing-style choice to be available in onboarding as well as Settings. Added `OnboardingStep.KEYBOARD_STYLE` (after the language step, shown only for languages with more than one style), `KeyboardStyleStep` with `StyleCard`s, view-model support (step list, counts, skipping both ways, saving the style) and 6 strings in 8 languages. Lint, build and unit tests pass. Installed on the phone if it was connected (see the reply); the new screen was not viewed and no instrumented test covers it.

## 2026-09-21 — Chinese keys are Chinese: strokes and Zhuyin; Settings lists both languages

Owner feedback: the default 中文 and 日本語 keys should be the language's own characters, and the Japanese Kana/Romaji choice was not visible; pinyin on nine keys is not needed. Built: stroke keyboard (default) and Zhuyin keyboard for 中文 next to Pinyin QWERTY (kept as an option), two new Rime schemas with their data (`stroke_simp`, `zhuyin_simp`, `zhuyin.yaml`), a five-row key area for Zhuyin, styles listed for both languages in Settings and in onboarding, nine-key pinyin removed. Discovered: Zhuyin with the toneless pinyin dictionary cannot use tone marks; they are left off the keys. Two earlier edit scripts partly applied when a command was interrupted; the state was checked before continuing. Verification: lint, build, unit tests, 76 instrumented tests on the emulator (stroke and Zhuyin engine tests included). Installed on the phone; not looked at yet.

## 2026-09-21 — Handwriting pad for every language

Owner asked for a drawing pad (like Gboard's 手写) on the keyboard for all languages, chose Google ML Kit Digital Ink as the recogniser (not open source, on-device, model downloads) and a pen key next to the space bar. Built: `HandwritingController` (debounced recognition of all strokes, choose/clear/retry, model states), `MlKitInkRecognizer`, `DrawingView`, `HandwritingPanel`, the pen key on every bottom row, service wiring (backspace clears the drawing before deleting text; the pad closes when the language changes or the input ends), 8 strings x 8 languages, privacy and licence notes. **Problem found:** a model download's task result is empty (null), so treating a null result as failure made the first emulator test fail; success is now tracked separately. Verification: unit tests, lint, build, and the emulator test with the real library (English T). **Not tried on the phone.**

## 2026-09-21 — Keyboard style in Settings only for the language typed in

Owner: the Zhuyin and kana styles in Settings should appear only when 中文 or 日本語 is chosen as the default (My language). Reversed the earlier "always list both languages" change: the Settings "Keyboard style" section now shows the typing styles of the language typed in, and only when it has more than one; other languages show nothing. Unit-tested for all eight languages. Lint, build and unit tests pass; installed on the phone; not viewed.

## 2026-09-21 — Backend: Mistral providers, access control, deployment package

**Owner decisions:** Mistral AI as the provider (free tokens), a Contabo VPS as the host. **Built:** `MistralClient` (auth, error mapping without echoing bodies), translation provider (chat model, JSON answer, instructions in the system turn and the message only in the user turn), speech-to-text (Voxtral) and text-to-speech (Voxtral TTS) providers, registered as `mistral`; API token guard and per-token rate limit, production refusing to start without tokens; Android sends the token (build property) and takes a release URL from a build property; `backend/deploy/` (Dockerfile, compose with Caddy, `.env` example, DEPLOY.md). **Found while checking Mistral's documentation:** Mistral's free plan may train on inputs by default, which conflicts with the privacy rules, so the docs and DEPLOY.md say to opt out or use the paid plan before real messages; Voxtral TTS has no Chinese or Japanese voice, so those return the normal no-voice error; the TTS voice-id list and audio zero-retention terms could not be confirmed from the documentation. **Verification:** 262 backend tests (async ones run through anyio), the image built and ran (read-only, non-root), Android unit tests and lint pass. **Not done:** any call to the real Mistral API, deployment, and testing Translate on the phone.

## 2026-09-22 — Groq as the main translation provider, Mistral as fallback

**Goal (owner):** use Groq as the main translation provider with Mistral as a fallback; owner supplied both a Mistral key ("alterlingua" key in the Mistral console) and a Groq key.

**Built:** `app/core/groq.py` (`GroqClient`, same shape as `MistralClient`: bearer auth, controlled errors, never echoes a body), `app/translation/groq_provider.py` (`GroqTranslationProvider`, the same JSON-answer chat contract as the Mistral provider), `app/translation/fallback_provider.py` (`FallbackTranslationProvider`: tries a list of providers in order, skips one that raises a runtime `ProviderError`/`ProviderUnavailableError`/`ProviderTimeoutError` and tries the next, re-raises the last error if all fail; capabilities are the intersection of every leg's languages and auto-detect). `app/translation/registry.py` now offers `groq` and `fallback` (Groq then Mistral; a leg with no key configured is left out at registry time rather than failing construction). New settings `ALTERLINGUA_GROQ_API_KEY`, `ALTERLINGUA_GROQ_BASE_URL`, `ALTERLINGUA_GROQ_TRANSLATION_MODEL`. `backend/.env` created locally (git-ignored, not committed) with `ALTERLINGUA_TRANSLATION_PROVIDER=fallback` and both keys.

**Decisions:** the fallback only covers the translation endpoint (`/v1/translate`), not speech-to-text/text-to-speech — not asked for, and Groq's Whisper/TTS models would need their own providers, which is out of scope here. The two legs currently share one timeout budget (`ALTERLINGUA_PROVIDER_TIMEOUT_SECONDS`) inside `TranslationService`, so a primary that uses the whole budget before timing out can leave little time for the fallback attempt — documented as a known trade-off rather than solved, since solving it would mean changing the shared timeout wrapper for all providers.

**Problems found and fixed:** the model documented as Groq's flagship general model, `llama-3.3-70b-versatile`, no longer exists on this account (`404 Not Found` on first live call); `GET /v1/models` was queried live with the real key to see what is actually available (11 models, no Llama chat model among them) and the default was changed to `openai/gpt-oss-120b`, which answered correctly.

**Verification:** 283 backend tests pass (21 new, `tests/test_groq_and_fallback.py`, all against a fake HTTP transport — no network, no real key touched by the test suite). **Manually verified against the real Groq API**: `POST /v1/translate` through the running server for English → Français/Español/Deutsch/Italiano/Nederlands/中文/日本語 and Español → English and 日本語 → English (auto-detected) all returned `200` with a correct translation (per CLAUDE.md 6.22, not just English → Français). The real Mistral key answered `429` (quota/rate limit) in the same session; the fallback chain treated that as a skippable leg, not a crash, matching the design (still needs Mistral's own quota/billing resolved to be a real fallback rather than one that also 429s). Not yet checked: Groq behaviour on a genuinely long/slow request, and whether Groq's free-tier rate limits hold up under real WhatsApp-scale traffic.

**Manual test for the owner:** `cd backend && .venv/bin/uvicorn app.main:app_factory --factory --reload --port 8000`, then `curl -X POST http://localhost:8000/v1/translate -H 'content-type: application/json' -d '{"text":"Are you coming tomorrow?","source":"auto","target":"ja","context":"messaging","tone":"natural"}'` — expect a 200 with a Japanese translation from Groq. Real keys live only in `backend/.env` (git-ignored); nothing secret was committed.

## 2026-09-22 — Voice checked against the real Mistral API: a TTS bug fixed, an STT auto-detect gap found

**Goal (owner):** "is it translating and is the voice working now" — checked live rather than assumed.

**Text translation:** confirmed working end-to-end through Groq (primary) for all 8 catalogue languages both directions (English → each, plus Español → English and 日本語 → English, auto-detected).

**Found and fixed — text-to-speech had no real voice:** `MistralTextToSpeechProvider.capabilities()` invented a voice id (`mistral-default-{code}`) for all 6 "spoken" languages whenever none was configured in `ALTERLINGUA_MISTRAL_TTS_VOICES`, and `synthesize()` then silently omitted `voice_id` from the request. Calling the real API confirmed there is no server-side default: it answers `400 "Either ref_audio or voice must be provided."` This meant `/v1/audio/speak` would run a real transcription and translation and only then fail on a plain `502`, for every language, always — the "no voice → refused before any recognition or translation" promise in the README was not actually true. Fixed in `app/speech/mistral_tts_provider.py`: `capabilities()` now only advertises a language that has a real configured voice id, so an unconfigured language is refused up front (`422 unsupported_language`) as designed, and `synthesize()` refuses defensively instead of calling the API without a voice. `GET /v1/audio/voices` was queried live with the real key: this Mistral account currently has 10 preset voices and all of them are English (`en_us`/`en_gb`) — no French/Spanish/German/Italian/Dutch preset exists yet, despite the Voxtral TTS documentation describing those languages; a custom cloned voice (`ref_audio`) could add one later. `ALTERLINGUA_MISTRAL_TTS_VOICES={"en": "en_paul_neutral"}` (a real id from that list) was added to `backend/.env` and to the `.env.example` comment. Two tests in `tests/test_mistral_providers.py` that encoded the old (wrong) assumption were rewritten to match the real contract; 283 tests still pass.

**Found, not fixed — STT auto-detect does not actually detect:** calling `POST /v1/audio/transcriptions` on the real API with no `language` field set returns `"language": null` — Mistral's Voxtral transcription does not report what it heard, even though `MistralSpeechToTextProvider.capabilities()` claims `auto_detect=True`. In the app this surfaces as a controlled `422 source_language_undetected` on any incoming audio where the spoken language isn't already known (for example an incoming WhatsApp voice note with `source=auto`) — audio translation with an **explicit** source language is unaffected and was verified working. Left as a known gap, not fixed here: fixing it is a design decision (e.g. a separate detection pass, defaulting the app to always send an explicit source, or trying Groq's Whisper endpoint instead) that wasn't asked for in this session.

**Manually verified against the real Mistral + Groq APIs in this session:**
- `POST /v1/audio/translate` with real synthesized English speech (`source=en`, generated by Mistral TTS itself so the content is known) and `target=fr`: transcript `"Good morning. Are you coming tomorrow?"`, translation `"Bonjour. Tu viens demain ?"` — both correct.
- `MistralTextToSpeechProvider.synthesize()` for English produced a real 69 KB playable WAV file using the now-configured `en_paul_neutral` voice.
- `POST /v1/audio/speak` with `source=auto` on that same real English audio returned the controlled `422 source_language_undetected` described above (not a crash) — this is the auto-detect gap, not a new bug from today's TTS fix.
- Groq quota/model: this account's live `/v1/models` list has 11 models and does not include `llama-3.3-70b-versatile`; `openai/gpt-oss-120b` was confirmed working (see the previous entry).

**Manual test for the owner:** `backend/.env` (git-ignored, not committed) already has both real keys and the real voice id. Run the server and:
```
curl -X POST http://localhost:8000/v1/audio/translate -F "audio=@clip.wav;type=audio/wav" -F "source=en" -F "target=fr"
```
using a real English recording, with an explicit `source` (not `auto`) until the auto-detect gap above is addressed.

## 2026-09-22 — Translated outgoing voice moved from Mistral TTS to Android's on-device speech

**Goal (owner):** after finding that Mistral's real text-to-speech only has English preset voices (previous entry), the owner chose "option 1" from three alternatives offered (on-device Android TTS, a dedicated multilingual cloud TTS provider, or Mistral voice cloning): use Android's own on-device `TextToSpeech` instead.

**Inspected first:** the incoming voice-note screen (`SharedVoiceViewModel`) already used on-device `Speaker`/`AndroidSpeaker` for its "Listen" button and never called the backend's TTS at all — only the outgoing "translated voice message" screen (`SpokenTranslationFlow`) called `POST /v1/audio/speak` (transcribe + translate + cloud TTS in one call).

**Built:** `Speaker` (`app/src/main/java/com/alterlingua/app/share/Speaker.kt`) gained `synthesizeToFile(text, language, file)`, implemented in `AndroidSpeaker` with `TextToSpeech.synthesizeToFile` wrapped as a suspend call. `SpokenTranslationFlow` now calls `VoiceApi.translate` (the same transcribe+translate endpoint the incoming-voice screen already uses) and then synthesizes the target-language speech itself, checking `speaker.canSpeak(target)` first. `AppViewModelProvider` now gives it an `AndroidSpeaker`. Removed as dead code once nothing called it any more: `SpeakApi`, `SpeakResult`, `SpokenTranslation`, `HttpVoiceApi.speak()`/`interpretSpeak`, and `HttpSpeakApiTest.kt` (the backend's `/v1/audio/speak` endpoint itself is untouched, just no longer called from this Android flow).

**Problem found while designing this and fixed before it shipped:** the first draft deleted the recording as soon as transcription+translation succeeded (matching the old code, since the recording is genuinely no longer needed for that). But a *later* on-device synthesis failure was marked retryable, and `retry()` only knew how to re-send the recording — which was already gone, so retry would have silently done nothing. Fixed by keeping the already-translated text in memory (`PendingSpeech`) until it is either spoken or the screen is left, and having `retry()` re-speak that directly (no network call) when it is set, only falling back to re-sending the recording otherwise. Caught before any device testing, by writing the retry test case and noticing the recording would be null.

**Verification:** `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin` and `:app:testDebugUnitTest` — 768 of 768 Android unit tests pass (`SpokenTranslationFlowTest` rewritten around the new two-step flow, including new "no voice on this phone" and "synthesis itself fails, retry doesn't touch the network" cases); `:app:lintDebug` clean (22 pre-existing findings, none in the touched files). **Not manually verified on a phone**: whether the shipped Android voice packs actually cover all 8 catalogue languages depends on the device and what the user has installed; that has not been observed on real hardware.

**Manual test for the owner:** install the debug build, open Home → "Record a voice message", choose a target language, record a few seconds, Stop, wait for the result, tap Listen. Try a language your phone likely has a voice for (Français, Español) and, if possible, one it might not (日本語, 中文) to see the "no voice for that language" message rather than a crash.

## 2026-09-22 — On-device translated voice, verified on the phone

**Goal (owner):** "have reconnected the phone" — verify the just-built on-device TTS change for real, after the earlier report flagged it as implemented but not yet run on hardware.

**Setup:** installed the debug build (`./gradlew :app:assembleDebug`, `adb install -r`), started the backend locally with the real Groq+Mistral `.env` from the earlier sessions, and connected it to the phone with `adb reverse tcp:8000 tcp:8000` (the debug build's default `TRANSLATION_BASE_URL` is `http://127.0.0.1:8000`, matching the app's build config).

**Verified by driving the UI over `adb` (taps + screenshots, since no human was speaking into the phone) and reading the backend's own log for each request:**
- Real ambient speech in the room ("Hello, how are you? Hope you are fine.", "Hello.") was picked up by real recordings, correctly transcribed by Mistral, and correctly translated by Groq.
- This phone's on-device Google TTS engine then actually spoke and produced a real, playable WAV for **Français**, **日本語** and **中文** — the three languages Mistral's own cloud TTS cannot speak at all (see the previous two entries). The Français one was confirmed audibly playing (the Listen button correctly toggled to "Stop" while it played).
- Android's Share sheet opened correctly each time with the generated file offered to WhatsApp and other apps; each share sheet was cancelled with the back button rather than actually sent, so nothing was sent to a real contact.
- Two error paths were also seen for real: "Couldn't reach the translation service" before the backend/reverse tunnel was set up, and "Unclear voice recording" on a couple of recordings that happened not to catch any speech — both matched their designed copy, left Try again / Record again available, and did not lose state.
- Home's real counts (messages translated, new words, a generated micro-lesson) updated correctly from these interactions.
- No crash anywhere in `logcat` for the whole session.

**Not covered this pass:** Español, Deutsch, Italiano, Nederlands were not confirmed with real playback (no ambient speech happened to land in a recording aimed at those targets in the time available) — only Français, 日本語 and 中文 were actually heard. Recorded as an open item in `docs/progress.md` rather than assumed fine.

**Left running for the owner:** the backend server (local, port 8000) and the `adb reverse tcp:8000 tcp:8000` tunnel, so the owner can keep testing without restarting them.

## 2026-09-22 — Keyboard's bottom row was cut off behind the gesture navigation area

**Goal (owner, with a screenshot):** "fix the keyboard cut at the down" — the bottom row of keys (?123, globe, handwriting, comma, spacebar, period, enter) was clipped at the very bottom of the screen in a real WhatsApp conversation.

**Found:** `AlterLinguaKeyboardView` already had code to pad itself by the navigation bar's inset (added earlier for Android 15's edge-to-edge IME windows), but only reactively, through `ViewCompat.setOnApplyWindowInsetsListener`. On this phone, the very first time a fresh keyboard window is shown, that callback can arrive a frame after the IME window's height is already locked in by the system, so the first frame renders with no bottom padding at all and the last row is clipped. Reproduced on the device: a genuinely fresh keyboard process showed the cut row; the same conversation with an already-warm keyboard session did not.

**Fixed** in `app/src/main/java/com/alterlingua/app/keyboard/AlterLinguaKeyboardView.kt`: the view now starts with a safe minimum bottom padding (`MIN_NAVIGATION_BAR_INSET_DP = 48`, applied at construction, before any inset has arrived) in addition to the existing reactive listener, which still corrects it to the exact real value once it fires. Also added `onAttachedToWindow` calling `ViewCompat.requestApplyInsets(this)` to nudge the system into dispatching insets promptly on attach. A first attempt read the private `navigation_bar_height` system resource via reflection for the same purpose; lint correctly flagged it (`InternalInsetResource`, `DiscouragedApi`) as unsupported and OEM-unreliable, so it was replaced with the fixed dp constant instead — simpler, portable, and it errs toward slightly more padding rather than depending on a resource that could itself be customized incorrectly by an OEM.

**Verified on the owner's phone:** rebuilt, reinstalled, and reproduced the fix in the exact conversation from the bug screenshot ("Website Manager", pinned "JobPilotNG Employer Features") on a guaranteed-fresh keyboard process (confirmed via `adb shell am force-stop` before reopening the field) — full bottom row visible with correct padding and the gesture-navigation pill clear of it. (One retest during this session showed the cut again, but that turned out to be `adb install -r` not having restarted the already-running process, so it was still exercising the pre-fix code in memory; force-stopping to guarantee a fresh process confirmed the fix.) Lint clean (22 pre-existing findings, none in this file — the two the fix itself introduced were removed by switching away from the reflection approach). Unit tests and compile pass.

**Manual test for the owner:** open any WhatsApp conversation with the AlterLingua keyboard, on a phone that has been rebooted or where AlterLingua's process was recently killed (the case most likely to show a first-frame cut) — the full bottom row (?123, globe, pencil, comma, spacebar, period, enter) should be clear of the gesture bar.

## 2026-09-22 — Same missing-inset bug at the top: onboarding and the standalone voice screens

**Goal (owner, with a screenshot):** "its look at the top its not properly done same with the one in onboarding and some screen" — the "Translated voice message" screen's title sat flush under the status bar, and the owner noted onboarding had the same problem.

**Found the pattern:** the app's main 5-tab shell (`AlterLinguaApp`) uses a Material3 `Scaffold`, which already handles status/navigation-bar insets correctly for Home, Learn, Words, Progress and Settings. But three screens live outside that Scaffold, each its own `enableEdgeToEdge()` Activity (or shown before the main shell) with no inset handling of its own:
- `ui/onboarding/AppLanguageScreen.kt` — the very first screen on a fresh install ("choose your language"), shown before `OnboardingRoute` (which already had the fix — see `OnboardingScreen.kt`'s `.systemBarsPadding()`).
- `speak/SpeakScreen.kt` — "Translated voice message" (the one in the screenshot).
- `share/SharedVoiceScreen.kt` — the incoming voice-note screen, built the same way.

All three used a fixed `.padding(vertical = 24.dp)` regardless of the actual status/navigation bar size, so on Android 15's enforced edge-to-edge the title rendered under the status bar and (less visibly, same root cause) scrollable content could run under the gesture navigation area at the bottom.

**Fixed:** added `.systemBarsPadding()` to all three, in the same position and style already used and working in `OnboardingScreen.kt` — no new pattern invented, just applied consistently to the screens that were missing it.

**Verification:** compiles clean, lint clean (22 pre-existing findings, none new), unit tests pass. **Manually verified on the phone** for `SpeakScreen` ("Translated voice message"): reinstalled the rebuilt APK and confirmed a clear gap between the status bar and the title, matching the owner's screenshot but fixed. `AppLanguageScreen` and `SharedVoiceScreen` use the identical fix on the identical composable structure but were not separately re-screenshotted this session (the former needs the app's data cleared to see again, which was not done without asking first).

**Manual test for the owner, once reconnected:** `adb install -r` the rebuilt debug APK (or reinstall from Android Studio), then check the very first "choose your language" screen on a fresh install (or `pm clear` to see it again), the "Translated voice message" screen (Home → "Record a voice message"), and the incoming voice-note screen (share a voice note into AlterLingua) — the title on each should sit clearly below the status bar icons, not touching them.

## 2026-09-22 — A welcome screen before "Choose your language" (owner request, with a Stitch reference)

**Goal (owner, with a screenshot of "Choose your language"):** "before this you suppose to give a main page with enough description about the app and add good image and animation that would attract users to proceed with the onboarding button. design professionally well."

**Checked Stitch first (CLAUDE.md 10, 53):** the "AlterLingua UI/UX System" project's "Part A — Onboarding & System Setup" bundle has an "A1: Hero Linguistic Promise Card" with the exact headline "You write in your language. They receive theirs." and subtitle "Communicate now. Learn as you go. Need less translation over time." — but embedded inside a later settings-review screen, not a standalone first-launch welcome page. No standalone welcome/splash screen exists in the design system, so this screen was designed fresh, reusing that approved copy and the existing brand mark rather than inventing new visual language.

**Built:**
- `ui/onboarding/WelcomeScreen.kt`: brand mark (`AlterLinguaMark`, already used elsewhere — no new image asset needed), the headline and tagline, a card that cycles through "Bonjour / Français", "Hola / Español", "Hallo / Deutsch", "Ciao / Italiano", "Hallo / Nederlands", "你好 / 中文", "こんにちは / 日本語" every 2.2 seconds with a fade/slide transition (`AnimatedContent`) — one purposeful animation that demonstrates the product itself, not decoration (CLAUDE.md 36: calm, not flashy), a description paragraph, a "Get started" button and a privacy reassurance line.
- `AppViewModel.kt`: new `AppStartState.Welcome`, shown once, before `ChooseAppLanguage`, gated by a new persisted `welcomeSeen` flag (`UserSettings.kt`, `UserSettingsRepository.kt`) so a returning user (`onboardingCompleted`) never sees it, and a user who has already gotten past it does not see it again even if they restart mid-onboarding.
- Strings added in all 8 languages (`welcome_headline`, `welcome_tagline`, `welcome_greeting_caption`, `welcome_description`, `welcome_get_started`, `welcome_privacy_note`), translated by hand into fr/es/de/it/nl/zh/ja matching the tone of the existing onboarding strings.

**Verification:** compiles clean; lint clean (22 pre-existing findings, none new); 769 of 769 unit tests pass (2 new: the state transition to `ChooseAppLanguage` and that nothing else changes, plus the persistence round-trip for `welcomeSeen`). **Manually seen on the owner's phone**: rebuilt, reinstalled, and (since the device had already completed onboarding) `pm clear`'d the app's own local data to preview a fresh first launch — this only resets AlterLingua's local test progress, nothing WhatsApp- or account-related. The screen rendered exactly as designed and the greeting carousel was confirmed cycling (Ciao → Hallo, observed across two screenshots). Tapping "Get started" was confirmed working once the owner unlocked the phone: it correctly leads to "Choose your language", matching the owner's original screenshot exactly (and confirming the earlier top-status-bar padding fix holds there too).

**Left as found:** re-enabled AlterLingua as the default keyboard (clearing app data un-registers an IME) and restored the device's normal screen timeout (temporarily raised, then `svc power stayon` used, to fight what looked like a proximity-sensor or handling-related auto-sleep during testing — both undone before finishing).

**Manual test for the owner:** `pm clear com.alterlingua.app` (or a fresh install) and open the app — the welcome screen should appear before "Choose your language", the greeting card should visibly cycle through languages, and "Get started" should lead into the language choice as before.

## 2026-09-22 — Welcome screen wording corrected: the keyboard is not WhatsApp-only

**Owner feedback:** "the main page writeup is specifying the keyboard is only for whatsapp but its not only for whatsapp." Correct — the AlterLingua keyboard is a real Android input method (CLAUDE.md 16), usable in any app; WhatsApp is the flagship integration, not the only one. The onboarding screens already said this correctly ("AlterLingua works through its own keyboard, so you can write in WhatsApp and other apps without leaving them") but the new welcome screen's description said "adds a translating keyboard right inside WhatsApp," implying exclusivity.

**Fixed:** `welcome_description` reworded to "AlterLingua adds a translating keyboard to your phone. Type normally in WhatsApp or any app, ..." in all 8 languages, matching the phrasing already used correctly elsewhere in onboarding. The other WhatsApp-specific strings in the app (incoming notification translation, voice-note sharing) were checked and left as they are, since those particular features genuinely are WhatsApp-only in the current scope.

**Verification:** compiles clean, lint clean, unit tests pass (text-only change, no logic touched). **Manually confirmed on the owner's phone**: reinstalled, previewed the welcome screen again (another local `pm clear`, same as before — local test data only) and read the corrected paragraph on screen.

## 2026-09-22 — Auto-translate: a Settings toggle to translate without tapping the button

**Goal (owner):** "add this feature to the settings to allow or disable auto translate meaning as i type it translate automatically without tapping the translate button in the keyboard and i can also disable it and use the translate button instead."

**Built:** a new `autoTranslateEnabled` setting (off by default, so nothing changes for anyone who hasn't turned it on), a "Auto-translate" toggle in Settings → Languages (right under "Detect source automatically"), and `TranslationFlow.scheduleAutoTranslate()`: called on every key that changes the text when the setting is on, it (re)starts a ~900ms countdown, cancelling whatever countdown was already running, so a real translate request is only sent once the user actually pauses — never character-by-character (CLAUDE.md 17). It is otherwise the exact same `translate()` used by the button, so Undo, failure handling and everything else behaves identically; it just triggers itself.

**Verification:** compiles clean, lint clean (22 pre-existing findings, none new), 774 of 774 unit tests pass (4 new for the debounce: fires only after a pause, a burst of keys sends only one request, does nothing if the field is blank when it fires, cancels cleanly when the field is left; 1 new for the Settings toggle). **Manually verified on the owner's phone**, end to end: turned the toggle on in Settings, typed "hi" with real taps on the on-screen keys (not `adb input text`, which bypasses the keyboard's own key handling entirely and would not have exercised this path), and watched it become "salut" by itself with the usual Undo banner — confirmed via the production server's own log that exactly one request was sent (not one per keystroke), through Groq, with no message content in the log.

**Manual test for the owner:** Settings → turn on Auto-translate. In any app, type a message and stop — a moment later it should translate itself, with Undo available exactly as when tapping the button. Turn it back off to return to manual-only.

## 2026-09-23 — Auto-translate debounce lengthened, and re-verified live

**Owner feedback:** "before auto translating it suppose to wait for me to finish typing because it auto translate words i have not even finished." Confirmed on a real device the previous 900ms pause fired mid-sentence. Increased `TranslationFlow.AUTO_TRANSLATE_DEBOUNCE_MILLIS` to 2.5s and added a unit test (`autoTranslate_theRealDefaultPause_isLongEnoughToNotFireBetweenWords`) that locks in the reported scenario: nothing fires at 1.5s of pause, it does fire once genuinely paused past 2.5s.

**Verifying this live took two attempts, and both dead ends were useful findings, not code bugs:**
1. First retest used Chrome's address bar and never fired even past 3.8s of pause. Root cause: the Settings "Auto-translate" toggle had gone back to **off** by the time of this retest (turned off at some point after the earlier session, not by any code in this change) — so nothing was supposed to fire. Confirmed by reopening Settings and seeing the switch off; turned it back on.
2. Second retest, this time in WhatsApp's own search field, fired correctly at the right time (silent for 1.5s, translated by ~3.5s) but then reported "Couldn't apply the translation. Tap Restore" — because a live search-as-you-filter box does not accept a straightforward text replacement the way a normal message field does. This is the same `apply()`/`putOriginalBack()` path the manual Translate button already uses (not new to auto-translate), and it degraded exactly as designed: the failure was reported, "hi" was fully restored on tapping Restore, nothing was lost. Translating from a search box was never really the intended use case; this only surfaced because of where the test happened to run.

**Verification:** compiles clean, lint clean, 775 of 775 unit tests pass. **Manually confirmed on the owner's phone** that the new pause length holds in real use: silent through a normal pause (1.5s) between words, fires once genuinely paused (past 2.5s).

## 2026-09-23 — Keyboard keys widened: touch targets now meet edge to edge

**Owner feedback:** "the keyboard keys card in each keys are small so as a result when i place my hand on it my hand might press the other letter close to it."

**Root cause:** the visual gap between keys was a real layout margin (2dp on every side of each key, `LayoutParams(...).apply { setMargins(gap, gap, gap, gap) }`), so the actual touchable area of each key was smaller than the key's own share of the row by that margin on every side — and the margin itself was a dead zone that belonged to neither key, wasted rather than given to whichever key the finger was actually closer to.

**Fixed:** removed the layout margin entirely. Each key's touch target (the `KeyCapView`) now fills its *full* share of the row and column, so adjacent keys' touch targets meet exactly at the midpoint — no dead zone, and a touch that lands off-centre is no longer lost, it registers as whichever key it is actually closer to. The visible gap between keys (unchanged look) is now drawn *inside* `KeyCapView.onDraw` instead, via a small `visualInset` that shrinks only the drawn rounded rectangle, not the clickable bounds. Applied uniformly to every row (letters, symbols, the slim "extras" row for accented characters) since they all go through the same row-building code.

**Verification:** compiles clean, lint clean (22 pre-existing findings, none new), 775 of 775 unit tests pass (this is a pure layout/rendering change with no logic branch to unit test; no existing test asserted on key margins or pixel sizes). **Manually confirmed on the owner's phone**: the keyboard's visual appearance is unchanged (compared side by side with the previous build — same gaps, same look) while the underlying touch targets are now larger, edge to edge.

**Not changed:** the keys' own height/width (`KEY_HEIGHT_DP`, `EXTRAS_KEY_HEIGHT_DP`) — this fix reclaims the wasted margin space rather than growing the keyboard's total footprint, so the visible message area behind the keyboard is unaffected.

## 2026-09-23 — Keyboard keys made visibly bigger, not just easier to hit

**Owner feedback:** "it still look small" — after the previous fix (touch targets widened to fill the full row/column, meeting edge to edge), the keys looked exactly as before by design, since that fix only changed how much of the existing space registered a touch, not how big the keys are drawn. This is a different, valid follow-up ask: make the keys actually look bigger.

**Changed** in `AlterLinguaKeyboardView.kt` and `KeyCapView.kt`:
- Key row height: 42dp → 48dp (the slim accented-character row: 34dp → 40dp, keeping the same 8dp difference between the two).
- Letter label size: 20sp → 23sp; function-key label size: 14sp → 15sp; space bar's language-name label: 13sp → 14sp; key icons (shift, backspace, etc.): 22dp → 24dp.
- The drawn visual gap between keys (see the earlier fix's `visualInset`): 2dp → 1.5dp, so keys look a little more filled in without touching the underlying touch-target logic from the previous fix.

**Verification:** compiles clean, lint clean (22 pre-existing findings, none new), 775 of 775 unit tests pass (a rendering/sizing change with nothing new to unit test). **Manually confirmed on the owner's phone**, comparing a zoomed crop of the same QWERTY rows before and after: taller keys, visibly larger letters, tighter gaps.

**Trade-off, not hidden:** taller keys mean the keyboard takes a little more of the screen, leaving slightly less room for the message above it. This was not made configurable; if the owner wants a size choice later (e.g., a "compact" option), that would be a separate, deliberate Settings addition rather than assumed here.

## 2026-09-23 — Incoming translation widened beyond WhatsApp; AccessibilityService investigated and rejected

**Owner request:** translate incoming messages "in any android and chat screen" (Telegram, Messenger, WhatsApp, etc.), including auto-transcribing voice messages seen in the chat screen, gated by a Settings toggle.

**Split into three, since the request bundled things with very different feasibility:**

1. **Text from other chat apps, not just WhatsApp.** Buildable and safe — the existing WhatsApp-only `NotificationListenerService` architecture (Milestone 11) already separates "which apps are a source" (`IncomingSources`) from the extraction/translation logic, so this was a matter of widening the whitelist, not redesigning anything. Built now (see below).

2. **Auto-transcribing voice messages "in the chat screen," with no user action.** Not built, and not planned: Android sandboxes each app's private files, so AlterLingua has no way to reach another app's voice-note storage automatically, and there is no official API for it. The existing manual Share flow (Milestone 19) is the sanctioned equivalent and remains the only way to get a voice note translated.

3. **Live translation shown over/inside another app's chat bubbles.** Investigated in real time with the owner rather than assumed. First pass: proposed an `AccessibilityService`-driven overlay, then flagged it as risky and, on a closer read, initially over-stated the risk ("plainly doesn't qualify, would cause account termination") — the owner pushed back with a more precise read of Google's actual policy, which was then verified directly against Google's Play Console Help pages and a March 2026 report of Google's own accessibility-API enforcement tightening (both fetched live, not recalled from training data). The corrected picture: apps that are not accessibility tools *can* use `AccessibilityService`, provided they add an in-app disclosure (shown during normal use, requiring active consent) and complete Google's declaration form — it is not an automatic ban — but Google's own stated principle is to use the most narrowly-scoped API that accomplishes the goal, and non-accessibility uses have been targeted by a real 2026 enforcement tightening (30-day compliance deadlines, apps removed). Since `NotificationListenerService` already gets the same text without ever reading another app's screen, there was no reason to reach for the higher-risk, higher-scrutiny API. **Decision: not built.** If a more "live" feel than a notification is wanted later, a `SYSTEM_ALERT_WINDOW` floating overlay — fed by the same notification data, never by reading another app's UI — was identified as a separate, much lower-risk option, and left for its own explicitly-scoped milestone rather than folded into this one.

**Built (item 1):**
- `IncomingSources` (`notifications/IncomingModels.kt`): replaced the single `WHATSAPP`-only check with a small package→display-name map (WhatsApp, WhatsApp Business, Telegram, Messenger, Signal). `accepts()` now covers all of them; a new `displayNameOf()`/`displayNames` support the placeholder-title check below and future Settings copy.
- `NotificationExtractor`: the "app posted its own name as a placeholder title" hidden-content check (previously hardcoded to the string `"whatsapp"`) now looks up the actual source app's display name via `IncomingSources.displayNameOf(packageName)`, so the same protection now applies to Telegram/Messenger/Signal's own placeholder notifications, not just WhatsApp's.
- Settings copy (`strings.xml`, all 8 languages) and two onboarding/notification-channel strings: generalised from "Translate WhatsApp messages" / "It only reads the text WhatsApp shows…" to app-agnostic wording that names the four supported apps, since the existing `incomingTranslationEnabled` toggle now gates all of them together (one toggle, not four — kept simple; per-app toggles were not asked for and would be premature).
- No change was needed to `IncomingTranslator`, `AlterLinguaNotificationListener`, the Settings view model, or the DataStore schema — they already depended only on `IncomingSources.accepts()`, which is exactly the seam this was designed around.

**Known, already-handled limitation surfaced during this work:** Signal lets a user set notifications to show the sender's name only, with no message text. The extractor already treats a source app's own name as the title (a hidden-content placeholder) as `SkipReason.HIDDEN_CONTENT` — that same path now correctly covers Signal's "name only" mode, reported in Settings as nothing translated, never guessed at.

**Verification:** compiles clean. Updated/added unit tests: `NotificationExtractorTest` (`theDefaultSourceListCoversTheSupportedChatApps_andNothingElse` replaces the old WhatsApp-only assertion; new `aGenericPlaceholderUnderTheAppNameIsHidden_forEverySupportedApp` covers the placeholder-title check across all four apps) — 22 of 22 pass. Full existing suites re-run and still green: `IncomingTranslatorTest` 25/25, `IncomingSettingsTest` 6/6, full `testDebugUnitTest` suite green. All 8 locale `strings.xml` files re-parsed as valid XML after hand-editing. **Not yet manually verified on the owner's phone with a real Telegram, Messenger or Signal message** — that needs those apps installed and a real message sent, the same way WhatsApp was verified for Milestone 11.

**Manual test for the owner:** with Telegram, Messenger or Signal installed and its notifications enabled (message preview on, not "name only"), Settings → "Translate incoming messages" on, send yourself (or have someone send you) a message in your learning language from one of those apps. AlterLingua should post its own quiet "Translated from …" notification next to the app's own, exactly as it already does for WhatsApp.

## 2026-09-23 — Floating translation bubble (SYSTEM_ALERT_WINDOW, not AccessibilityService)

**Follow-up to the previous entry.** After investigating and rejecting an `AccessibilityService`-based live overlay, the owner asked for the lower-risk alternative that had been identified: a floating translation bubble fed by the same data the incoming-notification pipeline already produces, using Android's separate `SYSTEM_ALERT_WINDOW` / "Display over other apps" mechanism instead. Verified this really is a separate mechanism (not a restricted subset of `AccessibilityService`) before building it.

**Built:**
- `notifications/CompositeTranslationPresenter.kt`: a `TranslationPresenter` that forwards to a list of delegate presenters (`canPost()` true if any can; `show()`/`remove()` reach all of them), so `IncomingTranslator` did not need to change at all — it already only knew about one `TranslationPresenter`.
- `notifications/FloatingBubblePresenter.kt`: on `show()`, draws a small rounded card (sender + translated text, "Translated from …", reusing the keyboard's own `KeyboardColors` for a consistent look) as a `TYPE_APPLICATION_OVERLAY` window near the top of the screen, auto-dismissing after 8 seconds or when the original notification is removed; tapping it runs the same "open this chat" `PendingIntent` the notification uses. `canPost()` requires both the new setting and `Settings.canDrawOverlays()`, checked again just before drawing in case either changed between the check and the post.
- `AlterLinguaApplication.kt`: `incomingTranslator`'s presenter is now `CompositeTranslationPresenter(listOf(TranslatedNotificationPresenter(...), FloatingBubblePresenter(...)))`; a new `@Volatile floatingTranslationEnabledNow` mirrors the existing `appLanguageNow` pattern, since the presenter's `canPost()` must be callable synchronously.
- New setting `floatingTranslationEnabled: Boolean = false` (off by default — this permission is more sensitive than notification access, and the bubble is an addition on top of the existing notification, not a replacement for it), wired through `UserSettings`, `SettingsKeys`, `SettingsViewModel`, and a new "Show a floating translation" switch in the Settings "Incoming messages" card.
- `SetupChecker.overlayPermissionGranted()` (`Settings.canDrawOverlays`), added to `SetupStatus`/`SetupViewModel` alongside the existing keyboard/notification/microphone checks, and `SystemSettings.overlayPermission()` (`ACTION_MANAGE_OVERLAY_PERMISSION`) for the "open settings" button — same established pattern as every other permission in Setup, not a one-off.
- Manifest: added `SYSTEM_ALERT_WINDOW`, documented inline as opt-in and unrelated to reading any other app's screen.

**Verification:** compiles clean, Android lint clean (24 findings, all pre-existing categories — the one new suggestion my own code triggered, `Uri.parse` → `.toUri()`, was fixed rather than left). 784 of 784 unit tests pass, including: 5 new for `CompositeTranslationPresenter` (delegates called correctly, `canPost` is true if any delegate can), 1 new for the Settings toggle, 1 new for `SetupViewModel` reading the overlay permission from Android. `FloatingBubblePresenter` itself has no direct unit test, matching the existing `TranslatedNotificationPresenter` (also untested directly) — both are thin wrappers around real Android framework calls (`WindowManager`, `Settings.canDrawOverlays`) that need a real device, not a JVM test.

**Privacy-audit tests updated, not bypassed:** `PrivacyAuditTest.onlyTheNecessaryPermissionsAreRequested` now expects `SYSTEM_ALERT_WINDOW` in the manifest's permission whitelist (a deliberate, reviewed addition — the test would otherwise have caught an unreviewed permission creeping in, which is exactly its job). A new `noAccessibilityServiceIsDeclared` test asserts the manifest never binds `BIND_ACCESSIBILITY_SERVICE`, as a permanent regression guard for the decision made in the previous entry.

**Not yet manually verified on the owner's phone.** This needs a real device: granting "Display over other apps", receiving a translated message while a supported chat app is in the foreground, confirming the bubble appears without blocking typing or the AlterLingua keyboard, confirming it auto-dismisses, and confirming tapping it opens the right chat.

**Manual test for the owner:** Settings → turn on "Show a floating translation" → tap "App settings" when prompted and allow "Display over other apps" for AlterLingua → receive (or send yourself, via the existing debug test-message broadcast) a message in a supported chat app → a small translated card should appear near the top of the screen for a few seconds, on top of whatever app is open, and tapping it should open that chat.
