package dev.readana.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

/** Everything the assistant needs: which provider answers, with which key and which model. */
@Composable
internal fun AiSettingsSection(state: AiSettingsUiState, actions: AiSettingsActions, modifier: Modifier = Modifier) {
    var expandedProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var addingCustom by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.settings.providers.forEach { provider ->
            ProviderRow(
                provider = provider,
                probe = state.probes[provider.id] ?: ProviderProbe(),
                isActive = provider.id == state.settings.activeProviderId,
                expanded = expandedProvider == provider.id,
                onToggleDetails = {
                    expandedProvider = if (expandedProvider == provider.id) null else provider.id
                },
                actions = actions
            )
        }
        OutlinedButton(onClick = { addingCustom = true }) {
            Text(stringResource(R.string.feature_settings_ai_add_custom))
        }
        ContextSizeRow(state.settings.contextSize, actions.onContextSizeChange)
        RecapRow(state.settings, actions)
        Text(
            text = stringResource(R.string.feature_settings_ai_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

/** One provider: a radio for "use this one", and its own settings when opened. */
@Composable
private fun ProviderRow(
    provider: AiProviderConfig,
    probe: ProviderProbe,
    isActive: Boolean,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
    actions: AiSettingsActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleDetails)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Always tappable: a disabled radio gives the reader no way to tell a provider they
            // have not finished setting up from one the app has silently refused.
            RadioButton(
                selected = isActive,
                onClick = { actions.onSetActiveProvider(provider.id) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = provider.statusLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            ProviderDetails(provider = provider, probe = probe, actions = actions)
        }
    }
}

@Composable
private fun ProviderDetails(
    provider: AiProviderConfig,
    probe: ProviderProbe,
    actions: AiSettingsActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.padding(start = 40.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                probe.models.forEach { model ->
                    FilterChip(
                        selected = model == provider.effectiveModel,
                        onClick = { actions.onModelChange(provider.id, model) },
                        label = { Text(model) }
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            if (provider.kind == AiProviderKind.Custom) {
                IconButton(onClick = { actions.onRemoveProvider(provider.id) }) {
                    Icon(
                        ReadanaIcons.Delete,
                        contentDescription = stringResource(R.string.feature_settings_ai_remove)
                    )
                }
            }
        }
        ProbeResult(probe)
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
 * into a field that is then collapsed away — or a row whose radio button is waiting on that key —
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
private fun ContextSizeRow(selected: AiContextSize, onSelect: (AiContextSize) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.feature_settings_ai_context))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiContextSize.entries.forEach { size ->
                FilterChip(
                    selected = size == selected,
                    onClick = { onSelect(size) },
                    label = { Text(stringResource(size.labelRes())) }
                )
            }
        }
        Text(
            text = stringResource(R.string.feature_settings_ai_context_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecapRow(settings: AiSettings, actions: AiSettingsActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.feature_settings_ai_recap))
            Switch(checked = settings.recapEnabled, onCheckedChange = actions.onRecapEnabledChange)
        }
        if (settings.recapEnabled) {
            Text(
                text = pluralStringResource(
                    R.plurals.feature_settings_ai_recap_days,
                    settings.recapAfterDays,
                    settings.recapAfterDays
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = settings.recapAfterDays.toFloat(),
                onValueChange = { actions.onRecapAfterDaysChange(it.toInt()) },
                valueRange = AiSettings.RECAP_DAYS_RANGE.first.toFloat()..AiSettings.RECAP_DAYS_RANGE.last.toFloat(),
                steps = AiSettings.RECAP_DAYS_RANGE.last - AiSettings.RECAP_DAYS_RANGE.first - 1
            )
        }
    }
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
    val onRecapAfterDaysChange: (Int) -> Unit
)
