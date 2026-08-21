package dev.readana.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import kotlin.math.roundToInt

private const val PERCENT = 100

/**
 * Bottom half of the reader chrome: where you are in the book, a slider to move, and the way into
 * the contents and settings sheets.
 */
@Composable
internal fun ReaderBottomBar(
    state: ReaderUiState.Ready,
    onSeek: (Float) -> Unit,
    onShowContents: () -> Unit,
    onShowSettings: () -> Unit,
    onShowConversations: () -> Unit,
    /** The assistant's button is only there once there is an assistant to talk to. */
    conversationsAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.chromeVisible,
        modifier = modifier,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = CHROME_ALPHA)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ProgressSlider(progression = state.progression, onSeek = onSeek)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        state.chapterTitle?.let { chapter ->
                            Text(text = chapter, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = positionLabel(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onShowContents) {
                            Icon(
                                ReadanaIcons.Library,
                                contentDescription = stringResource(R.string.feature_reader_contents)
                            )
                        }
                        if (conversationsAvailable) {
                            IconButton(onClick = onShowConversations) {
                                Icon(
                                    ReadanaIcons.Conversations,
                                    contentDescription = stringResource(
                                        R.string.feature_reader_ai_conversations
                                    )
                                )
                            }
                        }
                        IconButton(onClick = onShowSettings) {
                            Icon(
                                ReadanaIcons.Settings,
                                contentDescription = stringResource(R.string.feature_reader_settings)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dragging is tracked locally so the thumb follows the finger; the navigator only jumps once the
 * drag ends, which keeps page turns off the 144 Hz drag path.
 */
@Composable
private fun ProgressSlider(progression: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(progression) }
    val value = if (dragging) dragValue else progression
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = value,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragValue)
            },
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(
                R.string.feature_reader_progress,
                (value * PERCENT).roundToInt()
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun positionLabel(state: ReaderUiState.Ready): String = stringResource(
    R.string.feature_reader_position,
    state.currentPosition.coerceAtLeast(1),
    state.positionCount.coerceAtLeast(1)
)
