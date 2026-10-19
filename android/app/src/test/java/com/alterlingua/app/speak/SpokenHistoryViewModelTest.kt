package com.alterlingua.app.speak

import com.alterlingua.app.learning.Languages
import com.alterlingua.app.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SpokenHistoryViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val store = InMemorySpokenHistoryStore()
    private var nextMillis = 1_000L

    private suspend fun entry(text: String) = store.add(
        SpokenHistoryEntry(createdAtMillis = nextMillis++, sourceLanguage = Languages.English, targetLanguage = Languages.French, transcript = text, translation = "traduit: $text"),
    )

    @Test fun loadsRecentEntries_newestFirst() = runTest {
        entry("one")
        entry("two")
        val vm = SpokenHistoryViewModel(store)
        assertFalse(vm.uiState.value.loading)
        assertEquals(listOf("two", "one"), vm.uiState.value.entries.map { it.transcript })
    }

    @Test fun deletingOneEntry_leavesTheOthers() = runTest {
        entry("one")
        entry("two")
        val vm = SpokenHistoryViewModel(store)
        val toDelete = vm.uiState.value.entries.first { it.transcript == "one" }.id
        vm.delete(toDelete)
        assertEquals(listOf("two"), vm.uiState.value.entries.map { it.transcript })
    }

    @Test fun clearAll_emptiesTheList() = runTest {
        entry("one")
        entry("two")
        val vm = SpokenHistoryViewModel(store)
        vm.clearAll()
        assertTrue(vm.uiState.value.entries.isEmpty())
    }

    @Test fun startsEmpty_whenNothingWasEverSaved() = runTest {
        val vm = SpokenHistoryViewModel(store)
        assertTrue(vm.uiState.value.entries.isEmpty())
    }
}
