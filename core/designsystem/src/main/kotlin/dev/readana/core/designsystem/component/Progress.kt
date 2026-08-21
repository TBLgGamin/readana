package dev.readana.core.designsystem.component

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The bar for work happening right now: Material 3 Expressive's wavy indicator. Downloading,
 * searching, loading a page — anything whose motion is the point. For a stored value, use
 * [SteadyProgress] instead.
 *
 * Wrapped rather than called directly so every bar in the app moves together, and so the one place
 * to adjust when the expressive API settles is here — it is still experimental in the alpha this
 * project pins for it.
 */
@Composable
fun WavyProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(progress = progress, modifier = modifier)
}

/** The same bar for work whose end is not known yet. */
@Composable
fun WavyProgress(modifier: Modifier = Modifier) {
    LinearWavyProgressIndicator(modifier = modifier)
}

/**
 * A progress bar for a value that is simply true, rather than work happening right now: how far
 * through a book you are, say.
 *
 * The wave means "this is moving"; a book sitting on a shelf is not, and a squiggle under every
 * cover reads as activity that is not there. This is Material 3 Expressive's flat determinate bar
 * — rounded caps, a gap before the track, and a stop indicator marking the end.
 */
@Composable
fun SteadyProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = progress, modifier = modifier)
}
