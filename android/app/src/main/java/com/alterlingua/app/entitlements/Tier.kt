package com.alterlingua.app.entitlements

/**
 * The subscription tier (CLAUDE.md section 26). Scaffolding only for now: there is no real purchase flow yet (no
 * Google Play Billing), so this is set locally and is not yet backed by server-verified entitlement state. Once
 * billing exists, the backend becomes authoritative and this field is set from its answer instead.
 */
enum class Tier { FREE, STANDARD, PREMIUM }
