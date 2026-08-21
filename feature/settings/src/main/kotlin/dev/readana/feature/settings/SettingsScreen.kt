package dev.readana.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.data.repository.SourceDefaults
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.LoadingState
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.HeadingSensitivity
import dev.readana.core.model.SourceSettings

private const val PERSISTABLE_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

@Composable
internal fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onLibraryFolderChange = viewModel::setLibraryFolder,
        onSourceEnabledChange = viewModel::setSourceEnabled,
        onAnnasMirrorsChange = viewModel::setAnnasMirrors,
        onAnnasSecretKeyChange = viewModel::setAnnasSecretKey,
        onAutoConvertChange = viewModel::setAutoConvertPdf,
        onRemoveHeadersChange = viewModel::setRemoveHeadersAndFooters,
        onHeadingSensitivityChange = viewModel::setHeadingSensitivity,
        onExtractImagesChange = viewModel::setExtractImages,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onLibraryFolderChange: (String?) -> Unit,
    onSourceEnabledChange: (sourceId: String, enabled: Boolean) -> Unit,
    onAnnasMirrorsChange: (String) -> Unit,
    onAnnasSecretKeyChange: (String) -> Unit,
    onAutoConvertChange: (Boolean) -> Unit,
    onRemoveHeadersChange: (Boolean) -> Unit,
    onHeadingSensitivityChange: (HeadingSensitivity) -> Unit,
    onExtractImagesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.feature_settings_title)) }) }
    ) { padding ->
        when (uiState) {
            SettingsUiState.Loading -> LoadingState(Modifier.padding(padding))

            is SettingsUiState.Loaded -> {
                // One group open at a time: the whole list stays scannable, and each header says
                // what its settings are currently doing so most questions need no tap at all.
                var openGroup by rememberSaveable { mutableStateOf<SettingsGroupId?>(null) }
                fun toggle(id: SettingsGroupId) {
                    openGroup = if (openGroup == id) null else id
                }

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsGroup(
                        title = stringResource(R.string.feature_settings_section_sources),
                        summary = uiState.sourcesSummary(),
                        icon = ReadanaIcons.Discover,
                        expanded = openGroup == SettingsGroupId.Sources,
                        onToggle = { toggle(SettingsGroupId.Sources) }
                    ) {
                        SourcesSection(
                            settings = uiState.sources,
                            toggles = uiState.sourceToggles,
                            onSourceEnabledChange = onSourceEnabledChange,
                            onAnnasMirrorsChange = onAnnasMirrorsChange,
                            onAnnasSecretKeyChange = onAnnasSecretKeyChange
                        )
                    }
                    SettingsGroup(
                        title = stringResource(R.string.feature_settings_section_conversion),
                        summary = uiState.conversion.summary(),
                        icon = ReadanaIcons.Convert,
                        expanded = openGroup == SettingsGroupId.Conversion,
                        onToggle = { toggle(SettingsGroupId.Conversion) }
                    ) {
                        ConversionSection(
                            settings = uiState.conversion,
                            onAutoConvertChange = onAutoConvertChange,
                            onRemoveHeadersChange = onRemoveHeadersChange,
                            onHeadingSensitivityChange = onHeadingSensitivityChange,
                            onExtractImagesChange = onExtractImagesChange
                        )
                    }
                    SettingsGroup(
                        title = stringResource(R.string.feature_settings_section_storage),
                        summary = uiState.libraryFolderUri?.let { folderLabel(it) }
                            ?: stringResource(R.string.feature_settings_library_folder_default),
                        icon = ReadanaIcons.Folder,
                        expanded = openGroup == SettingsGroupId.Storage,
                        onToggle = { toggle(SettingsGroupId.Storage) }
                    ) {
                        StorageSection(
                            libraryFolderUri = uiState.libraryFolderUri,
                            onLibraryFolderChange = onLibraryFolderChange
                        )
                    }
                    SettingsGroup(
                        title = stringResource(R.string.feature_settings_section_background),
                        summary = stringResource(R.string.feature_settings_background_summary),
                        icon = ReadanaIcons.Download,
                        expanded = openGroup == SettingsGroupId.Background,
                        onToggle = { toggle(SettingsGroupId.Background) }
                    ) {
                        BackgroundWorkSection()
                    }
                }
            }
        }
    }
}

private enum class SettingsGroupId { Sources, Conversion, Storage, Background }

/**
 * One collapsible settings group. Collapsed it is a single row — icon, name and what the settings
 * inside currently add up to — so the screen reads as a short list instead of a wall of controls.
 */
