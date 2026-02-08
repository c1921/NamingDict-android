package io.github.c1921.namingdict.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.c1921.namingdict.R
import io.github.c1921.namingdict.data.IndexCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainTabbedContent(
    uiState: UiState,
    onSelectCategory: (IndexCategory) -> Unit,
    onToggleValue: (IndexCategory, String) -> Unit,
    onClearCategory: (IndexCategory) -> Unit,
    onClearAll: () -> Unit,
    onUpdateWebDavConfig: (String, String, String) -> Unit,
    onManualUploadFavorites: () -> Unit,
    onManualDownloadFavorites: () -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onSelectEntry: (Int) -> Unit,
    onSelectEntryFromSearch: (Int) -> Unit,
    onPersistDictionaryScrollState: (Int?, Int) -> Unit,
    onSetDictionaryShowFavoritesOnly: (Boolean) -> Unit,
    onPersistDictionaryFavoritesScrollState: (Int?, Int) -> Unit,
    showDictionarySearchDialog: Boolean,
    onShowDictionarySearchDialogChange: (Boolean) -> Unit,
    dictionarySearchQuery: String,
    onDictionarySearchQueryChange: (String) -> Unit,
    dictionarySearchErrorResId: Int?,
    onDictionarySearchErrorResIdChange: (Int?) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Dictionary) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Home) }
    val charToEntryId = remember(uiState.entries) {
        uiState.entries.associate { entry -> entry.char to entry.id }
    }
    val showClearAll = selectedTab == MainTab.Filter && uiState.selectedValues.values.any { it.isNotEmpty() }
    val isSettingsSubPage = selectedTab == MainTab.Settings && settingsPage != SettingsPage.Home
    val submitDictionarySearch: () -> Unit = {
        val normalized = dictionarySearchQuery.trim()
        val codePointCount = normalized.codePointCount(0, normalized.length)
        when {
            codePointCount != 1 -> {
                onDictionarySearchErrorResIdChange(R.string.dictionary_search_error_single_char)
            }

            else -> {
                val entryId = charToEntryId[normalized]
                if (entryId == null) {
                    onDictionarySearchErrorResIdChange(R.string.dictionary_search_error_not_found)
                } else {
                    onDictionarySearchErrorResIdChange(null)
                    onSelectEntryFromSearch(entryId)
                }
            }
        }
    }

    BackHandler(enabled = isSettingsSubPage) {
        settingsPage = SettingsPage.Home
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleResId = if (selectedTab == MainTab.Settings) {
                        settingsPage.titleResId
                    } else {
                        selectedTab.titleResId
                    }
                    Text(text = stringResource(titleResId))
                },
                navigationIcon = {
                    if (isSettingsSubPage) {
                        IconButton(onClick = { settingsPage = SettingsPage.Home }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                actions = {
                    if (selectedTab == MainTab.Filter) {
                        Text(
                            text = stringResource(
                                R.string.filter_results_compact,
                                uiState.filteredEntries.size
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    if (selectedTab == MainTab.Dictionary) {
                        IconButton(
                            onClick = {
                                onShowDictionarySearchDialogChange(true)
                                onDictionarySearchErrorResIdChange(null)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.dictionary_search_open)
                            )
                        }
                    }
                    if (showClearAll) {
                        TextButton(onClick = onClearAll) {
                            Text(text = stringResource(R.string.clear_all))
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = {
                            selectedTab = tab
                            if (tab != MainTab.Settings) {
                                settingsPage = SettingsPage.Home
                            }
                        },
                        icon = {
                            val icon = when (tab) {
                                MainTab.Filter -> Icons.Outlined.FilterList
                                MainTab.Dictionary -> Icons.AutoMirrored.Outlined.MenuBook
                                MainTab.NewFeature -> Icons.Outlined.StarBorder
                                MainTab.Settings -> Icons.Outlined.Settings
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = stringResource(tab.titleResId)
                            )
                        },
                        label = { Text(text = stringResource(tab.titleResId)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            MainTab.Filter -> FilterScreen(
                uiState = uiState,
                onSelectCategory = onSelectCategory,
                onToggleValue = onToggleValue,
                onClearCategory = onClearCategory,
                modifier = Modifier.padding(innerPadding)
            )

            MainTab.Dictionary -> DictionaryScreen(
                uiState = uiState,
                onToggleFavorite = onToggleFavorite,
                onSelectEntry = onSelectEntry,
                showFavoritesOnly = uiState.dictionaryShowFavoritesOnly,
                onSetShowFavoritesOnly = onSetDictionaryShowFavoritesOnly,
                savedAnchorEntryId = uiState.dictionaryScrollAnchorEntryId,
                savedOffsetPx = uiState.dictionaryScrollOffsetPx,
                favoritesSavedAnchorEntryId = uiState.dictionaryFavoritesScrollAnchorEntryId,
                favoritesSavedOffsetPx = uiState.dictionaryFavoritesScrollOffsetPx,
                onPersistScrollState = onPersistDictionaryScrollState,
                onPersistFavoritesScrollState = onPersistDictionaryFavoritesScrollState,
                modifier = Modifier.padding(innerPadding)
            )

            MainTab.NewFeature -> NewFeaturePlaceholderScreen(modifier = Modifier.padding(innerPadding))

            MainTab.Settings -> SettingsScreen(
                uiState = uiState,
                onUpdateWebDavConfig = onUpdateWebDavConfig,
                onManualUploadFavorites = onManualUploadFavorites,
                onManualDownloadFavorites = onManualDownloadFavorites,
                settingsPage = settingsPage,
                onOpenBackupRestore = { settingsPage = SettingsPage.BackupRestore },
                onOpenAbout = { settingsPage = SettingsPage.About },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }

    if (showDictionarySearchDialog) {
        AlertDialog(
            onDismissRequest = { onShowDictionarySearchDialogChange(false) },
            title = { Text(text = stringResource(R.string.dictionary_search_title)) },
            text = {
                OutlinedTextField(
                    value = dictionarySearchQuery,
                    onValueChange = { value ->
                        onDictionarySearchQueryChange(value)
                        if (dictionarySearchErrorResId != null) {
                            onDictionarySearchErrorResIdChange(null)
                        }
                    },
                    placeholder = {
                        Text(text = stringResource(R.string.dictionary_search_hint))
                    },
                    singleLine = true,
                    isError = dictionarySearchErrorResId != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { submitDictionarySearch() }
                    ),
                    supportingText = {
                        dictionarySearchErrorResId?.let { errorResId ->
                            Text(text = stringResource(errorResId))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = submitDictionarySearch) {
                    Text(text = stringResource(R.string.dictionary_search_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowDictionarySearchDialogChange(false) }) {
                    Text(text = stringResource(R.string.dictionary_search_cancel))
                }
            }
        )
    }
}
