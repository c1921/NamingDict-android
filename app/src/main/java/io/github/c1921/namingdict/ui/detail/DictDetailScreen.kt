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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.c1921.namingdict.R
import io.github.c1921.namingdict.data.model.DictEntry
import io.github.c1921.namingdict.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DictDetailScreen(
    entry: DictEntry,
    isFavorited: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit
) {
    val spacing = AppTheme.spacing
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
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
                .padding(spacing.large)
                .verticalScroll(rememberScrollState())
        ) {
            HeroHeader(entry = entry)
            Spacer(modifier = Modifier.height(spacing.large))

            val metaItems = listOf(
                MetaItem(label = stringResource(R.string.detail_radical), value = entry.structure.radical.ifBlank { "-" }),
                MetaItem(label = stringResource(R.string.detail_strokes_total), value = entry.structure.strokesTotal.toString()),
                MetaItem(label = stringResource(R.string.detail_strokes_other), value = entry.structure.strokesOther.toString()),
                MetaItem(label = stringResource(R.string.detail_structure_type), value = entry.structure.structureType.ifBlank { "-" }),
                MetaItem(label = stringResource(R.string.detail_unicode), value = entry.unicode.ifBlank { "-" }),
                MetaItem(label = stringResource(R.string.detail_gscc), value = entry.gscc.ifBlank { "-" })
            )
            MetaGrid(items = metaItems)

            Spacer(modifier = Modifier.height(spacing.large))

            Text(text = stringResource(R.string.detail_definitions), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(spacing.small))
            if (entry.definitions.isEmpty()) {
                Text(text = "-", style = MaterialTheme.typography.bodyMedium)
            } else {
                entry.definitions.forEachIndexed { index, definition ->
                    Text(
                        text = "${index + 1}. $definition",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                }
            }
        }
    }
}

@Composable
private fun HeroHeader(entry: DictEntry) {
    val spacing = AppTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.large),
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
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val spacing = AppTheme.spacing
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isSingleColumn = maxWidth < 340.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            if (isSingleColumn) {
                items.forEach { item ->
                    MetaCard(item = item, modifier = Modifier.fillMaxWidth())
                }
            } else {
                items.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small)
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
    val spacing = AppTheme.spacing
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = item.label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .padding(start = spacing.medium)
                    .weight(1f)
            )
        }
    }
}
