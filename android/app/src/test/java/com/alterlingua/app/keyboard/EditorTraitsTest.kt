package com.alterlingua.app.keyboard

import android.text.InputType
import com.alterlingua.app.R
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.VoiceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTraitsTest {

    @Test
    fun passwordFieldsAreRecognised() {
        val passwords = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )
        passwords.forEach { assertTrue(EditorTraits(it).isPassword) }
    }

    @Test
    fun ordinaryFieldsAreNotPasswords() {
        val ordinary = listOf(
            InputType.TYPE_CLASS_TEXT,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_NULL,
        )
        ordinary.forEach { assertFalse(EditorTraits(it).isPassword) }
    }

    @Test
    fun onlyKeysThatChangeTextDismissTheTranslationBanner() {
        assertTrue(KeySpec.Character("a").changesText)
        assertTrue(KeySpec.Function(KeyAction.SPACE, 1f).changesText)
        assertTrue(KeySpec.Function(KeyAction.BACKSPACE, 1f).changesText)
        assertTrue(KeySpec.Function(KeyAction.ENTER, 1f).changesText)
        assertFalse(KeySpec.Function(KeyAction.SHIFT, 1f).changesText)
        assertFalse(KeySpec.Function(KeyAction.SHOW_SYMBOLS, 1f).changesText)
        assertFalse(KeySpec.Function(KeyAction.SWITCH_KEYBOARD, 1f).changesText)
        assertFalse(KeySpec.Spacer(0.5f).changesText)
    }

    @Test
    fun everyVoiceFailureHasItsOwnMessage() {
        val messages = VoiceFailure.entries.map { it.messageRes() }
        assertEquals(VoiceFailure.entries.size, messages.toSet().size)
        assertEquals(R.string.failure_voice_no_speech, VoiceFailure.NO_SPEECH.messageRes())
    }

    @Test
    fun everyFailureHasItsOwnMessage() {
        val messages = TranslationFailure.entries.map { it.messageRes() }
        assertEquals(TranslationFailure.entries.size, messages.toSet().size)
        assertNotEquals(TranslationFailure.APPLY_FAILED.messageRes(false), TranslationFailure.APPLY_FAILED.messageRes(true))
        assertEquals(R.string.failure_offline, TranslationFailure.OFFLINE.messageRes())
    }
}
