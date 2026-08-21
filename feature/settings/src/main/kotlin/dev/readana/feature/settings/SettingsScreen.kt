package dev.readana.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.component.LoadingState
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderColumns
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme
import dev.readana.core.model.SourceSettings

private const val PERSISTABLE_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@Composable
internal fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onFontScaleChange = viewModel::setFontScale,
        onThemeChange = viewModel::setTheme,
        onPageTurnModeChange = viewModel::setPageTurnMode,
        onColumnsChange = viewModel::setColumns,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        onVolumeKeysChange = viewModel::setVolumeKeysTurnPages,
        onLibraryFolderChange = viewModel::setLibraryFolder,
        onSourceEnabledChange = viewModel::setSourceEnabled,
        onAnnasMirrorsChange = viewModel::setAnnasMirrors,
        onAnnasSecretKeyChange = viewModel::setAnnasSecretKey,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onFontScaleChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onPageTurnModeChange: (PageTurnMode) -> Unit,
    onColumnsChange: (ReaderColumns) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    onLibraryFolderChange: (String?) -> Unit,
    onSourceEnabledChange: (sourceId: String, enabled: Boolean) -> Unit,
    onAnnasMirrorsChange: (String) -> Unit,
    onAnnasSecretKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_settings_title)) }) }
    ) { padding ->
        when (uiState) {
            SettingsUiState.Loading -> LoadingState(Modifier.padding(padding))

            is SettingsUiState.Loaded -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ReaderSection(
                    settings = uiState.reader,
                    onFontScaleChange = onFontScaleChange,
                    onThemeChange = onThemeChange,
                    onPageTurnModeChange = onPageTurnModeChange,
                    onColumnsChange = onColumnsChange,
                    onKeepScreenOnChange = onKeepScreenOnChange,
                    onVolumeKeysChange = onVolumeKeysChange
                )
                HorizontalDivider()
                StorageSection(
                    libraryFolderUri = uiState.libraryFolderUri,
                    onLibraryFolderChange = onLibraryFolderChange
                )
                HorizontalDivider()
                SourcesSection(
                    settings = uiState.sources,
                    toggles = uiState.sourceToggles,
                    onSourceEnabledChange = onSourceEnabledChange,
                    onAnnasMirrorsChange = onAnnasMirrorsChange,
                    onAnnasSecretKeyChange = onAnnasSecretKeyChange
                )
                HorizontalDivider()
                BackgroundWorkSection()
            }
        }
    }
}

@Composable
private fun ReaderSection(
    settings: ReaderSettings,
    onFontScaleChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onPageTurnModeChange: (PageTurnMode) -> Unit,
    onColumnsChange: (ReaderColumns) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.feature_settings_section_reading))

        Text(stringResource(R.string.feature_settings_font_scale, settings.fontScale))
        Slider(
            value = settings.fontScale,
            onValueChange = onFontScaleChange,
            valueRange = ReaderSettings.FONT_SCALE_RANGE
        )

        Text(stringResource(R.string.feature_settings_theme))
        ChipRow(
            options = ReaderTheme.entries,
            selected = settings.theme,
            label = { it.name },
            onSelect = onThemeChange
        )

        Text(stringResource(R.string.feature_settings_page_turn_mode))
        ChipRow(
            options = PageTurnMode.entries,
            selected = settings.pageTurnMode,
            label = { it.name },
            onSelect = onPageTurnModeChange
        )

        Text(stringResource(R.string.feature_settings_columns))
        ChipRow(
            options = ReaderColumns.entries,
            selected = settings.columns,
            label = { it.name },
            onSelect = onColumnsChange
        )

        SwitchRow(
            label = stringResource(R.string.feature_settings_keep_screen_on),
            checked = settings.keepScreenOn,
            onCheckedChange = onKeepScreenOnChange
        )
        SwitchRow(
            label = stringResource(R.string.feature_settings_volume_keys),
            checked = settings.volumeKeysTurnPages,
            onCheckedChange = onVolumeKeysChange
        )
    }
}

/**
 * Where imported books are written (ADR 0007). A picked folder needs a persisted permission so it
 * survives restarts; clearing it releases that permission again.
 */
