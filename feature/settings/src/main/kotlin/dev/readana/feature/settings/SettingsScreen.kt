package dev.readana.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.readana.core.designsystem.component.LoadingState
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme
import dev.readana.core.model.SourceSettings

@Composable
internal fun SettingsRoute(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onFontScaleChange = viewModel::setFontScale,
        onThemeChange = viewModel::setTheme,
        onPageTurnModeChange = viewModel::setPageTurnMode,
        onKeepScreenOnChange = viewModel::setKeepScreenOn,
        onVolumeKeysChange = viewModel::setVolumeKeysTurnPages,
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
    onKeepScreenOnChange: (Boolean) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
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
                    onKeepScreenOnChange = onKeepScreenOnChange,
                    onVolumeKeysChange = onVolumeKeysChange
                )
                HorizontalDivider()
                SourcesSection(
                    settings = uiState.sources,
                    toggles = uiState.sourceToggles,
                    onSourceEnabledChange = onSourceEnabledChange,
                    onAnnasMirrorsChange = onAnnasMirrorsChange,
                    onAnnasSecretKeyChange = onAnnasSecretKeyChange
                )
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
