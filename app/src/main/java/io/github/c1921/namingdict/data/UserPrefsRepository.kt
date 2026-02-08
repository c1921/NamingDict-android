package io.github.c1921.namingdict.data

import android.content.Context
import io.github.c1921.namingdict.data.model.NamingScheme
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private const val USER_PREFS_NAME = "user_prefs"
private val Context.userPrefsDataStore by preferencesDataStore(name = USER_PREFS_NAME)

data class UserPrefsSnapshot(
    val favoriteOrder: List<Int> = emptyList(),
    val selectedCategoryKey: String? = null,
    val selectedValuesByCategoryKey: Map<String, Set<String>> = emptyMap(),
    val dictionaryScrollAnchorEntryId: Int? = null,
    val dictionaryScrollOffsetPx: Int = 0,
    val dictionaryShowFavoritesOnly: Boolean = false,
    val dictionaryFavoritesScrollAnchorEntryId: Int? = null,
    val dictionaryFavoritesScrollOffsetPx: Int = 0,
    val namingSurname: String = "",
    val namingSchemes: List<NamingScheme> = emptyList(),
    val namingActiveSchemeId: Long? = null,
    val namingActiveSlotIndex: Int = 0
)

class UserPrefsRepository(
    private val context: Context,
    private val secureWebDavStore: SecureWebDavStore = EncryptedPrefsSecureWebDavStore(context)
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun readSnapshot(): UserPrefsSnapshot {
        val preferences = context.userPrefsDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        return UserPrefsSnapshot(
            favoriteOrder = decodeFavoriteOrder(preferences[FAVORITE_ORDER_KEY]),
            selectedCategoryKey = preferences[SELECTED_CATEGORY_KEY],
            selectedValuesByCategoryKey = decodeSelectedValues(preferences[SELECTED_VALUES_KEY]),
            dictionaryScrollAnchorEntryId = preferences[DICTIONARY_SCROLL_ANCHOR_ID_KEY],
            dictionaryScrollOffsetPx = preferences[DICTIONARY_SCROLL_OFFSET_KEY] ?: 0,
            dictionaryShowFavoritesOnly = preferences[DICTIONARY_SHOW_FAVORITES_ONLY_KEY] ?: false,
            dictionaryFavoritesScrollAnchorEntryId = preferences[DICTIONARY_FAVORITES_SCROLL_ANCHOR_ID_KEY],
            dictionaryFavoritesScrollOffsetPx = preferences[DICTIONARY_FAVORITES_SCROLL_OFFSET_KEY] ?: 0,
            namingSurname = preferences[NAMING_SURNAME_KEY].orEmpty(),
            namingSchemes = decodeNamingSchemes(preferences[NAMING_SCHEMES_JSON_KEY]),
            namingActiveSchemeId = preferences[NAMING_ACTIVE_SCHEME_ID_KEY],
            namingActiveSlotIndex = preferences[NAMING_ACTIVE_SLOT_INDEX_KEY] ?: 0
        )
    }

    suspend fun readWebDavConfig(): WebDavConfig {
        val preferences = context.userPrefsDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        val securePassword = secureWebDavStore.readPassword()
        val legacyPassword = preferences[WEBDAV_PASSWORD_KEY].orEmpty()
        val password = when {
            securePassword.isNotBlank() -> securePassword
            legacyPassword.isNotBlank() -> {
                secureWebDavStore.writePassword(legacyPassword)
                context.userPrefsDataStore.edit { mutablePreferences ->
                    mutablePreferences.remove(WEBDAV_PASSWORD_KEY)
                }
                legacyPassword
            }
            else -> ""
        }

        return WebDavConfig(
            serverUrl = preferences[WEBDAV_SERVER_URL_KEY].orEmpty(),
            username = preferences[WEBDAV_USERNAME_KEY].orEmpty(),
            password = password
        )
    }

    suspend fun writeFavoritesOrder(order: List<Int>) {
        context.userPrefsDataStore.edit { preferences ->
            preferences[FAVORITE_ORDER_KEY] = json.encodeToString(order)
        }
    }

    suspend fun writeFilterState(categoryKey: String, selectedValues: Map<String, Set<String>>) {
        context.userPrefsDataStore.edit { preferences ->
            preferences[SELECTED_CATEGORY_KEY] = categoryKey
            val serializableValues = selectedValues.mapValues { entry ->
                entry.value.sorted()
            }
            preferences[SELECTED_VALUES_KEY] = json.encodeToString(serializableValues)
        }
    }

    suspend fun writeWebDavConfig(config: WebDavConfig) {
        val normalizedServerUrl = config.serverUrl.trim()
        val normalizedUsername = config.username.trim()
        context.userPrefsDataStore.edit { preferences ->
            preferences[WEBDAV_SERVER_URL_KEY] = normalizedServerUrl
            preferences[WEBDAV_USERNAME_KEY] = normalizedUsername
            preferences.remove(WEBDAV_PASSWORD_KEY)
        }
        if (config.password.isBlank()) {
            secureWebDavStore.clearPassword()
        } else {
            secureWebDavStore.writePassword(config.password)
        }
    }

    suspend fun writeDictionaryScrollState(anchorEntryId: Int?, offsetPx: Int) {
        context.userPrefsDataStore.edit { preferences ->
            if (anchorEntryId == null) {
                preferences.remove(DICTIONARY_SCROLL_ANCHOR_ID_KEY)
            } else {
                preferences[DICTIONARY_SCROLL_ANCHOR_ID_KEY] = anchorEntryId
            }
            preferences[DICTIONARY_SCROLL_OFFSET_KEY] = offsetPx.coerceAtLeast(0)
        }
    }

    suspend fun writeDictionaryShowFavoritesOnly(enabled: Boolean) {
        context.userPrefsDataStore.edit { preferences ->
            preferences[DICTIONARY_SHOW_FAVORITES_ONLY_KEY] = enabled
        }
    }

    suspend fun writeDictionaryFavoritesScrollState(anchorEntryId: Int?, offsetPx: Int) {
        context.userPrefsDataStore.edit { preferences ->
            if (anchorEntryId == null) {
                preferences.remove(DICTIONARY_FAVORITES_SCROLL_ANCHOR_ID_KEY)
            } else {
                preferences[DICTIONARY_FAVORITES_SCROLL_ANCHOR_ID_KEY] = anchorEntryId
            }
            preferences[DICTIONARY_FAVORITES_SCROLL_OFFSET_KEY] = offsetPx.coerceAtLeast(0)
        }
    }

    suspend fun writeNamingDraft(
        surname: String,
        schemes: List<NamingScheme>,
        activeSchemeId: Long?,
        activeSlotIndex: Int
    ) {
        context.userPrefsDataStore.edit { preferences ->
            preferences[NAMING_SURNAME_KEY] = surname
            preferences[NAMING_SCHEMES_JSON_KEY] = json.encodeToString(schemes)
            if (activeSchemeId == null) {
                preferences.remove(NAMING_ACTIVE_SCHEME_ID_KEY)
            } else {
                preferences[NAMING_ACTIVE_SCHEME_ID_KEY] = activeSchemeId
            }
            preferences[NAMING_ACTIVE_SLOT_INDEX_KEY] = activeSlotIndex.coerceIn(0, 1)
        }
    }

    private fun decodeFavoriteOrder(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<Int>>(raw)
        }.getOrElse {
            emptyList()
        }
    }

    private fun decodeSelectedValues(raw: String?): Map<String, Set<String>> {
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        return runCatching {
            json.decodeFromString<Map<String, List<String>>>(raw)
                .mapValues { (_, values) -> values.toSet() }
        }.getOrElse {
            emptyMap()
        }
    }

    private fun decodeNamingSchemes(raw: String?): List<NamingScheme> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<NamingScheme>>(raw)
        }.getOrElse {
            emptyList()
        }
    }

    private companion object {
        val FAVORITE_ORDER_KEY = stringPreferencesKey("favorite_order")
        val SELECTED_CATEGORY_KEY = stringPreferencesKey("selected_category_key")
        val SELECTED_VALUES_KEY = stringPreferencesKey("selected_values_map")
        val DICTIONARY_SCROLL_ANCHOR_ID_KEY = intPreferencesKey("dictionary_scroll_anchor_id")
        val DICTIONARY_SCROLL_OFFSET_KEY = intPreferencesKey("dictionary_scroll_offset_px")
        val DICTIONARY_SHOW_FAVORITES_ONLY_KEY = booleanPreferencesKey("dictionary_show_favorites_only")
        val DICTIONARY_FAVORITES_SCROLL_ANCHOR_ID_KEY = intPreferencesKey("dictionary_favorites_scroll_anchor_id")
        val DICTIONARY_FAVORITES_SCROLL_OFFSET_KEY = intPreferencesKey("dictionary_favorites_scroll_offset_px")
        val NAMING_SURNAME_KEY = stringPreferencesKey("naming_surname")
        val NAMING_SCHEMES_JSON_KEY = stringPreferencesKey("naming_schemes_json")
        val NAMING_ACTIVE_SCHEME_ID_KEY = longPreferencesKey("naming_active_scheme_id")
        val NAMING_ACTIVE_SLOT_INDEX_KEY = intPreferencesKey("naming_active_slot_index")
        val WEBDAV_SERVER_URL_KEY = stringPreferencesKey("webdav_server_url")
        val WEBDAV_USERNAME_KEY = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD_KEY = stringPreferencesKey("webdav_password")
    }
}
