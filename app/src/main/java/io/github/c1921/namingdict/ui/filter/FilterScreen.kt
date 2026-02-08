package io.github.c1921.namingdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.c1921.namingdict.R
import io.github.c1921.namingdict.data.IndexCategory
import io.github.c1921.namingdict.data.sortIndexValues
import io.github.c1921.namingdict.ui.theme.AppTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun FilterScreen(
    uiState: UiState,
    onSelectCategory: (IndexCategory) -> Unit,
    onToggleValue: (IndexCategory, String) -> Unit,
    onClearCategory: (IndexCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    val selectedCategory = uiState.selectedCategory
    val pagerState = rememberPagerState(
        initialPage = selectedCategory.ordinal,
        pageCount = { IndexCategory.entries.size }
    )
    val latestSelectedCategory by rememberUpdatedState(selectedCategory)
    val latestOnSelectCategory by rememberUpdatedState(onSelectCategory)

    LaunchedEffect(selectedCategory.ordinal) {
        if (pagerState.currentPage != selectedCategory.ordinal) {
            pagerState.animateScrollToPage(selectedCategory.ordinal)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .filter { page -> page in IndexCategory.entries.indices }
            .collect { page ->
                val category = IndexCategory.entries[page]
                if (category != latestSelectedCategory) {
                    latestOnSelectCategory(category)
                }
            }
    }

    val selectedPairs = remember(uiState.selectedValues) {
        uiState.selectedValues.entries
            .sortedBy { it.key.ordinal }
            .flatMap { entry ->
                entry.value.sorted().map { value -> entry.key to value }
            }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedCategory.ordinal,
            edgePadding = spacing.medium,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            IndexCategory.entries.forEach { category ->
                Tab(
                    selected = category == selectedCategory,
                    onClick = { onSelectCategory(category) },
                    text = { Text(text = category.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        if (selectedPairs.isNotEmpty()) {
            Text(
                text = stringResource(R.string.selected_filters),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.small)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                items(selectedPairs.size) { index ->
                    val (category, value) = selectedPairs[index]
                    val displayValue = displayFilterValue(category = category, value = value)
                    FilterChip(
                        selected = true,
                        onClick = { onToggleValue(category, value) },
                        shape = MaterialTheme.shapes.large,
                        label = { Text(text = "${category.label}: $displayValue") }
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            val category = IndexCategory.entries[page]
            val categoryValues = uiState.index[category.key].orEmpty()
            val sortedValues = remember(categoryValues, category) {
                sortIndexValues(categoryValues.keys, category)
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.large, vertical = spacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (uiState.selectedValues[category].orEmpty().isNotEmpty()) {
                        TextButton(onClick = { onClearCategory(category) }) {
                            Text(text = stringResource(R.string.clear_category))
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .padding(horizontal = spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                    contentPadding = PaddingValues(bottom = spacing.small)
                ) {
                    items(sortedValues, key = { it }) { value ->
                        val displayValue = displayFilterValue(category = category, value = value)
                        val isSelected = uiState.selectedValues[category].orEmpty().contains(value)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleValue(category, value) },
                            shape = MaterialTheme.shapes.large,
                            label = { Text(text = displayValue) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun displayFilterValue(category: IndexCategory, value: String): String {
    return when (category) {
        IndexCategory.PhoneticsInitials -> {
            if (value.isBlank()) stringResource(R.string.filter_empty_initials) else value
        }
        IndexCategory.PhoneticsFinals -> {
            if (value.isBlank()) stringResource(R.string.filter_empty_finals) else value
        }
        IndexCategory.PhoneticsTones -> {
            when (value.trim()) {
                "1" -> stringResource(R.string.filter_tone_first)
                "2" -> stringResource(R.string.filter_tone_second)
                "3" -> stringResource(R.string.filter_tone_third)
                "4" -> stringResource(R.string.filter_tone_fourth)
                "5", "0" -> stringResource(R.string.filter_tone_light)
                else -> value
            }
        }
        else -> value
    }
}
