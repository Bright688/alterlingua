package com.alterlingua.app.speak

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.storage.DatabaseEncryptor
import com.alterlingua.app.storage.DatabasePassphrase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "spoken_history")
data class SpokenHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val sourceLanguage: String?,
    val targetLanguage: String,
    val transcript: String,
    val translation: String,
)

@Dao
interface SpokenHistoryDao {
    @Insert
    suspend fun insert(entity: SpokenHistoryEntity): Long

    @Query("SELECT * FROM spoken_history ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SpokenHistoryEntity>

    @Query("DELETE FROM spoken_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM spoken_history")
    suspend fun deleteAll()

    /** Keeps the table from growing without bound: run after every insert. */
    @Query("DELETE FROM spoken_history WHERE id NOT IN (SELECT id FROM spoken_history ORDER BY createdAtMillis DESC LIMIT :keep)")
    suspend fun prune(keep: Int)
}

@Database(entities = [SpokenHistoryEntity::class], version = 1, exportSchema = true)
abstract class SpokenHistoryDatabase : RoomDatabase() {
    abstract fun dao(): SpokenHistoryDao

    companion object {
        const val NAME = "spoken_history.db"

        /** Encrypted at rest (SQLCipher), the same way and for the same reason as the other local databases
         * ([com.alterlingua.app.learning.map.LanguageMapDatabase], [com.alterlingua.app.learning.progress.ProgressDatabase]). */
        fun create(context: Context): SpokenHistoryDatabase {
            val app = context.applicationContext
            val passphrase = DatabasePassphrase.get(app)
            DatabaseEncryptor.ensureEncrypted(app, NAME, passphrase)
            return Room.databaseBuilder(app, SpokenHistoryDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}

class RoomSpokenHistoryStore(private val database: SpokenHistoryDatabase) : SpokenHistoryStore {
    private val dao get() = database.dao()

    override suspend fun add(entry: SpokenHistoryEntry) {
        dao.insert(entry.toEntity())
        dao.prune(SpokenHistoryStore.MAX_KEPT)
    }

    override suspend fun recent(limit: Int): List<SpokenHistoryEntry> = dao.recent(limit).map { it.toEntry() }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}

private fun SpokenHistoryEntry.toEntity() = SpokenHistoryEntity(
    createdAtMillis = createdAtMillis,
    sourceLanguage = sourceLanguage?.code,
    targetLanguage = targetLanguage.code,
    transcript = transcript,
    translation = translation,
)

private fun SpokenHistoryEntity.toEntry() = SpokenHistoryEntry(
    id = id,
    createdAtMillis = createdAtMillis,
    sourceLanguage = Languages.fromCode(sourceLanguage),
    targetLanguage = Languages.fromCode(targetLanguage) ?: Languages.English,
    transcript = transcript,
    translation = translation,
)
