package io.github.c1921.namingdict.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.namingdict.data.DictionaryRepository
import io.github.c1921.namingdict.data.FilterEngine
import io.github.c1921.namingdict.data.IndexCategory
import io.github.c1921.namingdict.data.UserPrefsRepository
import io.github.c1921.namingdict.data.model.DictEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val entries: List<DictEntry> = emptyList(),
    val index: Map<String, Map<String, List<Int>>> = emptyMap(),
    val idToEntry: Map<Int, DictEntry> = emptyMap(),
    val selectedCategory: IndexCategory = IndexCategory.StructureRadical,
    val selectedValues: Map<IndexCategory, Set<String>> = emptyMap(),
    val filteredIds: Set<Int> = emptySet(),
    val filteredEntries: List<DictEntry> = emptyList(),
    val favoriteIds: Set<Int> = emptySet(),
    val favoriteEntries: List<DictEntry> = emptyList(),
    val selectedEntryId: Int? = null
)

class DictViewModel(
    private val repository: DictionaryRepository,
    private val userPrefsRepository: UserPrefsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var entries: List<DictEntry> = emptyList()
    private var index: Map<String, Map<String, List<Int>>> = emptyMap()
    private var idToEntry: Map<Int, DictEntry> = emptyMap()
    private var allIds: Set<Int> = emptySet()
    private var favoriteOrder: List<Int> = emptyList()

    init {
        load()
    }

    fun reload() {
        load()
    }

    fun selectCategory(category: IndexCategory) {
        val updated = _uiState.value.copy(selectedCategory = category)
        _uiState.value = updated
        persistFilterState(
            category = updated.selectedCategory,
            selectedValues = updated.selectedValues
        )
    }

    fun toggleValue(category: IndexCategory, value: String) {
        val current = _uiState.value
        val currentSet = current.selectedValues[category].orEmpty()
        val newSet = if (currentSet.contains(value)) {
            currentSet - value
        } else {
            currentSet + value
        }
        val newSelectedValues = current.selectedValues.toMutableMap().apply {
            if (newSet.isEmpty()) {
                remove(category)
            } else {
                put(category, newSet)
            }
        }
        recomputeFilters(newSelectedValues)
    }

    fun clearCategory(category: IndexCategory) {
        val current = _uiState.value
        if (!current.selectedValues.containsKey(category)) {
            return
        }
        val newSelectedValues = current.selectedValues.toMutableMap().apply {
            remove(category)
        }
        recomputeFilters(newSelectedValues)
    }

    fun clearAll() {
        recomputeFilters(emptyMap())
    }

    fun toggleFavorite(id: Int) {
        val current = _uiState.value
        val isFavorited = current.favoriteIds.contains(id)
        favoriteOrder = if (isFavorited) {
            favoriteOrder.filterNot { it == id }
        } else {
            listOf(id) + favoriteOrder.filterNot { it == id }
        }
        val newFavoriteIds = favoriteOrder.toSet()
        val newFavoriteEntries = favoriteOrder.mapNotNull { favoriteId -> idToEntry[favoriteId] }
        _uiState.value = current.copy(
            favoriteIds = newFavoriteIds,
            favoriteEntries = newFavoriteEntries
        )
        persistFavoritesOrder(favoriteOrder)
    }

    fun selectEntry(id: Int) {
        _uiState.value = _uiState.value.copy(selectedEntryId = id)
    }

    fun backToList() {
        _uiState.value = _uiState.value.copy(selectedEntryId = null)
    }

    private fun load() {
        _uiState.value = UiState(isLoading = true)
        viewModelScope.launch {
            try {
                val data = repository.loadAll()
                val snapshot = userPrefsRepository.readSnapshot()

                entries = data.entries
                index = data.index
                idToEntry = entries.associateBy { it.id }
                allIds = entries.map { it.id }.toSet()

                favoriteOrder = snapshot.favoriteOrder.filter { favoriteId ->
                    idToEntry.containsKey(favoriteId)
                }
                val favoriteIds = favoriteOrder.toSet()
                val favoriteEntries = favoriteOrder.mapNotNull { favoriteId -> idToEntry[favoriteId] }

                val selectedCategory = resolveCategory(snapshot.selectedCategoryKey)
                val selectedValues = sanitizeSelectedValues(snapshot.selectedValuesByCategoryKey)
                val filteredIds = FilterEngine.filterIds(index, selectedValues, allIds)
                val filteredEntries = filteredIds.mapNotNull { idToEntry[it] }.sortedBy { it.id }

                _uiState.value = UiState(
                    isLoading = false,
                    entries = entries,
                    index = index,
                    idToEntry = idToEntry,
                    selectedCategory = selectedCategory,
                    selectedValues = selectedValues,
                    filteredIds = filteredIds,
                    filteredEntries = filteredEntries,
                    favoriteIds = favoriteIds,
                    favoriteEntries = favoriteEntries
                )
            } catch (ex: Exception) {
                _uiState.value = UiState(
                    isLoading = false,
                    error = ex.message ?: "数据加载失败"
                )
            }
        }
    }

    private fun recomputeFilters(selectedValues: Map<IndexCategory, Set<String>>) {
        viewModelScope.launch(Dispatchers.Default) {
            val filteredIds = FilterEngine.filterIds(index, selectedValues, allIds)
            val filteredEntries = filteredIds.mapNotNull { idToEntry[it] }.sortedBy { it.id }
            withContext(Dispatchers.Main) {
                val latest = _uiState.value
                val updated = latest.copy(
                    selectedValues = selectedValues,
                    filteredIds = filteredIds,
                    filteredEntries = filteredEntries
                )
                _uiState.value = updated
                persistFilterState(
                    category = updated.selectedCategory,
                    selectedValues = updated.selectedValues
                )
            }
        }
    }

    private fun resolveCategory(categoryKey: String?): IndexCategory {
        return IndexCategory.entries.firstOrNull { category -> category.key == categoryKey }
            ?: IndexCategory.StructureRadical
    }

    private fun sanitizeSelectedValues(
        selectedValuesByCategoryKey: Map<String, Set<String>>
    ): Map<IndexCategory, Set<String>> {
        if (selectedValuesByCategoryKey.isEmpty()) {
            return emptyMap()
        }
        return buildMap {
            selectedValuesByCategoryKey.forEach { (categoryKey, selectedValues) ->
                val category = IndexCategory.entries.firstOrNull { it.key == categoryKey }
                    ?: return@forEach
                val availableValues = index[category.key].orEmpty().keys
                val cleanedValues = selectedValues.filter { value -> value in availableValues }.toSet()
                if (cleanedValues.isNotEmpty()) {
                    put(category, cleanedValues)
                }
            }
        }
    }

    private fun persistFavoritesOrder(order: List<Int>) {
        val orderSnapshot = order.toList()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                userPrefsRepository.writeFavoritesOrder(orderSnapshot)
            }.onFailure { exception ->
                Log.w(TAG, "Failed to persist favorites order.", exception)
            }
        }
    }

    private fun persistFilterState(
        category: IndexCategory,
        selectedValues: Map<IndexCategory, Set<String>>
    ) {
        val selectedValuesByCategoryKey = selectedValues.mapKeys { (key, _) -> key.key }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                userPrefsRepository.writeFilterState(category.key, selectedValuesByCategoryKey)
            }.onFailure { exception ->
                Log.w(TAG, "Failed to persist filter state.", exception)
            }
        }
    }

    companion object {
        private const val TAG = "DictViewModel"

        fun factory(
            repository: DictionaryRepository,
            userPrefsRepository: UserPrefsRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DictViewModel(repository, userPrefsRepository) as T
                }
            }
        }
    }
}
