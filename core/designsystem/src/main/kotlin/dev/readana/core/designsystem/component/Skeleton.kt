package dev.readana.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.theme.ReadanaTheme

private const val COVER_ASPECT_RATIO = 2f / 3f
private const val PULSE_MIN_ALPHA = 0.35f
private const val PULSE_MAX_ALPHA = 1f
private const val PULSE_DURATION_MS = 900
private const val STAGGER_STEP_MS = 90
private const val MAX_STAGGER_STEPS = 6
private const val SUBTITLE_WIDTH_FRACTION = 0.6f
private val TitleLineHeight = 14.dp
private val SubtitleLineHeight = 12.dp
private val GridSpacing = 16.dp

/**
 * A placeholder block that breathes while real content loads.
 *
 * Material 3 has no skeleton component, so this follows the expressive guidance instead: one shared
 * surface tint, the theme's own shapes, and a slow alpha pulse rather than a sweeping shimmer,
 * which reads as calmer on a large tablet screen. [index] staggers neighbouring blocks so a grid
 * ripples instead of blinking as one.
 */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small, index: Int = 0) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = PULSE_MIN_ALPHA,
        targetValue = PULSE_MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PULSE_DURATION_MS,
                delayMillis = (index % MAX_STAGGER_STEPS) * STAGGER_STEP_MS
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton-alpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

/** One book-shaped placeholder: cover, title line, author line. */
@Composable
fun BookCardSkeleton(modifier: Modifier = Modifier, index: Int = 0) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT_RATIO),
            shape = MaterialTheme.shapes.medium,
            index = index
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(TitleLineHeight),
            index = index
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(SUBTITLE_WIDTH_FRACTION)
                .height(SubtitleLineHeight),
            index = index
        )
    }
}

/** A grid of book placeholders, sized like the real grid so nothing jumps when results arrive. */
@Composable
fun BookGridSkeleton(modifier: Modifier = Modifier, columns: Int = DEFAULT_SKELETON_COLUMNS, count: Int = 6) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(GridSpacing),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
        userScrollEnabled = false
    ) {
        items(count) { index -> BookCardSkeleton(index = index) }
    }
}

const val DEFAULT_SKELETON_COLUMNS = 2

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun BookGridSkeletonPreview() {
    ReadanaTheme {
        BookGridSkeleton(modifier = Modifier.padding(8.dp))
    }
}
