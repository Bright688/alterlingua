# Disabled: voice-note capture from any chat app

**Status: built, then switched off by the project owner (2026).** Not deleted — every file listed below still
exists and still compiles. This document exists so a future developer can decide whether to revive it, reuse
parts of it, or leave it alone, without having to rediscover any of this by reading diffs.

For the full back-and-forth that led here — every recommendation, every round of correction, and why the
owner decided to disable it — see `docs/conversation.md` (sections 2–8) and `docs/build-log.md`'s entries
from late September 2026. This file is the short, practical summary: what exists, where it lives, what was
removed, and how to bring it back.

---

## What the feature was

A keyboard-triggered and listening-session-based way to capture a voice note played in any chat app
(WhatsApp, Telegram, Messenger, Signal, Instagram, Viber, Line, Discord), using Android's
`AudioPlaybackCapture` API, as an alternative to the user manually sharing the voice note to AlterLingua.
It included:

- A keyboard toolbar button to capture one voice note from the app currently being typed into.
- A "listening session" (started from onboarding or Settings) that captured every voice note from chosen
  apps automatically until Android ended the session or the user stopped it.
- Automatic transcription and translation when a note finished, shown on the keyboard.
- A "Voice note captured" notification, with a Settings switch to mute it.

## Why it was disabled

The project owner decided, after trying it, to go back to the existing **Share-to-AlterLingua** flow
(`share/ShareVoiceActivity` and everything under `share/`) as the only way to get a voice note transcribed
and translated. That flow was never touched by any of this work and needs nothing further to keep working.

The practical reasons that made the owner's choice reasonable, for whoever reads this later:

- Android requires its own screen-capture consent dialog for **every** listening session on Android 14+; it
  cannot be skipped, pre-answered, or narrowed to hide the "Entire screen" option (see
  `docs/conversation.md` §4 and §7). The feature could never become fully frictionless — that is a
  platform-level limit, not a code bug.
- Google Play requires declaring the `mediaProjection` foreground-service type and reviews that declaration;
  this was never obtained.
- The notification behaviour went through six rounds of correction before it matched what was actually
  wanted (`docs/conversation.md` §8) — a sign the feature carried more day-to-day friction than its value.

## What was removed, and what was kept

**Removed** (so the shipped app requests none of this and shows no trace of it):

- `android/app/src/main/AndroidManifest.xml`: the `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PROJECTION`
  permissions, the `<queries>` block of nine chat-app package names, and the three component declarations
  (`.capture.VoiceCaptureActivity`, `.capture.VoiceCaptureResultActivity`, `.capture.VoiceCaptureService`).
- The keyboard toolbar button ("capture a voice note" icon) in `keyboard/AlterLinguaKeyboardView.kt`.
- The onboarding step "Voice notes from other apps": still exists as `OnboardingStep.VOICE_NOTES` in the
  enum (so its ordinal position is stable), but is unconditionally filtered out of
  `OnboardingUiState.steps` in `ui/onboarding/OnboardingViewModel.kt`, so it is never shown or counted.
- The Settings section "Voice notes from chat apps": the call to `VoiceNotesSection(...)` was removed from
  `ui/settings/SettingsScreen.kt`; the composable function itself is still there, just unused.

**Kept, compiling, unit-tested, but unreachable from any UI:**

- The whole `android/app/src/main/java/com/alterlingua/app/capture/` package: `VoiceNoteRecorder` (the
  silence/sound detector and resampler), `VoiceCaptureService`, `VoiceCaptureActivity`,
  `VoiceCaptureResultActivity`, `CapturedAudio.kt` (`WavFile`, `CapturedAudioSource`), `CapturedNotes.kt`,
  `CapturedNoteNotifier.kt`, `CapturedNoteAlerts.kt`, `CapturedNoteViewModels.kt`, `ChatApps.kt`
  (`ChatApp`, `KnownChatApps`, `CaptureRequest`, `VoiceCaptureLauncher`, `VoiceCaptureState`).
  `VoiceCaptureService` carries a `@SuppressLint("ForegroundServiceType")` with a comment explaining why:
  lint would otherwise (correctly) flag that its `startForeground()` call no longer matches a manifest
  declaration, because that declaration was removed.
