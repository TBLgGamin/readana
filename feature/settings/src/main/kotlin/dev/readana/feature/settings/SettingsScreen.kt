package dev.readana.feature.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

/** Wide enough to read comfortably, narrow enough that a tablet does not stretch rows to the edge. */
private val ContentMaxWidth = 720.dp

/** Air between two sections, so the headings do the dividing instead of lines. */
private val SectionSpacing = 32.dp

@Composable
internal fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    aiViewModel: AiSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        aiState = aiState,
        aiActions = AiSettingsActions(
            onSetActiveProvider = aiViewModel::setActiveProvider,
            onApiKeyChange = aiViewModel::setApiKey,
            onModelChange = aiViewModel::setModel,
            onBaseUrlChange = aiViewModel::setBaseUrl,
            onAddCustomProvider = aiViewModel::addCustomProvider,
            onRemoveProvider = aiViewModel::removeProvider,
            onFetchModels = aiViewModel::fetchModels,
            onTestProvider = aiViewModel::testProvider,
            onContextSizeChange = aiViewModel::setContextSize,
            onRecapEnabledChange = aiViewModel::setRecapEnabled,
            onIllustrateChange = aiViewModel::setIllustrateFromWeb,
            onRecapAfterDaysChange = aiViewModel::setRecapAfterDays
        ),
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

/**
 * Every setting on one scrolling page, grouped under headings. Nothing is hidden behind a tap:
 * what the app is set to can be read by scrolling, which is what a settings screen is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    aiState: AiSettingsUiState,
    aiActions: AiSettingsActions,
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.feature_settings_title)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        when (uiState) {
            SettingsUiState.Loading -> LoadingState(Modifier.padding(padding))

            is SettingsUiState.Loaded -> Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing)
                ) {
                    SourcesSection(
                        settings = uiState.sources,
                        toggles = uiState.sourceToggles,
                        onSourceEnabledChange = onSourceEnabledChange,
                        onAnnasMirrorsChange = onAnnasMirrorsChange,
                        onAnnasSecretKeyChange = onAnnasSecretKeyChange
                    )
                    ConversionSection(
                        settings = uiState.conversion,
                        onAutoConvertChange = onAutoConvertChange,
                        onRemoveHeadersChange = onRemoveHeadersChange,
                        onHeadingSensitivityChange = onHeadingSensitivityChange,
                        onExtractImagesChange = onExtractImagesChange
                    )
                    AiSettingsSection(state = aiState, actions = aiActions)
                    StorageSection(
                        libraryFolderUri = uiState.libraryFolderUri,
                        onLibraryFolderChange = onLibraryFolderChange
                    )
                    Spacer(Modifier.height(SectionSpacing))
                }
            }
        }
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

    SettingsSection(
        title = stringResource(R.string.feature_settings_section_sources),
        icon = ReadanaIcons.Discover,
        modifier = modifier
    ) {
        SettingsGroup {
            toggles.forEach { toggle ->
                item { shapes ->
                    SwitchItem(
                        shapes = shapes,
                        label = toggle.name,
                        checked = toggle.enabled,
                        onCheckedChange = { enabled -> onSourceEnabledChange(toggle.id, enabled) }
                    )
                }
            }
        }
        SettingsNote(stringResource(R.string.feature_settings_annas_notice))
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
            SettingsNote(
                text = settings.annasDownloadsPerDay?.let { perDay ->
                    stringResource(R.string.feature_settings_annas_quota, downloadsLeft, perDay)
                } ?: stringResource(R.string.feature_settings_annas_quota_left, downloadsLeft)
            )
        }
    }
}

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
    SettingsSection(
        title = stringResource(R.string.feature_settings_section_conversion),
        icon = ReadanaIcons.Convert,
        modifier = modifier
    ) {
        SettingsGroup {
            item { shapes ->
                SwitchItem(
                    shapes = shapes,
                    label = stringResource(R.string.feature_settings_conversion_auto),
                    checked = settings.autoConvertPdf,
                    onCheckedChange = onAutoConvertChange,
                    help = stringResource(R.string.feature_settings_conversion_auto_help)
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes = shapes,
                    label = stringResource(R.string.feature_settings_conversion_headers),
                    checked = settings.removeHeadersAndFooters,
                    onCheckedChange = onRemoveHeadersChange
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes = shapes,
                    label = stringResource(R.string.feature_settings_conversion_images),
                    checked = settings.extractImages,
                    onCheckedChange = onExtractImagesChange
                )
            }
        }
        ChoiceRow(
            label = stringResource(R.string.feature_settings_conversion_headings),
            options = HeadingSensitivity.entries,
            selected = settings.headingSensitivity,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = onHeadingSensitivityChange,
            help = stringResource(R.string.feature_settings_conversion_headings_help)
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

    SettingsSection(
        title = stringResource(R.string.feature_settings_section_storage),
        icon = ReadanaIcons.Folder,
        modifier = modifier
    ) {
        SettingsGroup {
            item { shapes ->
                SegmentedListItem(
                    onClick = { folderLauncher.launch(null) },
                    shapes = shapes,
                    leadingContent = { Icon(ReadanaIcons.Folder, contentDescription = null) },
                    supportingContent = {
                        Text(
                            text = libraryFolderUri?.let { folderLabel(it) }
                                ?: stringResource(R.string.feature_settings_library_folder_default)
                        )
                    },
                    trailingContent = {
                        if (libraryFolderUri != null) {
                            IconButton(
                                onClick = {
                                    releaseFolder(context, libraryFolderUri)
                                    onLibraryFolderChange(null)
                                }
                            ) {
                                Icon(
                                    ReadanaIcons.Close,
                                    contentDescription = stringResource(
                                        R.string.feature_settings_library_folder_clear
                                    )
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.segmentedColors()
                ) {
                    Text(stringResource(R.string.feature_settings_library_folder))
                }
            }
        }
        SettingsNote(stringResource(R.string.feature_settings_library_folder_note))
    }
}

/** Shows `primary:Books` rather than the whole opaque tree URI. */
private fun folderLabel(treeUri: String): String = treeUri.toUri().lastPathSegment ?: treeUri

private fun releaseFolder(context: Context, treeUri: String) {
    runCatching {
        context.contentResolver.releasePersistableUriPermission(treeUri.toUri(), PERSISTABLE_FLAGS)
    }
}
