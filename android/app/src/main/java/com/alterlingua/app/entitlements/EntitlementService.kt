package com.alterlingua.app.entitlements

import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.first

/** How many curriculum words a FREE user may learn, per language, before being asked to upgrade to Standard. */
const val FREE_CURRICULUM_TRIAL_WORDS: Int = 50

/** The normal daily-word cap for the curriculum: FREE (within its trial) and STANDARD. */
const val STANDARD_CURRICULUM_DAILY_CAP: Int = 20

/** PREMIUM's daily-word cap: effectively none, far past what the curriculum lists could need in a day. */
const val PREMIUM_CURRICULUM_DAILY_CAP: Int = 10_000

/** How many translated-keyboard-message voice shares a FREE user may make a month (see [EntitlementService.ttsShareLimitPerMonth]). */
const val FREE_TTS_SHARES_PER_MONTH: Int = 5

/** STANDARD's monthly cap for the same feature. PREMIUM has no cap at all (see [EntitlementService.ttsShareLimitPerMonth]). */
const val STANDARD_TTS_SHARES_PER_MONTH: Int = 30

/** Whether a translated keyboard message may be given a voice and shared right now. */
sealed interface TtsShareAccess {
    /** May proceed. */
    data object Allowed : TtsShareAccess

    /** The tier's monthly count has been reached; [limit] is how many it allowed. */
    data class LimitReached(val limit: Int) : TtsShareAccess
}

/** Whether the curriculum (Standard/Premium lessons) is open right now, for one language. */
sealed interface CurriculumAccess {
    /** Learning may continue. */
    data object Allowed : CurriculumAccess

    /** The FREE trial's word count has been reached; [limit] is how many it allowed. */
    data class TrialLimitReached(val limit: Int) : CurriculumAccess
}

/**
 * A depth/richness label reused across several AI-assistance dimensions that differ by tier (the owner's own tier
 * comparison table: speaking exercises, pronunciation feedback, lesson recommendations, adaptive difficulty, AI
 * explanations, learning analytics). Not a universal currency -- each function below defines what its own levels
 * mean -- but shared rather than one bespoke enum per dimension, since all of them are the same shape: a tier-graded
 * richness label with no behavior of its own yet.
 *
 * Policy only: none of the features these levels describe (a real AI tutor, adaptive difficulty, recommendations,
 * analytics) are built yet -- that is the separate, larger "AI Learning Engine" (CLAUDE.md's own distinction from
 * the Curriculum Engine), explicitly deferred until an AI provider and conversation UX are chosen. These functions
 * exist now so that engine has a tier policy to read from the day it is built, rather than retrofitting one then.
 */
enum class AssistanceLevel { NONE, LIMITED, BASIC, STANDARD, ADVANCED, EXTENDED }

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

    /** AI conversation-practice minutes allowed per day, by tier. */
    fun aiConversationMinutesPerDay(tier: Tier): Int = when (tier) {
        Tier.FREE -> 5
        Tier.STANDARD -> 15
        Tier.PREMIUM -> 60
    }

    /** How rich speaking-exercise coaching is, by tier. */
    fun speakingExerciseLevel(tier: Tier): AssistanceLevel = when (tier) {
        Tier.FREE -> AssistanceLevel.LIMITED
        Tier.STANDARD -> AssistanceLevel.BASIC
        Tier.PREMIUM -> AssistanceLevel.ADVANCED
    }

    /** How rich pronunciation feedback is, by tier (FREE gets none at all, not merely a limited amount). */
    fun pronunciationFeedbackLevel(tier: Tier): AssistanceLevel = when (tier) {
        Tier.FREE -> AssistanceLevel.NONE
        Tier.STANDARD -> AssistanceLevel.BASIC
        Tier.PREMIUM -> AssistanceLevel.ADVANCED
    }

    /** How rich AI-generated exercises are, by tier. */
    fun aiExerciseLevel(tier: Tier): AssistanceLevel = if (tier == Tier.PREMIUM) AssistanceLevel.EXTENDED else AssistanceLevel.LIMITED

    /** How rich AI explanations of difficult concepts are, by tier. */
    fun aiExplanationLevel(tier: Tier): AssistanceLevel = if (tier == Tier.PREMIUM) AssistanceLevel.EXTENDED else AssistanceLevel.LIMITED

    /** How personalized the next-lesson recommendation is, by tier. */
    fun lessonRecommendationLevel(tier: Tier): AssistanceLevel = when (tier) {
        Tier.FREE -> AssistanceLevel.BASIC
        Tier.STANDARD -> AssistanceLevel.STANDARD
        Tier.PREMIUM -> AssistanceLevel.ADVANCED
    }

    /** How much lesson difficulty adapts to the learner, by tier (FREE gets none at all). */
    fun adaptiveDifficultyLevel(tier: Tier): AssistanceLevel = when (tier) {
        Tier.FREE -> AssistanceLevel.NONE
        Tier.STANDARD -> AssistanceLevel.BASIC
        Tier.PREMIUM -> AssistanceLevel.ADVANCED
    }

    /** How detailed the learner's own progress analytics are, by tier. */
    fun learningAnalyticsLevel(tier: Tier): AssistanceLevel = when (tier) {
        Tier.FREE -> AssistanceLevel.BASIC
        Tier.STANDARD -> AssistanceLevel.STANDARD
        Tier.PREMIUM -> AssistanceLevel.ADVANCED
    }

    /** Whether [tier] is free of advertisements (only PREMIUM is). */
    fun isAdFree(tier: Tier): Boolean = tier == Tier.PREMIUM

    /** How many translated-message voice shares [tier] may make a month: Free 5, Standard 30, or null for no
     * limit at all (Premium). */
    fun ttsShareLimitPerMonth(tier: Tier): Int? = when (tier) {
        Tier.FREE -> FREE_TTS_SHARES_PER_MONTH
        Tier.STANDARD -> STANDARD_TTS_SHARES_PER_MONTH
        Tier.PREMIUM -> null
    }
}
