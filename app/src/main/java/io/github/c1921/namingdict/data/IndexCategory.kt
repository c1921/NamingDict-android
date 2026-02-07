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
    StructureRadical("structure.radical", "部首", ValueSort.Alpha),
    StructureStrokesTotal("structure.strokes_total", "总笔画", ValueSort.Numeric),
    StructureStrokesOther("structure.strokes_other", "部外笔画", ValueSort.Numeric),
    StructureType("structure.structure_type", "结构类型", ValueSort.Alpha),
    PhoneticsInitials("phonetics.initials", "声母", ValueSort.Alpha),
    PhoneticsFinals("phonetics.finals", "韵母", ValueSort.Alpha),
    PhoneticsTones("phonetics.tones", "声调", ValueSort.Numeric)
}

fun sortIndexValues(values: Set<String>, category: IndexCategory): List<String> {
    return when (category.sort) {
        ValueSort.Numeric -> values.sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        ValueSort.Alpha -> values.sorted()
    }
}
