package dev.readana.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.ReadanaIcons
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Navigator
import org.readium.r2.shared.publication.Locator

/** Its own decoration group, so marking the spoken sentence never disturbs the highlights. */
private const val SPOKEN_GROUP = "tts"

/**
 * Read-aloud's controls, present only while a voice is going.
 *
 * A floating toolbar rather than more icons in the bottom bar: listening is a mode with its own
 * handful of actions, and when it ends they should leave with it rather than sit there greyed out.
 */
@Composable
internal fun ReaderTtsBar(
    state: TtsUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.active,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        HorizontalFloatingToolbar(expanded = true, modifier = Modifier.padding(16.dp)) {
            IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
                Icon(
                    ReadanaIcons.Previous,
                    contentDescription = stringResource(R.string.feature_reader_tts_previous)
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (state.playing) ReadanaIcons.Pause else ReadanaIcons.Play,
                    contentDescription = stringResource(
                        if (state.playing) R.string.feature_reader_tts_pause else R.string.feature_reader_tts_play
                    )
                )
            }
            IconButton(onClick = onNext, enabled = state.hasNext) {
                Icon(
                    ReadanaIcons.Next,
                    contentDescription = stringResource(R.string.feature_reader_tts_next)
                )
            }
            TextButton(onClick = onCycleSpeed) {
                Text(
                    text = stringResource(R.string.feature_reader_tts_speed, state.speed),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            IconButton(onClick = onStop) {
                Icon(
                    ReadanaIcons.Close,
                    contentDescription = stringResource(R.string.feature_reader_tts_stop)
                )
            }
        }
    }
}

/**
 * Keeps the page with the voice: turns to the sentence being spoken and marks it, so a reader who
 * looks up can find their place again straight away.
 */
@Composable
internal fun SpokenUtteranceEffect(navigator: Navigator?, utterance: Locator?) {
    val decorable = navigator as? DecorableNavigator
    val currentNavigator by rememberUpdatedState(navigator)
    // The spoken sentence is marked in the theme's own accent, not in a highlighter colour: it is
    // where the voice is, not something the reader chose to keep.
    val tint = MaterialTheme.colorScheme.primaryContainer.toArgb()

    LaunchedEffect(decorable, utterance, tint) {
        decorable ?: return@LaunchedEffect
        val locator = utterance
        if (locator == null) {
            decorable.applyDecorations(emptyList(), SPOKEN_GROUP)
            return@LaunchedEffect
        }
        currentNavigator?.go(locator, animated = false)
        decorable.applyDecorations(
            listOf(
                Decoration(
                    id = SPOKEN_GROUP,
                    locator = locator,
                    style = Decoration.Style.Highlight(tint = tint, isActive = true)
                )
            ),
            SPOKEN_GROUP
        )
    }
}
