package dev.readana.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.NoteRow
import kotlin.math.roundToInt

private val IndentPerLevel = 16.dp
private val HeaderPadding = 24.dp
private const val PERCENT = 100

/** The three ways of moving around a book, behind the one button that has always opened them. */
private enum class ContentsTab { Contents, Bookmarks, Notes }

/**
 * Chapters, saved places and marked passages. Tapping a row jumps there and closes the sheet.
 *
 * Tabs rather than stacked headings: with notes in it the sheet holds three lists that have nothing
 * to do with each other, and a reader looking for a note should not have to scroll past the whole
 * table of contents to find out whether they have any.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderContentsSheet(state: ReaderUiState.Ready, actions: ReaderActions, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(ContentsTab.Contents) }

    ModalBottomSheet(onDismissRequest = actions.onDismissSheet, modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            ContentsTab.entries.forEach { option ->
                Tab(
                    selected = option == tab,
                    onClick = { tab = option },
                    text = { Text(stringResource(option.labelRes())) }
                )
            }
        }
        when (tab) {
            ContentsTab.Contents -> ContentsList(state.toc, actions.onSelectChapter)
            ContentsTab.Bookmarks -> BookmarksList(state.bookmarks, actions)
            ContentsTab.Notes -> NotesList(state, actions)
        }
    }
}

@Composable
private fun ContentsList(toc: List<TocItem>, onSelect: (TocItem) -> Unit, modifier: Modifier = Modifier) {
    if (toc.isEmpty()) {
        EmptyState(
            icon = ReadanaIcons.Library,
            title = stringResource(R.string.feature_reader_contents_empty),
            modifier = modifier
        )
        return
    }
    LazyColumn(modifier) {
        items(toc, key = { "toc-${it.index}" }) { item ->
            ListItem(
                modifier = Modifier
                    .clickable { onSelect(item) }
                    .padding(start = IndentPerLevel * item.depth)
            ) {
                Text(item.title)
            }
        }
    }
}

@Composable
private fun BookmarksList(bookmarks: List<BookmarkItem>, actions: ReaderActions, modifier: Modifier = Modifier) {
    if (bookmarks.isEmpty()) {
        EmptyState(
            icon = ReadanaIcons.BookmarkOff,
            title = stringResource(R.string.feature_reader_bookmarks_empty),
            description = stringResource(R.string.feature_reader_bookmarks_empty_help),
            modifier = modifier
        )
        return
    }
    LazyColumn(modifier) {
        items(bookmarks, key = { it.id }) { bookmark ->
            ListItem(
                modifier = Modifier.clickable { actions.onSelectBookmark(bookmark) },
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
                    IconButton(onClick = { actions.onDeleteBookmark(bookmark.id) }) {
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
    }
}

@Composable
private fun NotesList(state: ReaderUiState.Ready, actions: ReaderActions, modifier: Modifier = Modifier) {
    if (state.highlights.isEmpty()) {
        EmptyState(
            icon = ReadanaIcons.Note,
            title = stringResource(R.string.feature_reader_notes_empty),
            description = stringResource(R.string.feature_reader_notes_empty_help),
            modifier = modifier
        )
        return
    }
    LazyColumn(modifier) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = HeaderPadding, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.feature_reader_notes_count,
                        state.highlights.size,
                        state.highlights.size
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = actions.onShareNotes) {
                    Icon(
                        ReadanaIcons.Share,
                        contentDescription = stringResource(R.string.feature_reader_notes_share)
                    )
                }
            }
        }
        items(state.highlights, key = { it.id }) { highlight ->
            NoteRow(
                text = highlight.text,
                tint = highlight.tint,
                note = highlight.note,
                progression = highlight.progression,
                onClick = { actions.onSelectHighlight(highlight.id) },
                trailing = {
                    IconButton(onClick = { actions.onEditHighlight(highlight.id) }) {
                        Icon(
                            ReadanaIcons.Edit,
                            contentDescription = stringResource(R.string.feature_reader_note_edit)
                        )
                    }
                }
            )
        }
    }
}

private fun ContentsTab.labelRes(): Int = when (this) {
    ContentsTab.Contents -> R.string.feature_reader_contents
    ContentsTab.Bookmarks -> R.string.feature_reader_bookmarks
    ContentsTab.Notes -> R.string.feature_reader_notes
}
