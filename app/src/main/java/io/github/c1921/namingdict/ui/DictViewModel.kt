package io.github.c1921.namingdict.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.namingdict.data.DictionaryRepository
import io.github.c1921.namingdict.data.FilterEngine
import io.github.c1921.namingdict.data.IndexCategory
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

class DictViewModel(private val repository: DictionaryRepository) : ViewModel() {
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
        _uiState.value = _uiState.value.copy(selectedCategory = category)
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
        val newFavoriteIds = if (isFavorited) {
            current.favoriteIds - id
        } else {
            current.favoriteIds + id
        }
        favoriteOrder = if (isFavorited) {
            favoriteOrder.filterNot { it == id }
        } else {
            listOf(id) + favoriteOrder.filterNot { it == id }
        }
        val newFavoriteEntries = favoriteOrder.mapNotNull { favoriteId -> idToEntry[favoriteId] }
        _uiState.value = current.copy(
            favoriteIds = newFavoriteIds,
            favoriteEntries = newFavoriteEntries
        )
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
                entries = data.entries
                index = data.index
                idToEntry = entries.associateBy { it.id }
                allIds = entries.map { it.id }.toSet()
                favoriteOrder = favoriteOrder.filter { idToEntry.containsKey(it) }
                val favoriteIds = favoriteOrder.toSet()
                val favoriteEntries = favoriteOrder.mapNotNull { favoriteId -> idToEntry[favoriteId] }
                val sortedEntries = entries.sortedBy { it.id }
                _uiState.value = UiState(
                    isLoading = false,
                    entries = entries,
                    index = index,
                    idToEntry = idToEntry,
                    filteredIds = allIds,
                    filteredEntries = sortedEntries,
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
                _uiState.value = latest.copy(
                    selectedValues = selectedValues,
                    filteredIds = filteredIds,
                    filteredEntries = filteredEntries
                )
            }
        }
    }

    companion object {
        fun factory(repository: DictionaryRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DictViewModel(repository) as T
                }
            }
        }
    }
}
