package dev.readana.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.readana.core.designsystem.R
import dev.readana.core.designsystem.ReadanaIcons
import dev.readana.core.designsystem.theme.ReadanaTheme

/**
 * The app's own passing message: a warm, fully rounded pill that arrives with an expressive
 * spring rather than the stock dark slab.
 *
 * Material 3's default snackbar is deliberately foreign to whatever is behind it — inverted
 * colours, a small radius, a flat fade. This one is drawn from the same palette as every other
 * surface in Readana, so a message reads as the app speaking rather than the system interrupting.
 */
@Composable
fun ReadanaSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null
) {
    // Each message is a fresh composition, so the entrance plays once per message: a short rise
    // and an overshooting scale, which is what makes it feel handed to you rather than switched on.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val entrance by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "snackbarEntrance"
    )
    val rise = with(LocalDensity.current) { RISE.toPx() }

    Surface(
        modifier = modifier
            .padding(HORIZONTAL_INSET)
            .widthIn(max = MAX_WIDTH)
            .graphicsLayer {
                alpha = entrance.coerceIn(0f, 1f)
                scaleX = SHRUNK + (1f - SHRUNK) * entrance
                scaleY = SHRUNK + (1f - SHRUNK) * entrance
                translationY = (1f - entrance) * rise
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = ELEVATION
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (icon == null) TEXT_INSET else BADGE_INSET,
                end = if (actionLabel == null && onDismiss == null) TEXT_INSET else BADGE_INSET,
                top = BADGE_INSET,
                bottom = BADGE_INSET
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(BADGE)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(BADGE_ICON),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Text(
                text = message,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMediumEmphasized,
                maxLines = MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = ReadanaIcons.Close,
                        contentDescription = stringResource(R.string.core_designsystem_snackbar_dismiss),
                        modifier = Modifier.size(BADGE_ICON)
                    )
                }
            }
        }
    }
}

/**
 * Drop-in host for [SnackbarHostState] that shows [ReadanaSnackbar] instead of the stock one.
 *
 * The badge is off unless [icon] is given: messages reach the shell named only by string
 * resource, so there is no tone to draw an icon from, and a fixed one would end up lying.
 */
@Composable
fun ReadanaSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    SnackbarHost(hostState = hostState, modifier = modifier.fillMaxWidth()) { data: SnackbarData ->
        ReadanaSnackbar(
            message = data.visuals.message,
            icon = icon,
            actionLabel = data.visuals.actionLabel,
            onAction = data::performAction,
            onDismiss = if (data.visuals.withDismissAction) data::dismiss else null
        )
    }
}

private val HORIZONTAL_INSET = 16.dp
private val MAX_WIDTH = 520.dp
private val ELEVATION = 6.dp
private val RISE = 28.dp
private val GAP = 8.dp
private val BADGE = 36.dp
private val BADGE_ICON = 20.dp
private val BADGE_INSET = 8.dp
private val TEXT_INSET = 20.dp
private const val SHRUNK = 0.88f
private const val MAX_LINES = 3

@Preview(showBackground = true)
@Composable
private fun ReadanaSnackbarPreview() {
    ReadanaTheme {
        ReadanaSnackbar(message = "Added to the download queue", icon = ReadanaIcons.Download)
    }
}

@Preview(showBackground = true)
@Composable
private fun ReadanaSnackbarActionPreview() {
    ReadanaTheme {
        ReadanaSnackbar(
            message = "Couldn't reach the mirror",
            icon = ReadanaIcons.Failed,
            actionLabel = "Retry",
            onDismiss = {}
        )
    }
}
