package com.alterlingua.app.learning.lessons

import com.alterlingua.app.learning.Language
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.CancellationException

/** Finds what a word or phrase means in the learner's own language. */
interface MeaningProvider {
    /** The meaning of [unit] (written in [learning]) in [native], or null if it cannot be found right now. */
    suspend fun meaningOf(unit: String, learning: Language, native: Language): String?
}

/**
 * Uses the translation service to give a unit's meaning: the unit alone (a word or short phrase, never a message) is
 * translated from the language being learned into the learner's language. Failures give null, never an error, so a
 * lesson still works offline.
 */
class TranslationMeaningProvider(private val api: TranslationApi) : MeaningProvider {
    override suspend fun meaningOf(unit: String, learning: Language, native: Language): String? = try {
        when (val result = api.translate(TranslationRequest(text = unit, target = native.code, source = learning.code))) {
            is TranslationResult.Success -> result.translation.text.trim().takeIf { it.isNotEmpty() && result.translation.targetLanguage == native.code }
            is TranslationResult.Failure -> null
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
