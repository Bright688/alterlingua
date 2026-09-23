package com.alterlingua.app.learning.progress

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.map.LanguageMapService
import java.time.ZoneId

/** Builds the Progress report for the language being learned from the real Personal Language Map and daily counts. */
class ProgressService(
    private val map: LanguageMapService,
    private val log: ProgressLog,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val rules: ProgressRules = ProgressRules(),
) {
    suspend fun report(language: Language): ProgressReport {
        val now = clock()
        // States are evaluated now, so a word not seen for a long time is shown as it stands today.
        val states = map.items(language.code).map { map.evaluate(it, now).state }
        val totals = ProgressTotals(
            encountered = states.size,
            newCount = states.count { it == MasteryStatus.UNKNOWN },
            learning = states.count { it == MasteryStatus.LEARNING },
            familiar = states.count { it == MasteryStatus.FAMILIAR },
            mastered = states.count { it == MasteryStatus.MASTERED },
        )
        return ProgressCalculator.build(language.code, log.days(language.code), totals, now, zone(), rules)
    }
}
