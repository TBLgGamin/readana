package dev.readana.feature.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.LoadingState
import dev.readana.core.model.CatalogPreset
import dev.readana.core.model.ConversionSettings
import dev.readana.core.model.HeadingSensitivity
import dev.readana.core.model.ReaderSettings
import dev.readana.source.api.SourceConfigField

/** Lines an editable URL list shows before it scrolls. */
private const val URL_LIST_LINES = 2

/** Air between the fields of one source's settings. */
private val FieldSpacing = 12.dp

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
        actions = SettingsActions(
            onLibraryFolderChange = viewModel::setLibraryFolder,
            onSourceEnabledChange = viewModel::setSourceEnabled,
            onSourceConfigChange = viewModel::setSourceConfig,
            onAddPresetCatalog = viewModel::addPresetCatalog,
            onAddCatalog = viewModel::addCatalog,
            onRemoveCatalog = viewModel::removeCatalog,
            onAutoConvertChange = viewModel::setAutoConvertPdf,
            onRemoveHeadersChange = viewModel::setRemoveHeadersAndFooters,
            onHeadingSensitivityChange = viewModel::setHeadingSensitivity,
            onExtractImagesChange = viewModel::setExtractImages,
            onReadingChange = viewModel::updateReadingDefaults
        ),
        modifier = modifier
    )
}

/** Everything the settings page can do, gathered so the screen takes one parameter for it. */
internal data class SettingsActions(
    val onLibraryFolderChange: (String?) -> Unit,
    val onSourceEnabledChange: (sourceId: String, enabled: Boolean) -> Unit,
    val onSourceConfigChange: (sourceId: String, field: SourceConfigField, value: String) -> Unit,
    val onAddPresetCatalog: (CatalogPreset) -> Unit,
    val onAddCatalog: (name: String, feedUrl: String) -> Boolean,
    val onRemoveCatalog: (catalogId: String) -> Unit,
    val onAutoConvertChange: (Boolean) -> Unit,
    val onRemoveHeadersChange: (Boolean) -> Unit,
    val onHeadingSensitivityChange: (HeadingSensitivity) -> Unit,
    val onExtractImagesChange: (Boolean) -> Unit,
    val onReadingChange: ((ReaderSettings) -> ReaderSettings) -> Unit
)

/**
 * Two pages rather than one long scroll.
 *
 * Basic holds what a reader touches while they are actually reading: how a book looks, how pages
 * turn, and which model answers questions. Advanced holds the rest, which is set up once and then
 * left alone. Splitting them keeps the first screen short enough to read at a glance without
 * hiding anything behind a chevron.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    aiState: AiSettingsUiState,
    aiActions: AiSettingsActions,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var tab by rememberSaveable { mutableStateOf(SettingsTab.Reading) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.feature_settings_title)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp) {
                SettingsTab.entries.forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        text = { Text(stringResource(entry.labelRes())) }
                    )
                }
            }
            when (uiState) {
                SettingsUiState.Loading -> LoadingState()

                is SettingsUiState.Loaded -> SettingsPage(tab) {
                    when (tab) {
                        SettingsTab.Reading -> {
                            ReadingSettingsSection(
                                settings = uiState.reading,
                                onChange = actions.onReadingChange
                            )
                            ReadingLayoutSection(
                                settings = uiState.reading,
                                onChange = actions.onReadingChange
                            )
                        }

                        SettingsTab.Sources -> SourcesSection(
                            rows = uiState.sourceRows,
                            presets = uiState.availablePresets,
                            actions = actions
                        )

                        SettingsTab.Assistant -> {
                            AiSettingsSection(state = aiState, actions = aiActions)
                            AiAdvancedSection(state = aiState, actions = aiActions)
                        }

                        SettingsTab.Files -> {
                            ConversionSection(
                                settings = uiState.conversion,
                                onAutoConvertChange = actions.onAutoConvertChange,
                                onRemoveHeadersChange = actions.onRemoveHeadersChange,
                                onHeadingSensitivityChange = actions.onHeadingSensitivityChange,
                                onExtractImagesChange = actions.onExtractImagesChange
                            )
                            StorageSection(
                                libraryFolderUri = uiState.libraryFolderUri,
                                onLibraryFolderChange = actions.onLibraryFolderChange
                            )
                        }

                        SettingsTab.About -> AboutSection()
                    }
                }
            }
        }
    }
}

/**
 * One tab per thing a reader might have come here to change. Five short pages beat two long ones:
 * every tab now fits on a screen, so nothing is found by scrolling past something unrelated.
 */
private enum class SettingsTab { Reading, Sources, Assistant, Files, About }

private fun SettingsTab.labelRes(): Int = when (this) {
    SettingsTab.Reading -> R.string.feature_settings_tab_reading
    SettingsTab.Sources -> R.string.feature_settings_tab_sources
    SettingsTab.Assistant -> R.string.feature_settings_tab_assistant
    SettingsTab.Files -> R.string.feature_settings_tab_files
    SettingsTab.About -> R.string.feature_settings_tab_about
}

/**
 * The column both tabs are poured into.
 *
 * Keyed on the tab so switching starts at the top rather than at wherever the other tab had been
 * scrolled to, which otherwise lands the reader in the middle of a section they did not choose.
 */
@Composable
private fun SettingsPage(tab: SettingsTab, content: @Composable ColumnScope.() -> Unit) {
    key(tab) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                content = content
            )
            Spacer(Modifier.height(SectionSpacing))
        }
    }
}

