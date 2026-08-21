package dev.readana.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The pieces the settings screen is built from.
 *
 * The screen is one continuous list: a section is a heading followed by a group of Material 3
 * segmented list items, which round their outer corners as a group so related settings read as one
 * block without needing a card, a chevron or a tap to reveal them.
 */
@Composable
internal fun SettingsSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

/** Collects the rows of one group so each can be told where it sits and shape itself to match. */
internal class SettingsGroupScope {
    val items = mutableListOf<@Composable (ListItemShapes) -> Unit>()

    fun item(content: @Composable (ListItemShapes) -> Unit) {
        items += content
    }
}

@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: SettingsGroupScope.() -> Unit) {
    val items = SettingsGroupScope().apply(content).items
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        items.forEachIndexed { index, item ->
            item(ListItemDefaults.segmentedShapes(index = index, count = items.size))
        }
    }
}

/** A setting that is on or off. The whole row is the target, not just the switch. */
@Composable
internal fun SwitchItem(
    shapes: ListItemShapes,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    help: String? = null
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        modifier = modifier,
        supportingContent = if (help != null) {
            { Text(help) }
        } else {
            null
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.segmentedColors()
    ) {
        Text(label)
    }
}

/**
 * One choice out of a handful, as a connected button group: the options sit shoulder to shoulder
 * and the chosen one swells, so the row reads as a single control rather than loose chips.
 */
@Composable
internal fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    help: String? = null
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            options.forEachIndexed { index, option ->
                ToggleButton(
                    checked = option == selected,
                    onCheckedChange = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                ) {
                    Text(text = optionLabel(option), maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
        if (help != null) SettingsNote(help)
    }
}

/** Explanatory text under a control: quieter than the setting it belongs to. */
@Composable
internal fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
