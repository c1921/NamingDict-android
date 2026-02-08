package io.github.c1921.namingdict.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.c1921.namingdict.BuildConfig
import io.github.c1921.namingdict.R

@Composable
internal fun SettingsScreen(
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
