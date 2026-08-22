package dev.readana.feature.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.BookCoverThumb
import dev.readana.core.designsystem.component.WavyProgress
import dev.readana.core.model.DownloadJob
import dev.readana.core.model.DownloadStatus

private val CoverWidth = 44.dp
private val CoverGap = 14.dp
private val CardPadding = 16.dp
private val StageDotSize = 18.dp
private const val PERCENT = 100
private const val EXPANDED_TURN = 180f

/**
 * One download, with as much of its story as is known: what it is, where it has got to, how fast it
 * is moving and what can be done about it.
 */
@Composable
internal fun DownloadCard(item: QueueItem.Download, actions: DownloadActions, modifier: Modifier = Modifier) {
    val job = item.job
    QueueCard(
        title = job.title,
        coverUri = job.coverUrl,
        meta = job.metaLine(),
        containerColor = job.containerColor(),
        modifier = modifier
    ) {
        if (job.isPending) {
            StagePanel(
                stages = QueueStages.of(job, item.conversion),
                detail = item.detailLine()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = job.statusLine(),
                style = MaterialTheme.typography.bodyMedium,
                color = job.statusColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            RowActions(job = job, actions = actions)
        }
    }
}

/**
 * A PDF the reader asked the library to convert.
 *
 * A conversion that came out of a download is shown inside that download's card instead; this one
 * has no download behind it, so it is a row of its own with the engine's own phases as its steps.
 */
@Composable
internal fun ConversionCard(item: QueueItem.Conversion, modifier: Modifier = Modifier) {
    val conversion = item.conversion
    QueueCard(
        title = conversion.title,
        coverUri = conversion.coverUri,
        meta = stringResource(R.string.feature_downloads_conversion_meta),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        StagePanel(
            stages = QueueStages.of(conversion),
            detail = conversion.pagesLine()
        )
    }
}

/** The shape every row in the queue shares: the cover, the title, then whatever the row adds. */
@Composable
private fun QueueCard(
    title: String,
    coverUri: String?,
    meta: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Animated so a card changing state settles into its new tint instead of snapping to it.
    val background by animateColorAsState(containerColor, label = "queueCardContainer")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Column(modifier = Modifier.padding(CardPadding)) {
            Row {
                // The book itself, always in the same corner, rather than an icon for whichever
                // state the work happens to be in.
                BookCoverThumb(
                    title = title,
                    coverUri = coverUri,
                    modifier = Modifier.width(CoverWidth)
                )
                Column(modifier = Modifier.weight(1f).padding(start = CoverGap)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            content()
        }
    }
}

/**
 * The steps, folded away by default.
 *
 * Closed it is the one line that matters — what is happening now, and how far along — so a queue of
 * five books stays readable. Opened it lists every step, indented to sit under the title so it is
 * plainly about this book and not the next one.
 */
@Composable
private fun StagePanel(stages: List<QueueStage>, detail: String?, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val current = stages.firstOrNull { it.state == StageState.Running } ?: stages.lastOrNull() ?: return
    val turn by animateFloatAsState(if (expanded) EXPANDED_TURN else 0f, label = "stageChevron")

    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp, start = CoverWidth + CoverGap)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(current.labelRes),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            current.fraction?.let { Percentage(it) }
            Icon(
                imageVector = ReadanaIcons.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) {
                        R.string.feature_downloads_stages_collapse
                    } else {
                        R.string.feature_downloads_stages_expand
                    }
                ),
                modifier = Modifier.rotate(turn)
            )
        }
        // Closed, the current step keeps its bar: the card would otherwise say a book is
        // downloading and show nothing moving.
        if (!expanded) {
            StageBar(current, Modifier.padding(top = 6.dp))
            if (detail != null) StageDetail(detail, Modifier.padding(top = 6.dp))
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                stages.forEach { stage ->
                    StageRow(
                        stage = stage,
                        detail = detail.takeIf { stage.state == StageState.Running }
                    )
                }
            }
        }
    }
}

