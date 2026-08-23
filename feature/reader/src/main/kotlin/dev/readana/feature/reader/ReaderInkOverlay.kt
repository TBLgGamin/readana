package dev.readana.feature.reader

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.component.color
import dev.readana.core.model.InkStroke

/**
 * The ink on the page.
 *
 * Drawn above the navigator but never touched: the overlay does not take pointer input, because
 * strokes are captured below, in [ReaderSurfaceView], where a pen can still be told from a finger.
 *
 * Strokes whose layout no longer matches the one they were drawn under are drawn faintly rather
 * than confidently. On a reflowable EPUB, changing the font size moves the text out from under the
 * ink, and pretending otherwise would put a line through the wrong sentence — see [InkStroke].
 */
@Composable
internal fun ReaderInkOverlay(
    strokes: List<ReaderInk>,
    inProgress: List<PointF>,
    inProgressTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        strokes.forEach { ink ->
            val path = Path()
            ink.stroke.points.forEachIndexed { index, point ->
                val offset = Offset(point.x * size.width, point.y * size.height)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(
                path = path,
                color = ink.stroke.tint.color().copy(alpha = if (ink.isMisplaced) FADED else SOLID),
                style = Stroke(
                    width = StrokeWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        if (inProgress.size > 1) {
            val path = Path()
            inProgress.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(
                path = path,
                color = inProgressTint.copy(alpha = SOLID),
                style = Stroke(
                    width = StrokeWidth.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/** A stored stroke, and whether the page has moved under it since it was drawn. */
internal data class ReaderInk(val stroke: InkStroke, val isMisplaced: Boolean)

private const val SOLID = 0.75f
private const val FADED = 0.3f
private val StrokeWidth = 3.dp
