package com.alterlingua.app.learning.progress

import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Counts each translation interaction the app is told about (kind only) and passes it on unchanged. The interaction's text
 * is handed to [delegate] exactly as before and is never given to the progress log.
 *
 * Counting happens on the same signal the learning engine uses: a translation the user actually received or used (outgoing text
 * once it is inserted, keyboard voice once inserted, a translated voice message once shared, an incoming message once
 * translated, a voice note once translated). It is counted under the learning language selected at that moment.
 */
class MeteredLearningRecorder(
    private val delegate: LearningRecorder,
    private val log: ProgressLog,
    private val learningLanguage: suspend () -> String,
    private val scope: CoroutineScope,
) : LearningRecorder {
    override fun record(interaction: TranslationInteraction) {
        val kind = interaction.kind // only the kind is kept from here on
        scope.launch {
            try {
                log.translation(learningLanguage(), kind)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Metrics must never get in the way of translating.
            }
        }
        delegate.record(interaction)
    }
}
