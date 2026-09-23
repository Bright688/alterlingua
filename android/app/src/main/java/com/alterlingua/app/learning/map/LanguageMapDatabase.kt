package com.alterlingua.app.learning.map

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The Personal Language Map's table. One row per (language, normalized form, type): the same word in two languages is two
 * rows. It stores counts, a display form and a mastery result: no message text, no sender, nothing about where a unit was seen.
 */
@Entity(
    tableName = "language_map_items",
    indices = [Index(value = ["language", "normalized", "type"], unique = true), Index(value = ["language", "masteryState"])],
)
data class LanguageMapItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val language: String,
    val normalized: String,
    val type: String,
    val displayForm: String,
    val meaning: String?,
    val meaningLanguage: String?,
    val exposureCount: Int,
    val helpRequests: Int,
    val lessonEncounters: Int,
    val correctRecognitions: Int,
    val incorrectRecognitions: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val masteryScore: Double,
    val masteryState: String,
    // Added in version 2 (the defaults let existing rows migrate).
    @ColumnInfo(defaultValue = "0") val usefulness: Double = 0.0,
    val lastContext: String? = null,
    @ColumnInfo(defaultValue = "0") val lastLessonAt: Long = 0,
    // Added in version 3.
    @ColumnInfo(defaultValue = "0") val lastHelpAt: Long = 0,
    // Added in version 4.
    @ColumnInfo(defaultValue = "0") val pronunciationTries: Int = 0,
    @ColumnInfo(defaultValue = "0") val pronunciationGood: Int = 0,
)

@Dao
interface LanguageMapDao {
    @Query("SELECT * FROM language_map_items WHERE language = :language AND normalized = :normalized AND type = :type LIMIT 1")
    suspend fun find(language: String, normalized: String, type: String): LanguageMapItemEntity?

    @Upsert
    suspend fun upsert(entity: LanguageMapItemEntity): Long

    @Query("SELECT * FROM language_map_items WHERE language = :language ORDER BY lastSeen DESC")
    suspend fun items(language: String): List<LanguageMapItemEntity>

    @Query("SELECT * FROM language_map_items WHERE language = :language ORDER BY lastSeen DESC")
    fun observe(language: String): Flow<List<LanguageMapItemEntity>>

    @Query("DELETE FROM language_map_items WHERE language = :language")
    suspend fun deleteLanguage(language: String)

    @Query("DELETE FROM language_map_items")
    suspend fun deleteAll()
}

@Database(
    entities = [LanguageMapItemEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class LanguageMapDatabase : RoomDatabase() {
    abstract fun dao(): LanguageMapDao

    companion object {
        const val NAME = "language_map.db"

        fun create(context: Context): LanguageMapDatabase =
            Room.databaseBuilder(context.applicationContext, LanguageMapDatabase::class.java, NAME).build()
    }
}

/** The Room version of [LanguageMapStore]: the map on the phone, in a private database no other app can read. */
class RoomLanguageMapStore(private val database: LanguageMapDatabase) : LanguageMapStore {
    private val dao get() = database.dao()

    override suspend fun update(key: UnitKey, change: (LanguageMapItem?) -> LanguageMapItem?): LanguageMapItem? =
        database.withTransaction {
            val existing = dao.find(key.language, key.normalized, key.type.name)
            val updated = change(existing?.toItem()) ?: return@withTransaction existing?.toItem()
            require(updated.key == key) { "an update may not change which unit an item is" }
            dao.upsert(updated.toEntity(id = existing?.id ?: 0))
            updated
        }

    override suspend fun get(key: UnitKey): LanguageMapItem? = dao.find(key.language, key.normalized, key.type.name)?.toItem()

    override suspend fun items(language: String): List<LanguageMapItem> = dao.items(language).map { it.toItem() }

    override fun observe(language: String): Flow<List<LanguageMapItem>> = dao.observe(language).map { rows -> rows.map { it.toItem() } }

    override suspend fun deleteLanguage(language: String) = dao.deleteLanguage(language)

    override suspend fun deleteAll() = dao.deleteAll()
}

internal fun LanguageMapItemEntity.toItem() = LanguageMapItem(
    language = language, normalized = normalized, type = UnitType.valueOf(type), displayForm = displayForm,
    meaning = meaning, meaningLanguage = meaningLanguage, exposureCount = exposureCount, helpRequests = helpRequests,
    lessonEncounters = lessonEncounters, correctRecognitions = correctRecognitions, incorrectRecognitions = incorrectRecognitions,
    firstSeen = firstSeen, lastSeen = lastSeen, masteryScore = masteryScore, masteryState = MasteryStatus.valueOf(masteryState),
    usefulness = usefulness, lastContext = lastContext?.let { runCatching { InteractionKind.valueOf(it) }.getOrNull() }, lastLessonAt = lastLessonAt, lastHelpAt = lastHelpAt,
    pronunciationTries = pronunciationTries, pronunciationGood = pronunciationGood,
)

internal fun LanguageMapItem.toEntity(id: Long) = LanguageMapItemEntity(
    id = id, language = language, normalized = normalized, type = type.name, displayForm = displayForm,
    meaning = meaning, meaningLanguage = meaningLanguage, exposureCount = exposureCount, helpRequests = helpRequests,
    lessonEncounters = lessonEncounters, correctRecognitions = correctRecognitions, incorrectRecognitions = incorrectRecognitions,
    firstSeen = firstSeen, lastSeen = lastSeen, masteryScore = masteryScore, masteryState = masteryState.name,
    usefulness = usefulness, lastContext = lastContext?.name, lastLessonAt = lastLessonAt, lastHelpAt = lastHelpAt,
    pronunciationTries = pronunciationTries, pronunciationGood = pronunciationGood,
)
