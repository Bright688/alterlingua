package com.alterlingua.app.learning.progress

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction

/** One row per language and local day. Counts only. */
@Entity(tableName = "daily_progress", primaryKeys = ["date", "language"])
data class DailyProgressEntity(
    val date: String,
    val language: String,
    val wordsMet: Int,
    val wordsAssisted: Int,
    val newWords: Int,
    val helpRequests: Int,
    val lessonCards: Int,
    val practiceTries: Int,
    val practiceGood: Int,
    @ColumnInfo(defaultValue = "0") val hasSnapshot: Boolean,
    val encountered: Int,
    val learning: Int,
    val familiar: Int,
    val mastered: Int,
    // Added in version 2 (pilot metrics); the defaults let existing rows migrate.
    @ColumnInfo(defaultValue = "0") val translationsOutgoingText: Int = 0,
    @ColumnInfo(defaultValue = "0") val translationsOutgoingVoice: Int = 0,
    @ColumnInfo(defaultValue = "0") val translationsIncomingText: Int = 0,
    @ColumnInfo(defaultValue = "0") val translationsIncomingVoice: Int = 0,
    @ColumnInfo(defaultValue = "0") val toLearning: Int = 0,
    @ColumnInfo(defaultValue = "0") val toFamiliar: Int = 0,
    @ColumnInfo(defaultValue = "0") val toMastered: Int = 0,
    @ColumnInfo(defaultValue = "0") val lessonsCompleted: Int = 0,
    @ColumnInfo(defaultValue = "0") val actionsFullSupport: Int = 0,
    @ColumnInfo(defaultValue = "0") val actionsAdaptive: Int = 0,
    @ColumnInfo(defaultValue = "0") val actionsOnDemand: Int = 0,
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM daily_progress WHERE date = :date AND language = :language")
    suspend fun find(date: String, language: String): DailyProgressEntity?

    @Upsert
    suspend fun upsert(entity: DailyProgressEntity)

    @Query("SELECT * FROM daily_progress WHERE language = :language ORDER BY date ASC")
    suspend fun days(language: String): List<DailyProgressEntity>

    @Query("DELETE FROM daily_progress WHERE language = :language")
    suspend fun deleteLanguage(language: String)

    @Query("DELETE FROM daily_progress")
    suspend fun deleteAll()
}

@Database(
    entities = [DailyProgressEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [androidx.room.AutoMigration(from = 1, to = 2)],
)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun dao(): ProgressDao

    companion object {
        const val NAME = "progress.db"

        fun create(context: Context): ProgressDatabase =
            Room.databaseBuilder(context.applicationContext, ProgressDatabase::class.java, NAME).build()
    }
}

/** The Room version of [ProgressStore]: in a private database no other app can read. */
class RoomProgressStore(private val database: ProgressDatabase) : ProgressStore {
    private val dao get() = database.dao()

    override suspend fun update(date: String, language: String, change: (DailyActivity) -> DailyActivity) {
        database.withTransaction {
            val current = dao.find(date, language)?.toActivity() ?: DailyActivity(date, language)
            dao.upsert(change(current).copy(date = date, language = language).toEntity())
        }
    }

    override suspend fun days(language: String): List<DailyActivity> = dao.days(language).map { it.toActivity() }

    override suspend fun deleteLanguage(language: String) = dao.deleteLanguage(language)

    override suspend fun deleteAll() = dao.deleteAll()
}

internal fun DailyProgressEntity.toActivity() = DailyActivity(
    date, language, wordsMet, wordsAssisted, newWords, helpRequests, lessonCards, practiceTries, practiceGood,
    snapshot = if (hasSnapshot) MasterySnapshot(encountered, learning, familiar, mastered) else null,
    translationsOutgoingText = translationsOutgoingText, translationsOutgoingVoice = translationsOutgoingVoice,
    translationsIncomingText = translationsIncomingText, translationsIncomingVoice = translationsIncomingVoice,
    toLearning = toLearning, toFamiliar = toFamiliar, toMastered = toMastered, lessonsCompleted = lessonsCompleted,
    actionsFullSupport = actionsFullSupport, actionsAdaptive = actionsAdaptive, actionsOnDemand = actionsOnDemand,
)

internal fun DailyActivity.toEntity() = DailyProgressEntity(
    date, language, wordsMet, wordsAssisted, newWords, helpRequests, lessonCards, practiceTries, practiceGood,
    hasSnapshot = snapshot != null,
    encountered = snapshot?.encountered ?: 0, learning = snapshot?.learning ?: 0, familiar = snapshot?.familiar ?: 0, mastered = snapshot?.mastered ?: 0,
    translationsOutgoingText = translationsOutgoingText, translationsOutgoingVoice = translationsOutgoingVoice,
    translationsIncomingText = translationsIncomingText, translationsIncomingVoice = translationsIncomingVoice,
    toLearning = toLearning, toFamiliar = toFamiliar, toMastered = toMastered, lessonsCompleted = lessonsCompleted,
    actionsFullSupport = actionsFullSupport, actionsAdaptive = actionsAdaptive, actionsOnDemand = actionsOnDemand,
)
