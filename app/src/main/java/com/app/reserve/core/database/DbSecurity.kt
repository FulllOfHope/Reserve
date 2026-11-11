package com.app.reserve.core.database

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object DbSecurity {

    private const val PREFS_NAME = "db_security_prefs"
    private const val KEY_PASSPHRASE = "db_passphrase"

    fun getPassphrase(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        var passphraseStr = sharedPreferences.getString(KEY_PASSPHRASE, null)
        if (passphraseStr == null) {
            passphraseStr = generateRandomPassphrase()
            sharedPreferences.edit().putString(KEY_PASSPHRASE, passphraseStr).apply()
        }
        return passphraseStr.toByteArray()
    }

    private fun generateRandomPassphrase(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32) // 256 bits
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
