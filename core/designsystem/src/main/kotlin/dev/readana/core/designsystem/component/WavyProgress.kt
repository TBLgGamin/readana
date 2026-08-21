package dev.readana.core.designsystem.component

import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's progress bar: Material 3 Expressive's wavy indicator.
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
