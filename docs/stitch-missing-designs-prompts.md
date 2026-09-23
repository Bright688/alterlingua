# Stitch prompts for the missing and partial designs

Project: **AlterLingua UI/UX System** (`312001149311948663`). Design system: **AlterLingua Design System**.
Origin: `docs/design-audit.md` section 3 (missing designs and partial designs).

Paste each prompt into Stitch as a new screen. Every prompt already follows CLAUDE.md:
no auto-send, no "zero-log / on-device" claims, no provider names, no prices, no streaks or XP,
and AlterLingua shows its **own** notification (never one injected into WhatsApp).

Common prefix for every prompt: *"Use the AlterLingua Design System. Native Android, light theme, primary #2563EB,
Plus Jakarta Sans and Inter, calm minimal premium. Sample user Alex, contact Marie, message
'Are you coming tomorrow?' → 'Tu viens demain ?'."*

## Missing

**1. D/K3 — Keyboard translating + error states**
Tall sheet with four labelled keyboard states over a WhatsApp-style chat (Marie: "Salut ! Tu viens demain ?"; composer holds
"Are you coming tomorrow?"): (a) TRANSLATING, a small loading pill "Translating to French..." with thin progress bar and
"Cancel", keys still usable; (b) ERROR, amber banner "Couldn't translate." with "Retry"; (c) OFFLINE, "You're offline.
Translation needs a connection." with "Retry" and "You can keep typing normally." (no offline-engine wording);
(d) SLOW, "Translation is taking longer than usual." with "Keep waiting" / "Cancel". Composer text is unchanged in all four.

**2. Error and empty states**
Sheet with: speech error "I couldn't understand that. [Try again]" with editable partial transcript; voice-note failure;
hidden-preview and missing-text notification states; empty Home (no lessons yet), empty Words (nothing learned yet),
empty Learn ("No lesson today"); lesson complete summary.

## Partial

**3. Onboarding as a step flow (Step 1–6 of 6, one counter everywhere)**
Eight phone screens: Welcome; Languages (English → French, goal chips, level); Assistance mode (Full Support / Adaptive /
On-demand cards); Daily reminder; Turn on keyboard (Enable button opens Android keyboard settings, Switch button opens the
keyboard picker, live status pills); Incoming translation (optional, with plain disclosure "AlterLingua reads WhatsApp
notification text only to translate it. Text is sent securely to translate and is not kept."; "Skip for now"; note that
WhatsApp may hide previews); Microphone (rationale plus a separate system-style dialog "While using the app / Only this time /
Don't allow"); All set (checklist and a "permission revoked — Fix now" re-entry banner). No payment, no SMS/Slack/Mail.

**4. Voice result with edit + voice errors**
Editable "You said (English)" and "French translation" fields, "Re-translate" link, buttons Cancel / Again / Listen /
**Insert as text**, caption "Nothing is inserted until you tap Insert. AlterLingua never sends the message." Add recording,
processing ("Understanding your message..."), speech-error, and microphone-off states. The microphone-off state says
"Open AlterLingua to allow" (permission is requested in a full-screen app screen, not inside the keyboard).
Copy: "Voice is sent securely for translation and deleted after processing." No "Whisper".

**5. Incoming translation — AlterLingua's own notification**
Show WhatsApp's original notification and, separately, AlterLingua's own notification below it: "Marie — Are you coming
tomorrow? — Translated from French" with actions "View original" (opens WhatsApp) and "Copy translation". No quick-reply
that sends. States: normal, preview hidden by WhatsApp, notification access off (in-app banner "Turn on"), grouped
conversation.

**6. Assistance modes working (Full Support / Adaptive / On-demand)**
Three states of the same incoming message shown in AlterLingua's own notification and translation screen (never inside
WhatsApp): Full Support (full English), Adaptive (known French words kept and marked, unknown ones translated, with a short
"why" note), On-demand (original French with a "Translate" button that opens the AlterLingua screen).

**7. Incoming voice note (share flow)**
Android share sheet with AlterLingua as a target; then processing ("Transcribing", "Translating") with Cancel; result
(French transcript, English translation, "Listen to translation", extracted words with Learning/Familiar chips);
error ("I couldn't understand that. Try again"); unsupported/too-long audio. Use single-file share wording, no provider name.

**8. Privacy and data screen (accurate)**
Data-flow diagram: Your device → AlterLingua servers (text and audio are sent to translate and transcribe, then discarded;
audio deleted after processing) → what stays: words, counts and status only. Toggles: Translate notifications, Learn from
translations. Actions: Export learning data, Delete all learning data (with confirmation), Analytics opt-out. No "zero-log",
"on-device", "volatile RAM" or "edge" claims. No stored chat snippets or contact names.

**9. Learn: recall check and phrase item**
Recognition question ("What does devis mean?" with 3 choices), correct/incorrect feedback, a phrase item
("je vous tiens au courant"), and lesson-complete summary. No XP or streak.

**10. Keyboard: French accents and modifier states**
Long-press accent popup on e (é è ê ë), a (à â), c (ç); Shift, Caps-lock and pressed key states; light and dark theme using
the design system dark tokens (#0B0F19 / #131B2E / #1E293B / #2563EB).

**11. Language pair settings**
App Settings screen to change native and target language and direction (English ↔ French), with a note "More languages
coming later".

## Also fix in existing screens (edits, not new screens)

- Onboarding sheet: replace "Step 1/3" and "Step 4 of 6" with one counter.
- Data totals: one consistent set of numbers across Home, Words, Progress and the plan screens.
- Replace "Zero-log / on-device / Edge Whisper / zero server storage" copy everywhere.
- Remove chat snippets and contact names from Words and Word Detail; remove Slack/Mail/Teams/SMS.
- Remove streaks and XP; remove live/ghost preview and hard-coded prices.