@Composable
private fun StorageSection(
    libraryFolderUri: String?,
    onLibraryFolderChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, PERSISTABLE_FLAGS)
            onLibraryFolderChange(uri.toString())
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.feature_settings_section_storage))
        Text(stringResource(R.string.feature_settings_library_folder))
        Text(
            text = libraryFolderUri?.let { folderLabel(it) }
                ?: stringResource(R.string.feature_settings_library_folder_default),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { folderLauncher.launch(null) }) {
                Text(stringResource(R.string.feature_settings_library_folder_pick))
            }
            if (libraryFolderUri != null) {
                OutlinedButton(
                    onClick = {
                        releaseFolder(context, libraryFolderUri)
                        onLibraryFolderChange(null)
                    }
                ) {
                    Text(stringResource(R.string.feature_settings_library_folder_clear))
                }
            }
        }
        Text(
            text = stringResource(R.string.feature_settings_library_folder_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Shows `primary:Books` rather than the whole opaque tree URI. */
private fun folderLabel(treeUri: String): String = treeUri.toUri().lastPathSegment ?: treeUri

private fun releaseFolder(context: Context, treeUri: String) {
    runCatching {
        context.contentResolver.releasePersistableUriPermission(treeUri.toUri(), PERSISTABLE_FLAGS)
    }
}

@Composable
private fun SourcesSection(
    settings: SourceSettings,
    toggles: List<SourceToggle>,
    onSourceEnabledChange: (sourceId: String, enabled: Boolean) -> Unit,
    onAnnasMirrorsChange: (String) -> Unit,
    onAnnasSecretKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Text fields keep local drafts so typing doesn't round-trip through DataStore per keystroke.
    var mirrorsDraft by rememberSaveable(settings.annasMirrors) {
        mutableStateOf(settings.annasMirrors.joinToString("\n"))
    }
    var keyDraft by rememberSaveable(settings.annasSecretKey) {
        mutableStateOf(settings.annasSecretKey.orEmpty())
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.feature_settings_section_sources))
        toggles.forEach { toggle ->
            SwitchRow(
                label = toggle.name,
                checked = toggle.enabled,
                onCheckedChange = { enabled -> onSourceEnabledChange(toggle.id, enabled) }
            )
        }
        Text(
            text = stringResource(R.string.feature_settings_annas_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = mirrorsDraft,
            onValueChange = {
                mirrorsDraft = it
                onAnnasMirrorsChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feature_settings_annas_mirrors)) },
            supportingText = { Text(stringResource(R.string.feature_settings_annas_mirrors_help)) },
            minLines = 2
        )
        OutlinedTextField(
            value = keyDraft,
            onValueChange = {
                keyDraft = it
                onAnnasSecretKeyChange(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feature_settings_annas_secret_key)) },
            supportingText = {
                Text(stringResource(R.string.feature_settings_annas_secret_key_help))
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
    }
}

/**
 * HyperOS kills background work hard, which is what stops long downloads (docs/DEVICE.md). The
 * MIUI screens are not public API, so each button falls back to the app's own settings page.
 */
@Composable
private fun BackgroundWorkSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.feature_settings_section_background))
        Text(
            text = stringResource(R.string.feature_settings_background_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { context.openBatterySettings() }) {
                Text(stringResource(R.string.feature_settings_background_battery))
            }
            OutlinedButton(onClick = { context.openAutostartSettings() }) {
                Text(stringResource(R.string.feature_settings_background_autostart))
            }
        }
        Text(
            text = stringResource(R.string.feature_settings_background_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Context.openBatterySettings() = startFirstResolvable(
    Intent().setClassName(MIUI_POWER_PACKAGE, MIUI_POWER_ACTIVITY)
        .putExtra("package_name", packageName),
    appDetailsIntent()
)

private fun Context.openAutostartSettings() = startFirstResolvable(
    Intent().setClassName(MIUI_SECURITY_PACKAGE, MIUI_AUTOSTART_ACTIVITY),
    appDetailsIntent()
)

private fun Context.appDetailsIntent() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

private fun Context.startFirstResolvable(vararg intents: Intent) {
    intents.forEach { intent ->
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Not a MIUI build, or the screen moved; try the next one.
        } catch (_: SecurityException) {
            // Some HyperOS builds export the activity but refuse third-party callers.
        }
    }
}

private const val MIUI_POWER_PACKAGE = "com.miui.powerkeeper"
private const val MIUI_POWER_ACTIVITY = "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
private const val MIUI_SECURITY_PACKAGE = "com.miui.securitycenter"
private const val MIUI_AUTOSTART_ACTIVITY = "com.miui.permcenter.autostart.AutoStartManagementActivity"

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp)
    )
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
