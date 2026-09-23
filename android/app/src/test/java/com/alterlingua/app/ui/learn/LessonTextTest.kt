package com.alterlingua.app.ui.learn

import com.alterlingua.app.R
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.CardKind
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.lessons.LessonContext
import com.alterlingua.app.learning.lessons.LessonReason
import com.alterlingua.app.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class LessonTextTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 10_000L * day + 9 * 60 * 60 * 1000

    private fun card(times: Int = 4, kind: InteractionKind? = InteractionKind.INCOMING_MESSAGE, seen: Long = now, help: Int = 0, type: UnitType = UnitType.WORD) =
        LessonCard(UnitKey("fr", "x", type), "x", CardKind.WORD_CARD, null, null, LessonContext(times, kind, seen, help, emptyList()), MasteryStatus.LEARNING)

    private fun parts(c: LessonCard) = LessonText.metParts(c, now, ZoneOffset.UTC)

    @Test fun metParts_sayHowOftenWhereAndWhen() {
        assertEquals(listOf(UiText(R.string.lt_met_times, 4), UiText(R.string.lt_where_in_text), UiText(R.string.lt_last_today)), parts(card()))
        assertEquals(listOf(UiText(R.string.lt_met_once), UiText(R.string.lt_where_out_text), UiText(R.string.lt_last_yesterday)), parts(card(1, InteractionKind.OUTGOING_TEXT, now - day)))
        assertEquals(listOf(UiText(R.string.lt_met_times, 2), UiText(R.string.lt_where_out_voice), UiText(R.string.lt_last_days, 3)), parts(card(2, InteractionKind.OUTGOING_VOICE, now - 3 * day)))
        assertEquals(UiText(R.string.lt_where_in_voice), parts(card(kind = InteractionKind.INCOMING_VOICE))[1])
    }

    @Test fun metParts_copeWithMissingDetails() {
        assertEquals(listOf(UiText(R.string.lt_met_times, 2)), parts(card(2, null, 0)))
    }

    @Test fun helpLine_onlyWhenTranslationWasAsked() {
        assertNull(LessonText.helpLine(card(help = 0)))
        assertEquals(UiText(R.string.lt_help_once), LessonText.helpLine(card(help = 1)))
        assertEquals(UiText(R.string.lt_help_times, 3), LessonText.helpLine(card(help = 3)))
    }

    @Test fun kindLabels() {
        assertEquals(UiText(R.string.lt_kind_word), LessonText.kindLabel(card(type = UnitType.WORD)))
        assertEquals(UiText(R.string.lt_kind_phrase), LessonText.kindLabel(card(type = UnitType.PHRASE)))
        assertEquals(UiText(R.string.lt_kind_expression), LessonText.kindLabel(card(type = UnitType.EXPRESSION)))
    }

    @Test fun everyReasonHasItsOwnWording() {
        val ids = LessonReason.entries.map { LessonText.reason(it).id }
        assertEquals(LessonReason.entries.size, ids.toSet().size)
    }

    @Test fun contextNeverContainsAnyTextOfAMessage() {
        // Only counts, a kind of message and how recent: the pieces are resources plus numbers.
        for (part in parts(card())) assertEquals(true, part.args.all { it is Int })
    }
}
