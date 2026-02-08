package io.github.c1921.namingdict.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.c1921.namingdict.R
import io.github.c1921.namingdict.data.model.DictEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DictDetailScreen(
    entry: DictEntry,
    isFavorited: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(
                                if (isFavorited) R.string.favorite_remove else R.string.favorite_add
                            )
                        )
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
            HeroHeader(entry = entry)
            Spacer(modifier = Modifier.height(16.dp))

            val metaItems = listOf(
                MetaItem(label = stringResource(R.string.detail_radical), value = entry.structure.radical.ifBlank { "-" }),
                MetaItem(label = stringResource(R.string.detail_strokes_total), value = entry.structure.strokesTotal.toString()),
                MetaItem(label = stringResource(R.string.detail_strokes_other), value = entry.structure.strokesOther.toString()),
                MetaItem(label = stringResource(R.string.detail_structure_type), value = entry.structure.structureType.ifBlank { "-" }),
                MetaItem(label = stringResource(R.string.detail_unicode), value = entry.unicode.ifBlank { "-" }),
                MetaItem(label = stringResource(R.string.detail_gscc), value = entry.gscc.ifBlank { "-" })
            )
            MetaGrid(items = metaItems)

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.detail_definitions), style = MaterialTheme.typography.titleMedium)
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
private fun HeroHeader(entry: DictEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.char,
            style = MaterialTheme.typography.displayLarge,
            fontFamily = HanziFontFamily
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = formatPinyinList(entry.phonetics.pinyin),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private data class MetaItem(
    val label: String,
    val value: String
)

@Composable
private fun MetaGrid(items: List<MetaItem>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isSingleColumn = maxWidth < 340.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSingleColumn) {
                items.forEach { item ->
                    MetaCard(item = item, modifier = Modifier.fillMaxWidth())
                }
            } else {
                items.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetaCard(
                            item = rowItems[0],
                            modifier = Modifier.weight(1f)
                        )
                        if (rowItems.size == 2) {
                            MetaCard(
                                item = rowItems[1],
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaCard(
    item: MetaItem,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = item.label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            )
        }
    }
}
