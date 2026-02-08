package io.github.c1921.namingdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.c1921.namingdict.R
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val DICTIONARY_SCROLL_PERSIST_DEBOUNCE_MS = 250L

@OptIn(FlowPreview::class)
@Composable
internal fun DictionaryScreen(
    uiState: UiState,
    onToggleFavorite: (Int) -> Unit,
    onSelectEntry: (Int) -> Unit,
    showFavoritesOnly: Boolean,
    onSetShowFavoritesOnly: (Boolean) -> Unit,
    savedAnchorEntryId: Int?,
    savedOffsetPx: Int,
    favoritesSavedAnchorEntryId: Int?,
    favoritesSavedOffsetPx: Int,
    onPersistScrollState: (Int?, Int) -> Unit,
    onPersistFavoritesScrollState: (Int?, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dictionaryEntries = uiState.filteredEntries
    val favoritesEntries = uiState.favoriteEntries
    val displayEntries = if (showFavoritesOnly) favoritesEntries else dictionaryEntries

    val initialDictionaryAnchorIndex = savedAnchorEntryId?.let { anchorEntryId ->
        dictionaryEntries.indexOfFirst { entry -> entry.id == anchorEntryId }
    } ?: -1
    val initialDictionaryIndex = if (initialDictionaryAnchorIndex >= 0) initialDictionaryAnchorIndex else 0
    val initialDictionaryOffset = if (initialDictionaryAnchorIndex >= 0) {
        savedOffsetPx.coerceAtLeast(0)
    } else {
        0
    }

    val initialFavoritesAnchorIndex = favoritesSavedAnchorEntryId?.let { anchorEntryId ->
        favoritesEntries.indexOfFirst { entry -> entry.id == anchorEntryId }
    } ?: -1
    val initialFavoritesIndex = if (initialFavoritesAnchorIndex >= 0) initialFavoritesAnchorIndex else 0
    val initialFavoritesOffset = if (initialFavoritesAnchorIndex >= 0) {
        favoritesSavedOffsetPx.coerceAtLeast(0)
    } else {
        0
    }

    val dictionaryListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialDictionaryIndex,
        initialFirstVisibleItemScrollOffset = initialDictionaryOffset
    )
    val favoritesListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialFavoritesIndex,
        initialFirstVisibleItemScrollOffset = initialFavoritesOffset
    )
    val latestOnPersistScrollState by rememberUpdatedState(onPersistScrollState)
    val latestOnPersistFavoritesScrollState by rememberUpdatedState(onPersistFavoritesScrollState)
    val latestDictionaryEntries by rememberUpdatedState(dictionaryEntries)
    val latestFavoritesEntries by rememberUpdatedState(favoritesEntries)

    val activeListState = if (showFavoritesOnly) favoritesListState else dictionaryListState

    LaunchedEffect(dictionaryListState) {
        snapshotFlow {
            dictionaryListState.firstVisibleItemIndex to dictionaryListState.firstVisibleItemScrollOffset
        }
            .map { (firstVisibleIndex, firstVisibleOffset) ->
                latestDictionaryEntries.getOrNull(firstVisibleIndex)?.id to firstVisibleOffset
            }
            .distinctUntilChanged()
            .debounce(DICTIONARY_SCROLL_PERSIST_DEBOUNCE_MS)
            .collect { (anchorEntryId, offsetPx) ->
                latestOnPersistScrollState(anchorEntryId, offsetPx)
            }
    }

    LaunchedEffect(favoritesListState) {
        snapshotFlow {
            favoritesListState.firstVisibleItemIndex to favoritesListState.firstVisibleItemScrollOffset
        }
            .map { (firstVisibleIndex, firstVisibleOffset) ->
                latestFavoritesEntries.getOrNull(firstVisibleIndex)?.id to firstVisibleOffset
            }
            .distinctUntilChanged()
            .debounce(DICTIONARY_SCROLL_PERSIST_DEBOUNCE_MS)
            .collect { (anchorEntryId, offsetPx) ->
                latestOnPersistFavoritesScrollState(anchorEntryId, offsetPx)
            }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.results, displayEntries.size),
                style = MaterialTheme.typography.titleSmall
            )
            DictionaryModeControl(
                showFavoritesOnly = showFavoritesOnly,
                onSetShowFavoritesOnly = onSetShowFavoritesOnly
            )
        }
        HorizontalDivider()

        if (displayEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(
                        if (showFavoritesOnly) R.string.favorites_empty else R.string.no_results
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = activeListState
            ) {
                items(displayEntries.size, key = { displayEntries[it].id }) { index ->
                    val entry = displayEntries[index]
                    DictListItem(
                        entry = entry,
                        isFavorited = uiState.favoriteIds.contains(entry.id),
                        onToggleFavorite = { onToggleFavorite(entry.id) },
                        onClick = {
                            if (showFavoritesOnly) {
                                val anchorEntryId = favoritesEntries
                                    .getOrNull(favoritesListState.firstVisibleItemIndex)
                                    ?.id
                                latestOnPersistFavoritesScrollState(
                                    anchorEntryId,
                                    favoritesListState.firstVisibleItemScrollOffset
                                )
                            } else {
                                val anchorEntryId = dictionaryEntries
                                    .getOrNull(dictionaryListState.firstVisibleItemIndex)
                                    ?.id
                                latestOnPersistScrollState(
                                    anchorEntryId,
                                    dictionaryListState.firstVisibleItemScrollOffset
                                )
                            }
                            onSelectEntry(entry.id)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionaryModeControl(
    showFavoritesOnly: Boolean,
    onSetShowFavoritesOnly: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = !showFavoritesOnly,
            onClick = { onSetShowFavoritesOnly(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
                Icon(
                    imageVector = Icons.Outlined.FilterList,
                    contentDescription = null
                )
            }
        ) {
            Text(text = stringResource(R.string.dictionary_mode_filtered))
        }
        SegmentedButton(
            selected = showFavoritesOnly,
            onClick = { onSetShowFavoritesOnly(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null
                )
            }
        ) {
            Text(text = stringResource(R.string.dictionary_mode_favorites))
        }
    }
}

@Composable
internal fun NewFeaturePlaceholderScreen(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.new_feature_placeholder))
    }
}
