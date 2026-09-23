package com.alterlingua.app.ui.learn

import com.alterlingua.app.R
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.lessons.LessonReason
import com.alterlingua.app.ui.UiText
import java.time.Instant
import java.time.ZoneId

/**
 * The plain words for a card's context, as text the screen shows in the app language. They describe how the item was met (how
 * often, in what kind of message, when) and never quote or name anything from the message itself.
 */
object LessonText {

    /** The pieces of "how you met it", in order: how often, in what, and when. The screen joins them with a separator. */
    fun metParts(card: LessonCard, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<UiText> {
        val times = card.context.timesMet
        val parts = mutableListOf<UiText>()
        parts += if (times == 1) UiText(R.string.lt_met_once) else UiText(R.string.lt_met_times, times)
        when (card.context.lastMetIn) {
            InteractionKind.OUTGOING_TEXT -> parts += UiText(R.string.lt_where_out_text)
            InteractionKind.OUTGOING_VOICE -> parts += UiText(R.string.lt_where_out_voice)
            InteractionKind.INCOMING_MESSAGE -> parts += UiText(R.string.lt_where_in_text)
            InteractionKind.INCOMING_VOICE -> parts += UiText(R.string.lt_where_in_voice)
            null -> Unit
        }
        whenText(card.context.lastSeenMillis, nowMillis, zone)?.let { parts += it }
        return parts
    }

    fun helpLine(card: LessonCard): UiText? {
        val help = card.context.helpRequests
        return when {
            help <= 0 -> null
            help == 1 -> UiText(R.string.lt_help_once)
            else -> UiText(R.string.lt_help_times, help)
        }
    }

    private fun whenText(seenMillis: Long, nowMillis: Long, zone: ZoneId): UiText? {
        if (seenMillis <= 0) return null
        val seen = Instant.ofEpochMilli(seenMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(seen, today)
        return when {
            days <= 0 -> UiText(R.string.lt_last_today)
            days == 1L -> UiText(R.string.lt_last_yesterday)
            else -> UiText(R.string.lt_last_days, days.toInt())
        }
    }

    fun kindLabel(card: LessonCard): UiText = when (card.key.type) {
        UnitType.WORD -> UiText(R.string.lt_kind_word)
        UnitType.PHRASE -> UiText(R.string.lt_kind_phrase)
        UnitType.EXPRESSION -> UiText(R.string.lt_kind_expression)
    }

    fun reason(reason: LessonReason): UiText = UiText(
        when (reason) {
            LessonReason.ASKED_TRANSLATION -> R.string.lt_reason_asked
            LessonReason.USEFUL -> R.string.lt_reason_useful
            LessonReason.RECENT -> R.string.lt_reason_recent
            LessonReason.STILL_LEARNING -> R.string.lt_reason_still_learning
            LessonReason.NEW -> R.string.lt_reason_new
            LessonReason.WORTH_REVIEW -> R.string.lt_reason_review
            LessonReason.TIME_TO_REVIEW -> R.string.lt_reason_due
        },
    )
}
