package io.github.c1921.namingdict.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.c1921.namingdict.BuildConfig
import io.github.c1921.namingdict.R
import io.github.c1921.namingdict.data.IndexCategory
import io.github.c1921.namingdict.data.sortIndexValues
import io.github.c1921.namingdict.data.model.DictEntry

private val HanziFontFamily = FontFamily.Serif

private enum class MainTab(val titleResId: Int) {
    Filter(R.string.tab_filter),
    Dictionary(R.string.tab_dictionary),
    Favorites(R.string.tab_favorites),
    Settings(R.string.tab_settings)
}

private enum class SettingsPage(val titleResId: Int) {
    Home(R.string.tab_settings),
    BackupRestore(R.string.settings_backup_restore_title),
    About(R.string.settings_about_title)
}

@Composable
fun AppRoot(viewModel: DictViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingUnfavoriteId by rememberSaveable { mutableStateOf<Int?>(null) }

    val requestToggleFavorite: (Int) -> Unit = { id ->
        if (uiState.favoriteIds.contains(id)) {
            pendingUnfavoriteId = id
        } else {
            viewModel.toggleFavorite(id)
        }
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
                    onBack = viewModel::backToList
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
            onSelectEntry = viewModel::selectEntry
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
private fun MainTabbedContent(
    uiState: UiState,
    onSelectCategory: (IndexCategory) -> Unit,
    onToggleValue: (IndexCategory, String) -> Unit,
    onClearCategory: (IndexCategory) -> Unit,
    onClearAll: () -> Unit,
    onUpdateWebDavConfig: (String, String, String) -> Unit,
    onManualUploadFavorites: () -> Unit,
    onManualDownloadFavorites: () -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onSelectEntry: (Int) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Dictionary) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Home) }
    val showClearAll = selectedTab == MainTab.Filter && uiState.selectedValues.values.any { it.isNotEmpty() }
    val isSettingsSubPage = selectedTab == MainTab.Settings && settingsPage != SettingsPage.Home

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
                                MainTab.Favorites -> Icons.Outlined.StarBorder
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
                modifier = Modifier.padding(innerPadding)
            )

            MainTab.Favorites -> FavoritesScreen(
                uiState = uiState,
                onToggleFavorite = onToggleFavorite,
                onSelectEntry = onSelectEntry,
                modifier = Modifier.padding(innerPadding)
            )

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
}

@Composable
private fun FilterScreen(
    uiState: UiState,
    onSelectCategory: (IndexCategory) -> Unit,
    onToggleValue: (IndexCategory, String) -> Unit,
    onClearCategory: (IndexCategory) -> Unit,
    modifier: Modifier = Modifier
) {
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

    Column(
        modifier = modifier.fillMaxSize()
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
                .fillMaxSize()
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
    }
}

@Composable
private fun DictionaryScreen(
    uiState: UiState,
    onToggleFavorite: (Int) -> Unit,
    onSelectEntry: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = stringResource(R.string.results, uiState.filteredEntries.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider()

        if (uiState.filteredEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.no_results))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.filteredEntries.size, key = { uiState.filteredEntries[it].id }) { index ->
                    val entry = uiState.filteredEntries[index]
                    DictListItem(
                        entry = entry,
                        isFavorited = uiState.favoriteIds.contains(entry.id),
                        onToggleFavorite = { onToggleFavorite(entry.id) },
                        onClick = { onSelectEntry(entry.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(
    uiState: UiState,
    onToggleFavorite: (Int) -> Unit,
    onSelectEntry: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState.favoriteEntries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.favorites_empty))
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(uiState.favoriteEntries.size, key = { uiState.favoriteEntries[it].id }) { index ->
                val entry = uiState.favoriteEntries[index]
                DictListItem(
                    entry = entry,
                    isFavorited = true,
                    onToggleFavorite = { onToggleFavorite(entry.id) },
                    onClick = { onSelectEntry(entry.id) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: UiState,
    onUpdateWebDavConfig: (String, String, String) -> Unit,
    onManualUploadFavorites: () -> Unit,
    onManualDownloadFavorites: () -> Unit,
    settingsPage: SettingsPage,
    onOpenBackupRestore: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (settingsPage) {
        SettingsPage.Home -> SettingsHomeScreen(
            onOpenBackupRestore = onOpenBackupRestore,
            onOpenAbout = onOpenAbout,
            modifier = modifier
        )
        SettingsPage.BackupRestore -> SettingsBackupRestoreScreen(
            uiState = uiState,
            onUpdateWebDavConfig = onUpdateWebDavConfig,
            onManualUploadFavorites = onManualUploadFavorites,
            onManualDownloadFavorites = onManualDownloadFavorites,
            modifier = modifier
        )
        SettingsPage.About -> SettingsAboutScreen(modifier = modifier)
    }
}

@Composable
private fun SettingsHomeScreen(
    onOpenBackupRestore: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.settings_entry_backup_restore)) },
            supportingContent = { Text(text = stringResource(R.string.settings_entry_backup_restore_desc)) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenBackupRestore)
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.settings_entry_about)) },
            supportingContent = { Text(text = stringResource(R.string.settings_entry_about_desc)) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenAbout)
        )
        HorizontalDivider()
    }
}

