package io.github.c1921.namingdict.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.c1921.namingdict.R
import io.github.c1921.namingdict.data.IndexCategory
import io.github.c1921.namingdict.data.sortIndexValues
import io.github.c1921.namingdict.data.model.DictEntry

@Composable
fun AppRoot(viewModel: DictViewModel) {
    val uiState by viewModel.uiState.collectAsState()

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
                DictDetailScreen(entry = entry, onBack = viewModel::backToList)
            }
        }
        else -> DictListScreen(
            uiState = uiState,
            onSelectCategory = viewModel::selectCategory,
            onToggleValue = viewModel::toggleValue,
            onClearCategory = viewModel::clearCategory,
            onClearAll = viewModel::clearAll,
            onSelectEntry = viewModel::selectEntry
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.loading))
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictListScreen(
    uiState: UiState,
    onSelectCategory: (IndexCategory) -> Unit,
    onToggleValue: (IndexCategory, String) -> Unit,
    onClearCategory: (IndexCategory) -> Unit,
    onClearAll: () -> Unit,
    onSelectEntry: (Int) -> Unit
) {
    val hasFilters = uiState.selectedValues.values.any { it.isNotEmpty() }
    val selectedCategory = uiState.selectedCategory
    val categoryValues = uiState.index[selectedCategory.key].orEmpty()
    val sortedValues = remember(categoryValues, selectedCategory) {
        sortIndexValues(categoryValues.keys, selectedCategory)
    }
    val selectedPairs = remember(uiState.selectedValues) {
        uiState.selectedValues.entries
            .sortedBy { it.key.ordinal }
            .flatMap { entry ->
                entry.value.sorted().map { value -> entry.key to value }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.app_name)) },
                actions = {
                    if (hasFilters) {
                        TextButton(onClick = onClearAll) {
                            Text(text = stringResource(R.string.clear_all))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedCategory.ordinal) {
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedPairs.size) { index ->
                        val (category, value) = selectedPairs[index]
                        FilterChip(
                            selected = true,
                            onClick = { onToggleValue(category, value) },
                            label = { Text(text = "${category.label}: $value") }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedCategory.label,
                    style = MaterialTheme.typography.titleMedium
                )
                if (uiState.selectedValues[selectedCategory].orEmpty().isNotEmpty()) {
                    TextButton(onClick = { onClearCategory(selectedCategory) }) {
                        Text(text = stringResource(R.string.clear_category))
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(sortedValues, key = { it }) { value ->
                    val isSelected = uiState.selectedValues[selectedCategory].orEmpty().contains(value)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleValue(selectedCategory, value) },
                        label = { Text(text = value) }
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.results, uiState.filteredEntries.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.filteredEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = stringResource(R.string.no_results))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.filteredEntries.size, key = { uiState.filteredEntries[it].id }) { index ->
                        val entry = uiState.filteredEntries[index]
                        DictListItem(entry = entry, onClick = { onSelectEntry(entry.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DictListItem(entry: DictEntry, onClick: () -> Unit) {
    val pinyin = entry.phonetics.pinyin.joinToString(" ").ifBlank { "-" }
    val definition = entry.definitions.firstOrNull().orEmpty().ifBlank { "-" }
    ListItem(
        headlineContent = { Text(text = entry.char) },
        supportingContent = { Text(text = "$pinyin - $definition", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictDetailScreen(entry: DictEntry, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = entry.char) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = entry.char, style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(16.dp))

            DetailRow(label = "Pinyin", value = formatList(entry.phonetics.pinyin))
            DetailRow(label = "Initials", value = formatList(entry.phonetics.initials))
            DetailRow(label = "Finals", value = formatList(entry.phonetics.finals))
            DetailRow(label = "Tones", value = formatIntList(entry.phonetics.tones))

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(label = "Radical", value = entry.structure.radical.ifBlank { "-" })
            DetailRow(label = "Strokes Total", value = entry.structure.strokesTotal.toString())
            DetailRow(label = "Strokes Other", value = entry.structure.strokesOther.toString())
            DetailRow(label = "Structure Type", value = entry.structure.structureType.ifBlank { "-" })

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(label = "Unicode", value = entry.unicode.ifBlank { "-" })
            DetailRow(label = "GSCC", value = entry.gscc.ifBlank { "-" })

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Definitions", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (entry.definitions.isEmpty()) {
                Text(text = "-")
            } else {
                entry.definitions.forEachIndexed { index, definition ->
                    Text(text = "${index + 1}. $definition")
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatList(values: List<String>): String {
    if (values.isEmpty()) {
        return "-"
    }
    return values.joinToString(" ")
}

private fun formatIntList(values: List<Int>): String {
    if (values.isEmpty()) {
        return "-"
    }
    return values.joinToString(" ")
}
