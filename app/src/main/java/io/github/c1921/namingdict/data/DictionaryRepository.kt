package io.github.c1921.namingdict.data

import android.content.Context
import io.github.c1921.namingdict.data.model.DictEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

data class DictionaryData(
    val entries: List<DictEntry>,
    val index: Map<String, Map<String, List<Int>>>
)

class DictionaryRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun loadAll(): DictionaryData = withContext(Dispatchers.IO) {
        val entriesText = readAssetText("data/dict.json")
        val indexText = readAssetText("data/index.json")
        val entries = json.decodeFromString<List<DictEntry>>(entriesText)
        val index = json.decodeFromString<Map<String, Map<String, List<Int>>>>(indexText)
        DictionaryData(entries = entries, index = index)
    }

    private fun readAssetText(path: String): String {
        return context.assets.open(path).use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }
}
