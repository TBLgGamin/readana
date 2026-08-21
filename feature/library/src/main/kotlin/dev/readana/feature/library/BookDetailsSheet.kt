package dev.readana.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.BookCover
import dev.readana.core.model.Book
import dev.readana.core.model.BookFormat
import dev.readana.core.model.ConversionReport
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val SheetPadding = 24.dp
private val DetailCoverWidth = 120.dp
private val DetailValueWidth = 220.dp
private const val PERCENT = 100

private val AddedDateFormatter: DateTimeFormatter = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withZone(ZoneId.systemDefault())

/** Everything known about one book, with the two actions that act on it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookDetailsSheet(
    book: Book,
    isConverting: Boolean,
    onDelete: () -> Unit,
    onConvert: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showReport by rememberSaveable { mutableStateOf(false) }
    var confirmReconvert by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SheetPadding)
                .padding(bottom = SheetPadding)
        ) {
            Row {
                BookCover(
                    title = book.title,
                    coverUri = book.coverUri,
                    modifier = Modifier.width(DetailCoverWidth)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = book.authorsLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(SheetPadding))
            BookFacts(book, onShowReport = { showReport = true })
            Spacer(Modifier.height(SheetPadding))
            // No "Read" here: tapping the book in the grid already opens it, and this sheet is
            // reached by long-pressing that same cover.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ConvertButton(
                    book = book,
                    isConverting = isConverting,
                    onConvert = onConvert,
                    onConfirmReconvert = { confirmReconvert = true }
                )
                OutlinedButton(onClick = onDelete) {
                    Icon(ReadanaIcons.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.feature_library_details_delete))
                }
            }
        }
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
                onConvert()
            },
            onDismiss = { confirmReconvert = false }
        )
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
                Fact(stringResource(R.string.feature_library_conversion_pages), report.pages.toString())
                Fact(stringResource(R.string.feature_library_conversion_chapters), report.chapters.toString())
                Fact(stringResource(R.string.feature_library_conversion_headings), report.headings.toString())
                Fact(stringResource(R.string.feature_library_conversion_images), report.images.toString())
                if (report.multiColumnPages > 0) {
                    Fact(
                        label = stringResource(R.string.feature_library_conversion_columns),
                        value = report.multiColumnPages.toString()
                    )
                }
                Fact(
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
        Fact(stringResource(R.string.feature_library_details_format), book.format.extension.uppercase())
        Fact(
            label = stringResource(R.string.feature_library_details_size),
            value = book.sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: unknown
        )
        Fact(
            label = stringResource(R.string.feature_library_details_added),
            value = AddedDateFormatter.format(book.addedAt)
        )
        Fact(stringResource(R.string.feature_library_details_language), book.language ?: unknown)
        book.progress?.let { progress ->
            Fact(
                label = stringResource(R.string.feature_library_details_progress),
                value = stringResource(
                    R.string.feature_library_details_progress_value,
                    (progress.fraction * PERCENT).roundToInt()
                )
            )
        }
        Fact(stringResource(R.string.feature_library_details_location), book.fileUri, maxLines = 2)
        book.conversion?.let { conversion ->
            Fact(
                label = stringResource(R.string.feature_library_details_converted),
                value = AddedDateFormatter.format(conversion.convertedAt)
            )
            Fact(
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
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(DetailValueWidth)
        )
    }
}

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
