package dev.readana.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.model.PageTurnMode
import dev.readana.core.model.ReaderColumns
import dev.readana.core.model.ReaderFont
import dev.readana.core.model.ReaderSettings
import dev.readana.core.model.ReaderTheme
import kotlin.math.roundToInt

private val SheetPadding = 24.dp
private const val PERCENT = 100
private val IndentPerLevel = 16.dp

/** Chapters and bookmarks; tapping a row jumps there and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderContentsSheet(
    toc: List<TocItem>,
    bookmarks: List<BookmarkItem>,
    onSelectChapter: (TocItem) -> Unit,
    onSelectBookmark: (BookmarkItem) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        if (toc.isEmpty() && bookmarks.isEmpty()) {
            EmptyState(
                icon = ReadanaIcons.Library,
                title = stringResource(R.string.feature_reader_contents_empty)
            )
            return@ModalBottomSheet
        }
        LazyColumn {
            if (bookmarks.isNotEmpty()) {
                item { SheetHeading(stringResource(R.string.feature_reader_bookmarks)) }
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onSelect = onSelectBookmark,
                        onDelete = onDeleteBookmark
                    )
                }
            }
            if (toc.isNotEmpty()) {
                item { SheetHeading(stringResource(R.string.feature_reader_contents)) }
                items(toc, key = { "toc-${it.index}" }) { item ->
                    ListItem(
                        modifier = Modifier
                            .clickable { onSelectChapter(item) }
                            .padding(start = IndentPerLevel * item.depth)
                    ) {
                        Text(item.title)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = SheetPadding, vertical = 8.dp)
    )
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkItem,
    onSelect: (BookmarkItem) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier.clickable { onSelect(bookmark) },
        supportingContent = bookmark.progression?.let { progression ->
            {
                Text(
                    stringResource(
                        R.string.feature_reader_progress,
                        (progression * PERCENT).roundToInt()
                    )
                )
            }
        },
        trailingContent = {
            IconButton(onClick = { onDelete(bookmark.id) }) {
                Icon(
                    ReadanaIcons.Delete,
                    contentDescription = stringResource(R.string.feature_reader_bookmark_delete)
                )
            }
        }
    ) {
        Text(bookmark.title ?: stringResource(R.string.feature_reader_bookmark_untitled))
    }
}

/** Reading settings, applied live to the open book and shared with the Settings screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
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
            ChipRow(
                options = PageTurnMode.entries,
                selected = settings.pageTurnMode,
                onSelect = { mode -> onChange { it.copy(pageTurnMode = mode) } }
            )

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

@Composable
private fun <T : Enum<T>> ChipRow(options: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.name) }
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
