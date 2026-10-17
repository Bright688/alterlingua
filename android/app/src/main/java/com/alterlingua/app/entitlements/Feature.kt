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

    /**
     * Translating a shared PDF or Word (.docx) file, with the output itself a translated file of the same format.
     * Premium only, with no FREE/Standard trial -- the owner's own request split image translation (Standard) from
     * document file translation (Premium), see docs/build-log.md.
     */
    DOCUMENT_TRANSLATION,
}

// Note: giving a just-translated keyboard message a voice and sharing it (on-device text-to-speech) is available
// to every tier, just with a different monthly cap per tier (Free 10, Standard 50, Premium 1,000) -- see
// EntitlementService.ttsShareLimitPerMonth and TtsShareUsageService. It has no Feature/TierPolicy entry because,
// unlike the gated features above, there is no tier that unlocks it and no tier that lacks it entirely; the policy
// is a graded cap, the same shape as EntitlementService.aiConversationMinutesPerDay.
