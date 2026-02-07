package io.github.c1921.namingdict.data

enum class ValueSort {
    Alpha,
    Numeric
}

enum class IndexCategory(
    val key: String,
    val label: String,
    val sort: ValueSort
) {
    StructureRadical("structure.radical", "Radical", ValueSort.Alpha),
    StructureStrokesTotal("structure.strokes_total", "Strokes Total", ValueSort.Numeric),
    StructureStrokesOther("structure.strokes_other", "Strokes Other", ValueSort.Numeric),
    StructureType("structure.structure_type", "Structure Type", ValueSort.Alpha),
    PhoneticsPinyin("phonetics.pinyin", "Pinyin", ValueSort.Alpha),
    PhoneticsInitials("phonetics.initials", "Initials", ValueSort.Alpha),
    PhoneticsFinals("phonetics.finals", "Finals", ValueSort.Alpha),
    PhoneticsTones("phonetics.tones", "Tones", ValueSort.Numeric)
}

fun sortIndexValues(values: Set<String>, category: IndexCategory): List<String> {
    return when (category.sort) {
        ValueSort.Numeric -> values.sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        ValueSort.Alpha -> values.sorted()
    }
}
