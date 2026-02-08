package io.github.c1921.namingdict.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface SecureWebDavStore {
    suspend fun readPassword(): String
    suspend fun writePassword(password: String)
    suspend fun clearPassword()
}

class EncryptedPrefsSecureWebDavStore(context: Context) : SecureWebDavStore {
    private val appContext = context.applicationContext
    private val securePrefs: SharedPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createSecurePrefs(appContext)
    }

    override suspend fun readPassword(): String {
        return securePrefs.getString(KEY_WEBDAV_PASSWORD, "").orEmpty()
    }

    override suspend fun writePassword(password: String) {
        securePrefs.edit().putString(KEY_WEBDAV_PASSWORD, password).apply()
    }

    override suspend fun clearPassword() {
        securePrefs.edit().remove(KEY_WEBDAV_PASSWORD).apply()
    }

    private fun createSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private companion object {
        const val SECURE_PREFS_FILE_NAME = "secure_webdav_prefs"
        const val KEY_WEBDAV_PASSWORD = "webdav_password"
    }
}
