package dev.readana.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.model.AiContextSize
import dev.readana.core.model.AiProviderConfig
import dev.readana.core.model.AiProviderKind
import dev.readana.core.model.AiSettings

/**
 * What the assistant needs to work at all: which provider answers, with which key and which model,
 * plus the two things a reader turns on or off from one week to the next.
 *
 * The providers are a single-choice list, and the chosen one's own settings sit directly under it.
 * Only one provider is ever in use, so only one set of fields is ever worth showing.
 */
@Composable
internal fun AiSettingsSection(state: AiSettingsUiState, actions: AiSettingsActions, modifier: Modifier = Modifier) {
    var addingCustom by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    val active = settings.active

    SettingsSection(
        title = stringResource(R.string.feature_settings_section_ai),
        icon = ReadanaIcons.Assistant,
        modifier = modifier
    ) {
        SettingsGroup {
            settings.providers.forEach { provider ->
                item { shapes ->
                    ProviderItem(
                        provider = provider,
                        shapes = shapes,
                        isActive = provider.id == settings.activeProviderId,
                        onSelect = { actions.onSetActiveProvider(provider.id) },
                        onRemove = { actions.onRemoveProvider(provider.id) }
                    )
                }
            }
        }
        OutlinedButton(onClick = { addingCustom = true }) {
            Icon(ReadanaIcons.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.feature_settings_ai_add_custom),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (active != null) {
            ProviderDetails(
                provider = active,
                probe = state.probes[active.id] ?: ProviderProbe(),
                actions = actions
            )
        }
        SettingsGroup {
            item { shapes ->
                SwitchItem(
                    shapes = shapes,
                    label = stringResource(R.string.feature_settings_ai_illustrate),
                    checked = settings.illustrateFromWeb,
                    onCheckedChange = actions.onIllustrateChange,
                    help = stringResource(R.string.feature_settings_ai_illustrate_help)
                )
            }
            item { shapes ->
                SwitchItem(
                    shapes = shapes,
                    label = stringResource(R.string.feature_settings_ai_recap),
                    checked = settings.recapEnabled,
                    onCheckedChange = actions.onRecapEnabledChange,
                    help = pluralStringResource(
                        R.plurals.feature_settings_ai_recap_days,
                        settings.recapAfterDays,
                        settings.recapAfterDays
                    )
                )
            }
        }
    }

    if (addingCustom) {
        CustomProviderDialog(
            onAdd = {
                actions.onAddCustomProvider(it)
                addingCustom = false
            },
            onDismiss = { addingCustom = false }
        )
    }
}

/**
 * What the assistant costs and how much of the book it is trusted with.
 *
 * Both are set once, when a reader decides how they want to use it, and then left alone.
 */
@Composable
internal fun AiAdvancedSection(state: AiSettingsUiState, actions: AiSettingsActions, modifier: Modifier = Modifier) {
    val settings = state.settings
    SettingsSection(
        title = stringResource(R.string.feature_settings_section_ai_advanced),
        icon = ReadanaIcons.Assistant,
        modifier = modifier
    ) {
        ChoiceRow(
            label = stringResource(R.string.feature_settings_ai_context),
            options = AiContextSize.entries,
            selected = settings.contextSize,
            optionLabel = { stringResource(it.labelRes()) },
            onSelect = actions.onContextSizeChange,
            help = stringResource(R.string.feature_settings_ai_context_help)
        )
        if (settings.recapEnabled) {
            Column {
                Text(
                    text = stringResource(R.string.feature_settings_ai_recap_delay),
                    style = MaterialTheme.typography.titleSmall
                )
                val days = AiSettings.RECAP_DAYS_RANGE
                Slider(
                    value = settings.recapAfterDays.toFloat(),
                    onValueChange = { actions.onRecapAfterDaysChange(it.toInt()) },
                    valueRange = days.first.toFloat()..days.last.toFloat(),
                    steps = days.last - days.first - 1
                )
            }
        }
        SettingsNote(stringResource(R.string.feature_settings_ai_privacy))
    }
}

/**
 * One provider in the list. The row picks it; the bin next to it is there whether or not the
 * provider is in use, so a custom one can be removed without being selected first.
 */
@Composable
private fun ProviderItem(
    provider: AiProviderConfig,
    shapes: ListItemShapes,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        selected = isActive,
        onClick = onSelect,
        shapes = shapes,
        modifier = modifier,
        // Always tappable: a disabled radio gives the reader no way to tell a provider they have
        // not finished setting up from one the app has silently refused.
        leadingContent = { RadioButton(selected = isActive, onClick = null) },
        supportingContent = { Text(provider.statusLine()) },
        trailingContent = {
            if (provider.kind == AiProviderKind.Custom) {
                IconButton(onClick = onRemove) {
                    Icon(
                        ReadanaIcons.Delete,
                        contentDescription = stringResource(R.string.feature_settings_ai_remove)
                    )
                }
            }
        },
        colors = ListItemDefaults.segmentedColors()
    ) {
        Text(provider.name)
    }
}

