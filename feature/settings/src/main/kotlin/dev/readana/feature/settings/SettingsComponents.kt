package dev.readana.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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

/**
 * A setting that is on or off. The whole row is the target, not just the switch.
 *
 * [trailing] sits before the switch, for a row that has somewhere else to go as well as something
 * to turn on — a source with its own settings, say.
 */
@Composable
internal fun SwitchItem(
    shapes: ListItemShapes,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    help: String? = null,
    trailing: (@Composable () -> Unit)? = null
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
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                Switch(checked = checked, onCheckedChange = null)
            }
        },
        colors = ListItemDefaults.segmentedColors()
    ) {
        Text(label)
    }
}

/** A row that does something when tapped rather than holding a value. */
@Composable
internal fun ActionItem(
    shapes: ListItemShapes,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        supportingContent = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        colors = ListItemDefaults.segmentedColors()
    ) {
        Text(label)
    }
}

/**
 * One choice out of a handful, as a group of toggle buttons.
 *
 * Every button takes the width its own label needs and the group wraps onto a second line when the
 * window is too narrow for all of them. Sharing the width out equally instead would squeeze five
 * theme names into five slivers on a phone, which is how a label ends up broken across three lines
 * or clipped to a letter and a half.
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                ToggleButton(
                    checked = option == selected,
                    onCheckedChange = { onSelect(option) }
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
