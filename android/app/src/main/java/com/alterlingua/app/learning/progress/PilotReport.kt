package com.alterlingua.app.learning.progress

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * The pilot's metric and event vocabulary. These names are the contract between the app, the pilot report and any future upload;
 * docs/pilot.md defines each one exactly. Every one is a COUNT. None can carry text, a word, a sender, a chat, audio or a time
 * of day: the finest time resolution is one local calendar day.
 */
object PilotMetrics {
    const val SCHEMA = "alterlingua.pilot.v1"

    /** Event names. Events are counted on the phone into daily totals; they are not stored one by one. */
    object Event {
        const val TRANSLATION_COMPLETED = "translation_completed"
        const val VOCABULARY_EXPOSURE = "vocabulary_exposure"
        const val TRANSLATION_HELP_REQUESTED = "translation_help_requested"
        const val WORD_STATE_ADVANCED = "word_state_advanced"
        const val LESSON_CARD_COMPLETED = "lesson_card_completed"
        const val LESSON_COMPLETED = "lesson_completed"
        const val PRONUNCIATION_ATTEMPTED = "pronunciation_attempted"
        const val ASSISTANCE_MODE_ACTION = "assistance_mode_action"
    }

    /** The exact set of keys a weekly report row may contain. A test fails if a key is added without being listed here. */
    val WEEK_KEYS = setOf(
        "week", "start", "translations", "vocabulary_exposures", "new_words", "words_assisted", "translation_help_requests",
        "word_state_advances", "lessons_completed", "lesson_cards", "pronunciation_attempts", "pronunciation_understood",
        "mode_actions", "translation_dependence_percent", "active_days", "map_snapshot",
    )
}

/**
 * The aggregate report one pilot participant can hand over: per week, counts only. It is built from the same daily totals as the
 * Progress screen, so what the pilot measures is what the user can see.
 *
 * PRIVACY: the report has no message, word, phrase, transcript, sender, chat, audio, device identifier, or time of day. The only
 * identifiers are an optional pseudonymous participant code chosen for the pilot (never derived from the phone or an account) and
 * the language code.
 */
object PilotReport {

    fun build(
        participant: String?,
        appVersion: String,
        language: String,
        days: List<DailyActivity>,
        nowMillis: Long,
        zone: ZoneId,
        rules: ProgressRules = ProgressRules(weeksShown = 26),
    ): JSONObject {
        val report = ProgressCalculator.build(language, days, ProgressTotals(0, 0, 0, 0, 0), nowMillis, zone, rules)
        val weeks = org.json.JSONArray()
        for (week in report.weeks) {
            weeks.put(
                JSONObject()
                    .put("week", week.weekNumber)
                    .put("start", week.start.toString())
                    .put(
                        "translations",
                        JSONObject()
                            .put("outgoing_text", week.translationsOutgoingText)
                            .put("outgoing_voice", week.translationsOutgoingVoice)
                            .put("incoming_text", week.translationsIncomingText)
                            .put("incoming_voice", week.translationsIncomingVoice)
                            .put("total", week.translations),
                    )
                    .put("vocabulary_exposures", week.wordsMet)
                    .put("new_words", week.newWords)
                    .put("words_assisted", week.wordsAssisted)
                    .put("translation_help_requests", week.helpRequests)
                    .put(
                        "word_state_advances",
                        JSONObject().put("unknown_to_learning", week.toLearning).put("learning_to_familiar", week.toFamiliar).put("familiar_to_mastered", week.toMastered),
                    )
                    .put("lessons_completed", week.lessonsCompleted)
                    .put("lesson_cards", week.lessonCards)
                    .put("pronunciation_attempts", week.practiceTries)
                    .put("pronunciation_understood", week.practiceGood)
                    .put("mode_actions", JSONObject().put("full_support", week.actionsFullSupport).put("adaptive", week.actionsAdaptive).put("on_demand", week.actionsOnDemand))
                    .put("translation_dependence_percent", week.dependencePercent ?: JSONObject.NULL)
                    .put("active_days", week.activeWeekdays.size)
                    .put(
                        "map_snapshot",
                        week.snapshot?.let { JSONObject().put("encountered", it.encountered).put("learning", it.learning).put("familiar", it.familiar).put("mastered", it.mastered) } ?: JSONObject.NULL,
                    ),
            )
        }
        val trend = JSONObject()
        when (val status = report.dependence) {
            DependenceStatus.NoData -> trend.put("status", "no_data")
            is DependenceStatus.Collecting -> trend.put("status", "collecting").put("measured_weeks", status.measuredWeeks)
            is DependenceStatus.Ready -> trend
                .put("status", "ready")
                .put("first_week", status.points.first().weekNumber).put("first_percent", status.first)
                .put("latest_week", status.points.last().weekNumber).put("latest_percent", status.current)
                .put("change_points", status.current - status.first)
        }
        return JSONObject()
            .put("schema", PilotMetrics.SCHEMA)
            .put("participant", participant ?: JSONObject.NULL)
            .put("app_version", appVersion)
            .put("generated_on", Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toString())
            .put("language", language)
            .put("dependence_trend", trend)
            .put("weeks", weeks)
    }
}
