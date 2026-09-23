package com.alterlingua.app.learning.engine

import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/** The settings the pipeline needs, read for each interaction. */
data class LearningContext(val learning: Language, val native: Language, val enabled: Boolean)

/** How one interaction ended in the pipeline. */
sealed interface PipelineResult {
    data class Recorded(val event: LearningEvent) : PipelineResult
    data class Skipped(val reason: Reason) : PipelineResult

    enum class Reason { DISABLED, NOT_THE_LEARNING_LANGUAGE, UNSUPPORTED_LANGUAGE, NOTHING_USEFUL }
}

/**
 * The learning-event pipeline:
 *
 *   translation interaction  ->  is the text in the learning language?  ->  language-aware analysis
 *   ->  candidate units  ->  LearningEvent (units only)  ->  exposure store
 *
 * The message text is used inside [record] and then dropped; the event and the store hold only short units and counts.
 * Nothing is logged. A language with no analyzer is skipped as unsupported, never analysed with the wrong rules.
 */
class LearningPipeline(
    private val analyzers: AnalyzerRegistry,
    private val context: suspend () -> LearningContext,
    private val store: ExposureStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun record(interaction: TranslationInteraction): PipelineResult {
        val settings = context()
        if (!settings.enabled) return PipelineResult.Skipped(PipelineResult.Reason.DISABLED)

        // Only text in the language being learned teaches that language. Outgoing translations are in the target language;
        // an incoming message counts only when it was written in it.
        val textLanguage = Languages.fromCode(interaction.textLanguage)
        if (textLanguage == null || textLanguage.code != settings.learning.code) {
            return PipelineResult.Skipped(PipelineResult.Reason.NOT_THE_LEARNING_LANGUAGE)
        }
        val analyzer = analyzers.forLanguage(textLanguage) ?: return PipelineResult.Skipped(PipelineResult.Reason.UNSUPPORTED_LANGUAGE)

        val now = clock()
        val tokens = analyzer.analyze(interaction.text, textLanguage)
        val candidates = CandidateExtractor(analyzer.profile(textLanguage)).extract(tokens, settings.native.code, now)
        if (candidates.isEmpty()) return PipelineResult.Skipped(PipelineResult.Reason.NOTHING_USEFUL)

        val event = LearningEvent(newId(), now, interaction.kind, textLanguage.code, settings.native.code, candidates)
        store.record(event)
        return PipelineResult.Recorded(event)
    }
}

/** Hands interactions to the pipeline without waiting, so the translation the user asked for is never delayed by it. */
class PipelineRecorder(private val pipeline: LearningPipeline, private val scope: CoroutineScope) : LearningRecorder {
    override fun record(interaction: TranslationInteraction) {
        scope.launch {
            try {
                pipeline.record(interaction)
            } catch (_: Exception) {
                // Learning must never get in the way of translating.
            }
        }
    }
}

/** What is known about one unit after one or more events. */
data class ExposureEntry(
    val key: UnitKey,
    val surface: String,
    val meaningLanguage: String,
    val exposure: Exposure,
    val bestUsefulness: Double,
    val meaning: String? = null,
)

/**
 * Keeps the units met and how often, and nothing else: no message, no sender, no time of day beyond first and last seen.
 * Saving this to the phone's database arrives with the Personal Language Map; until then it lives in memory only.
 */
interface ExposureStore {
    suspend fun record(event: LearningEvent)
}

/** A bounded in-memory [ExposureStore]. Each language is kept apart from every other. */
class InMemoryExposureStore(private val maxUnitsPerLanguage: Int = 2_000) : ExposureStore {
    private val units = LinkedHashMap<UnitKey, ExposureEntry>()

    override suspend fun record(event: LearningEvent) = recordSynchronously(event)

    @Synchronized
    private fun recordSynchronously(event: LearningEvent) {
        for (candidate in event.candidates) {
            val existing = units[candidate.key]
            units[candidate.key] = if (existing == null) {
                ExposureEntry(candidate.key, candidate.surface, candidate.meaningLanguage, candidate.exposure, candidate.usefulness.score, candidate.meaning)
            } else {
                existing.copy(
                    exposure = existing.exposure.copy(count = existing.exposure.count + candidate.exposure.count, lastSeenMillis = candidate.exposure.lastSeenMillis),
                    bestUsefulness = maxOf(existing.bestUsefulness, candidate.usefulness.score),
                )
            }
        }
        trim(event.learningLanguage)
    }

    /** The units of one language, most often met first. */
    @Synchronized
    fun entries(language: String): List<ExposureEntry> =
        units.values.filter { it.key.language == language }.sortedByDescending { it.exposure.count }

    private fun trim(language: String) {
        val ofLanguage = units.entries.filter { it.key.language == language }
        if (ofLanguage.size > maxUnitsPerLanguage) {
            // Forget the least recently seen units first.
            ofLanguage.sortedBy { it.value.exposure.lastSeenMillis }.take(ofLanguage.size - maxUnitsPerLanguage).forEach { units.remove(it.key) }
        }
    }
}