- `keyboard/CapturedNotePanel.kt` and the keyboard view/service's wiring to show it
  (`AlterLinguaKeyboardView.renderCapturedNote`, `AlterLinguaKeyboardService.watchCapturedNotes` /
  `onCapturedNoteAction`) — harmless to leave: nothing can ever populate `AlterLinguaApplication.capturedNotes`
  now, so this panel will always render `CapturedNoteUi.Hidden`.
  `ToolbarAction.VoiceNote` / `ToolbarEvent.VoiceNote` / `ToolbarController.onVoiceNote()` — also kept, also
  unreachable from any button, so `AlterLinguaKeyboardService`'s existing dispatch code needed no change.
- `ui/onboarding/SetupSteps.kt`'s `VoiceNotesStep` composable, and `OnboardingViewModel.onVoiceCaptureAppToggled`.
- `ui/settings/SettingsScreen.kt`'s `VoiceNotesSection` composable, and `SettingsViewModel`'s
  `onVoiceCaptureAppToggled` / `onTranslateCapturedNotesChanged` / `onNotifyOnCapturedNotesChanged`.
- The settings fields themselves: `UserSettings.voiceCaptureApps`, `.translateCapturedNotes`,
  `.notifyOnCapturedNotes` (all with harmless defaults; nothing reads or writes them now except the unused
  composables above).
- `android/app/src/main/res/drawable/ic_tool_voicenote.xml` and the `toolbar_voice_note`, `vc_*`, `kbn_*`,
  `capture_*` string resources (in all 8 languages) — unused, but left rather than hunted down and deleted
  one by one.
- Unit tests: `capture/VoiceNoteRecorderTest.kt`, `capture/ChatAppsTest.kt`, `capture/CapturedNotesTest.kt`,
  `capture/CapturedNoteAlertsTest.kt`, and the `ToolbarControllerTest` case for `onVoiceNote()`. These test
  the pure logic classes directly (bypassing the UI), so they still compile and still pass; they prove the
  underlying mechanism still works correctly, even though nothing in the shipped app can reach it.
- `android/app/src/debug/java/com/alterlingua/app/capture/DebugCapturedNoteReceiver.kt` (debug builds only):
  fires the "Voice note captured" notification through `adb` without a real voice note. Still works in debug
  builds, since debug builds don't touch the capture package's compiled code either way. Left as a reference
  for how to drive the notification logic directly if it is ever revived.

**Separately removed already, before the disable decision:** the earlier *feasibility-test* screen
("AlterLingua capture test", `src/debug/.../capturetest/`) was deleted outright back when the real feature
replaced it (see `docs/build-log.md`, 2026-09-26). That is a different, smaller thing from everything listed
above and has no trace left anywhere.

## How to bring it back

1. Restore the manifest entries removed above (permissions, `<queries>`, the three components) — they are
   preserved verbatim in git history on this commit's parent, or can be rewritten from the component
   doc-comments still present in the Kotlin files.
2. Remove the `it != OnboardingStep.VOICE_NOTES` filter in `OnboardingUiState.steps`
   (`ui/onboarding/OnboardingViewModel.kt`) and add `OnboardingStep.VOICE_NOTES -> VoiceNotesStep(...)` back
   if it was ever removed from the `when` in `OnboardingScreen.kt` (it was not, in this version).
3. Add the keyboard toolbar button back in `AlterLinguaKeyboardView.createToolbar()` (a `ToolbarButtonView`
   dispatching `ToolbarAction.VoiceNote`, using the still-present `ic_tool_voicenote` drawable and
   `toolbar_voice_note` string) and re-add its `addView(...)` call in the toolbar row.
4. Add the `VoiceNotesSection(...)` call back into `SettingsScreen.kt`'s main column.
5. Remove the `@SuppressLint("ForegroundServiceType")` on `VoiceCaptureService` once the manifest entry (with
   `android:foregroundServiceType="mediaProjection"`) is back — the annotation is only there to keep lint
   quiet about a mismatch this restores.
6. Build, test, lint, and — before shipping — get the Google Play foreground-service-type declaration and
   `mediaProjection` policy review the owner never obtained (CLAUDE.md §39).
7. Re-read `docs/conversation.md` first: it records several rounds of the owner changing their mind about
   notification behaviour, and the Android platform limits (§4, §7) that any revival still has to live with.
