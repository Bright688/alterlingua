package com.alterlingua.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAppsTest {
    private val whatsapp = ChatApp("com.whatsapp", "WhatsApp", 10201)
    private val telegram = ChatApp("org.telegram.messenger", "Telegram", 10202)
    private val signal = ChatApp("org.thoughtcrime.securesms", "Signal", 10203)

    @Test
    fun aListeningSession_coversOnlyTheChosenAppsThatAreInstalled_inThePhonesOrder() {
        val request = CaptureRequest.forChosen(setOf("org.telegram.messenger", "com.whatsapp", "com.not.installed"), listOf(whatsapp, telegram, signal))
        assertEquals(listOf(whatsapp, telegram), request.apps)
        assertTrue(request.keepListening)
        assertTrue(request.isValid)
        assertEquals("WhatsApp, Telegram", request.labels())
    }

    @Test
    fun aListeningSessionWithNoApps_isNotValid_soItNeverListensToEverything() {
        val request = CaptureRequest.forChosen(setOf("com.not.installed"), listOf(whatsapp))
        assertTrue(request.apps.isEmpty())
        assertFalse(request.isValid)
    }

    @Test
    fun aSingleCapture_mayHaveNoApp_andThenListensToAnyApp() {
        assertTrue(CaptureRequest(emptyList(), keepListening = false).isValid)
    }

    @Test
    fun theKnownApps_haveNoDuplicates_andIncludeTheMainChatApps() {
        val packages = KnownChatApps.packages
        assertEquals(packages.size, packages.toSet().size)
        assertTrue(packages.containsAll(listOf("com.whatsapp", "org.telegram.messenger", "com.facebook.orca", "org.thoughtcrime.securesms")))
    }

    @Test
    fun theListeningState_isFalseUntilASessionSetsIt() {
        VoiceCaptureState.setListening(false)
        assertFalse(VoiceCaptureState.listening.value)
        VoiceCaptureState.setListening(true)
        assertTrue(VoiceCaptureState.listening.value)
        VoiceCaptureState.setListening(false)
    }
}
