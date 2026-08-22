package dev.readana.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderColumns
import dev.readana.core.model.ReaderFont
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme

private val SheetPadding = 24.dp

/** Reading settings, applied live to the open book and shared with the Settings screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    isFixedLayout: Boolean,
    hasOwnSettings: Boolean,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SheetPadding)
                .padding(bottom = SheetPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.feature_reader_settings),
                style = MaterialTheme.typography.titleLarge
            )

            Label(stringResource(R.string.feature_reader_theme))
            ChipRow(
                options = ReaderTheme.entries,
                selected = settings.theme,
                onSelect = { theme -> onChange { it.copy(theme = theme) } }
            )

            Label(stringResource(R.string.feature_reader_font))
            ChipRow(
                options = ReaderFont.entries,
                selected = settings.fontFamily,
                onSelect = { font -> onChange { it.copy(fontFamily = font) } }
            )

            Label(stringResource(R.string.feature_reader_font_size))
            Slider(
                value = settings.fontScale,
                onValueChange = { scale -> onChange { it.copy(fontScale = scale) } },
                valueRange = ReaderSettings.FONT_SCALE_RANGE
            )

            Label(stringResource(R.string.feature_reader_line_height))
            Slider(
                value = settings.lineHeight,
                onValueChange = { height -> onChange { it.copy(lineHeight = height) } },
                valueRange = ReaderSettings.LINE_HEIGHT_RANGE
            )

            Label(stringResource(R.string.feature_reader_paragraph_spacing))
            Slider(
                value = settings.paragraphSpacing,
                onValueChange = { spacing -> onChange { it.copy(paragraphSpacing = spacing) } },
                valueRange = ReaderSettings.PARAGRAPH_SPACING_RANGE
            )

            Label(stringResource(R.string.feature_reader_margins))
            Slider(
                value = settings.pageMargins,
                onValueChange = { margins -> onChange { it.copy(pageMargins = margins) } },
                valueRange = ReaderSettings.PAGE_MARGIN_RANGE
            )

            Label(stringResource(R.string.feature_reader_page_turn))
            if (isFixedLayout) {
                Text(
                    text = stringResource(R.string.feature_reader_fixed_layout_pages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ChipRow(
                    options = PageTurnMode.entries,
                    selected = settings.pageTurnMode,
                    onSelect = { mode -> onChange { it.copy(pageTurnMode = mode) } }
                )
            }

            Label(stringResource(R.string.feature_reader_columns))
            ChipRow(
                options = ReaderColumns.entries,
                selected = settings.columns,
                onSelect = { columns -> onChange { it.copy(columns = columns) } }
            )

            SwitchRow(
                label = stringResource(R.string.feature_reader_tap_to_turn),
                checked = settings.tapToTurnPages,
                onCheckedChange = { enabled -> onChange { it.copy(tapToTurnPages = enabled) } }
            )
            SwitchRow(
                label = stringResource(R.string.feature_reader_volume_keys),
                checked = settings.volumeKeysTurnPages,
                onCheckedChange = { enabled -> onChange { it.copy(volumeKeysTurnPages = enabled) } }
            )
            SwitchRow(
                label = stringResource(R.string.feature_reader_keep_screen_on),
                checked = settings.keepScreenOn,
                onCheckedChange = { enabled -> onChange { it.copy(keepScreenOn = enabled) } }
            )

            // Only once there is something to go back from: until then this book reads with the
            // defaults and there is nothing to say about it.
            if (hasOwnSettings) {
                Text(
                    text = stringResource(R.string.feature_reader_settings_own),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(onClick = onReset, modifier = Modifier.align(Alignment.Start)) {
                    Text(stringResource(R.string.feature_reader_settings_reset))
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp)
    )
}

/**
 * The options for one setting, wrapping rather than running off the edge.
 *
 * Five theme names do not fit across a phone in a plain row, and the ones past the edge were
 * simply unreachable.
 */
@Composable
private fun <T : Enum<T>> ChipRow(options: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.name, maxLines = 1) }
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
