package dev.readana.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.BookDetailsHeader
import dev.readana.core.designsystem.component.BookDetailsPadding
import dev.readana.core.designsystem.component.BookFact
import dev.readana.core.designsystem.component.NoteRow
import dev.readana.core.model.Book
import dev.readana.core.model.BookCollection
import dev.readana.core.model.BookFormat
import dev.readana.core.model.ConversionReport
import dev.readana.core.model.Highlight
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val SheetPadding = BookDetailsPadding
private val DetailValueWidth = 220.dp
private const val PERCENT = 100

/** "Book 2", not "Book 2.0"; half-numbers exist in series and should survive. */
private fun Float.seriesLabel(): String = if (this == toInt().toFloat()) toInt().toString() else toString()

private val AddedDateFormatter: DateTimeFormatter = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withZone(ZoneId.systemDefault())

/** What the details sheet can ask for, grouped so the sheet keeps one parameter. */
internal data class BookDetailsActions(
    val onDelete: () -> Unit,
    val onConvert: () -> Unit,
    val onShareNotes: () -> Unit,
    val onSetCollectionMembership: (collectionId: String, member: Boolean) -> Unit,
    val onCreateCollection: (String) -> Unit,
    val onFinishedChange: (Boolean) -> Unit,
    val onDismiss: () -> Unit
)

/** Everything known about one book, with the actions that act on it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookDetailsSheet(
    book: Book,
    isConverting: Boolean,
    notes: List<Highlight>,
    collections: List<BookCollection>,
    memberOf: Set<String>,
    actions: BookDetailsActions,
    modifier: Modifier = Modifier
) {
    var showReport by rememberSaveable { mutableStateOf(false) }
    var confirmReconvert by rememberSaveable { mutableStateOf(false) }
    var editCollections by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = actions.onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Notes make this sheet as long as the reader has been busy.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SheetPadding)
                .padding(bottom = SheetPadding)
        ) {
            BookDetailsHeader(
                title = book.title,
                subtitle = book.authorsLabel,
                coverUri = book.coverUri
            )
            Spacer(Modifier.height(SheetPadding))
            BookFacts(book, onShowReport = { showReport = true })
            Spacer(Modifier.height(SheetPadding))
            // No "Read" here: tapping the book in the grid already opens it, and this sheet is
            // reached by long-pressing that same cover.
            // Wrapping, not sharing the width out: "Convert to EPUB" beside two more labelled
            // buttons is wider than a phone, and squeezed buttons read as broken ones.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConvertButton(
                    book = book,
                    isConverting = isConverting,
                    onConvert = actions.onConvert,
                    onConfirmReconvert = { confirmReconvert = true }
                )
                OutlinedButton(onClick = { editCollections = true }) {
                    Icon(ReadanaIcons.Collection, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.feature_library_collections_edit))
                }
                OutlinedButton(onClick = actions.onDelete) {
                    Icon(ReadanaIcons.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.feature_library_details_delete))
                }
            }
            FinishedButton(book = book, onFinishedChange = actions.onFinishedChange)
            NotesSection(notes = notes, onShare = actions.onShareNotes)
        }
    }

    if (editCollections) {
        CollectionsDialog(
            collections = collections,
            memberOf = memberOf,
            onSetMembership = actions.onSetCollectionMembership,
            onCreate = actions.onCreateCollection,
            onDismiss = { editCollections = false }
        )
    }
    if (showReport) {
        book.conversion?.report?.let { report ->
            ConversionReportDialog(report = report, onDismiss = { showReport = false })
        }
    }
    if (confirmReconvert) {
        ReconvertDialog(
            onConfirm = {
                confirmReconvert = false
                actions.onConvert()
            },
            onDismiss = { confirmReconvert = false }
        )
    }
}

/**
 * The reader's own verdict on a book.
 *
 * A text button rather than a switch: it is a thing you do once, not a setting you keep. The label
 * says what will happen, so nothing has to be read twice.
 *
 * It tracks the mark the reader actually set, not the status derived from it. Derived status also
 * calls a book read once the bar is within a couple of percent of the end, so on a book you had
 * finished anyway the button changed nothing when pressed and looked broken.
 */
@Composable
private fun FinishedButton(book: Book, onFinishedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val finished = book.finishedAt != null
    TextButton(onClick = { onFinishedChange(!finished) }, modifier = modifier) {
        Icon(if (finished) ReadanaIcons.Refresh else ReadanaIcons.Done, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                if (finished) {
                    R.string.feature_library_mark_unfinished
                } else {
                    R.string.feature_library_mark_finished
                }
            )
        )
    }
}

/**
 * What the reader marked while reading, and one way to take it with them.
 *
 * Absent entirely until there is something to show: a book nobody has annotated should not carry an
 * empty heading.
 */
