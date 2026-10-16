package com.alterlingua.app.entitlements

/**
 * A capability whose availability depends on the subscription tier (CLAUDE.md section 27). Kept separate from
 * [Tier] so which tier unlocks which feature is a policy decision in one place ([TierPolicy]), not a scattered
 * "isPremium" check wherever the feature is used.
 */
enum class Feature {
    /** Learning past the free trial's word count in the curriculum (Standard and Premium lessons). */
    CURRICULUM_UNLIMITED_TRIAL,

    /**
     * What Premium adds on top of Standard for the curriculum: every learning language's curriculum at once (not
     * just the active target language), and a much higher daily-word cap (see EntitlementService.maxCurriculumDailyGoal).
     */
    CURRICULUM_PREMIUM,

    /** Translating the text found in a shared image (on-device OCR, then the normal translation service). */
    IMAGE_TRANSLATION,
}
