package com.alterlingua.app.notifications

/**
 * Shows a translated conversation through every delegate that can (a system notification, and optionally a floating
 * bubble). [canPost] is true as long as at least one delegate can show something, so a translation is not skipped
 * just because one output channel is unavailable — for example notifications turned off but the floating bubble
 * permission granted, or the other way round.
 */
class CompositeTranslationPresenter(private val delegates: List<TranslationPresenter>) : TranslationPresenter {

    override fun canPost(): Boolean = delegates.any { it.canPost() }

    override fun show(conversation: TranslatedConversation) {
        delegates.forEach { it.show(conversation) }
    }

    override fun remove(key: String) {
        delegates.forEach { it.remove(key) }
    }
}
