package com.alterlingua.app.ui.settings

import androidx.lifecycle.ViewModel
import com.alterlingua.app.learning.progress.PilotReport
import com.alterlingua.app.learning.progress.ProgressLog
import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * Builds the pilot report (`docs/pilot.md`) the user can choose to share: the same weekly counts the Progress tab
 * already shows them, in the agreed JSON shape. Nothing here sends anything anywhere by itself; the screen that
 * calls this only ever hands the result to Android's own Share sheet, at the user's own request.
 */
class PilotReportViewModel(
    private val settings: UserSettingsRepository,
    private val progressLog: ProgressLog,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** The report for the language currently being learned, as pretty-printed JSON. [participant] is never required. */
    suspend fun build(participant: String): String {
        val language = settings.settings.first().targetLanguage
        val days = progressLog.days(language.code)
        val report = PilotReport.build(
            participant = participant.trim().takeIf { it.isNotEmpty() },
            appVersion = appVersion,
            language = language.code,
            days = days,
            nowMillis = clock(),
            zone = zone,
        )
        return report.toString(2)
    }
}
