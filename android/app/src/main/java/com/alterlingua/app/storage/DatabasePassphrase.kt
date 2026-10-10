package com.alterlingua.app.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * The random passphrase SQLCipher uses to encrypt the app's local databases (the Personal Language Map, the progress
 * log). Generated once, the first time either database is opened, and kept only inside Android Keystore-backed
 * encrypted storage: never in plain text on disk, never sent anywhere, never logged. Losing it (a factory reset, for
 * example) makes the encrypted databases unreadable, which is the same as losing the device — there is nothing to
 * recover, by design, since nothing about this passphrase is escrowed anywhere else.
 */
object DatabasePassphrase {
    private const val PREFS_NAME = "alterlingua_db_key"
    private const val KEY_NAME = "passphrase"
    private const val PASSPHRASE_BYTES = 32 // 256-bit, what SQLCipher's default cipher expects

    @Synchronized
    fun get(context: Context): ByteArray {
        val prefs = encryptedPrefs(context)
        val existing = prefs.getString(KEY_NAME, null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val fresh = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_NAME, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
        return fresh
    }

    private fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
