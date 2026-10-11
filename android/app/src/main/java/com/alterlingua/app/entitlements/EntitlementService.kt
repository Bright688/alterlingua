package com.alterlingua.app.entitlements

import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.first

/** How many standard-curriculum words a FREE user may learn, per language, before being asked to upgrade. */
const val FREE_CURRICULUM_TRIAL_WORDS: Int = 50

/** Whether the standard curriculum is open right now, for one language. */
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

    /** Whether [languageCode]'s standard curriculum may still be learned from, given the current tier and trial use. */
    suspend fun curriculumAccess(languageCode: String): CurriculumAccess {
        val current = settings.settings.first()
        if (TierPolicy.includes(current.tier, Feature.STANDARD_CURRICULUM_UNLIMITED)) return CurriculumAccess.Allowed
        val learned = current.curriculumProgressFor(languageCode).cursor
        return if (learned < FREE_CURRICULUM_TRIAL_WORDS) {
            CurriculumAccess.Allowed
        } else {
            CurriculumAccess.TrialLimitReached(FREE_CURRICULUM_TRIAL_WORDS)
        }
    }
}