@Composable
private fun SettingsGroup(
    title: String,
    summary: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (expanded) ReadanaIcons.ExpandLess else ReadanaIcons.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.feature_settings_collapse else R.string.feature_settings_expand
                )
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ConversionSettings.summary(): String = if (!autoConvertPdf) {
    stringResource(R.string.feature_settings_summary_conversion_off)
} else {
    listOf(
        stringResource(R.string.feature_settings_summary_conversion_on),
        stringResource(headingSensitivity.labelRes())
    ).joinToString(SUMMARY_SEPARATOR)
}

@Composable
private fun SettingsUiState.Loaded.sourcesSummary(): String {
    val enabled = sourceToggles.filter { it.enabled }
    if (enabled.isEmpty()) return stringResource(R.string.feature_settings_summary_no_sources)
    val names = enabled.joinToString(SUMMARY_SEPARATOR) { it.name }
    return if (sources.annasSecretKey.isNullOrBlank()) {
        names
    } else {
        names + SUMMARY_SEPARATOR + stringResource(R.string.feature_settings_summary_member_key)
    }
}

private const val SUMMARY_SEPARATOR = " · "

/**
 * How PDFs become EPUB. Only the choices a reader can judge from the result are exposed; the
 * reflow engine's other tunables keep their defaults (ADR 0011).
 */
@Composable
private fun ConversionSection(
    settings: ConversionSettings,
    onAutoConvertChange: (Boolean) -> Unit,
    onRemoveHeadersChange: (Boolean) -> Unit,
    onHeadingSensitivityChange: (HeadingSensitivity) -> Unit,
    onExtractImagesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(
            label = stringResource(R.string.feature_settings_conversion_auto),
            checked = settings.autoConvertPdf,
            onCheckedChange = onAutoConvertChange
        )
        Text(
            text = stringResource(R.string.feature_settings_conversion_auto_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwitchRow(
            label = stringResource(R.string.feature_settings_conversion_headers),
            checked = settings.removeHeadersAndFooters,
            onCheckedChange = onRemoveHeadersChange
        )
        SwitchRow(
            label = stringResource(R.string.feature_settings_conversion_images),
            checked = settings.extractImages,
            onCheckedChange = onExtractImagesChange
        )
        Text(stringResource(R.string.feature_settings_conversion_headings))
        ChipRow(
            options = HeadingSensitivity.entries,
            selected = settings.headingSensitivity,
            label = { stringResource(it.labelRes()) },
            onSelect = onHeadingSensitivityChange
        )
        Text(
            text = stringResource(R.string.feature_settings_conversion_headings_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun HeadingSensitivity.labelRes(): Int = when (this) {
    HeadingSensitivity.Conservative -> R.string.feature_settings_conversion_headings_conservative
    HeadingSensitivity.Balanced -> R.string.feature_settings_conversion_headings_balanced
    HeadingSensitivity.Aggressive -> R.string.feature_settings_conversion_headings_aggressive
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
            onValueChange = { mirrorsDraft = it },
            // Saving per keystroke rewrites the mirror list on every letter typed, and every
            // search reads it; committing once the field is done is enough.
            modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                if (!focus.isFocused) onAnnasMirrorsChange(mirrorsDraft)
            },
            label = { Text(stringResource(R.string.feature_settings_annas_mirrors)) },
            // An empty box does not mean "no mirrors": it means the built-in list is in use, so
            // show that list as the placeholder rather than leaving it a mystery.
            placeholder = { Text(SourceDefaults.ANNAS_MIRRORS.joinToString("\n")) },
            supportingText = { Text(stringResource(R.string.feature_settings_annas_mirrors_help)) },
            minLines = 2
        )
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                if (!focus.isFocused) onAnnasSecretKeyChange(keyDraft)
            },
            label = { Text(stringResource(R.string.feature_settings_annas_secret_key)) },
            supportingText = {
                Text(stringResource(R.string.feature_settings_annas_secret_key_help))
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAnnasSecretKeyChange(keyDraft) }),
            visualTransformation = PasswordVisualTransformation()
        )
        val downloadsLeft = settings.annasDownloadsLeft
        if (settings.annasSecretKey != null && downloadsLeft != null) {
            Text(
                text = settings.annasDownloadsPerDay?.let { perDay ->
                    stringResource(R.string.feature_settings_annas_quota, downloadsLeft, perDay)
                } ?: stringResource(R.string.feature_settings_annas_quota_left, downloadsLeft),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    label: @Composable (T) -> String,
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
