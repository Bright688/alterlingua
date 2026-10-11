package com.alterlingua.app.entitlements

/**
 * A capability whose availability depends on the subscription tier (CLAUDE.md section 27). Kept separate from
 * [Tier] so which tier unlocks which feature is a policy decision in one place ([TierPolicy]), not a scattered
 * "isPremium" check wherever the feature is used.
 */
enum class Feature {
    /** Learning past the free trial's word count in the standard curriculum (see CurriculumProgressService). */
    STANDARD_CURRICULUM_UNLIMITED,
}
