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
    val selectedEntryId: Int? = null
)

class DictViewModel(private val repository: DictionaryRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var entries: List<DictEntry> = emptyList()
    private var index: Map<String, Map<String, List<Int>>> = emptyMap()
    private var idToEntry: Map<Int, DictEntry> = emptyMap()
    private var allIds: Set<Int> = emptySet()

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
                val sortedEntries = entries.sortedBy { it.id }
                _uiState.value = UiState(
                    isLoading = false,
                    entries = entries,
                    index = index,
                    idToEntry = idToEntry,
                    filteredIds = allIds,
                    filteredEntries = sortedEntries
                )
            } catch (ex: Exception) {
                _uiState.value = UiState(
                    isLoading = false,
                    error = ex.message ?: "Failed to load data"
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