@Composable
private fun SettingsBackupRestoreScreen(
    uiState: UiState,
    onUpdateWebDavConfig: (String, String, String) -> Unit,
    onManualUploadFavorites: () -> Unit,
    onManualDownloadFavorites: () -> Unit,
    modifier: Modifier = Modifier
) {
    val webDavConfig = uiState.webDavConfig
    var serverUrl by rememberSaveable(webDavConfig.serverUrl) { mutableStateOf(webDavConfig.serverUrl) }
    var username by rememberSaveable(webDavConfig.username) { mutableStateOf(webDavConfig.username) }
    var password by rememberSaveable(webDavConfig.password) { mutableStateOf(webDavConfig.password) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_webdav_title),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text(text = stringResource(R.string.settings_webdav_server_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(text = stringResource(R.string.settings_webdav_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(text = stringResource(R.string.settings_webdav_password)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(
                            if (passwordVisible) {
                                R.string.settings_webdav_password_hide
                            } else {
                                R.string.settings_webdav_password_show
                            }
                        )
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.settings_webdav_default_location),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                onUpdateWebDavConfig(
                    serverUrl,
                    username,
                    password
                )
            },
            enabled = !uiState.syncInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.settings_webdav_save))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onManualUploadFavorites,
                enabled = !uiState.syncInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.settings_webdav_upload))
            }
            Button(
                onClick = onManualDownloadFavorites,
                enabled = !uiState.syncInProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.settings_webdav_download))
            }
        }

        if (serverUrl.trim().startsWith("http://", ignoreCase = true)) {
            Text(
                text = stringResource(R.string.settings_webdav_http_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            text = stringResource(R.string.settings_webdav_sync_status),
            style = MaterialTheme.typography.titleSmall
        )
        if (uiState.syncInProgress) {
            Text(
                text = stringResource(R.string.settings_webdav_status_syncing),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = uiState.lastSyncMessage ?: stringResource(R.string.settings_webdav_status_idle),
            style = MaterialTheme.typography.bodyMedium,
            color = if ((uiState.lastSyncMessage ?: "").contains("失败")) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun SettingsAboutScreen(
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val licenseUrl = stringResource(R.string.settings_about_license_url)
    val githubUrl = stringResource(R.string.settings_about_github_url)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.settings_about_license_title)) },
            supportingContent = { Text(text = stringResource(R.string.settings_about_license_value)) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.settings_about_license_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri(licenseUrl)
                }
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.settings_about_github_title)) },
            supportingContent = { Text(text = stringResource(R.string.settings_about_github_value)) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(R.string.settings_about_github_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    uriHandler.openUri(githubUrl)
                }
        )
    }
}

@Composable
private fun DictListItem(
    entry: DictEntry,
    isFavorited: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val pinyin = formatPinyinList(entry.phonetics.pinyin)
    val definition = entry.definitions.firstOrNull().orEmpty().ifBlank { "-" }
    ListItem(
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.char,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = HanziFontFamily
                )
                Text(
                    text = pinyin,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp)
                )
            }
        },
        supportingContent = {
            Text(
                text = definition,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorited) R.string.favorite_remove else R.string.favorite_add
                    )
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictDetailScreen(
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

private fun formatPinyinList(values: List<String>): String {
    if (values.isEmpty()) {
        return "-"
    }
    return values.joinToString(", ")
}

private fun formatIntList(values: List<Int>): String {
    if (values.isEmpty()) {
        return "-"
    }
    return values.joinToString(" ")
}



