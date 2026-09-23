package com.alterlingua.app.learning.lessons

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** Keeps today's lesson, so it is made once a day and reopening the app shows the same one. */
interface DailyLessonStore {
    suspend fun load(): DailyLesson?

    suspend fun save(lesson: DailyLesson)

    suspend fun clear()
}

class InMemoryDailyLessonStore(private var lesson: DailyLesson? = null) : DailyLessonStore {
    override suspend fun load() = lesson
    override suspend fun save(lesson: DailyLesson) { this.lesson = lesson }
    override suspend fun clear() { lesson = null }
}

/**
 * The lesson as JSON. It holds unit keys, display forms, meanings and counts (no message text, no sender), so keeping
 * it on the phone does not keep any private communication.
 */
object DailyLessonCodec {
    fun encode(lesson: DailyLesson): String = JSONObject()
        .put("date", lesson.date)
        .put("language", lesson.language)
        .put("position", lesson.position)
        .put("completed", lesson.completed)
        .put("encountered", JSONArray(lesson.encountered.sorted()))
        .put(
            "cards",
            JSONArray(
                lesson.cards.map { card ->
                    JSONObject()
                        .put("language", card.key.language).put("normalized", card.key.normalized).put("type", card.key.type.name)
                        .put("term", card.term).put("kind", card.kind.name)
                        .put("meaning", card.meaning ?: JSONObject.NULL).put("meaningLanguage", card.meaningLanguage ?: JSONObject.NULL)
                        .put("timesMet", card.context.timesMet).put("lastMetIn", card.context.lastMetIn?.name ?: JSONObject.NULL)
                        .put("lastSeen", card.context.lastSeenMillis).put("help", card.context.helpRequests)
                        .put("reasons", JSONArray(card.context.reasons.map { it.name })).put("state", card.masteryState.name)
                },
            ),
        )
        .toString()

    fun decode(text: String): DailyLesson? = try {
        val json = JSONObject(text)
        val cards = json.getJSONArray("cards").let { array ->
            (0 until array.length()).map { i ->
                val c = array.getJSONObject(i)
                LessonCard(
                    key = UnitKey(c.getString("language"), c.getString("normalized"), UnitType.valueOf(c.getString("type"))),
                    term = c.getString("term"),
                    kind = CardKind.valueOf(c.getString("kind")),
                    meaning = c.optString("meaning").takeIf { !c.isNull("meaning") && it.isNotBlank() },
                    meaningLanguage = c.optString("meaningLanguage").takeIf { !c.isNull("meaningLanguage") && it.isNotBlank() },
                    context = LessonContext(
                        timesMet = c.getInt("timesMet"),
                        lastMetIn = if (c.isNull("lastMetIn")) null else runCatching { InteractionKind.valueOf(c.getString("lastMetIn")) }.getOrNull(),
                        lastSeenMillis = c.getLong("lastSeen"),
                        helpRequests = c.getInt("help"),
                        reasons = c.getJSONArray("reasons").let { r -> (0 until r.length()).mapNotNull { runCatching { LessonReason.valueOf(r.getString(it)) }.getOrNull() } },
                    ),
                    masteryState = MasteryStatus.valueOf(c.getString("state")),
                )
            }
        }
        val encountered = json.getJSONArray("encountered").let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }
        cards.takeIf { it.isNotEmpty() }?.let { DailyLesson(json.getString("date"), json.getString("language"), it, json.getInt("position"), encountered, json.getBoolean("completed")) }
    } catch (_: JSONException) {
        null // a damaged or older copy is ignored; a new lesson is made
    } catch (_: IllegalArgumentException) {
        null
    }
}

private val Context.dailyLessonDataStore: DataStore<Preferences> by preferencesDataStore(name = "daily_lesson")

class DataStoreDailyLessonStore(private val dataStore: DataStore<Preferences>) : DailyLessonStore {
    override suspend fun load(): DailyLesson? =
        dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()[KEY]?.let(DailyLessonCodec::decode)

    override suspend fun save(lesson: DailyLesson) {
        dataStore.edit { it[KEY] = DailyLessonCodec.encode(lesson) }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    companion object {
        private val KEY = stringPreferencesKey("daily_lesson")

        fun create(context: Context) = DataStoreDailyLessonStore(context.applicationContext.dailyLessonDataStore)
    }
}
