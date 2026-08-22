package dev.readana.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.readana.core.model.DictionarySense

private val SheetPadding = 24.dp

/**
 * What a word means, and a way out when a dictionary is the wrong tool.
 *
 * The reader can select anything — a word, a phrase, a whole sentence — and only one of those has a
 * dictionary entry. Rather than guess before asking, the sheet offers the assistant underneath, and
 * that offer becomes the whole answer when the lookup finds nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderDefinitionSheet(
    state: DefinitionState,
    assistantEnabled: Boolean,
    onAskAssistant: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = state.word, style = MaterialTheme.typography.headlineSmall)

            when {
                state.isLoading -> LinearWavyProgressIndicator(Modifier.fillMaxWidth())

                state.entry == null || state.entry.isEmpty -> Text(
                    text = stringResource(R.string.feature_reader_define_nothing, state.word),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> state.entry.senses.forEachIndexed { index, sense ->
                    Sense(index = index + 1, sense = sense)
                }
            }

            if (assistantEnabled) {
                TextButton(onClick = onAskAssistant, modifier = Modifier.align(Alignment.Start)) {
                    Text(stringResource(R.string.feature_reader_define_ask_assistant))
                }
            }
        }
    }
}

@Composable
private fun Sense(index: Int, sense: DictionarySense, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (sense.partOfSpeech.isNotBlank()) {
            Text(
                text = sense.partOfSpeech,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = stringResource(R.string.feature_reader_define_sense, index, sense.definition),
            style = MaterialTheme.typography.bodyLarge
        )
        sense.example?.let { example ->
            Text(
                text = example,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
