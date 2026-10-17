package com.alterlingua.app.entitlements

/** Which tier unlocks which [Feature]. The only place commercial rules are decided (CLAUDE.md section 27). */
object TierPolicy {
    private val featuresByTier: Map<Tier, Set<Feature>> = mapOf(
        Tier.FREE to emptySet(),
        Tier.STANDARD to setOf(Feature.CURRICULUM_UNLIMITED_TRIAL, Feature.IMAGE_TRANSLATION),
        Tier.PREMIUM to setOf(
            Feature.CURRICULUM_UNLIMITED_TRIAL,
            Feature.CURRICULUM_PREMIUM,
            Feature.IMAGE_TRANSLATION,
            Feature.DOCUMENT_TRANSLATION,
            Feature.TTS_SHARING,
        ),
    )

    fun includes(tier: Tier, feature: Feature): Boolean = featuresByTier[tier]?.contains(feature) == true
}
