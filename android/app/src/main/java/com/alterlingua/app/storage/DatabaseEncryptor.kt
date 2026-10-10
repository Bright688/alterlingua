package com.alterlingua.app.storage

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Encrypts a database file left over from before encryption was added to this app, the one time it is needed. A
 * fresh install never has a plaintext file to find (Room creates the database already encrypted), so this does
 * nothing for it; an existing install's database is rewritten in place, once, the next time it is opened.
 *
 * This never writes to the system log (CLAUDE.md section 50): a failed migration simply leaves the original file
 * in place and returns, so Room's own open attempt reports whatever is actually wrong, honestly, through its own
 * normal error path rather than a log line.
 */
object DatabaseEncryptor {

    /** Rewrites [dbName] as an encrypted database under [passphrase] if it still exists in plain text. Idempotent. */
    fun ensureEncrypted(context: Context, dbName: String, passphrase: ByteArray) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return // nothing to migrate; Room will create it encrypted
        if (opens(dbFile, passphrase)) return // already encrypted, under this passphrase

        val tempFile = File(dbFile.parentFile, "$dbName.encrypting-tmp")
        tempFile.delete()
        var plain: SQLiteDatabase? = null
        try {
            // An empty key opens a database exactly as plain SQLite would: this only succeeds for a pre-encryption,
            // plain-text file. If it fails too, the file is neither encrypted-and-readable nor plain text (most
            // likely corrupt); nothing is touched, so Room's own open attempt can report the real error honestly.
            plain = SQLiteDatabase.openDatabase(dbFile.absolutePath, ByteArray(0), null, SQLiteDatabase.OPEN_READWRITE, null)
            plain.rawExecSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(tempFile.absolutePath, passphrase))
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plain.rawExecSQL("DETACH DATABASE encrypted")
        } catch (_: Exception) {
            tempFile.delete()
            return
        } finally {
            plain?.close()
        }

        // Swap in the encrypted copy only after it is confirmed to open under the real passphrase.
        if (opens(tempFile, passphrase)) {
            dbFile.delete()
            File(dbFile.parentFile, "$dbName-wal").delete()
            File(dbFile.parentFile, "$dbName-shm").delete()
            tempFile.renameTo(dbFile)
        } else {
            tempFile.delete()
        }
    }

    private fun opens(file: File, passphrase: ByteArray): Boolean {
        if (!file.exists()) return false
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, passphrase, null, SQLiteDatabase.OPEN_READONLY, null).use {
                it.rawQuery("SELECT count(*) FROM sqlite_master", arrayOf<String>()).use { cursor -> cursor.moveToFirst() }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
