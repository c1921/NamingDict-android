package io.github.c1921.namingdict.data

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    val dictionaryScrollOffsetPx: Int = 0
)

class UserPrefsRepository(private val context: Context) {
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
            dictionaryScrollOffsetPx = preferences[DICTIONARY_SCROLL_OFFSET_KEY] ?: 0
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

        return WebDavConfig(
            serverUrl = preferences[WEBDAV_SERVER_URL_KEY].orEmpty(),
            username = preferences[WEBDAV_USERNAME_KEY].orEmpty(),
            password = preferences[WEBDAV_PASSWORD_KEY].orEmpty()
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
        context.userPrefsDataStore.edit { preferences ->
            preferences[WEBDAV_SERVER_URL_KEY] = config.serverUrl.trim()
            preferences[WEBDAV_USERNAME_KEY] = config.username.trim()
            preferences[WEBDAV_PASSWORD_KEY] = config.password
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

    private companion object {
        val FAVORITE_ORDER_KEY = stringPreferencesKey("favorite_order")
        val SELECTED_CATEGORY_KEY = stringPreferencesKey("selected_category_key")
        val SELECTED_VALUES_KEY = stringPreferencesKey("selected_values_map")
        val DICTIONARY_SCROLL_ANCHOR_ID_KEY = intPreferencesKey("dictionary_scroll_anchor_id")
        val DICTIONARY_SCROLL_OFFSET_KEY = intPreferencesKey("dictionary_scroll_offset_px")
        val WEBDAV_SERVER_URL_KEY = stringPreferencesKey("webdav_server_url")
        val WEBDAV_USERNAME_KEY = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD_KEY = stringPreferencesKey("webdav_password")
    }
}
