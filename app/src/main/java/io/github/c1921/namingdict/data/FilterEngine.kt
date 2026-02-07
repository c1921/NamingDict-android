package io.github.c1921.namingdict.data

object FilterEngine {
    fun filterIds(
        index: Map<String, Map<String, List<Int>>>,
        selectedValues: Map<IndexCategory, Set<String>>,
        allIds: Set<Int>
    ): Set<Int> {
        var current: Set<Int>? = null
        for (category in IndexCategory.values()) {
            val values = selectedValues[category].orEmpty()
            if (values.isEmpty()) {
                continue
            }
            val categoryMap = index[category.key].orEmpty()
            val union = mutableSetOf<Int>()
            for (value in values) {
                categoryMap[value]?.let { union.addAll(it) }
            }
            current = if (current == null) {
                union
            } else {
                current.intersect(union)
            }
        }
        return current ?: allIds
    }
}