@Composable
private fun NotesSection(notes: List<Highlight>, onShare: () -> Unit, modifier: Modifier = Modifier) {
    if (notes.isEmpty()) return
    Column(modifier) {
        Spacer(Modifier.height(SheetPadding))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.feature_library_notes_count, notes.size, notes.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShare) {
                Icon(
                    ReadanaIcons.Share,
                    contentDescription = stringResource(R.string.feature_library_notes_share)
                )
            }
        }
        notes.forEach { note ->
            NoteRow(
                text = note.text,
                tint = note.tint,
                note = note.note,
                progression = note.progression
            )
        }
    }
}

/** Converts a PDF, or offers to run a converted book through the engine again. */
@Composable
private fun ConvertButton(book: Book, isConverting: Boolean, onConvert: () -> Unit, onConfirmReconvert: () -> Unit) {
    val converted = book.conversion != null
    if (!converted && book.format != BookFormat.PDF) return

    OutlinedButton(
        onClick = if (converted) onConfirmReconvert else onConvert,
        enabled = !isConverting
    ) {
        Text(
            stringResource(
                when {
                    isConverting -> R.string.feature_library_details_converting
                    converted -> R.string.feature_library_details_reconvert
                    else -> R.string.feature_library_details_convert
                }
            )
        )
    }
}

@Composable
private fun ConversionReportDialog(report: ConversionReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_library_conversion_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BookFact(stringResource(R.string.feature_library_conversion_pages), report.pages.toString())
                BookFact(stringResource(R.string.feature_library_conversion_chapters), report.chapters.toString())
                BookFact(stringResource(R.string.feature_library_conversion_headings), report.headings.toString())
                BookFact(stringResource(R.string.feature_library_conversion_images), report.images.toString())
                if (report.multiColumnPages > 0) {
                    BookFact(
                        label = stringResource(R.string.feature_library_conversion_columns),
                        value = report.multiColumnPages.toString()
                    )
                }
                BookFact(
                    label = stringResource(R.string.feature_library_conversion_source),
                    value = stringResource(
                        if (report.outlineUsed) {
                            R.string.feature_library_conversion_source_outline
                        } else {
                            R.string.feature_library_conversion_source_headings
                        }
                    ),
                    maxLines = 2
                )
                report.warnings.forEach { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feature_library_conversion_report_close))
            }
        }
    )
}

@Composable
private fun ReconvertDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_library_reconvert_title)) },
        text = { Text(stringResource(R.string.feature_library_reconvert_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.feature_library_reconvert_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feature_library_delete_dialog_cancel))
            }
        }
    )
}

@Composable
private fun BookFacts(book: Book, onShowReport: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.feature_library_details_unknown)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BookFact(stringResource(R.string.feature_library_details_format), book.format.extension.uppercase())
        BookFact(
            label = stringResource(R.string.feature_library_details_size),
            value = book.sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: unknown
        )
        BookFact(
            label = stringResource(R.string.feature_library_details_added),
            value = AddedDateFormatter.format(book.addedAt)
        )
        BookFact(stringResource(R.string.feature_library_details_language), book.language ?: unknown)
        book.series?.let { series ->
            BookFact(
                label = stringResource(R.string.feature_library_details_series),
                value = series.index?.let { index ->
                    stringResource(
                        R.string.feature_library_details_series_value,
                        series.name,
                        index.seriesLabel()
                    )
                } ?: series.name
            )
        }
        book.progress?.let { progress ->
            BookFact(
                label = stringResource(R.string.feature_library_details_progress),
                value = stringResource(
                    R.string.feature_library_details_progress_value,
                    (progress.fraction * PERCENT).roundToInt()
                )
            )
        }
        BookFact(stringResource(R.string.feature_library_details_location), book.fileUri, maxLines = 2)
        book.conversion?.let { conversion ->
            BookFact(
                label = stringResource(R.string.feature_library_details_converted),
                value = AddedDateFormatter.format(conversion.convertedAt)
            )
            BookFact(
                label = stringResource(R.string.feature_library_details_original),
                value = conversion.originalFileUri,
                maxLines = 2
            )
            conversion.report?.let { report ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onShowReport),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.feature_library_details_conversion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = report.summary(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(DetailValueWidth)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversionReport.summary(): String = listOfNotNull(
    pluralStringResource(R.plurals.feature_library_conversion_chapter_count, chapters, chapters),
    images.takeIf { it > 0 }?.let {
        pluralStringResource(R.plurals.feature_library_conversion_image_count, it, it)
    },
    warnings.size.takeIf { it > 0 }?.let {
        pluralStringResource(R.plurals.feature_library_conversion_warning_count, it, it)
    }
).joinToString(" · ")

@Composable
internal fun DeleteBookDialog(book: Book, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(ReadanaIcons.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_library_delete_dialog_title, book.title)) },
        text = { Text(stringResource(R.string.feature_library_delete_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.feature_library_delete_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.feature_library_delete_dialog_cancel))
            }
        }
    )
}
