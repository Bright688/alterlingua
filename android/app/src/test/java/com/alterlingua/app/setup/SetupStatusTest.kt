package com.alterlingua.app.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStatusTest {

    // ---- microphone ----

    @Test
    fun granted_winsOverEverythingElse() {
        assertEquals(MicrophoneStatus.GRANTED, microphoneStatus(granted = true, askedBefore = false, showRationale = false))
        assertEquals(MicrophoneStatus.GRANTED, microphoneStatus(granted = true, askedBefore = true, showRationale = true))
    }

    @Test
    fun neverAsked_isNotAsked() {
        assertEquals(MicrophoneStatus.NOT_ASKED, microphoneStatus(granted = false, askedBefore = false, showRationale = false))
    }

    @Test
    fun declinedOnce_canBeAskedAgain() {
        assertEquals(MicrophoneStatus.DECLINED, microphoneStatus(granted = false, askedBefore = true, showRationale = true))
    }

    @Test
    fun declinedForGood_isBlocked() {
        // Asked before, and Android no longer wants a rationale: the system will not show the question again.
        assertEquals(MicrophoneStatus.BLOCKED, microphoneStatus(granted = false, askedBefore = true, showRationale = false))
    }

    // ---- selected keyboard ----

    private val pkg = "com.alterlingua.app"
    private val cls = "com.alterlingua.app.keyboard.AlterLinguaKeyboardService"

    @Test
    fun shortAndFullServiceNames_bothMatch() {
        assertTrue(isSelectedKeyboard("$pkg/.keyboard.AlterLinguaKeyboardService", pkg, cls))
        assertTrue(isSelectedKeyboard("$pkg/$cls", pkg, cls))
    }

    @Test
    fun otherKeyboards_doNotMatch() {
        assertFalse(isSelectedKeyboard("com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME", pkg, cls))
        assertFalse(isSelectedKeyboard("$pkg/.keyboard.SomethingElse", pkg, cls))
        assertFalse(isSelectedKeyboard("other.app/.keyboard.AlterLinguaKeyboardService", pkg, cls))
    }

    @Test
    fun missingOrBrokenValues_doNotMatch_andDoNotCrash() {
        assertFalse(isSelectedKeyboard(null, pkg, cls))
        assertFalse(isSelectedKeyboard("", pkg, cls))
        assertFalse(isSelectedKeyboard("no-slash", pkg, cls))
        assertFalse(isSelectedKeyboard("/", pkg, cls))
        assertFalse(isSelectedKeyboard("$pkg/", pkg, cls))
    }

    // ---- summary flags ----

    @Test
    fun keyboardReady_needsBothEnabledAndSelected() {
        assertFalse(SetupStatus(keyboardEnabled = true).keyboardReady)
        assertFalse(SetupStatus(keyboardSelected = true).keyboardReady)
        assertTrue(SetupStatus(keyboardEnabled = true, keyboardSelected = true).keyboardReady)
    }
}
