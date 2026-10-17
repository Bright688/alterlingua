# AlterLingua — Entitlements

Status: **real billing code exists (Google Play Billing Library), but no purchase can actually complete yet** —
the subscription product IDs it uses do not exist in Google Play Console. This document describes what exists
today and what is deliberately deferred.

## Why this exists now

The owner asked for standard-curriculum lessons (see `docs/progress.md`, Milestone 26) to have "a standard and
premium lessons that would be paid for but they would have trial with limited number to learn." Building that
honestly — a real trial count, a real gate, a real upgrade prompt — needed *some* entitlement concept from the
start, even before payments themselves. The owner later corrected a single merged "Standard & Premium" button and
a "Coming soon" placeholder directly: Standard and Premium must be two separate cards, each with its own real
Google Play Billing (`docs/build-log.md`, 2026-10-11). That entry is what made `Tier` settable by something other
than the owner manually editing a setting.

## The model (CLAUDE.md section 26-27)

```
Tier              FREE / STANDARD / PREMIUM     (UserSettings.tier, default FREE)
Feature           a capability a tier may unlock (CURRICULUM_UNLIMITED_TRIAL, CURRICULUM_PREMIUM)
TierPolicy        the one place deciding which tier unlocks which feature
EntitlementService     answers questions ("can this language's curriculum still be learned from?", "what's the daily cap?",
                       "how many AI-conversation minutes/day?", "what pronunciation-feedback depth?", ...)
AssistanceLevel   a shared NONE/LIMITED/BASIC/STANDARD/ADVANCED/EXTENDED richness label, graded per tier, for
                  several AI-assistance dimensions from the owner's own tier comparison table (policy only --
                  see "What is not real yet")
BillingManager    talks to Google Play; turns a completed purchase into a Tier (PlayBillingManager)
SubscriptionProducts   the four Play product IDs and their mapping back to a Tier
```

`Tier` and `Feature` are kept apart on purpose, per CLAUDE.md's own instruction not to scatter `isPremium` checks:
a feature's gate is a policy decision in `TierPolicy`, not a tier check wherever the feature happens to be used.

## What is real right now

- `UserSettings.tier` is saved and read back like any other setting.
- `EntitlementService.curriculumAccess(languageCode)` is a real, tested gate: a FREE user is stopped at
  `FREE_CURRICULUM_TRIAL_WORDS` (50) words learned *in that language's* curriculum — the limit is per language, not
  shared, so a user learning two languages gets a full trial in each (CLAUDE.md section 6.21).
- `STANDARD` and `PREMIUM` both unlock `CURRICULUM_UNLIMITED_TRIAL`. `PREMIUM` additionally unlocks
  `CURRICULUM_PREMIUM`: every learning language's curriculum at once (not just the active target language), and a
  much higher daily-word cap than Standard's.