@Composable
private fun SourcesSection(
    rows: List<SourceRow>,
    presets: List<CatalogPreset>,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    // Which source's settings are open. Only one at a time: the rows are the index, and two
    // expanded sources would push the rest of the list off the screen.
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
        SettingsSection(
            title = stringResource(R.string.feature_settings_section_sources),
            icon = ReadanaIcons.Discover
        ) {
            SettingsGroup {
                rows.forEach { row ->
                    item { shapes ->
                        SourceHeaderRow(
                            row = row,
                            shapes = shapes,
                            isExpanded = expanded == row.id,
                            onToggleExpand = {
                                expanded = if (expanded == row.id) null else row.id
                            },
                            onEnabledChange = { actions.onSourceEnabledChange(row.id, it) }
                        )
                    }
                }
            }
            rows.firstOrNull { it.id == expanded }?.let { row ->
                SourceConfigFields(row = row, actions = actions)
            }
            SettingsNote(stringResource(R.string.feature_settings_sources_notice))
        }

        AddCatalogSection(presets = presets, actions = actions)
    }
}

/**
 * One source in the list: name, what it is, and a switch. The chevron only appears when the source
 * has something to configure, so a source that needs nothing looks like it needs nothing.
 */
@Composable
private fun SourceHeaderRow(
    row: SourceRow,
    shapes: ListItemShapes,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    SwitchItem(
        shapes = shapes,
        label = row.name,
        help = row.summary,
        checked = row.enabled,
        onCheckedChange = onEnabledChange,
        trailing = if (!row.isConfigurable) {
            null
        } else {
            {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) ReadanaIcons.ExpandLess else ReadanaIcons.ExpandMore,
                        contentDescription = stringResource(
                            if (isExpanded) {
                                R.string.feature_settings_source_collapse
                            } else {
                                R.string.feature_settings_source_expand
                            }
                        )
                    )
                }
            }
        }
    )
}

/**
 * A source's own settings, rendered from what the source declared. Nothing here knows which source
 * it is looking at, which is what keeps a new source from needing new UI.
 */
@Composable
private fun SourceConfigFields(row: SourceRow, actions: SettingsActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        row.fields.forEach { field ->
            // A local draft, so typing does not round-trip through DataStore per keystroke.
            var draft by rememberSaveable(row.id, field.key, row.values[field.key]) {
                mutableStateOf(row.values[field.key].orEmpty())
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                // Saving on every keystroke rewrites the value each letter, and every search
                // reads it; committing once the field is done is enough.
                modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                    if (!focus.isFocused) actions.onSourceConfigChange(row.id, field, draft)
                },
                label = { Text(field.label) },
                placeholder = field.placeholder?.let { { Text(it) } },
                supportingText = field.help?.let { { Text(it) } },
                singleLine = field.kind != SourceConfigField.Kind.UrlList,
                minLines = if (field.kind == SourceConfigField.Kind.UrlList) URL_LIST_LINES else 1,
                visualTransformation = if (field.kind == SourceConfigField.Kind.Secret) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { actions.onSourceConfigChange(row.id, field, draft) }
                )
            )
        }
        row.quota?.remaining?.let { remaining ->
            SettingsNote(
                text = row.quota.perDay?.let { perDay ->
                    stringResource(R.string.feature_settings_source_quota, remaining, perDay)
                } ?: stringResource(R.string.feature_settings_source_quota_left, remaining)
            )
        }
        if (row.isRemovable) {
            TextButton(onClick = { actions.onRemoveCatalog(row.id) }) {
                Text(stringResource(R.string.feature_settings_catalog_remove))
            }
        }
    }
}

/** Catalogs to add: the ready-made ones first, then anywhere else the user knows of. */
@Composable
private fun AddCatalogSection(presets: List<CatalogPreset>, actions: SettingsActions, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var rejected by rememberSaveable { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(R.string.feature_settings_section_catalogs),
        icon = ReadanaIcons.Add,
        modifier = modifier
    ) {
        if (presets.isNotEmpty()) {
            SettingsGroup {
                presets.forEach { preset ->
                    item { shapes ->
                        ActionItem(
                            shapes = shapes,
                            label = preset.name,
                            supporting = preset.description,
                            onClick = { actions.onAddPresetCatalog(preset) }
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feature_settings_catalog_name)) },
            singleLine = true
        )
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                rejected = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.feature_settings_catalog_url)) },
            supportingText = {
                Text(
                    stringResource(
                        if (rejected) {
                            R.string.feature_settings_catalog_url_invalid
                        } else {
                            R.string.feature_settings_catalog_url_help
                        }
                    )
                )
            },
            isError = rejected,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        TextButton(
            onClick = {
                if (actions.onAddCatalog(name, url)) {
                    name = ""
                    url = ""
                    rejected = false
                } else {
                    rejected = true
                }
            },
            enabled = url.isNotBlank()
        ) {
            Text(stringResource(R.string.feature_settings_catalog_add))
        }
    }
}

/**
 * What this build is, for when something needs reporting. The version is read from the installed
 * package rather than a `BuildConfig` constant, which library modules do not get.
 */
@Composable
private fun AboutSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    SettingsSection(
        title = stringResource(R.string.feature_settings_section_about),
        icon = ReadanaIcons.Library,
        modifier = modifier
    ) {
        SettingsNote(stringResource(R.string.feature_settings_about_version, version))
        SettingsNote(stringResource(R.string.feature_settings_about_blurb))
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
