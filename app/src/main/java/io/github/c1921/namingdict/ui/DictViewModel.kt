package io.github.c1921.namingdict.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.c1921.namingdict.data.DictionaryRepository
import io.github.c1921.namingdict.data.FavoritesSyncPayload
import io.github.c1921.namingdict.data.FilterEngine
import io.github.c1921.namingdict.data.IndexCategory
import io.github.c1921.namingdict.data.SyncResult
import io.github.c1921.namingdict.data.UserPrefsRepository
import io.github.c1921.namingdict.data.WebDavConfig
import io.github.c1921.namingdict.data.WebDavRepository
import io.github.c1921.namingdict.data.model.DictEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal data class DispatcherProvider(
    val main: CoroutineDispatcher = Dispatchers.Main,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default
)

internal typealias FilterIdsCalculator =
    suspend (
        index: Map<String, Map<String, List<Int>>>,
        selectedValues: Map<IndexCategory, Set<String>>,
        allIds: Set<Int>
    ) -> Set<Int>

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
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val syncInProgress: Boolean = false,
    val lastSyncMessage: String? = null,
    val selectedEntryId: Int? = null,
    val dictionaryScrollAnchorEntryId: Int? = null,
    val dictionaryScrollOffsetPx: Int = 0,
    val dictionaryShowFavoritesOnly: Boolean = false,
    val dictionaryFavoritesScrollAnchorEntryId: Int? = null,
    val dictionaryFavoritesScrollOffsetPx: Int = 0
)

