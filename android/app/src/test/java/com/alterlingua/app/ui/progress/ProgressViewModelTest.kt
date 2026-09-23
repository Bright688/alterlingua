package com.alterlingua.app.ui.progress

import com.alterlingua.app.R
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.ui.UiText
import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.progress.DailyActivity
import com.alterlingua.app.learning.progress.DependenceStatus
import com.alterlingua.app.learning.progress.InMemoryProgressStore
import com.alterlingua.app.learning.progress.ProgressLog
import com.alterlingua.app.learning.progress.ProgressService
import com.alterlingua.app.learning.progress.WeekStats
import com.alterlingua.app.testing.FakeUserSettingsRepository
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProgressViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val store = InMemoryProgressStore()
    private val now = LocalDate.of(2026, 9, 20).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val service = ProgressService(LanguageMapService(InMemoryLanguageMapStore()), ProgressLog(store, { now }, { ZoneOffset.UTC }), { now }, { ZoneOffset.UTC })
    private val settings = FakeUserSettingsRepository(UserSettings())

    private fun viewModel() = ProgressViewModel(service, settings)

    private fun ProgressViewModel.ready() = uiState.value as ProgressUiState.Ready

    @Test fun aNewUser_seesRealZeros_andEmptyStates_notSampleData() {
        val ready = viewModel().ready()
        assertEquals(Languages.French, ready.language)
        assertEquals(0, ready.report.totals.encountered)
        assertEquals(DependenceStatus.NoData, ready.report.dependence)
    }

    @Test fun refresh_picksUpNewActivity() = runBlocking {
        val vm = viewModel()
        store.update("2026-09-14", "fr") { DailyActivity(it.date, "fr", wordsMet = 30, wordsAssisted = 15) }
        vm.refresh()
        assertTrue(vm.ready().report.dependence is DependenceStatus.Collecting)
        assertEquals(50, vm.ready().report.thisWeek.dependencePercent)
    }

    @Test fun switchingTheLanguage_showsThatLanguagesProgress() = runBlocking {
        store.update("2026-09-14", "fr", { DailyActivity(it.date, "fr", wordsMet = 30) })
        store.update("2026-09-14", "es", { DailyActivity(it.date, "es", wordsMet = 5) })
        val vm = viewModel()
        assertEquals(30, vm.ready().report.thisWeek.wordsMet)
        settings.update { it.copy(targetLanguage = Languages.Spanish) }
        assertEquals(Languages.Spanish, vm.ready().language)
        assertEquals(5, vm.ready().report.thisWeek.wordsMet)
    }

    // ---- wording ----

    private fun week(number: Int, percent: Int) = WeekStats(number, LocalDate.of(2026, 8, 3).plusWeeks(number.toLong()), 100, percent, 0, 0, 0, 0, 0, emptySet(), null, percent)

    @Test fun theChangeIsDescribedPlainly_downUpOrUnchanged() {
        assertEquals(UiText(R.string.pg_change_down, 45, 1, 94), changeText(DependenceStatus.Ready(listOf(week(1, 94), week(8, 49)))))
        assertEquals(UiText(R.string.pg_change_up, 15, 1, 40), changeText(DependenceStatus.Ready(listOf(week(1, 40), week(2, 55)))))
        assertEquals(UiText(R.string.pg_change_same, 2, 60), changeText(DependenceStatus.Ready(listOf(week(2, 60), week(3, 60)))))
    }

    @Test fun theDefinitionStatesTheFormulaAndTheMinimums() {
        val definition = java.io.File(listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml").first { java.io.File(it).exists() }).readText()
            .substringAfter("name=\"pg_definition\">").substringBefore("</string>")
        assertTrue(definition.contains("divided by all words met"))
        assertTrue(definition.contains("at least 20 words"))
        assertTrue(definition.contains("two such weeks"))
        assertTrue(definition.contains("not counted as conversations"))
    }
}