/** The chosen provider's own settings, attached to the list above it. */
@Composable
private fun ProviderDetails(
    provider: AiProviderConfig,
    probe: ProviderProbe,
    actions: AiSettingsActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = provider.name, style = MaterialTheme.typography.titleSmall)
            CommittingField(
                value = provider.apiKey.orEmpty(),
                label = stringResource(R.string.feature_settings_ai_key),
                onCommit = { actions.onApiKeyChange(provider.id, it) },
                isSecret = true
            )
            if (provider.kind.allowsCustomBaseUrl) {
                CommittingField(
                    value = provider.baseUrl.orEmpty(),
                    label = stringResource(R.string.feature_settings_ai_base_url),
                    placeholder = provider.kind.defaultBaseUrl ?: OPENAI_COMPATIBLE_HINT,
                    onCommit = { actions.onBaseUrlChange(provider.id, it) }
                )
            }
            CommittingField(
                value = provider.model.orEmpty(),
                label = stringResource(R.string.feature_settings_ai_model),
                placeholder = provider.kind.defaultModel.orEmpty(),
                onCommit = { actions.onModelChange(provider.id, it) }
            )
            if (probe.models.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    probe.models.forEach { model ->
                        FilterChip(
                            selected = model == provider.effectiveModel,
                            onClick = { actions.onModelChange(provider.id, model) },
                            label = { Text(model) }
                        )
                    }
                }
            }
            // Three labelled buttons do not fit side by side on a phone; wrapping beats clipping
            // the last one off the edge of the card.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { actions.onFetchModels(provider.id) },
                    enabled = !probe.isBusy
                ) {
                    Text(stringResource(R.string.feature_settings_ai_fetch_models))
                }
                OutlinedButton(
                    onClick = { actions.onTestProvider(provider.id) },
                    enabled = !probe.isBusy
                ) {
                    Text(stringResource(R.string.feature_settings_ai_test))
                }
                provider.kind.consoleUrl?.let { url ->
                    TextButton(onClick = { context.openUrl(url) }) {
                        Text(stringResource(R.string.feature_settings_ai_get_key))
                    }
                }
            }
            ProbeResult(probe)
        }
    }
}

@Composable
private fun ProbeResult(probe: ProviderProbe, modifier: Modifier = Modifier) {
    val text = when {
        probe.error != null -> probe.error

        probe.latencyMillis != null ->
            stringResource(R.string.feature_settings_ai_test_ok, probe.latencyMillis)

        else -> null
    } ?: return
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = if (probe.error != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

/**
 * A settings text field that saves as it is typed.
 *
 * It deliberately does not wait for focus to be lost, the way the mirror list does: a key pasted
 * into a field that is then scrolled away — or a row whose radio button is waiting on that key —
 * left the assistant switched off with nothing on screen to say why.
 */
@Composable
private fun CommittingField(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isSecret: Boolean = false
) {
    var draft by rememberSaveable(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = draft,
        onValueChange = {
            draft = it
            onCommit(it)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(draft) }),
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@Composable
private fun CustomProviderDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.feature_settings_ai_add_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.feature_settings_ai_add_custom_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.feature_settings_ai_custom_name)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.feature_settings_ai_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feature_settings_ai_cancel))
            }
        }
    )
}

@Composable
private fun AiProviderConfig.statusLine(): String = when {
    !apiKey.isNullOrBlank() -> stringResource(R.string.feature_settings_ai_status_key, effectiveModel.orEmpty())
    kind.keyRequired -> stringResource(R.string.feature_settings_ai_status_no_key)
    isUsable -> stringResource(R.string.feature_settings_ai_status_local)
    else -> stringResource(R.string.feature_settings_ai_status_incomplete)
}

private fun AiContextSize.labelRes(): Int = when (this) {
    AiContextSize.Small -> R.string.feature_settings_ai_context_small
    AiContextSize.Medium -> R.string.feature_settings_ai_context_medium
    AiContextSize.Large -> R.string.feature_settings_ai_context_large
    AiContextSize.Whole -> R.string.feature_settings_ai_context_whole
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser on the device; the URL is written in the help text anyway.
    }
}

private const val OPENAI_COMPATIBLE_HINT = "http://192.168.1.10:11434/v1"

/** Grouped so the section keeps one parameter instead of a dozen lambdas. */
internal data class AiSettingsActions(
    val onSetActiveProvider: (String) -> Unit,
    val onApiKeyChange: (String, String) -> Unit,
    val onModelChange: (String, String) -> Unit,
    val onBaseUrlChange: (String, String) -> Unit,
    val onAddCustomProvider: (String) -> Unit,
    val onRemoveProvider: (String) -> Unit,
    val onFetchModels: (String) -> Unit,
    val onTestProvider: (String) -> Unit,
    val onContextSizeChange: (AiContextSize) -> Unit,
    val onRecapEnabledChange: (Boolean) -> Unit,
    val onIllustrateChange: (Boolean) -> Unit,
    val onRecapAfterDaysChange: (Int) -> Unit
)