- `Feature.IMAGE_TRANSLATION` (added 2026-10-16, see `docs/build-log.md`): `STANDARD` and `PREMIUM` both unlock it;
  `FREE` gets **no trial at all** (unlike the curriculum's 50-word trial) — an immediate upgrade prompt instead.
  Gates the Share-sheet image-translation flow (`SharedImageViewModel`): share a photo or screenshot to
  AlterLingua, it is OCR'd on the phone (ML Kit), and the extracted text is translated through the existing
  `/v1/translate` endpoint.
- `Feature.DOCUMENT_TRANSLATION` (added 2026-10-16, same day, see `docs/build-log.md`'s follow-up entry):
  **`PREMIUM` only** — unlike image translation, `STANDARD` does not unlock this, per the owner's own original
  split ("image translation under Standard, PDF/DOCX file translation under Premium"). No trial, same pattern as
  image translation. Gates the Share-sheet document-translation flow (`SharedDocumentViewModel`): share a PDF or
  Word (`.docx`) file, it is uploaded to the backend's `POST /v1/documents/translate`, and a real translated file
  of the same format comes back, which the user can save or share through Android's own Share sheet. Legacy
  binary `.doc` is not supported, only the modern `.docx` format.
- The keyboard's "give this translation a voice and share it" button (on-device text-to-speech, no backend call,
  then Android's Share sheet) is gated by a **graded monthly cap per tier, not a `Feature`/`TierPolicy` entry** —
  every tier has some real access, so there is no tier to "unlock" it. `EntitlementService.ttsShareLimitPerMonth`
  holds the owner's own numbers (corrected twice, most recently 2026-10-17): `FREE_TTS_SHARES_PER_MONTH=5`,
  `STANDARD_TTS_SHARES_PER_MONTH=30`, and **Premium is genuinely unlimited** (`null`, not a high finite cap).
  `TtsShareUsageService` is the app's first real usage quota (a resettable monthly counter). **Manually verified
  live on the owner's phone** (2026-10-17): translate, tap Voice, on-device TTS produces a real audio file,
  Android's Share sheet hands it to WhatsApp — see `docs/build-log.md` for the full device-test account, including
  a real manual-testing gotcha
  found along the way (not a code bug).
- `PlayBillingManager` is a real `BillingClient` wrapper: it queries Google Play's real catalog for the four
  `SubscriptionProducts` IDs, exposes what it finds as `offers`, and on a real completed purchase sets
  `UserSettings.tier` to the matching tier and acknowledges the purchase. It also re-checks Play's own purchase
  records on every app launch (`restorePurchases`), not only right after a purchase.
- Standard and Premium each have their own card (Learn tab) and their own row (Settings → Plan), each with its own
  monthly/annual price pulled from `offers` and its own Subscribe button — never a shared or merged purchase flow.
  Both draw from the same underlying curriculum content; only entitlement and price differ per plan, per the
  owner's own explicit "Final decision: Use one curriculum for all three plans" (see `docs/build-log.md`).
- **The Forge** (`docs/build-log.md`, 2026-10-11): the shared curriculum is organized as Levels (a frequency-based
  CEFR *proxy*, A1-C2 — not an officially validated placement) -> Units -> a Lesson menu, the identical structure
  for every plan. `EntitlementService.curriculumAccess` still gates it exactly as before — jumping straight to a
  later unit does not bypass the FREE trial limit.
- A demonstrated-mastery word is skipped for free (no cost to the daily goal) in both the daily "Continue" session
  and when practicing a specific unit directly, for every tier — not a Premium-only perk, per the owner's own
  explicit instruction.
- The owner's full tier comparison table is modeled as entitlement policy on `EntitlementService`:
  `aiConversationMinutesPerDay` (Free 5 / Standard 15 / Premium 60), and `AssistanceLevel`-graded
  `speakingExerciseLevel`, `pronunciationFeedbackLevel`, `aiExerciseLevel`, `aiExplanationLevel`,
  `lessonRecommendationLevel`, `adaptiveDifficultyLevel`, `learningAnalyticsLevel`, plus `isAdFree`. **These are
  policy facts only — see below.**

## What is not real yet

- **No purchase can actually complete.** `standard_monthly`, `standard_annual`, `premium_monthly` and
  `premium_annual` (`SubscriptionProducts`) are placeholder product IDs with nothing matching them in Google Play
  Console yet. Until the owner creates real subscription products there with those exact IDs, Play reports an
  empty `offers` list for both tiers (not an error — the UI already shows "Not available yet" for this), and
  tapping Subscribe has nothing to launch.
- **Tier is not yet backend-verified.** `PlayBillingManager` sets `UserSettings.tier` directly from what Google
  Play's own client SDK reports; there is no backend purchase-verification step yet (CLAUDE.md section 30's
  "backend must be authoritative" is not built).
- **No AI Learning Engine exists.** `aiConversationMinutesPerDay`, `pronunciationFeedbackLevel`,
  `adaptiveDifficultyLevel`, `lessonRecommendationLevel`, `learningAnalyticsLevel` and `aiExplanationLevel` are real,
  tested *policy* functions with **no feature behind them** — no conversational AI tutor, no real pronunciation
  scoring, no adaptive difficulty, no real recommendations or analytics. This needs a chosen AI provider and a
  designed conversation UX, a separate larger product decision the owner has not made yet.
- **No real CEFR validation.** The Forge's A1-C2 banding is a frequency-based heuristic; no linguist has reviewed
  any language's placement.
- **No real content for Grammar, Listening, Reading, Writing or Speaking exercises** — only Vocabulary is real. The
  Lesson menu shows the other five honestly as "Coming soon" rather than fabricated content.
- **No AdMob.** `isAdFree(tier)` is real policy; no ad SDK is integrated — this needs the owner's own AdMob account
  and ad unit IDs, which do not exist yet, the same kind of external dependency the Play Billing product IDs were.
- Granular per-resource-type usage quotas beyond the curriculum trial and `TTS_SHARING`'s monthly count (added
  2026-10-17, see above) are still NOT STARTED — for example voice-note-translation minutes or pronunciation
  attempts have no quota of their own yet, even though `TtsShareUsageService`'s shape is reusable for one.
- Annual/monthly renewal, cancellation, grace periods, upgrade/downgrade and restore-purchases *UI* (the plumbing
  exists in `PlayBillingManager.restorePurchases`, but there's no "Restore purchases" button or account-hold
  messaging) are all still NOT STARTED (CLAUDE.md section 32).

## When billing is actually built out fully

CLAUDE.md section 30 is the standing instruction: the backend must become authoritative for paid entitlement
state, and the Android app must never trust client-only purchase state alone for anything that matters
commercially. The current `PlayBillingManager` is a reasonable local step (it does talk to real Google Play, not a
fake), but it is not yet backend-verified — when that milestone starts, a purchase token should be sent to the
backend for verification before `Tier` is trusted, the same way `curriculumAccess` is called today (the call sites
should not need to change, only where the verified answer ultimately comes from).
