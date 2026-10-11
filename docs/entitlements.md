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
EntitlementService     answers questions ("can this language's curriculum still be learned from?", "what's the daily cap?")
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
- `PlayBillingManager` is a real `BillingClient` wrapper: it queries Google Play's real catalog for the four
  `SubscriptionProducts` IDs, exposes what it finds as `offers`, and on a real completed purchase sets
  `UserSettings.tier` to the matching tier and acknowledges the purchase. It also re-checks Play's own purchase
  records on every app launch (`restorePurchases`), not only right after a purchase.
- Standard and Premium each have their own card (Learn tab) and their own row (Settings → Plan), each with its own
  monthly/annual price pulled from `offers` and its own Subscribe button — never a shared or merged purchase flow.
  Both draw from the same underlying curriculum content; only entitlement and price differ per plan, per the
  owner's own explicit "Final decision: Use one curriculum for all three plans" (see `docs/build-log.md`).

## What is not real yet

- **No purchase can actually complete.** `standard_monthly`, `standard_annual`, `premium_monthly` and
  `premium_annual` (`SubscriptionProducts`) are placeholder product IDs with nothing matching them in Google Play
  Console yet. Until the owner creates real subscription products there with those exact IDs, Play reports an
  empty `offers` list for both tiers (not an error — the UI already shows "Not available yet" for this), and
  tapping Subscribe has nothing to launch.
- **Tier is not yet backend-verified.** `PlayBillingManager` sets `UserSettings.tier` directly from what Google
  Play's own client SDK reports; there is no backend purchase-verification step yet (CLAUDE.md section 30's
  "backend must be authoritative" is not built).
- A dedicated AI-tutoring differentiator for Premium (a conversational AI assistant, beyond the higher cap and
  extra languages) — part of the owner's larger pasted vision on 2026-10-11 — is not built. Neither is a CEFR-level
  curriculum rebuild, AdMob, or granular per-resource-type usage quotas. All deliberately deferred; see
  `docs/build-log.md`'s 2026-10-11 entry for the reasoning.
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
