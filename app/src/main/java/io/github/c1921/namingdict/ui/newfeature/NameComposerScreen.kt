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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.github.c1921.namingdict.R
import io.github.c1921.namingdict.data.model.DictEntry
import io.github.c1921.namingdict.data.model.GivenNameMode
import io.github.c1921.namingdict.data.model.NamingGender
import io.github.c1921.namingdict.data.model.NamingScheme
import io.github.c1921.namingdict.ui.theme.AppTheme

private const val SCHEME_WARNING_THRESHOLD = 50
private const val SURNAME_MAX_CODE_POINTS = 4

@Composable
internal fun NameComposerScreen(
    uiState: UiState,
    onUpdateNamingSurname: (String) -> Unit,
    onAddNamingScheme: () -> Unit,
    onRemoveNamingScheme: (Long) -> Unit,
    onSetNamingMode: (Long, GivenNameMode) -> Unit,
    onSetNamingGender: (Long, NamingGender) -> Unit,
    onSetActiveNamingSlot: (Long, Int) -> Unit,
    onUpdateNamingSlotText: (Long, Int, String) -> Unit,
    onFillActiveSlotFromFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    var showSurnameDialog by rememberSaveable { mutableStateOf(false) }
    var surnameDraft by rememberSaveable { mutableStateOf("") }
    var editingSchemeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingOpenedFromAdd by rememberSaveable { mutableStateOf(false) }
    var editingSnapshotMode by rememberSaveable { mutableStateOf(GivenNameMode.Double) }
    var editingSnapshotGender by rememberSaveable { mutableStateOf(NamingGender.Unisex) }
    var editingSnapshotSlot1 by rememberSaveable { mutableStateOf("") }
    var editingSnapshotSlot2 by rememberSaveable { mutableStateOf("") }
    var preEditActiveSchemeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var preEditActiveSlotIndex by rememberSaveable { mutableStateOf(0) }
    var pendingOpenNewSchemeEditor by rememberSaveable { mutableStateOf(false) }
    var pendingPreEditActiveSchemeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingPreEditActiveSlotIndex by rememberSaveable { mutableStateOf(0) }
    var pendingRemoveSchemeId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCancelEditConfirm by rememberSaveable { mutableStateOf(false) }

    val surnameDisplay = uiState.namingSurname.ifBlank {
        stringResource(R.string.naming_surname_not_set)
    }
    val editingScheme = uiState.namingSchemes.firstOrNull { it.id == editingSchemeId }
    val closeEditorSession: () -> Unit = {
        editingSchemeId = null
        editingOpenedFromAdd = false
        showCancelEditConfirm = false
    }
    val openEditorSession: (NamingScheme, Boolean, Long?, Int) -> Unit =
        { scheme, openedFromAdd, activeSchemeId, activeSlotIndex ->
            editingSchemeId = scheme.id
            editingOpenedFromAdd = openedFromAdd
            editingSnapshotMode = scheme.givenNameMode
            editingSnapshotGender = scheme.gender
            editingSnapshotSlot1 = scheme.slot1
            editingSnapshotSlot2 = scheme.slot2
            preEditActiveSchemeId = activeSchemeId
            preEditActiveSlotIndex = activeSlotIndex.coerceIn(0, 1)
            showCancelEditConfirm = false
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

    LaunchedEffect(pendingRemoveSchemeId, uiState.namingSchemes) {
        val removeId = pendingRemoveSchemeId ?: return@LaunchedEffect
        if (uiState.namingSchemes.none { scheme -> scheme.id == removeId }) {
            pendingRemoveSchemeId = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
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
                .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
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
                    modifier = Modifier.padding(end = spacing.small)
                )
                Text(text = stringResource(R.string.naming_add_scheme))
            }
        }

        if (uiState.namingSchemes.size > SCHEME_WARNING_THRESHOLD) {
            Text(
                text = stringResource(R.string.naming_scheme_count_warning, SCHEME_WARNING_THRESHOLD),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    horizontal = spacing.medium,
                    vertical = spacing.extraSmall
                )
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
                contentPadding = PaddingValues(horizontal = spacing.medium, vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
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
                    val genderLabel = when (scheme.gender) {
                        NamingGender.Unisex -> stringResource(R.string.naming_gender_unisex)
                        NamingGender.Male -> stringResource(R.string.naming_gender_male)
                        NamingGender.Female -> stringResource(R.string.naming_gender_female)
                    }
                    SchemeRowItem(
                        previewText = previewText,
                        genderLabel = genderLabel,
                        onEdit = {
                            openEditorSession(
                                scheme,
                                false,
                                uiState.namingActiveSchemeId,
                                uiState.namingActiveSlotIndex
                            )
                        },
                        onRemove = {
                            pendingRemoveSchemeId = scheme.id
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
        val hasFilledContent = editingScheme.slot1.isNotBlank() || editingScheme.slot2.isNotBlank()
        val hasContentChanged = editingScheme.givenNameMode != editingSnapshotMode ||
            editingScheme.gender != editingSnapshotGender ||
            editingScheme.slot1 != editingSnapshotSlot1 ||
            editingScheme.slot2 != editingSnapshotSlot2
        val shouldConfirmCancel = if (editingOpenedFromAdd) {
            hasFilledContent
        } else {
            hasContentChanged
        }
        val applyCancelEdit: () -> Unit = {
            val editingId = editingScheme.id
            if (editingOpenedFromAdd) {
                onRemoveNamingScheme(editingId)
            } else {
                onSetNamingMode(editingId, editingSnapshotMode)
                onSetNamingGender(editingId, editingSnapshotGender)
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
            onCancel = {
                if (shouldConfirmCancel) {
                    showCancelEditConfirm = true
                } else {
                    applyCancelEdit()
                }
            },
            onSetNamingMode = { mode -> onSetNamingMode(editingScheme.id, mode) },
            onSetNamingGender = { gender -> onSetNamingGender(editingScheme.id, gender) },
            onSetActiveNamingSlot = { slotIndex -> onSetActiveNamingSlot(editingScheme.id, slotIndex) },
            onUpdateNamingSlotText = { slotIndex, value ->
                onUpdateNamingSlotText(editingScheme.id, slotIndex, value)
            },
            onSelectFavoriteChar = { char, slotIndex ->
                onSetActiveNamingSlot(editingScheme.id, slotIndex)
                onFillActiveSlotFromFavorite(char)
            }
        )

        if (showCancelEditConfirm) {
            AlertDialog(
                onDismissRequest = { showCancelEditConfirm = false },
                title = {
                    Text(
                        text = if (editingOpenedFromAdd) {
                            stringResource(R.string.naming_cancel_new_scheme_confirm_title)
                        } else {
                            stringResource(R.string.naming_cancel_edit_confirm_title)
                        }
                    )
                },
                text = {
                    Text(
                        text = if (editingOpenedFromAdd) {
                            stringResource(R.string.naming_cancel_new_scheme_confirm_message)
                        } else {
                            stringResource(R.string.naming_cancel_edit_confirm_message)
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = applyCancelEdit) {
                        Text(text = stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelEditConfirm = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    val pendingRemoveScheme = pendingRemoveSchemeId?.let { removeId ->
        uiState.namingSchemes.firstOrNull { scheme -> scheme.id == removeId }
    }
    if (pendingRemoveScheme != null) {
        val pendingRemovePreview = formatSchemePreview(
            surname = uiState.namingSurname,
            scheme = pendingRemoveScheme,
            placeholder = stringResource(R.string.naming_preview_row_placeholder)
        )
        AlertDialog(
            onDismissRequest = { pendingRemoveSchemeId = null },
            title = { Text(text = stringResource(R.string.naming_remove_scheme_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.naming_remove_scheme_confirm_message,
                        pendingRemovePreview
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editingSchemeId == pendingRemoveScheme.id) {
                            closeEditorSession()
                        }
                        onRemoveNamingScheme(pendingRemoveScheme.id)
                        pendingRemoveSchemeId = null
                    }
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveSchemeId = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SchemeRowItem(
    previewText: String,
    genderLabel: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val spacing = AppTheme.spacing
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.small),
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
            Text(
                text = genderLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.small)
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
    onSetNamingGender: (NamingGender) -> Unit,
    onSetActiveNamingSlot: (Int) -> Unit,
    onUpdateNamingSlotText: (Int, String) -> Unit,
    onSelectFavoriteChar: (String, Int) -> Unit
) {
    val spacing = AppTheme.spacing
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
            Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
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

                Text(
                    text = stringResource(R.string.naming_gender_title),
                    style = MaterialTheme.typography.titleSmall
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = scheme.gender == NamingGender.Unisex,
                        onClick = { onSetNamingGender(NamingGender.Unisex) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text(text = stringResource(R.string.naming_gender_unisex))
                    }
                    SegmentedButton(
                        selected = scheme.gender == NamingGender.Male,
                        onClick = { onSetNamingGender(NamingGender.Male) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text(text = stringResource(R.string.naming_gender_male))
                    }
                    SegmentedButton(
                        selected = scheme.gender == NamingGender.Female,
                        onClick = { onSetNamingGender(NamingGender.Female) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text(text = stringResource(R.string.naming_gender_female))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small)
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
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
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
