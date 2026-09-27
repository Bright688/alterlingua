# Voice message capture — recommendations and decisions (conversation record)

This file records, in order, the recommendations given, the questions asked, and the decisions made while
building AlterLingua's "capture a voice note from any chat app" feature. It complements `docs/build-log.md`
(what was built and verified) by keeping the reasoning and back-and-forth in one readable place.

---

## 1. The starting question

The owner asked whether it is possible to share a voice message from a chat app (for example WhatsApp)
directly to the AlterLingua keyboard used for typing, so it could be transcribed and translated, instead of
using the separate Share flow.

**Recommendation given:** a keyboard cannot "listen" to another app's screen for a voice note by itself.
Two real options exist:

1. **A small panel over the chat**, opened deliberately by the user (not automatic), that offers a Listen
   / Translate action.
2. **Android's screen/audio capture API (`AudioPlaybackCapture`)**, which can record the *sound* another
   app plays — including a voice note — if the user approves it.

**What was recommended against**, and why:
- Having the keyboard silently watch the screen for voice notes — no such passive capability exists on
  Android without accessibility/overlay permissions, and doing it invisibly would be a privacy problem.
- Reaching into WhatsApp's own storage folder to grab the audio file directly — blocked by Android 11+
  scoped storage for other apps' files, and explicitly forbidden by the project's own rules (CLAUDE.md
  §22: never access WhatsApp's private storage, never reverse-engineer WhatsApp).

The owner then asked to elaborate on both options, and separately noted: **"remember not only WhatsApp
voice note but any other chat messenger voice note"** — the feature was never meant to be WhatsApp-only.

---

## 2. `AudioPlaybackCapture`, elaborated

Checked against Android's official documentation (not from memory) before recommending it:

- Available from Android 10 (API 29).
- Requires the microphone permission (`RECORD_AUDIO`) to read the captured audio, even though the
  microphone itself is never used.
- Requires the user's approval of Android's own screen-capture prompt — and on Android 14+, this consent
  is required **for every capture session**; the approval token is single-use and cannot be cached or
  replayed. Attempting to reuse it throws a `SecurityException`.
- Requires a foreground service of type `mediaProjection` (`FOREGROUND_SERVICE` +
  `FOREGROUND_SERVICE_MEDIA_PROJECTION` manifest permissions).
- Only three playback "usages" can ever be captured by another app: `USAGE_MEDIA`, `USAGE_GAME`,
  `USAGE_UNKNOWN`. Calls and "voice communication" audio cannot be captured.
- The app being captured can opt out entirely (`ALLOW_CAPTURE_BY_NONE` / `ALLOW_CAPTURE_BY_SYSTEM`); apps
  built for Android 10+ allow capture by default unless they explicitly opt out.
- Capture can be limited to one chosen app by Android user id (`addMatchingUid`), which is what makes "any
  chat app, not the whole phone" possible.
- The session ends automatically if the screen locks, another projection starts, the user stops it from
  the system status-bar/Quick Settings indicator, or the app process is killed.
- Android 14+ also shows the user a system chooser between **"A single app"** and **"Entire screen"** every
  time consent is requested; an app cannot remove that choice or pre-select an answer (see §7 below).

**Recommendation given at this point:** build a small, honest **feasibility test** first — a debug-only
screen that asks for consent, listens for a chosen app's sound, and reports plainly whether anything was
heard and why — before committing to building the real feature. This was explicitly framed as *not*
touching the existing "share a voice note to AlterLingua" flow.

The owner agreed ("ok do it but dont touch the feature we had before"), and the test was built, installed,
and run on the owner's phone.

**Result of the test (2026-09-26):** WhatsApp's voice-note playback *is* capturable on the owner's device —
Android reported the sound as `USAGE_UNKNOWN` (a capturable kind) with capture policy
`ALLOW_CAPTURE_BY_ALL`. The owner then said: **"ok it worked so implement."**

---

## 3. The real feature: what was recommended and built

Once feasibility was confirmed, the recommended design (kept deliberately separate from the Share flow) was:

- A **new keyboard toolbar button** ("capture a voice note"), next to the microphone.
- Tapping it opens an explanation screen, then — only after the user taps Start — asks in order for:
  notifications, the microphone permission (needed to read captured sound), and Android's screen-capture
  approval.
- A foreground **capture service** records just the one chat app's sound, using the same
  `AudioPlaybackCapture` mechanism proven in the test, limited to that app's Android user id.
- A **recorder** that waits for real sound, starts a little before it, and ends after a natural pause —
  so a whole voice note is captured as one clean recording, not raw continuous audio.
- The recording is handed to the **same processing a shared voice note already uses** (transcribe →
  translate), so there is exactly one pipeline for voice notes, not two.
- Nothing under `share/` (the existing Share-to-AlterLingua flow) was touched at any point, per the
  owner's explicit instruction.

This was implemented, unit-tested, and installed on the phone.

---

## 4. "Approve once forever" — explained as not possible

The owner then asked for the capture to work with **one approval, reused automatically afterwards**, chosen
during onboarding for whichever apps are needed, changeable later in Settings.

**What was explained, checked against Android's documentation again before answering:** Android does not
allow this. For apps targeting Android 14+, the consent dialog must be shown, and approved, before *every*
capture session; the approval cannot be cached, stored, or replayed programmatically — doing so throws a
`SecurityException` by design. Building a workaround (for example, an accessibility service tapping the
consent dialog's buttons on the user's behalf) would mean acting on another app's screen without the
user's own touch, which the project's own rules explicitly forbid (CLAUDE.md §37: never bypass Android
security; never pretend a technical impossibility can be implemented anyway).

**The closest permitted design, recommended and built instead — a listening session:**
- One approval starts a **listening session** covering the apps the user chose (in onboarding or Settings).
- While the session runs, *every* voice note those apps play is captured automatically, with no further
  tap per note.
- The approval is asked again only when the session itself ends (screen lock, the user stopping it from
  Android's own indicator, etc.) — and then it takes one tap on a notification plus one more approval to
  restart it, rather than a full walk back through the setup screen.
- A dedicated onboarding step, "Voice notes from other apps", and a matching Settings section let the user
  choose which chat apps are covered, independent of the choice made during onboarding.

This trade-off — automatic within a session, but never permanently automatic — was written into
`docs/build-log.md` and into a standing memory (`android-mediaprojection-consent.md`) precisely so this
platform limit would not need re-explaining or re-litigating in a future session.

---

## 5. Getting the whole voice note without waiting for it to finish playing

The owner then asked whether the *complete* audio could be captured even if playback is stopped partway
through — i.e., grab the full file the moment Play is tapped, rather than only what has played so far.

**What was explained:**
- Detecting the tap on WhatsApp's own Play button would require watching taps inside another app via the
  accessibility service already used for live captions — technically possible, but fragile (breaks
  whenever WhatsApp changes its UI) and a materially more sensitive use of that permission than reading
  message text, which would need its own §39-style review before being built.
- Reaching into WhatsApp's storage to grab the underlying `.opus`/`.ogg` file directly remains forbidden
  by both Android's scoped storage rules and CLAUDE.md §22.
- The **existing Share flow already solves this**: sharing a voice note to AlterLingua hands over the
  complete file immediately, with no need to play it at all.
- The listening session, by contrast, can only ever capture audio *as it plays* — it cannot get ahead of
  the playback. Recommendation given: play the note at faster speed if a quicker capture is wanted, or use
  Share when the complete file is needed immediately.

**Recommendation for what to build next**, offered as two options:

1. **Show the result on the keyboard** once a captured note finishes and is translated — a small addition,
   consistent with how the app already works.
2. **Translate while it plays** (piece by piece, opt-in) — explained in detail on request: it would trail
   the voice by roughly 4–8 seconds per piece, reduce transcription accuracy (short pieces carry less
   context than a whole sentence), consume the free speech/translation quotas much faster, only ever be
   visible while the keyboard is open, and — critically — would mean uploading audio *before* the user
   opens or asks for it, reversing the "nothing is sent until you ask" privacy stance the capture feature
   had held until then.

**Recommendation given:** build option 1 first; treat option 2 as worth adding only if option 1, tried in
practice, turns out to be too slow.

The owner chose: **"so you do all at once and show on keyboard rather piece by piece"** — i.e., process
the whole note in one request when it ends, and show the result on the keyboard. This was built.

---

## 6. Consequence of that choice, stated plainly

Showing the translation on the keyboard the moment a note ends means the recording has to be uploaded
*before* the user opens anything — which is exactly the privacy trade-off flagged above. This reversal
of the earlier "nothing sent until opened" rule was written explicitly into the on-screen text (all 8
languages) and into `docs/privacy.md`, together with the plainly stated cost: any sound of about a second
or more that a listened-to app plays — a video with speech, for example — is also transcribed while this
is switched on, which uses the free quotas faster. A Settings switch ("Translate voice notes when they
end") was added so the owner (or a future user) can turn this back off.

---

## 7. The "single app / entire screen" consent dialog

The owner asked, having seen the dialog on the phone, whether AlterLingua could (a) skip straight to
listening to the one already-chosen app with no chooser shown, and (b) remove the "Entire screen" option
entirely, calling it a privacy breach risk.

**What was explained, checked against Android's `MediaProjectionConfig` documentation:**
- An app can pass `createConfigForUserChoice()` (the default, and what AlterLingua already used — same
  dialog as calling `createScreenCaptureIntent()` plainly) or `createConfigForDefaultDisplay()`, which
  *restricts* the user to capturing the **entire display** — the opposite of narrowing to one app.
- There is no public API to pre-select an app or hide the "Entire screen" choice; the only related getter,
  `getInitiallySelectedSource()`, was added in API level 37 — newer than the owner's phone (Android 15).
- Automating the dialog's buttons on the user's behalf would again require an accessibility service acting
  on system UI without the user's own tap, which is exactly what CLAUDE.md §37 rules out.

**What was confirmed and communicated as reassurance:** AlterLingua's code contains no path that creates a
virtual display, image reader, or video recorder — the projection is used solely to build an
audio-playback capture limited to media/game/unknown sound from the chosen apps' user ids. Choosing "Entire
screen" in Android's dialog therefore cannot expose the screen itself to AlterLingua; the warning text in
that system dialog is Android's own generic wording, not specific to what AlterLingua actually does. This
explanation was added to the in-app text shown just before the dialog, in all 8 languages.

The owner then explicitly decided: **"keep the listening feature with android dialog."**

---

## 8. Notification behaviour — several rounds of correction

This part went through more back-and-forth than any other, and is recorded in full because each round
corrected a specific misunderstanding.

**Round 1.** With the "translate on end" feature first built, a "Voice note captured" notification was
posted for every captured note. The owner reported it as **disturbing**, appearing as a pop-up for every
voice note. First fix: only post it when automatic translation was switched *off* (when it is the only way
to open a waiting note).

**Round 2.** The owner then said they didn't want to see it **at all**, ever. It was removed entirely, and
— going beyond what was asked — a "Translate" button was added to the keyboard for the case where automatic
translation is off, so a note could still be opened without any notification at all.

**Round 3.** The owner reconsidered: **"place back the notification but add to the setting for users to be
the one to decide to mute the notification or not."** A Settings switch, "Notify me when a voice note is
captured", was added (default on), and the notification itself was made *quiet* (low-importance channel,
no pop-up, no sound) on the reasoning that "quiet" would satisfy "not disturbing."

**Round 4 — a correction the owner had to point out explicitly:** *"this is not what i said you should
place... i only said add to setting where they can mute voice captured notifications meaning after the
voice is captured and transcribed and translated they wont get any notification and its sound."* Two
mistakes were acknowledged and fixed:
- The unrequested "Translate" button and "delete the waiting recording on close" behaviour were removed —
  they had never been asked for.
- The notification's timing was corrected to fire **after** transcription and translation finish (not at
  the moment of capture, as the interim version did), matching what was actually requested.

**Round 5 — the switch's own logic was still wrong:** the owner reported that turning the switch **on**
produced no notification, sound, or pop-up at all. The bug: the notification had been made silent (no
pop-up, no sound) unconditionally, regardless of the switch. It was rebuilt so that **on = a normal
notification with sound and a pop-up**, and **off = nothing at all** — matching the owner's plain mental
model of a mute switch, rather than the "quiet vs silent" distinction that had been invented instead.

**Round 6 — verifying the fix actually reached the phone.** The owner reported "same problem" after the
supposed fix. Investigation (notification dump, service state, server request log) showed the real cause:
installing the update had ended the running listening session, and no voice note had been captured since,
so the new notification code had simply never executed — the notification channel for it didn't even exist
yet on the phone. A debug-only broadcast receiver (`DebugCapturedNoteReceiver`) was added so the
notification's on/off behaviour could be fired and confirmed directly from `adb`, independent of actually
playing a voice note, and the fix was confirmed working via Android's own notification dump (posted on the
correct channel, at high importance, default sound) before reporting it back to the owner.

