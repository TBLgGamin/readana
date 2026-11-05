package dev.readana.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.readana.core.designsystem.R

/**
 * The horizontal Readana lockup from the brand kit (`artwork/svg/readana-horizontal*.svg`).
 *
 * The kit allows the violet symbol on light backgrounds only, so the dark lockup (lavender symbol,
 * paper wordmark) is chosen from the theme's own background rather than from the system setting.
 * Size it by height; the kit's minimum on screen is 110 dp wide.
 */
@Composable
fun ReadanaLogo(modifier: Modifier = Modifier) {
    val onDark = MaterialTheme.colorScheme.background.luminance() < HALF
    Image(
        painter = painterResource(
            if (onDark) R.drawable.core_designsystem_logo_dark else R.drawable.core_designsystem_logo
        ),
        contentDescription = stringResource(R.string.core_designsystem_logo_description),
        modifier = modifier
    )
}

private const val HALF = 0.5f
