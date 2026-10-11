# AlterLingua — Entitlements

Status: **scaffolding only**. There is no real purchase flow yet (no Google Play Billing — CLAUDE.md sections 31-32
explicitly postpone it). This document describes what exists today and what is deliberately deferred.

## Why this exists now

The owner asked for standard-curriculum lessons (see `docs/progress.md`, Milestone 26) to have "a standard and
premium lessons that would be paid for but they would have trial with limited number to learn." Building that
honestly — a real trial count, a real gate, a real (if placeholder) upgrade prompt — needs *some* entitlement
concept to exist now, even though actual payments are still out of scope. Asked directly whether to wire real
billing or build scaffolding first, the owner chose scaffolding.

## The model (CLAUDE.md section 26-27)

```
Tier            FREE / STANDARD / PREMIUM     (UserSettings.tier, default FREE)
Feature         a capability a tier may unlock (currently: STANDARD_CURRICULUM_UNLIMITED)
TierPolicy      the one place deciding which tier unlocks which feature
EntitlementService   answers questions ("can this language's curriculum still be learned from?")
```

`Tier` and `Feature` are kept apart on purpose, per CLAUDE.md's own instruction not to scatter `isPremium` checks:
a feature's gate is a policy decision in `TierPolicy`, not a tier check wherever the feature happens to be used.

## What is real right now

- `UserSettings.tier` is saved and read back like any other setting.
- `EntitlementService.curriculumAccess(languageCode)` is a real, tested gate: a FREE user is stopped at
  `FREE_CURRICULUM_TRIAL_WORDS` (50) words learned *in that language's* curriculum — the limit is per language, not
  shared, so a user learning two languages gets a full trial in each (CLAUDE.md section 6.21).
- `STANDARD` and `PREMIUM` both unlock `STANDARD_CURRICULUM_UNLIMITED` (removing the trial limit). CLAUDE.md's own
  tier table does not describe a difference between Standard and Premium specifically for the standard curriculum,
  so none was invented here.

## What is not real yet

- **Nothing can set `Tier` to anything but FREE.** There is no purchase flow, so there is nothing to verify and
  nothing to grant. This is deliberate, not an oversight.
- **"View plans" opens a placeholder** ("Coming soon" — see `ComingSoonDialog` in `ui/learn/CurriculumLessonScreen.kt`),
  not a real Plans screen, not Google Play Billing.
- Everything in CLAUDE.md sections 28 (quotas beyond the one curriculum trial), 32 (Play Billing integration itself),
  33 (Settings → Plan screens) and 34 (keyboard-specific paywall copy) is still NOT STARTED.

## When billing is actually built

CLAUDE.md section 30 is the standing instruction: the backend must become authoritative for paid entitlement state,
and the Android app must never trust a client-only purchase state. When that milestone starts, `Tier` should stop
being a plain local `UserSettings` field and instead be set from a server-verified answer (cached locally only for
responsive UX), the same way `curriculumAccess` is called today — the call sites should not need to change, only
where `UserSettings.tier` itself comes from.
