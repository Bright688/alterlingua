package com.alterlingua.app.capture

import com.alterlingua.app.storage.UserSettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Decides whether a translated voice note gets the "Voice note captured" notification: yes when the user has it switched on in
 * Settings (the default), never when it is switched off. The setting is read each time, so a change applies to the next note.
 */
class CapturedNoteAlerts(
    private val settings: UserSettingsRepository,
    private val notify: (address: String) -> Unit,
) {
    suspend fun onTranslated(address: String) {
        if (settings.settings.first().notifyOnCapturedNotes) notify(address)
    }
}
