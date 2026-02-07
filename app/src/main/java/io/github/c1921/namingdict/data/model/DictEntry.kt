package io.github.c1921.namingdict.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DictEntry(
    val id: Int = 0,
    val char: String = "",
    val phonetics: Phonetics = Phonetics(),
    val structure: Structure = Structure(),
    val unicode: String = "",
    val gscc: String = "",
    val definitions: List<String> = emptyList()
)

@Serializable
data class Phonetics(
    val pinyin: List<String> = emptyList(),
    val initials: List<String> = emptyList(),
    val finals: List<String> = emptyList(),
    val tones: List<Int> = emptyList()
)

@Serializable
data class Structure(
    val radical: String = "",
    @SerialName("strokes_total")
    val strokesTotal: Int = 0,
    @SerialName("strokes_other")
    val strokesOther: Int = 0,
    @SerialName("structure_type")
    val structureType: String = ""
)