/** One step in the opened list: where it stands, what it is called, and its bar while it runs. */
@Composable
private fun StageRow(stage: QueueStage, detail: String?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        StageDot(stage.state)
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(stage.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = stage.labelColor(),
                    modifier = Modifier.weight(1f)
                )
                if (stage.state == StageState.Running) stage.fraction?.let { Percentage(it) }
            }
            if (stage.state == StageState.Running) {
                StageBar(stage, Modifier.padding(top = 6.dp))
                if (detail != null) StageDetail(detail, Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Wavy throughout: every bar on this screen belongs to work happening right now. */
@Composable
private fun StageBar(stage: QueueStage, modifier: Modifier = Modifier) {
    val fraction = stage.fraction
    if (fraction != null) {
        WavyProgress(progress = { fraction }, modifier = modifier.fillMaxWidth())
    } else {
        WavyProgress(modifier.fillMaxWidth())
    }
}

@Composable
private fun StageDetail(detail: String, modifier: Modifier = Modifier) {
    Text(
        text = detail,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun Percentage(fraction: Float, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.feature_downloads_percent, (fraction * PERCENT).toInt()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 8.dp)
    )
}

/** A tick for what is finished, a filled disc for what is running, an outline for what is not. */
@Composable
private fun StageDot(state: StageState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(StageDotSize),
        shape = CircleShape,
        color = when (state) {
            StageState.Done -> MaterialTheme.colorScheme.tertiary
            StageState.Running -> MaterialTheme.colorScheme.primary
            StageState.Waiting -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when (state) {
            StageState.Done -> MaterialTheme.colorScheme.onTertiary
            StageState.Running -> MaterialTheme.colorScheme.onPrimary
            StageState.Waiting -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (state == StageState.Done) {
                Icon(ReadanaIcons.Done, contentDescription = null, modifier = Modifier.padding(2.dp))
            }
        }
    }
}

@Composable
private fun QueueStage.labelColor(): Color = when (state) {
    StageState.Waiting -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

/** What the running step has to add: bytes and speed while fetching, pages while converting. */
@Composable
private fun QueueItem.Download.detailLine(): String? = when (job.status) {
    DownloadStatus.Downloading -> {
        val size = job.totalBytes?.let {
            stringResource(
                R.string.feature_downloads_status_progress,
                humanSize(job.bytesDownloaded),
                humanSize(it)
            )
        }
        val speed = rate?.let { stringResource(R.string.feature_downloads_rate, humanSize(it.bytesPerSecond)) }
        val eta = rate?.etaMillis?.let { stringResource(R.string.feature_downloads_eta, humanDuration(it)) }
        listOfNotNull(size, speed, eta).joinToString(" · ").ifEmpty { null }
    }

    DownloadStatus.Converting -> conversion?.pagesLine()

    else -> null
}

@Composable
private fun ActiveConversion.pagesLine(): String? = if (totalPages > 0) {
    stringResource(R.string.feature_downloads_conversion_pages, currentPage, totalPages)
} else {
    null
}

/** The actions this row can offer, most useful one filled. */
@Composable
private fun RowActions(job: DownloadJob, actions: DownloadActions, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        when (job.status) {
            DownloadStatus.Queued, DownloadStatus.Resolving, DownloadStatus.Downloading -> {
                FilledTonalIconButton(onClick = { actions.onPause(job.id) }) {
                    Icon(ReadanaIcons.Pause, stringResource(R.string.feature_downloads_action_pause))
                }
                CancelButton(job.id, actions.onCancel)
            }

            // Nothing to pause once the bytes are down and the converter has them.
            DownloadStatus.Converting -> CancelButton(job.id, actions.onCancel)

            DownloadStatus.Paused -> {
                FilledTonalIconButton(onClick = { actions.onResume(job.id) }) {
                    Icon(ReadanaIcons.Play, stringResource(R.string.feature_downloads_action_resume))
                }
                CancelButton(job.id, actions.onCancel)
            }

            DownloadStatus.Completed -> {
                job.bookId?.let { bookId ->
                    FilledTonalIconButton(onClick = { actions.onOpenBook(bookId) }) {
                        Icon(
                            ReadanaIcons.Library,
                            stringResource(R.string.feature_downloads_action_open)
                        )
                    }
                }
                DismissButton(job.id, actions.onDismiss)
            }

            DownloadStatus.Failed, DownloadStatus.Cancelled -> {
                FilledTonalIconButton(onClick = { actions.onRetry(job.id) }) {
                    Icon(
                        imageVector = if (job.needsBrowser) {
                            ReadanaIcons.OpenInBrowser
                        } else {
                            ReadanaIcons.Refresh
                        },
                        contentDescription = stringResource(
                            if (job.needsBrowser) {
                                R.string.feature_downloads_action_open_browser
                            } else {
                                R.string.feature_downloads_action_retry
                            }
                        )
                    )
                }
                DismissButton(job.id, actions.onDismiss)
            }
        }
    }
}

@Composable
private fun CancelButton(jobId: String, onCancel: (String) -> Unit) {
    IconButton(onClick = { onCancel(jobId) }) {
        Icon(ReadanaIcons.Close, stringResource(R.string.feature_downloads_action_cancel))
    }
}

@Composable
private fun DismissButton(jobId: String, onDismiss: (String) -> Unit) {
    IconButton(
        onClick = { onDismiss(jobId) },
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(ReadanaIcons.Delete, stringResource(R.string.feature_downloads_action_dismiss))
    }
}

/**
 * The card's own tint.
 *
 * A row that needs a decision is lifted out of the list with the error container; everything else
 * stays quiet, so "needs you" is findable without reading a word.
 */
@Composable
private fun DownloadJob.containerColor(): Color = when (status) {
    DownloadStatus.Failed -> MaterialTheme.colorScheme.errorContainer

    DownloadStatus.Resolving, DownloadStatus.Downloading, DownloadStatus.Converting ->
        MaterialTheme.colorScheme.surfaceContainerHigh

    else -> MaterialTheme.colorScheme.surfaceContainerLow
}

@Composable
private fun DownloadJob.statusColor(): Color = when (status) {
    DownloadStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
