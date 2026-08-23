package dev.readana.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The Material 3 Expressive baseline, unaltered.
 *
 * [expressiveLightColorScheme] is what `MaterialExpressiveTheme` itself defaults to: the baseline
 * violet palette with the on-container roles pulled down to tone 30, which is what gives expressive
 * containers their contrast. Material ships no expressive dark counterpart, so dark is the baseline
 * [darkColorScheme] it is meant to pair with.
 */
private val LightColorScheme: ColorScheme = expressiveLightColorScheme()

private val DarkColorScheme: ColorScheme = darkColorScheme()

val ReadanaTypography = Typography()

/**
 * App-wide Material 3 Expressive theme: the baseline expressive palette, expressive motion for
 * every transition, and stock Material 3 components on every screen.
 *
 * [dynamicColor] is off by default so the app looks like itself rather than like the wallpaper.
 * Turn it on to take Material You colours from the system on Android 12+.
 */
@Composable
fun ReadanaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme

        else -> LightColorScheme
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = ReadanaTypography,
        content = content
    )
}