**Lesson carried forward:** don't report "fixed" on a change that depends on a running service/session
without checking that the service/session was actually restarted after the reinstall.

---

## 9. Two smaller items along the way

- **Second launcher icon.** The owner noticed a second "AlterLingua capture test" icon on the phone —
  the debug-only feasibility test from §2, left behind after the real feature replaced it. It, its test
  suite, and its debug-manifest entries were fully removed once the real feature existed, restoring a
  single AlterLingua icon.
- **Reinstall wipes state.** Several times during this work, reinstalling the debug build was found to
  have removed the app (or reset onboarding/permissions) between sessions — worth remembering when a
  report of "it doesn't work" might really mean "the update was never actually running."

---

## 10. Where things stand

- Branch: `remove-floating-bubble` (not merged into `main`).
- The keyboard-triggered single capture, the multi-app listening session (onboarding + Settings), the
  "translate on end" default with its Settings switch, and the corrected "Voice note captured" notification
  are all implemented and unit-tested; the Share-to-AlterLingua flow (`share/`) was never modified.
- Still open before this can ship: Google Play's foreground-service-type declaration and its
  `mediaProjection` policy review (owner's Play Console, not something this session can do); a real,
  on-device end-to-end run of a captured note reaching the corrected notification (the debug trigger only
  confirmed the notification path itself, not the full capture → upload → translate chain); and how well
  Telegram, Messenger and other non-WhatsApp apps behave under capture, which each app decides for itself.