class DictViewModel internal constructor(
    private val repository: DictionaryRepository,
    private val userPrefsRepository: UserPrefsRepository,
    private val webDavRepository: WebDavRepository,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider(),
    private val autoUploadDelayMs: Long = AUTO_UPLOAD_DELAY_MS,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val filterIdsCalculator: FilterIdsCalculator =
        { index, selectedValues, allIds -> FilterEngine.filterIds(index, selectedValues, allIds) }
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private var entries: List<DictEntry> = emptyList()
    private var index: Map<String, Map<String, List<Int>>> = emptyMap()
    private var idToEntry: Map<Int, DictEntry> = emptyMap()
    private var allIds: Set<Int> = emptySet()
    private var favoriteOrder: List<Int> = emptyList()
    private var autoUploadJob: Job? = null
    private var recomputeFiltersJob: Job? = null
    private val syncMutex = Mutex()

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
        scheduleAutoUploadFavorites()
    }

    fun updateWebDavConfig(
        serverUrl: String,
        username: String,
        password: String
    ) {
        val newConfig = WebDavConfig(
            serverUrl = serverUrl.trim(),
            username = username.trim(),
            password = password
        )
        if (newConfig.serverUrl.isNotBlank() && !newConfig.isHttps()) {
            postSyncMessage(HTTPS_REQUIRED_SAVE_MESSAGE)
            return
        }
        _uiState.value = _uiState.value.copy(
            webDavConfig = newConfig,
            lastSyncMessage = "WebDAV 配置已保存"
        )
        viewModelScope.launch(dispatcherProvider.io) {
            runCatching {
                userPrefsRepository.writeWebDavConfig(newConfig)
            }.onFailure { exception ->
                Log.w(TAG, "Failed to persist WebDAV config.", exception)
                postSyncMessage("保存配置失败：${exception.message ?: "未知错误"}")
            }
        }
    }

    fun manualUploadFavorites() {
        autoUploadJob?.cancel()
        viewModelScope.launch(dispatcherProvider.main) {
            runSyncExclusively(isAuto = false) {
                uploadFavoritesNow(isAuto = false)
            }
        }
    }

    fun manualDownloadFavoritesOverwriteLocal() {
        autoUploadJob?.cancel()
        viewModelScope.launch(dispatcherProvider.main) {
            runSyncExclusively(isAuto = false) {
                val config = _uiState.value.webDavConfig
                val httpsError = validateWebDavHttps(config, isAuto = false)
                if (httpsError != null) {
                    postSyncMessage(httpsError)
                    return@runSyncExclusively
                }
                if (!config.isComplete()) {
                    postSyncMessage("WebDAV 配置不完整，无法下载")
                    return@runSyncExclusively
                }

                _uiState.value = _uiState.value.copy(syncInProgress = true)
                try {
                    val downloadResult = withContext(dispatcherProvider.io) {
                        webDavRepository.downloadFavorites(config)
                    }
                    downloadResult.fold(
                        onSuccess = { payload ->
                            val sanitizedOrder = payload.favoriteOrder
                                .distinct()
                                .filter { favoriteId -> idToEntry.containsKey(favoriteId) }
                            favoriteOrder = sanitizedOrder
                            val favoriteIds = sanitizedOrder.toSet()
                            val favoriteEntries = sanitizedOrder.mapNotNull { idToEntry[it] }
                            _uiState.value = _uiState.value.copy(
                                favoriteIds = favoriteIds,
                                favoriteEntries = favoriteEntries,
                                lastSyncMessage = "下载成功，已覆盖本地收藏（${favoriteIds.size}）"
                            )
                            persistFavoritesOrder(sanitizedOrder)
                        },
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(
                                lastSyncMessage = "下载失败：${exception.message ?: "网络异常"}"
                            )
                        }
                    )
                } finally {
                    _uiState.value = _uiState.value.copy(syncInProgress = false)
                }
            }
        }
    }

    fun selectEntry(id: Int) {
        _uiState.value = _uiState.value.copy(selectedEntryId = id)
    }

    fun backToList() {
        _uiState.value = _uiState.value.copy(selectedEntryId = null)
    }

    fun persistDictionaryScrollState(anchorEntryId: Int?, offsetPx: Int) {
        val sanitizedOffsetPx = offsetPx.coerceAtLeast(0)
        val current = _uiState.value
        if (
            current.dictionaryScrollAnchorEntryId == anchorEntryId &&
            current.dictionaryScrollOffsetPx == sanitizedOffsetPx
        ) {
            return
        }

        _uiState.value = current.copy(
            dictionaryScrollAnchorEntryId = anchorEntryId,
            dictionaryScrollOffsetPx = sanitizedOffsetPx
        )
        persistDictionaryScrollStateToStorage(anchorEntryId = anchorEntryId, offsetPx = sanitizedOffsetPx)
    }

    fun setDictionaryShowFavoritesOnly(enabled: Boolean) {
        val current = _uiState.value
        if (current.dictionaryShowFavoritesOnly == enabled) {
            return
        }
        _uiState.value = current.copy(dictionaryShowFavoritesOnly = enabled)
        persistDictionaryShowFavoritesOnlyToStorage(enabled)
    }

    fun persistDictionaryFavoritesScrollState(anchorEntryId: Int?, offsetPx: Int) {
        val sanitizedOffsetPx = offsetPx.coerceAtLeast(0)
        val current = _uiState.value
        if (
            current.dictionaryFavoritesScrollAnchorEntryId == anchorEntryId &&
            current.dictionaryFavoritesScrollOffsetPx == sanitizedOffsetPx
        ) {
            return
        }

        _uiState.value = current.copy(
            dictionaryFavoritesScrollAnchorEntryId = anchorEntryId,
            dictionaryFavoritesScrollOffsetPx = sanitizedOffsetPx
        )
        persistDictionaryFavoritesScrollStateToStorage(
            anchorEntryId = anchorEntryId,
            offsetPx = sanitizedOffsetPx
        )
    }

    private fun load() {
        _uiState.value = UiState(isLoading = true)
        viewModelScope.launch(dispatcherProvider.main) {
            try {
                val data = repository.loadAll()
                val snapshot = userPrefsRepository.readSnapshot()
                val webDavConfig = userPrefsRepository.readWebDavConfig()

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
                val filteredIds = filterIdsCalculator(index, selectedValues, allIds)
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
                    favoriteEntries = favoriteEntries,
                    webDavConfig = webDavConfig,
                    dictionaryScrollAnchorEntryId = snapshot.dictionaryScrollAnchorEntryId,
                    dictionaryScrollOffsetPx = snapshot.dictionaryScrollOffsetPx.coerceAtLeast(0),
                    dictionaryShowFavoritesOnly = snapshot.dictionaryShowFavoritesOnly,
                    dictionaryFavoritesScrollAnchorEntryId = snapshot.dictionaryFavoritesScrollAnchorEntryId,
                    dictionaryFavoritesScrollOffsetPx = snapshot.dictionaryFavoritesScrollOffsetPx.coerceAtLeast(0)
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
        recomputeFiltersJob?.cancel()
        recomputeFiltersJob = viewModelScope.launch(dispatcherProvider.main) {
            val (filteredIds, filteredEntries) = withContext(dispatcherProvider.default) {
                val ids = filterIdsCalculator(index, selectedValues, allIds)
                val entries = ids.mapNotNull { idToEntry[it] }.sortedBy { it.id }
                ids to entries
            }

            val latest = _uiState.value
            val filterChanged = latest.selectedValues != selectedValues
            val updated = latest.copy(
                selectedValues = selectedValues,
                filteredIds = filteredIds,
                filteredEntries = filteredEntries,
                dictionaryScrollAnchorEntryId = if (filterChanged) null else latest.dictionaryScrollAnchorEntryId,
                dictionaryScrollOffsetPx = if (filterChanged) 0 else latest.dictionaryScrollOffsetPx
            )
            _uiState.value = updated
            persistFilterState(
                category = updated.selectedCategory,
                selectedValues = updated.selectedValues
            )
            if (filterChanged) {
                persistDictionaryScrollStateToStorage(anchorEntryId = null, offsetPx = 0)
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
        viewModelScope.launch(dispatcherProvider.io) {
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
        viewModelScope.launch(dispatcherProvider.io) {
            runCatching {
                userPrefsRepository.writeFilterState(category.key, selectedValuesByCategoryKey)
            }.onFailure { exception ->
                Log.w(TAG, "Failed to persist filter state.", exception)
            }
        }
    }

    private fun persistDictionaryScrollStateToStorage(anchorEntryId: Int?, offsetPx: Int) {
        val sanitizedOffsetPx = offsetPx.coerceAtLeast(0)
        viewModelScope.launch(dispatcherProvider.io) {
            runCatching {
                userPrefsRepository.writeDictionaryScrollState(anchorEntryId, sanitizedOffsetPx)
            }.onFailure { exception ->
                Log.w(TAG, "Failed to persist dictionary scroll state.", exception)
            }
        }
    }

    private fun persistDictionaryShowFavoritesOnlyToStorage(enabled: Boolean) {
        viewModelScope.launch(dispatcherProvider.io) {
            runCatching {
                userPrefsRepository.writeDictionaryShowFavoritesOnly(enabled)
            }.onFailure { exception ->
                Log.w(TAG, "Failed to persist dictionary favorites-only state.", exception)
            }
        }
    }

    private fun persistDictionaryFavoritesScrollStateToStorage(anchorEntryId: Int?, offsetPx: Int) {
        val sanitizedOffsetPx = offsetPx.coerceAtLeast(0)
        viewModelScope.launch(dispatcherProvider.io) {
            runCatching {
                userPrefsRepository.writeDictionaryFavoritesScrollState(anchorEntryId, sanitizedOffsetPx)
            }.onFailure { exception ->
                Log.w(TAG, "Failed to persist dictionary favorites scroll state.", exception)
            }
        }
    }

    private fun scheduleAutoUploadFavorites() {
        autoUploadJob?.cancel()
        autoUploadJob = viewModelScope.launch(dispatcherProvider.main) {
            delay(autoUploadDelayMs)
            runSyncExclusively(isAuto = true) {
                uploadFavoritesNow(isAuto = true)
            }
        }
    }

    private suspend fun uploadFavoritesNow(isAuto: Boolean) {
        val config = _uiState.value.webDavConfig
        val httpsError = validateWebDavHttps(config, isAuto = isAuto)
        if (httpsError != null) {
            postSyncMessage(httpsError)
            return
        }
        if (!config.isComplete()) {
            val message = if (isAuto) {
                "自动同步已跳过：WebDAV 配置不完整"
            } else {
                "WebDAV 配置不完整，无法上传"
            }
            postSyncMessage(message)
            return
        }

        _uiState.value = _uiState.value.copy(syncInProgress = true)
        try {
            val payload = FavoritesSyncPayload(
                version = 1,
                updatedAt = nowProvider(),
                favoriteOrder = favoriteOrder.toList()
            )
            val syncResult = withContext(dispatcherProvider.io) {
                webDavRepository.uploadFavorites(config, payload)
            }
            applyUploadResult(syncResult = syncResult, isAuto = isAuto)
        } finally {
            _uiState.value = _uiState.value.copy(syncInProgress = false)
        }
    }

    private fun applyUploadResult(syncResult: SyncResult, isAuto: Boolean) {
        val message = if (isAuto) {
            "自动同步：${syncResult.message}"
        } else {
            syncResult.message
        }
        _uiState.value = _uiState.value.copy(
            lastSyncMessage = message
        )
    }

    private suspend fun runSyncExclusively(isAuto: Boolean, block: suspend () -> Unit) {
        if (!syncMutex.tryLock()) {
            if (!isAuto) {
                postSyncMessage(SYNC_IN_PROGRESS_MESSAGE)
            }
            return
        }
        try {
            block()
        } finally {
            syncMutex.unlock()
        }
    }

    private fun postSyncMessage(message: String) {
        _uiState.value = _uiState.value.copy(lastSyncMessage = message)
    }

    private fun validateWebDavHttps(config: WebDavConfig, isAuto: Boolean): String? {
        if (config.serverUrl.isBlank() || config.isHttps()) {
            return null
        }
        return if (isAuto) {
            "自动同步已跳过：$HTTPS_REQUIRED_MESSAGE"
        } else {
            HTTPS_REQUIRED_MESSAGE
        }
    }

    companion object {
        private const val TAG = "DictViewModel"
        private const val AUTO_UPLOAD_DELAY_MS = 30_000L
        private const val HTTPS_REQUIRED_MESSAGE = "WebDAV 地址必须使用 HTTPS（https://）"
        private const val HTTPS_REQUIRED_SAVE_MESSAGE = "仅支持 HTTPS WebDAV 地址，请使用 https://"
        private const val SYNC_IN_PROGRESS_MESSAGE = "同步进行中，请稍后"

        fun factory(
            repository: DictionaryRepository,
            userPrefsRepository: UserPrefsRepository,
            webDavRepository: WebDavRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DictViewModel(repository, userPrefsRepository, webDavRepository) as T
                }
            }
        }
    }
}
