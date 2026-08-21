package dev.readana.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.readana.core.common.result.AppError
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.component.EmptyState
import dev.readana.core.designsystem.component.WavyProgress
import dev.readana.core.model.AiThreadKind
import kotlin.math.roundToInt

/**
 * The assistant's conversation. Everything it says comes from the part of the book above the
 * reader's position, which the header states plainly so it is never a mystery what was sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderAssistantSheet(
    state: AssistantUiState,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AssistantHeader(state)
            state.quote?.takeIf { it.isNotBlank() }?.let { QuoteCard(it) }
            Conversation(state, modifier = Modifier.weight(weight = 1f, fill = false))
            state.error?.let { AssistantError(error = it, onRetry = onRetry) }
            AskField(
                enabled = state.phase != AssistantPhase.Streaming,
                onSend = onSend
            )
        }
    }
}

@Composable
private fun AssistantHeader(state: AssistantUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = ReadanaIcons.Assistant,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = stringResource(state.kind.titleRes()),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = state.coveredFraction?.let { fraction ->
                    stringResource(
                        R.string.feature_reader_ai_coverage,
                        (fraction * PERCENT).roundToInt()
                    )
                } ?: stringResource(R.string.feature_reader_ai_coverage_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuoteCard(quote: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(quote) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        onClick = { expanded = !expanded }
    ) {
        Text(
            text = quote,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else QUOTE_LINES,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Conversation(state: AssistantUiState, modifier: Modifier = Modifier) {
    val listState = rememberScrollState()
    LaunchedEffect(state.messages.size, state.streaming) { listState.animateScrollTo(listState.maxValue) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .verticalScroll(listState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.messages.forEach { message -> Turn(message) }
        val streaming = state.streaming
        when {
            state.phase == AssistantPhase.Gathering -> GatheringRow()

            // An empty stream means the request is out but no word has come back yet.
            streaming != null && streaming.isEmpty() -> ThinkingRow()

            streaming != null -> AnswerText(text = streaming)
        }
    }
}

@Composable
private fun Turn(message: AssistantMessage, modifier: Modifier = Modifier) {
    if (message.fromReader) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    } else {
        AnswerText(text = message.text, modifier = modifier)
    }
}

/** The model is asked for plain prose, so plain text is all this needs to be. */
@Composable
private fun AnswerText(text: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier = modifier) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GatheringRow(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.feature_reader_ai_gathering),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WavyProgress()
    }
}

@Composable
private fun ThinkingRow(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        LoadingIndicator()
    }
}

@Composable
private fun AssistantError(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when (error) {
                is AppError.Unauthorized -> stringResource(R.string.feature_reader_ai_error_key)
                else -> error.message
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.feature_reader_ai_retry))
        }
    }
}

@Composable
private fun AskField(enabled: Boolean, onSend: (String) -> Unit, modifier: Modifier = Modifier) {
    var draft by rememberSaveable { mutableStateOf("") }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.feature_reader_ai_question)) },
            enabled = enabled,
            maxLines = 3
        )
        FilledIconButton(
            onClick = {
                onSend(draft)
                draft = ""
            },
            enabled = enabled && draft.isNotBlank()
        ) {
            Icon(
                imageVector = ReadanaIcons.Send,
                contentDescription = stringResource(R.string.feature_reader_ai_send)
            )
        }
    }
}

/** The offer to catch up, shown over the page when a book is reopened after a while. */
@Composable
internal fun RecapOffer(daysAway: Int, onAccept: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
        onClick = onAccept
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(ReadanaIcons.Assistant, contentDescription = null)
            Text(
                text = pluralStringResource(R.plurals.feature_reader_ai_recap_offer, daysAway, daysAway),
                style = MaterialTheme.typography.bodyMedium
            )
            FilledIconButton(onClick = onDismiss) {
                Icon(
                    ReadanaIcons.Close,
                    contentDescription = stringResource(R.string.feature_reader_ai_recap_dismiss)
                )
            }
        }
    }
}

/**
 * Every conversation held about this book, newest first. Its own sheet rather than a section of
 * the contents, so it is one tap from the reading chrome and scrolls on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderConversationsSheet(
    threads: List<ThreadItem>,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        if (threads.isEmpty()) {
            EmptyState(
                icon = ReadanaIcons.Conversations,
                title = stringResource(R.string.feature_reader_ai_conversations_empty),
                description = stringResource(R.string.feature_reader_ai_conversations_empty_help)
            )
            return@ModalBottomSheet
        }
        LazyColumn {
            item { ConversationsHeader() }
            items(threads, key = { it.id }) { thread ->
                ListItem(
                    headlineContent = {
                        Text(thread.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier.clickable { onOpen(thread.id) },
                    supportingContent = { Text(stringResource(thread.kind.titleRes())) },
                    leadingContent = { Icon(thread.kind.icon(), contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { onDelete(thread.id) }) {
                            Icon(
                                ReadanaIcons.Delete,
                                contentDescription = stringResource(R.string.feature_reader_ai_delete)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ConversationsHeader(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.feature_reader_ai_conversations),
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun AiThreadKind.icon() = when (this) {
    AiThreadKind.Recap -> ReadanaIcons.Refresh
    else -> ReadanaIcons.Assistant
}

private fun AiThreadKind.titleRes(): Int = when (this) {
    AiThreadKind.Ask -> R.string.feature_reader_ai_ask
    AiThreadKind.Explain -> R.string.feature_reader_ai_explain
    AiThreadKind.Recap -> R.string.feature_reader_ai_recap
}

private const val QUOTE_LINES = 6
private const val PERCENT = 100
