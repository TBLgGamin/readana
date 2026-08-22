package dev.readana.core.designsystem.component

import androidx.compose.ui.graphics.Color
import dev.readana.core.model.HighlightTint

/**
 * The paint behind each tint. [HighlightTint] names a colour without choosing one, because the
 * exact value is a UI concern — and it has to be the same value wherever a highlight is shown, so
 * that a note in the library looks like the passage it came from.
 */
private val TintColors = mapOf(
    HighlightTint.Yellow to Color(0xFFFFF176),
    HighlightTint.Green to Color(0xFFA5D6A7),
    HighlightTint.Blue to Color(0xFF90CAF9),
    HighlightTint.Pink to Color(0xFFF48FB1),
    HighlightTint.Purple to Color(0xFFCE93D8)
)

/** The colour this tint is drawn in, on the page and everywhere a highlight is listed. */
fun HighlightTint.color(): Color = TintColors.getValue(this)
