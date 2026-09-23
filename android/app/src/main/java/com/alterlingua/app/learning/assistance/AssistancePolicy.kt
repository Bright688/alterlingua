package com.alterlingua.app.learning.assistance

import com.alterlingua.app.learning.AssistanceMode

/**
 * What an assistance mode means for an incoming message. This is the one place that decides it, so the notification code
 * asks a question instead of checking which mode is on.
 *
 * @property translateWholeMessage the whole message is translated into the user's language.
 * @property feedLearning the message is offered to the learning pipeline, so the Personal Language Map keeps growing.
 *   (The user's own "learn from my messages" switch still applies inside the pipeline.)
 */
data class IncomingAssistance(val translateWholeMessage: Boolean, val feedLearning: Boolean)

object AssistancePolicy {

    /** Full Support: complete translation, and learning carries on. Being helped is not being "taught less". */
    val FULL_SUPPORT = IncomingAssistance(translateWholeMessage = true, feedLearning = true)

    /**
     * Adaptive and On-demand are separate milestones. Until they exist they keep the complete translation Full Support
     * gives, rather than hiding assistance on the basis of rules that have not been built.
     */
    fun incoming(mode: AssistanceMode): IncomingAssistance = when (mode) {
        AssistanceMode.FULL_SUPPORT -> FULL_SUPPORT
        AssistanceMode.ADAPTIVE, AssistanceMode.ON_DEMAND -> FULL_SUPPORT
    }
}
