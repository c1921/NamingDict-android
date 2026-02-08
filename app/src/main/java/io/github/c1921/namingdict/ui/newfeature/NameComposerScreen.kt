package io.github.c1921.namingdict.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.c1921.namingdict.R
import io.github.c1921.namingdict.data.model.DictEntry
import io.github.c1921.namingdict.data.model.GivenNameMode
import io.github.c1921.namingdict.data.model.NamingScheme

private const val SCHEME_WARNING_THRESHOLD = 50
private const val SURNAME_MAX_CODE_POINTS = 4

@Composable
internal fun NameComposerScreen(
    uiState: UiState,
    onUpdateNamingSurname: (String) -> Unit,
    onAddNamingScheme: () -> Unit,
    onRemoveNamingScheme: (Long) -> Unit,
    onSetNamingMode: (Long, GivenNameMode) -> Unit,
    onSetActiveNamingSlot: (Long, Int) -> Unit,
    onUpdateNamingSlotText: (Long, Int, String) -> Unit,
    onFillActiveSlotFromFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSurnameDialog by rememberSaveable { mutableStateOf(false) }
    var surnameDraft by rememberSaveable { mutableStateOf("") }
    var editingSchemeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingOpenedFromAdd by rememberSaveable { mutableStateOf(false) }
    var editingSnapshotMode by rememberSaveable { mutableStateOf(GivenNameMode.Double) }
    var editingSnapshotSlot1 by rememberSaveable { mutableStateOf("") }
    var editingSnapshotSlot2 by rememberSaveable { mutableStateOf("") }
    var preEditActiveSchemeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var preEditActiveSlotIndex by rememberSaveable { mutableStateOf(0) }
    var pendingOpenNewSchemeEditor by rememberSaveable { mutableStateOf(false) }
    var pendingPreEditActiveSchemeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingPreEditActiveSlotIndex by rememberSaveable { mutableStateOf(0) }

    val surnameDisplay = uiState.namingSurname.ifBlank {
        stringResource(R.string.naming_surname_not_set)
    }
    val editingScheme = uiState.namingSchemes.firstOrNull { it.id == editingSchemeId }
    val closeEditorSession: () -> Unit = {
        editingSchemeId = null
        editingOpenedFromAdd = false
    }
    val openEditorSession: (NamingScheme, Boolean, Long?, Int) -> Unit =
        { scheme, openedFromAdd, activeSchemeId, activeSlotIndex ->
            editingSchemeId = scheme.id
            editingOpenedFromAdd = openedFromAdd
            editingSnapshotMode = scheme.givenNameMode
            editingSnapshotSlot1 = scheme.slot1
            editingSnapshotSlot2 = scheme.slot2
            preEditActiveSchemeId = activeSchemeId
            preEditActiveSlotIndex = activeSlotIndex.coerceIn(0, 1)
        }

    LaunchedEffect(editingSchemeId, uiState.namingSchemes) {
        if (editingSchemeId != null && editingScheme == null) {
            closeEditorSession()
        }
    }

    LaunchedEffect(
        pendingOpenNewSchemeEditor,
        uiState.namingActiveSchemeId,
        uiState.namingSchemes
    ) {
        if (!pendingOpenNewSchemeEditor) {
            return@LaunchedEffect
        }
        val activeSchemeId = uiState.namingActiveSchemeId
        val activeScheme = activeSchemeId?.let { id ->
            uiState.namingSchemes.firstOrNull { scheme -> scheme.id == id }
        }
        if (activeScheme != null) {
            openEditorSession(
                activeScheme,
                true,
                pendingPreEditActiveSchemeId,
                pendingPreEditActiveSlotIndex
            )
            pendingOpenNewSchemeEditor = false
            pendingPreEditActiveSchemeId = null
            pendingPreEditActiveSlotIndex = 0
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.naming_surname_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = {
                            surnameDraft = uiState.namingSurname
                            showSurnameDialog = true
                        }
                    ) {
                        Text(text = stringResource(R.string.naming_surname_edit))
                    }
                }
                Text(
                    text = surnameDisplay,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = HanziFontFamily,
                    color = if (uiState.namingSurname.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.naming_schemes_count, uiState.namingSchemes.size),
                style = MaterialTheme.typography.titleSmall
            )
            Button(
                onClick = {
                    pendingPreEditActiveSchemeId = uiState.namingActiveSchemeId
                    pendingPreEditActiveSlotIndex = uiState.namingActiveSlotIndex.coerceIn(0, 1)
                    pendingOpenNewSchemeEditor = true
                    onAddNamingScheme()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(text = stringResource(R.string.naming_add_scheme))
            }
        }

        if (uiState.namingSchemes.size > SCHEME_WARNING_THRESHOLD) {
            Text(
                text = stringResource(R.string.naming_scheme_count_warning, SCHEME_WARNING_THRESHOLD),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        if (uiState.namingSchemes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.naming_empty_schemes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = uiState.namingSchemes,
                    key = { _, item -> item.id }
                ) { _, scheme ->
                    val previewText = formatSchemePreview(
                        surname = uiState.namingSurname,
                        scheme = scheme,
                        placeholder = stringResource(R.string.naming_preview_row_placeholder)
                    )
                    SchemeRowItem(
                        previewText = previewText,
                        onEdit = {
                            openEditorSession(
                                scheme,
                                false,
                                uiState.namingActiveSchemeId,
                                uiState.namingActiveSlotIndex
                            )
                        },
                        onRemove = {
                            if (editingSchemeId == scheme.id) {
                                closeEditorSession()
                            }
                            onRemoveNamingScheme(scheme.id)
                        }
                    )
                }
            }
        }
    }

    if (showSurnameDialog) {
        val surnameCodePointCount = surnameDraft.codePointCount(0, surnameDraft.length)
        AlertDialog(
            onDismissRequest = { showSurnameDialog = false },
            title = { Text(text = stringResource(R.string.naming_surname_edit_title)) },
            text = {
                OutlinedTextField(
                    value = surnameDraft,
                    onValueChange = { value ->
                        surnameDraft = takeLeadingCodePoints(value, SURNAME_MAX_CODE_POINTS)
                    },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.naming_surname_label)) },
                    supportingText = {
                        Text(
                            text = stringResource(
                                R.string.naming_surname_length,
                                surnameCodePointCount,
                                SURNAME_MAX_CODE_POINTS
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateNamingSurname(surnameDraft)
                        showSurnameDialog = false
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSurnameDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (editingScheme != null) {
        val editingTitle = formatSchemePreview(
            surname = uiState.namingSurname,
            scheme = editingScheme,
            placeholder = stringResource(R.string.naming_preview_row_placeholder)
        )
        val onCancelEdit: () -> Unit = {
            val editingId = editingScheme.id
            if (editingOpenedFromAdd) {
                onRemoveNamingScheme(editingId)
            } else {
                onSetNamingMode(editingId, editingSnapshotMode)
                onUpdateNamingSlotText(editingId, 0, editingSnapshotSlot1)
                onUpdateNamingSlotText(editingId, 1, editingSnapshotSlot2)
            }
            preEditActiveSchemeId?.let { activeSchemeId ->
                onSetActiveNamingSlot(activeSchemeId, preEditActiveSlotIndex)
            }
            closeEditorSession()
        }
        SchemeEditorDialog(
            titleText = editingTitle,
            scheme = editingScheme,
            favoriteEntries = uiState.favoriteEntries,
            onDismissRequest = closeEditorSession,
            onConfirm = closeEditorSession,
            onCancel = onCancelEdit,
            onSetNamingMode = { mode -> onSetNamingMode(editingScheme.id, mode) },
            onSetActiveNamingSlot = { slotIndex -> onSetActiveNamingSlot(editingScheme.id, slotIndex) },
            onUpdateNamingSlotText = { slotIndex, value ->
                onUpdateNamingSlotText(editingScheme.id, slotIndex, value)
            },
            onSelectFavoriteChar = { char, slotIndex ->
                onSetActiveNamingSlot(editingScheme.id, slotIndex)
                onFillActiveSlotFromFavorite(char)
            }
        )
    }
}

@Composable
private fun SchemeRowItem(
    previewText: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = previewText,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = HanziFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.naming_remove_scheme)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchemeEditorDialog(
    titleText: String,
    scheme: NamingScheme,
    favoriteEntries: List<DictEntry>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onSetNamingMode: (GivenNameMode) -> Unit,
    onSetActiveNamingSlot: (Int) -> Unit,
    onUpdateNamingSlotText: (Int, String) -> Unit,
    onSelectFavoriteChar: (String, Int) -> Unit
) {
    var slot1Focused by rememberSaveable(scheme.id) { mutableStateOf(false) }
    var slot2Focused by rememberSaveable(scheme.id) { mutableStateOf(false) }

    LaunchedEffect(scheme.givenNameMode) {
        if (scheme.givenNameMode == GivenNameMode.Single) {
            slot2Focused = false
        }
    }

    val activeSlotIndex = when {
        slot1Focused -> 0
        slot2Focused && scheme.givenNameMode == GivenNameMode.Double -> 1
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = titleText)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = scheme.givenNameMode == GivenNameMode.Single,
                        onClick = { onSetNamingMode(GivenNameMode.Single) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(text = stringResource(R.string.naming_mode_single))
                    }
                    SegmentedButton(
                        selected = scheme.givenNameMode == GivenNameMode.Double,
                        onClick = { onSetNamingMode(GivenNameMode.Double) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(text = stringResource(R.string.naming_mode_double))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = scheme.slot1,
                        onValueChange = { value -> onUpdateNamingSlotText(0, value) },
                        label = { Text(text = stringResource(R.string.naming_slot_first)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    slot1Focused = true
                                    slot2Focused = false
                                    onSetActiveNamingSlot(0)
                                } else {
                                    slot1Focused = false
                                }
                            }
                    )
                    if (scheme.givenNameMode == GivenNameMode.Double) {
                        OutlinedTextField(
                            value = scheme.slot2,
                            onValueChange = { value -> onUpdateNamingSlotText(1, value) },
                            label = { Text(text = stringResource(R.string.naming_slot_second)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        slot2Focused = true
                                        slot1Focused = false
                                        onSetActiveNamingSlot(1)
                                    } else {
                                        slot2Focused = false
                                    }
                                }
                        )
                    }
                }

                if (activeSlotIndex == null) {
                    Text(
                        text = stringResource(R.string.naming_dialog_focus_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.naming_dialog_favorites_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (favoriteEntries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.naming_favorites_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(items = favoriteEntries, key = { entry -> entry.id }) { entry ->
                                SuggestionChip(
                                    onClick = {
                                        onSelectFavoriteChar(entry.char, activeSlotIndex)
                                    },
                                    label = {
                                        Text(
                                            text = entry.char,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontFamily = HanziFontFamily
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatSchemePreview(
    surname: String,
    scheme: NamingScheme,
    placeholder: String
): String {
    val givenName = if (scheme.givenNameMode == GivenNameMode.Single) {
        scheme.slot1
    } else {
        scheme.slot1 + scheme.slot2
    }
    return (surname + givenName).ifBlank { placeholder }
}

private fun takeLeadingCodePoints(value: String, limit: Int): String {
    if (limit <= 0 || value.isEmpty()) {
        return ""
    }
    val codePointCount = value.codePointCount(0, value.length)
    if (codePointCount <= limit) {
        return value
    }
    val endIndex = value.offsetByCodePoints(0, limit)
    return value.substring(0, endIndex)
}
