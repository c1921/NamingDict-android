package io.github.c1921.namingdict.data

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
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
    val selectedValuesByCategoryKey: Map<String, Set<String>> = emptyMap()
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
            selectedValuesByCategoryKey = decodeSelectedValues(preferences[SELECTED_VALUES_KEY])
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
    }
}

