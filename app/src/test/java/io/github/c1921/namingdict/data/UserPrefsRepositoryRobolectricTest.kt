package io.github.c1921.namingdict.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPrefsRepositoryRobolectricTest {

    private lateinit var context: Context
    private lateinit var secureStore: FakeSecureWebDavStore
    private lateinit var repository: UserPrefsRepository

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        secureStore = FakeSecureWebDavStore()
        repository = UserPrefsRepository(context, secureStore)
        clearUserPrefsDataStore()
        secureStore.clearPassword()
    }

    @Test
    fun writeWebDavConfig_passwordNotStoredInDataStorePlaintext() = runTest {
        repository.writeWebDavConfig(
            WebDavConfig(
                serverUrl = "https://example.com/dav",
                username = "alice",
                password = "super-secret"
            )
        )

        val preferences = userPrefsDataStore().data.first()
        assertEquals("https://example.com/dav", preferences[WEBDAV_SERVER_URL_KEY])
        assertEquals("alice", preferences[WEBDAV_USERNAME_KEY])
        assertFalse(preferences.asMap().containsKey(WEBDAV_PASSWORD_KEY))
        assertEquals("super-secret", secureStore.password)
    }

    @Test
    fun readWebDavConfig_migratesLegacyPasswordAndCleansLegacyKey() = runTest {
        userPrefsDataStore().edit { preferences ->
            preferences[WEBDAV_SERVER_URL_KEY] = "https://example.com/dav"
            preferences[WEBDAV_USERNAME_KEY] = "bob"
            preferences[WEBDAV_PASSWORD_KEY] = "legacy-password"
        }

        val config = repository.readWebDavConfig()

        assertEquals("https://example.com/dav", config.serverUrl)
        assertEquals("bob", config.username)
        assertEquals("legacy-password", config.password)
        assertEquals("legacy-password", secureStore.password)

        val updatedPreferences = userPrefsDataStore().data.first()
        assertFalse(updatedPreferences.asMap().containsKey(WEBDAV_PASSWORD_KEY))
    }

    @Test
    fun dictionaryScrollAndFavoritesState_readWriteRoundTrip() = runTest {
        repository.writeDictionaryScrollState(anchorEntryId = 101, offsetPx = 48)
        repository.writeDictionaryShowFavoritesOnly(true)
        repository.writeDictionaryFavoritesScrollState(anchorEntryId = 202, offsetPx = 24)

        val snapshot = repository.readSnapshot()
        assertEquals(101, snapshot.dictionaryScrollAnchorEntryId)
        assertEquals(48, snapshot.dictionaryScrollOffsetPx)
        assertEquals(true, snapshot.dictionaryShowFavoritesOnly)
        assertEquals(202, snapshot.dictionaryFavoritesScrollAnchorEntryId)
        assertEquals(24, snapshot.dictionaryFavoritesScrollOffsetPx)

        repository.writeDictionaryScrollState(anchorEntryId = null, offsetPx = -10)
        repository.writeDictionaryFavoritesScrollState(anchorEntryId = null, offsetPx = -20)
        val resetSnapshot = repository.readSnapshot()
        assertNull(resetSnapshot.dictionaryScrollAnchorEntryId)
        assertEquals(0, resetSnapshot.dictionaryScrollOffsetPx)
        assertNull(resetSnapshot.dictionaryFavoritesScrollAnchorEntryId)
        assertEquals(0, resetSnapshot.dictionaryFavoritesScrollOffsetPx)
    }

    private suspend fun clearUserPrefsDataStore() {
        userPrefsDataStore().edit { preferences ->
            preferences.clear()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun userPrefsDataStore(): DataStore<Preferences> {
        val className = "io.github.c1921.namingdict.data.UserPrefsRepositoryKt"
        val holderClass = Class.forName(className)
        val accessor = holderClass.getDeclaredMethod("getUserPrefsDataStore", Context::class.java)
        accessor.isAccessible = true
        return accessor.invoke(null, context) as DataStore<Preferences>
    }

    private class FakeSecureWebDavStore : SecureWebDavStore {
        var password: String = ""

        override suspend fun readPassword(): String = password

        override suspend fun writePassword(password: String) {
            this.password = password
        }

        override suspend fun clearPassword() {
            password = ""
        }
    }

    private companion object {
        val WEBDAV_SERVER_URL_KEY = stringPreferencesKey("webdav_server_url")
        val WEBDAV_USERNAME_KEY = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD_KEY = stringPreferencesKey("webdav_password")
    }
}
