package com.alterlingua.app.capture

import com.alterlingua.app.learning.UserSettings
import com.alterlingua.app.testing.FakeUserSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedNoteAlertsTest {
    private val posted = mutableListOf<String>()
    private val address = "content://alterlingua-capture/note-one.wav"

    private fun alerts(repository: FakeUserSettingsRepository) = CapturedNoteAlerts(repository) { posted += it }

    @Test fun byDefault_aTranslatedNoteGetsTheNotification() = runBlocking {
        alerts(FakeUserSettingsRepository()).onTranslated(address)
        assertEquals(listOf(address), posted)
    }

    @Test fun whenTheUserHasMutedIt_nothingIsPosted() = runBlocking {
        alerts(FakeUserSettingsRepository(UserSettings(notifyOnCapturedNotes = false))).onTranslated(address)
        assertTrue(posted.isEmpty())
    }

    @Test fun aChangeInSettingsAppliesToTheNextNote() = runBlocking {
        val repository = FakeUserSettingsRepository()
        val alerts = alerts(repository)
        repository.update { it.copy(notifyOnCapturedNotes = false) }
        alerts.onTranslated(address)
        assertTrue(posted.isEmpty())
        repository.update { it.copy(notifyOnCapturedNotes = true) }
        alerts.onTranslated(address)
        assertEquals(1, posted.size)
    }
}
