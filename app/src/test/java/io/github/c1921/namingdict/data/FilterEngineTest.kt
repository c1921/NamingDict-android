package io.github.c1921.namingdict.data

import io.github.c1921.namingdict.data.model.DictEntry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    @Test
    fun filterIds_intersectsAcrossCategories() {
        val index = mapOf(
            "structure.radical" to mapOf(
                "A" to listOf(1, 2),
                "B" to listOf(3)
            ),
            "phonetics.tones" to mapOf(
                "1" to listOf(1, 3),
                "2" to listOf(2)
            )
        )
        val selected = mapOf(
            IndexCategory.StructureRadical to setOf("A"),
            IndexCategory.PhoneticsTones to setOf("1", "2")
        )
        val allIds = setOf(1, 2, 3)
        val result = FilterEngine.filterIds(index, selected, allIds)
        assertEquals(setOf(1, 2), result)
    }

    @Test
    fun filterIds_returnsAllWhenNoSelection() {
        val index = emptyMap<String, Map<String, List<Int>>>()
        val allIds = setOf(1, 2, 3)
        val result = FilterEngine.filterIds(index, emptyMap(), allIds)
        assertEquals(allIds, result)
    }

    @Test
    fun parseDictEntry_withSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = """
            [
              {
                "id": 1,
                "char": "A",
                "phonetics": {
                  "pinyin": ["a"],
                  "initials": ["a"],
                  "finals": ["a"],
                  "tones": [1]
                },
                "structure": {
                  "radical": "A",
                  "strokes_total": 1,
                  "strokes_other": 0,
                  "structure_type": "simple"
                },
                "unicode": "U+0041",
                "gscc": "0001",
                "definitions": ["alpha"]
              }
            ]
        """.trimIndent()

        val entries = json.decodeFromString<List<DictEntry>>(payload)
        assertEquals(1, entries.size)
        assertEquals("A", entries.first().char)
        assertTrue(entries.first().definitions.isNotEmpty())
    }
}
