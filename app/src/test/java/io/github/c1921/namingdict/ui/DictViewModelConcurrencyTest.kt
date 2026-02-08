package io.github.c1921.namingdict.ui

import io.github.c1921.namingdict.data.DictionaryData
import io.github.c1921.namingdict.data.DictionaryRepository
import io.github.c1921.namingdict.data.FavoritesSyncPayload
import io.github.c1921.namingdict.data.FilterEngine
import io.github.c1921.namingdict.data.IndexCategory
import io.github.c1921.namingdict.data.NamePlansSyncPayload
import io.github.c1921.namingdict.data.SyncResult
import io.github.c1921.namingdict.data.UserPrefsRepository
import io.github.c1921.namingdict.data.UserPrefsSnapshot
import io.github.c1921.namingdict.data.WebDavConfig
import io.github.c1921.namingdict.data.WebDavRepository
import io.github.c1921.namingdict.data.model.DictEntry
import io.github.c1921.namingdict.data.model.GivenNameMode
import io.github.c1921.namingdict.data.model.NamingScheme
import io.github.c1921.namingdict.data.model.Phonetics
import io.github.c1921.namingdict.data.model.Structure
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DictViewModelConcurrencyTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var dictionaryRepository: DictionaryRepository
    private lateinit var userPrefsRepository: UserPrefsRepository
    private lateinit var webDavRepository: WebDavRepository

    @Before
    fun setup() {
        dictionaryRepository = mockk()
        userPrefsRepository = mockk()
        webDavRepository = mockk()
    }

    @Test
    fun recomputeFilters_latestWins_whenRapidChanges() = runMainTest {
        val viewModel = createViewModel(
            filterIdsCalculator = { inputIndex, selectedValues, allIds ->
                if (selectedValues[IndexCategory.StructureRadical]?.contains("A") == true) {
                    delay(200)
                } else {
                    delay(10)
                }
                FilterEngine.filterIds(inputIndex, selectedValues, allIds)
            }
        )
        advanceUntilIdle()

        viewModel.toggleValue(IndexCategory.StructureRadical, "A")
        runCurrent()
        viewModel.clearAll()

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(emptyMap<IndexCategory, Set<String>>(), state.selectedValues)
        assertEquals(setOf(1, 2, 3), state.filteredIds)
    }

    @Test
    fun manualDownload_rejected_whenManualUploadInProgress() = runMainTest {
        val viewModel = createViewModel(
            webDavConfig = COMPLETE_HTTPS_CONFIG,
            uploadStub = { _, _ ->
                delay(1_000)
                SyncResult(success = true, message = "上传成功")
            }
        )
        advanceUntilIdle()

        viewModel.manualUploadFavorites()
        runCurrent()
        assertTrue(viewModel.uiState.value.syncInProgress)

        viewModel.manualDownloadFavoritesOverwriteLocal()
        runCurrent()

        assertEquals("同步进行中，请稍后", viewModel.uiState.value.lastSyncMessage)
        coVerify(exactly = 0) { webDavRepository.downloadFavorites(any()) }
        coVerify(exactly = 0) { webDavRepository.downloadNamePlans(any()) }
        advanceUntilIdle()
    }

    @Test
    fun autoSync_conflictWithManualUpload_skipsSilently() = runMainTest {
        val viewModel = createViewModel(
            webDavConfig = COMPLETE_HTTPS_CONFIG,
            autoUploadDelayMs = 100,
            uploadStub = { _, _ ->
                delay(1_000)
                SyncResult(success = true, message = "上传成功")
            },
            namePlansUploadStub = { _, _ ->
                delay(1_000)
                SyncResult(success = true, message = "上传成功")
            }
        )
        advanceUntilIdle()

        viewModel.manualUploadFavorites()
        runCurrent()
        assertTrue(viewModel.uiState.value.syncInProgress)

        viewModel.toggleFavorite(1)
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        coVerify(exactly = 1) { webDavRepository.uploadFavorites(any(), any()) }
        coVerify(exactly = 0) { webDavRepository.uploadNamePlans(any(), any()) }
        assertNull(viewModel.uiState.value.lastSyncMessage)

        advanceUntilIdle()
        coVerify(exactly = 1) { webDavRepository.uploadFavorites(any(), any()) }
        coVerify(exactly = 1) { webDavRepository.uploadNamePlans(any(), any()) }
    }

    @Test
    fun manualUpload_failureResult_resetsSyncFlag() = runMainTest {
        val viewModel = createViewModel(
            webDavConfig = COMPLETE_HTTPS_CONFIG,
            uploadStub = { _, _ ->
                SyncResult(success = false, message = "上传失败：HTTP 500")
            },
            namePlansUploadStub = { _, _ ->
                SyncResult(success = true, message = "上传成功")
            }
        )
        advanceUntilIdle()

        viewModel.manualUploadFavorites()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.syncInProgress)
        val message = viewModel.uiState.value.lastSyncMessage.orEmpty()
        assertTrue(message.contains("收藏：上传失败"))
        assertTrue(message.contains("起名方案：上传成功"))
    }

    @Test
    fun manualDownload_failureResult_resetsSyncFlag() = runMainTest {
        val viewModel = createViewModel(
            webDavConfig = COMPLETE_HTTPS_CONFIG,
            downloadStub = {
                Result.failure(IllegalStateException("下载失败：HTTP 500"))
            },
            namePlansDownloadStub = {
                Result.success(null)
            }
        )
        advanceUntilIdle()

        viewModel.manualDownloadFavoritesOverwriteLocal()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.syncInProgress)
        val message = viewModel.uiState.value.lastSyncMessage.orEmpty()
        assertTrue(message.contains("收藏：下载失败"))
        assertTrue(message.contains("起名方案：远端不存在"))
    }

    @Test
    fun filterChange_resetsOnlyDictionaryScrollState() = runMainTest {
        val viewModel = createViewModel(
            snapshot = UserPrefsSnapshot(
                dictionaryScrollAnchorEntryId = 2,
                dictionaryScrollOffsetPx = 64,
                dictionaryFavoritesScrollAnchorEntryId = 3,
                dictionaryFavoritesScrollOffsetPx = 96
            )
        )
        advanceUntilIdle()

        viewModel.toggleValue(IndexCategory.StructureRadical, "A")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.dictionaryScrollAnchorEntryId)
        assertEquals(0, state.dictionaryScrollOffsetPx)
        assertEquals(3, state.dictionaryFavoritesScrollAnchorEntryId)
        assertEquals(96, state.dictionaryFavoritesScrollOffsetPx)
        coVerify { userPrefsRepository.writeDictionaryScrollState(null, 0) }
        coVerify(exactly = 0) { userPrefsRepository.writeDictionaryFavoritesScrollState(any(), any()) }
    }

    @Test
    fun namingSurname_truncatedToFourCodePoints() = runMainTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateNamingSurname("abcde")
        advanceUntilIdle()

        assertEquals("abcd", viewModel.uiState.value.namingSurname)
    }

    @Test
    fun namingSlot_truncatedToSingleCodePoint_withEmoji() = runMainTest {
        val viewModel = createViewModel(
            snapshot = UserPrefsSnapshot(
                namingSchemes = listOf(
                    NamingScheme(id = 10L, givenNameMode = GivenNameMode.Double)
                ),
                namingActiveSchemeId = 10L,
                namingActiveSlotIndex = 0
            )
        )
        advanceUntilIdle()

        viewModel.updateNamingSlotText(id = 10L, slotIndex = 0, value = "😀x")
        advanceUntilIdle()

        val scheme = viewModel.uiState.value.namingSchemes.first()
        assertEquals("😀", scheme.slot1)
    }

    @Test
    fun fillActiveSlotFromFavorite_usesActiveSlot() = runMainTest {
        val viewModel = createViewModel(
            snapshot = UserPrefsSnapshot(
                namingSchemes = listOf(
                    NamingScheme(id = 10L, givenNameMode = GivenNameMode.Double)
                ),
                namingActiveSchemeId = 10L,
                namingActiveSlotIndex = 1
            )
        )
        advanceUntilIdle()

        viewModel.fillActiveSlotFromFavorite("a")
        advanceUntilIdle()

        val scheme = viewModel.uiState.value.namingSchemes.first()
        assertEquals("", scheme.slot1)
        assertEquals("a", scheme.slot2)
    }

    @Test
    fun namingMode_switchKeepsSecondSlotValue() = runMainTest {
        val viewModel = createViewModel(
            snapshot = UserPrefsSnapshot(
                namingSchemes = listOf(
                    NamingScheme(
                        id = 10L,
                        givenNameMode = GivenNameMode.Double,
                        slot1 = "a",
                        slot2 = "b"
                    )
                ),
                namingActiveSchemeId = 10L,
                namingActiveSlotIndex = 0
            )
        )
        advanceUntilIdle()

        viewModel.setNamingMode(10L, GivenNameMode.Single)
        viewModel.setNamingMode(10L, GivenNameMode.Double)
        advanceUntilIdle()

        val scheme = viewModel.uiState.value.namingSchemes.first()
        assertEquals("a", scheme.slot1)
        assertEquals("b", scheme.slot2)
    }

    @Test
    fun toggleFavorite_doesNotMutateNamingSchemes() = runMainTest {
        val viewModel = createViewModel(
            snapshot = UserPrefsSnapshot(
                namingSchemes = listOf(
                    NamingScheme(
                        id = 10L,
                        givenNameMode = GivenNameMode.Double,
                        slot1 = "x",
                        slot2 = "y"
                    )
                ),
                namingActiveSchemeId = 10L,
                namingActiveSlotIndex = 1
            )
        )
        advanceUntilIdle()

        viewModel.toggleFavorite(1)
        advanceUntilIdle()

        val scheme = viewModel.uiState.value.namingSchemes.first()
        assertEquals("x", scheme.slot1)
        assertEquals("y", scheme.slot2)
    }

    @Test
    fun namingEdit_triggersAutoUploadDebounce() = runMainTest {
        val viewModel = createViewModel(
            snapshot = UserPrefsSnapshot(
                namingSchemes = listOf(
                    NamingScheme(id = 10L, givenNameMode = GivenNameMode.Double)
                ),
                namingActiveSchemeId = 10L,
                namingActiveSlotIndex = 0
            ),
            webDavConfig = COMPLETE_HTTPS_CONFIG,
            autoUploadDelayMs = 100
        )
        advanceUntilIdle()

        viewModel.updateNamingSlotText(id = 10L, slotIndex = 0, value = "a")
        runCurrent()

        advanceTimeBy(99)
        runCurrent()
        coVerify(exactly = 0) { webDavRepository.uploadFavorites(any(), any()) }
        coVerify(exactly = 0) { webDavRepository.uploadNamePlans(any(), any()) }

        advanceTimeBy(1)
        runCurrent()

        coVerify(exactly = 1) { webDavRepository.uploadFavorites(any(), any()) }
        coVerify(exactly = 1) { webDavRepository.uploadNamePlans(any(), any()) }
    }

    private fun runMainTest(block: suspend TestScope.() -> Unit) {
        runTest(context = mainDispatcherRule.dispatcher, testBody = block)
    }

    private fun createViewModel(
        snapshot: UserPrefsSnapshot = UserPrefsSnapshot(),
        webDavConfig: WebDavConfig = WebDavConfig(),
        autoUploadDelayMs: Long = 30_000L,
        nowProvider: () -> Long = { 1_700_000_000_000L },
        filterIdsCalculator: FilterIdsCalculator = { inputIndex, selectedValues, allIds ->
            FilterEngine.filterIds(inputIndex, selectedValues, allIds)
        },
        uploadStub: suspend (WebDavConfig, FavoritesSyncPayload) -> SyncResult = { _, _ ->
            SyncResult(success = true, message = "上传成功")
        },
        namePlansUploadStub: suspend (WebDavConfig, NamePlansSyncPayload) -> SyncResult = { _, _ ->
            SyncResult(success = true, message = "上传成功")
        },
        downloadStub: suspend (WebDavConfig) -> Result<FavoritesSyncPayload> = {
            Result.success(FavoritesSyncPayload(favoriteOrder = listOf(1)))
        },
        namePlansDownloadStub: suspend (WebDavConfig) -> Result<NamePlansSyncPayload?> = {
            Result.success(null)
        }
    ): DictViewModel {
        coEvery { dictionaryRepository.loadAll() } returns dictionaryData()
        coEvery { userPrefsRepository.readSnapshot() } returns snapshot
        coEvery { userPrefsRepository.readWebDavConfig() } returns webDavConfig
        coEvery { webDavRepository.uploadFavorites(any(), any()) } coAnswers {
            uploadStub(firstArg(), secondArg())
        }
        coEvery { webDavRepository.uploadNamePlans(any(), any()) } coAnswers {
            namePlansUploadStub(firstArg(), secondArg())
        }
        coEvery { webDavRepository.downloadFavorites(any()) } coAnswers {
            downloadStub(firstArg())
        }
        coEvery { webDavRepository.downloadNamePlans(any()) } coAnswers {
            namePlansDownloadStub(firstArg())
        }

        coJustRun { userPrefsRepository.writeFavoritesOrder(any()) }
        coJustRun { userPrefsRepository.writeFilterState(any(), any()) }
        coJustRun { userPrefsRepository.writeWebDavConfig(any()) }
        coJustRun { userPrefsRepository.writeDictionaryScrollState(any(), any()) }
        coJustRun { userPrefsRepository.writeDictionaryShowFavoritesOnly(any()) }
        coJustRun { userPrefsRepository.writeDictionaryFavoritesScrollState(any(), any()) }
        coJustRun { userPrefsRepository.writeNamingDraft(any(), any(), any(), any()) }

        return DictViewModel(
            repository = dictionaryRepository,
            userPrefsRepository = userPrefsRepository,
            webDavRepository = webDavRepository,
            dispatcherProvider = DispatcherProvider(
                main = mainDispatcherRule.dispatcher,
                io = mainDispatcherRule.dispatcher,
                default = mainDispatcherRule.dispatcher
            ),
            autoUploadDelayMs = autoUploadDelayMs,
            nowProvider = nowProvider,
            filterIdsCalculator = filterIdsCalculator
        )
    }

    private fun dictionaryData(): DictionaryData {
        val entries = listOf(
            DictEntry(
                id = 1,
                char = "a",
                phonetics = Phonetics(tones = listOf(1)),
                structure = Structure(radical = "A", strokesTotal = 5, strokesOther = 3, structureType = "simple")
            ),
            DictEntry(
                id = 2,
                char = "b",
                phonetics = Phonetics(tones = listOf(2)),
                structure = Structure(radical = "B", strokesTotal = 1, strokesOther = 0, structureType = "simple")
            ),
            DictEntry(
                id = 3,
                char = "c",
                phonetics = Phonetics(tones = listOf(3)),
                structure = Structure(radical = "A", strokesTotal = 6, strokesOther = 4, structureType = "left-right")
            )
        )
        val index = mapOf(
            "structure.radical" to mapOf(
                "A" to listOf(1, 3),
                "B" to listOf(2)
            )
        )
        return DictionaryData(entries = entries, index = index)
    }

    private companion object {
        val COMPLETE_HTTPS_CONFIG = WebDavConfig(
            serverUrl = "https://example.com/webdav",
            username = "user",
            password = "pass"
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
