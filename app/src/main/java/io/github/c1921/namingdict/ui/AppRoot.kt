package io.github.c1921.namingdict.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.c1921.namingdict.R

@Composable
fun AppRoot(viewModel: DictViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingUnfavoriteId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDictionarySearchDialog by rememberSaveable { mutableStateOf(false) }
    var dictionarySearchQuery by rememberSaveable { mutableStateOf("") }
    var dictionarySearchErrorResId by rememberSaveable { mutableStateOf<Int?>(null) }
    var reopenDictionarySearchDialogOnBack by rememberSaveable { mutableStateOf(false) }

    val requestToggleFavorite: (Int) -> Unit = { id ->
        if (uiState.favoriteIds.contains(id)) {
            pendingUnfavoriteId = id
        } else {
            viewModel.toggleFavorite(id)
        }
    }
    val selectEntryFromList: (Int) -> Unit = { id ->
        reopenDictionarySearchDialogOnBack = false
        viewModel.selectEntry(id)
    }
    val selectEntryFromSearch: (Int) -> Unit = { id ->
        showDictionarySearchDialog = false
        reopenDictionarySearchDialogOnBack = true
        viewModel.selectEntry(id)
    }

    when {
        uiState.isLoading -> LoadingScreen()
        uiState.error != null -> ErrorScreen(
            message = uiState.error ?: stringResource(R.string.error_loading),
            onRetry = viewModel::reload
        )

        uiState.selectedEntryId != null -> {
            val entry = uiState.idToEntry[uiState.selectedEntryId]
            if (entry == null) {
                ErrorScreen(
                    message = stringResource(R.string.error_loading),
                    onRetry = viewModel::backToList
                )
            } else {
                DictDetailScreen(
                    entry = entry,
                    isFavorited = uiState.favoriteIds.contains(entry.id),
                    onToggleFavorite = { requestToggleFavorite(entry.id) },
                    onBack = {
                        viewModel.backToList()
                        if (reopenDictionarySearchDialogOnBack) {
                            dictionarySearchQuery = ""
                            dictionarySearchErrorResId = null
                            showDictionarySearchDialog = true
                            reopenDictionarySearchDialogOnBack = false
                        }
                    }
                )
            }
        }

        else -> MainTabbedContent(
            uiState = uiState,
            onSelectCategory = viewModel::selectCategory,
            onToggleValue = viewModel::toggleValue,
            onClearCategory = viewModel::clearCategory,
            onClearAll = viewModel::clearAll,
            onUpdateWebDavConfig = viewModel::updateWebDavConfig,
            onManualUploadFavorites = viewModel::manualUploadFavorites,
            onManualDownloadFavorites = viewModel::manualDownloadFavoritesOverwriteLocal,
            onToggleFavorite = requestToggleFavorite,
            onSelectEntry = selectEntryFromList,
            onSelectEntryFromSearch = selectEntryFromSearch,
            onPersistDictionaryScrollState = viewModel::persistDictionaryScrollState,
            onSetDictionaryShowFavoritesOnly = viewModel::setDictionaryShowFavoritesOnly,
            onPersistDictionaryFavoritesScrollState = viewModel::persistDictionaryFavoritesScrollState,
            onUpdateNamingSurname = viewModel::updateNamingSurname,
            onAddNamingScheme = viewModel::addNamingScheme,
            onRemoveNamingScheme = viewModel::removeNamingScheme,
            onSetNamingMode = viewModel::setNamingMode,
            onSetActiveNamingSlot = viewModel::setActiveNamingSlot,
            onUpdateNamingSlotText = viewModel::updateNamingSlotText,
            onFillActiveSlotFromFavorite = viewModel::fillActiveSlotFromFavorite,
            showDictionarySearchDialog = showDictionarySearchDialog,
            onShowDictionarySearchDialogChange = { showDictionarySearchDialog = it },
            dictionarySearchQuery = dictionarySearchQuery,
            onDictionarySearchQueryChange = { dictionarySearchQuery = it },
            dictionarySearchErrorResId = dictionarySearchErrorResId,
            onDictionarySearchErrorResIdChange = { dictionarySearchErrorResId = it }
        )
    }

    pendingUnfavoriteId?.let { id ->
        val entry = uiState.idToEntry[id]
        val displayChar = entry?.char ?: "-"
        AlertDialog(
            onDismissRequest = { pendingUnfavoriteId = null },
            title = { Text(text = stringResource(R.string.unfavorite_confirm_title)) },
            text = { Text(text = stringResource(R.string.unfavorite_confirm_message, displayChar)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.toggleFavorite(id)
                        pendingUnfavoriteId = null
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnfavoriteId = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}
