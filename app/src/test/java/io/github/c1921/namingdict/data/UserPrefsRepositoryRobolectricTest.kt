package io.github.c1921.namingdict.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import io.github.c1921.namingdict.data.model.GivenNameMode
import io.github.c1921.namingdict.data.model.NamingScheme
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

    @Test
    fun namingDraft_readWriteRoundTrip() = runTest {
        val schemes = listOf(
            NamingScheme(
                id = 10L,
                givenNameMode = GivenNameMode.Double,
                slot1 = "安",
                slot2 = "宁"
            ),
            NamingScheme(
                id = 11L,
                givenNameMode = GivenNameMode.Single,
                slot1 = "禾",
                slot2 = ""
            )
        )
        repository.writeNamingDraft(
            surname = "欧阳",
            schemes = schemes,
            activeSchemeId = 11L,
            activeSlotIndex = 1
        )

        val snapshot = repository.readSnapshot()
        assertEquals("欧阳", snapshot.namingSurname)
        assertEquals(schemes, snapshot.namingSchemes)
        assertEquals(11L, snapshot.namingActiveSchemeId)
        assertEquals(1, snapshot.namingActiveSlotIndex)
    }

    @Test
    fun namingDraft_invalidJson_returnsEmptySchemes() = runTest {
        userPrefsDataStore().edit { preferences ->
            preferences[NAMING_SURNAME_KEY] = "张"
            preferences[NAMING_SCHEMES_JSON_KEY] = "{not-json}"
            preferences[NAMING_ACTIVE_SCHEME_ID_KEY] = 99L
            preferences[NAMING_ACTIVE_SLOT_INDEX_KEY] = 9
        }

        val snapshot = repository.readSnapshot()
        assertEquals("张", snapshot.namingSurname)
        assertEquals(emptyList<NamingScheme>(), snapshot.namingSchemes)
        assertEquals(99L, snapshot.namingActiveSchemeId)
        assertEquals(9, snapshot.namingActiveSlotIndex)
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
        val NAMING_SURNAME_KEY = stringPreferencesKey("naming_surname")
        val NAMING_SCHEMES_JSON_KEY = stringPreferencesKey("naming_schemes_json")
        val NAMING_ACTIVE_SCHEME_ID_KEY = longPreferencesKey("naming_active_scheme_id")
        val NAMING_ACTIVE_SLOT_INDEX_KEY = intPreferencesKey("naming_active_slot_index")
    }
}
