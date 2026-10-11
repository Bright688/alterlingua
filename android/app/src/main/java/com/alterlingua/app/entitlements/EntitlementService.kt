package com.alterlingua.app.entitlements

import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.first

/** How many curriculum words a FREE user may learn, per language, before being asked to upgrade to Standard. */
const val FREE_CURRICULUM_TRIAL_WORDS: Int = 50

/** The normal daily-word cap for the curriculum: FREE (within its trial) and STANDARD. */
const val STANDARD_CURRICULUM_DAILY_CAP: Int = 20

/** PREMIUM's daily-word cap: effectively none, far past what the curriculum lists could need in a day. */
const val PREMIUM_CURRICULUM_DAILY_CAP: Int = 10_000

/** Whether the curriculum (Standard/Premium lessons) is open right now, for one language. */
sealed interface CurriculumAccess {
    /** Learning may continue. */
    data object Allowed : CurriculumAccess

    /** The FREE trial's word count has been reached; [limit] is how many it allowed. */
    data class TrialLimitReached(val limit: Int) : CurriculumAccess
}

/**
 * The single place that decides what the current subscription tier allows (CLAUDE.md section 26-27), so this
 * question is never answered by a scattered tier check elsewhere in the app.
 */
class EntitlementService(private val settings: UserSettingsRepository) {

    suspend fun tier(): Tier = settings.settings.first().tier

    /** Whether [languageCode]'s curriculum may still be learned from, given the current tier and trial use. */
    suspend fun curriculumAccess(languageCode: String): CurriculumAccess {
        val current = settings.settings.first()
        if (TierPolicy.includes(current.tier, Feature.CURRICULUM_UNLIMITED_TRIAL)) return CurriculumAccess.Allowed
        val learned = current.curriculumProgressFor(languageCode).cursor
        return if (learned < FREE_CURRICULUM_TRIAL_WORDS) {
            CurriculumAccess.Allowed
        } else {
            CurriculumAccess.TrialLimitReached(FREE_CURRICULUM_TRIAL_WORDS)
        }
    }

    /** The most curriculum words a day [tier] may set as its goal: the normal cap, or Premium's much higher one. */
    fun maxCurriculumDailyGoal(tier: Tier): Int =
        if (TierPolicy.includes(tier, Feature.CURRICULUM_PREMIUM)) PREMIUM_CURRICULUM_DAILY_CAP else STANDARD_CURRICULUM_DAILY_CAP

    /** Whether [tier] may learn the curriculum of any language it is learning, not just the active target language. */
    fun canPickAnyLearningLanguage(tier: Tier): Boolean = TierPolicy.includes(tier, Feature.CURRICULUM_PREMIUM)
}
