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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// The mtgfmax palette: shadcn/ui's neutral scale with one violet accent (its focus ring). Each
// value is the sRGB rendering of that project's OKLCH token, named after the token's lightness.
private val Neutral1000 = Color(0xFFFFFFFF) // oklch(1 0 0)      background, card (light)
private val Neutral985 = Color(0xFFFAFAFA) // oklch(0.985 0 0)  sidebar (light), foreground (dark)
private val Neutral970 = Color(0xFFF5F5F5) // oklch(0.97 0 0)   secondary, muted, accent (light)
private val Neutral922 = Color(0xFFE5E5E5) // oklch(0.922 0 0)  border, input (light), primary (dark)
private val Neutral708 = Color(0xFFA1A1A1) // oklch(0.708 0 0)  sidebar ring (light), muted text (dark)
private val Neutral556 = Color(0xFF737373) // oklch(0.556 0 0)  muted text (light), sidebar ring (dark)
private val Neutral269 = Color(0xFF262626) // oklch(0.269 0 0)  secondary, muted, accent (dark)
private val Neutral205 = Color(0xFF171717) // oklch(0.205 0 0)  primary (light), card, sidebar (dark)
private val Neutral145 = Color(0xFF0A0A0A) // oklch(0.145 0 0)  foreground (light), background (dark)
private val BorderDark = Color(0xFF232323) // oklch(1 0 0 / 10%) over the dark background
private val InputDark = Color(0xFF2F2F2F) // oklch(1 0 0 / 15%) over the dark background
private val Violet500 = Color(0xFF6F49B1) // oklch(0.5 0.16 297.347)    ring (light)
private val Violet580 = Color(0xFF8465C2) // oklch(0.58 0.14 297.347)   zone border (light)
private val Violet862 = Color(0xFFD6C9F9) // oklch(0.862 0.067 297.347) ring (dark)
private val DestructiveLight = Color(0xFFE7000B) // oklch(0.577 0.245 27.325)
private val DestructiveDark = Color(0xFFFF6467) // oklch(0.704 0.191 22.216)
private val DestructiveTintLight = Color(0xFFFDE5E7) // destructive / 10% over the light background
private val DestructiveTintDark = Color(0xFF3B1C1D) // destructive / 20% over the dark background

/**
 * mtgfmax's light theme mapped onto Material roles: near-black primary actions, grey tonal
 * containers, violet as the one chromatic accent (tertiary), shadcn's destructive red for errors.
 */
private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Neutral205,
    onPrimary = Neutral985,
    primaryContainer = Neutral922,
    onPrimaryContainer = Neutral205,
    inversePrimary = Neutral922,
    secondary = Neutral556,
    onSecondary = Neutral985,
    secondaryContainer = Neutral970,
    onSecondaryContainer = Neutral205,
    tertiary = Violet500,
    onTertiary = Neutral985,
    tertiaryContainer = Violet862,
    onTertiaryContainer = Neutral205,
    background = Neutral1000,
    onBackground = Neutral145,
    surface = Neutral1000,
    onSurface = Neutral145,
    surfaceVariant = Neutral970,
    onSurfaceVariant = Neutral556,
    surfaceTint = Neutral205,
    inverseSurface = Neutral145,
    inverseOnSurface = Neutral985,
    error = DestructiveLight,
    onError = Neutral985,
    errorContainer = DestructiveTintLight,
    onErrorContainer = DestructiveLight,
    outline = Neutral708,
    outlineVariant = Neutral922,
    scrim = Color.Black,
    surfaceBright = Neutral1000,
    surfaceContainer = Neutral970,
    surfaceContainerHigh = Neutral970,
    surfaceContainerHighest = Neutral922,
    surfaceContainerLow = Neutral985,
    surfaceContainerLowest = Neutral1000,
    surfaceDim = Neutral922,
    primaryFixed = Neutral922,
    primaryFixedDim = Neutral708,
    onPrimaryFixed = Neutral145,
    onPrimaryFixedVariant = Neutral269,
    secondaryFixed = Neutral970,
    secondaryFixedDim = Neutral922,
    onSecondaryFixed = Neutral145,
    onSecondaryFixedVariant = Neutral556,
    tertiaryFixed = Violet862,
    tertiaryFixedDim = Violet580,
    onTertiaryFixed = Neutral145,
    onTertiaryFixedVariant = Violet500
)

/** mtgfmax's dark theme: the same roles on its near-black surfaces, lighter layers raised. */
private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Neutral922,
    onPrimary = Neutral205,
    primaryContainer = Neutral269,
    onPrimaryContainer = Neutral985,
    inversePrimary = Neutral205,
    secondary = Neutral708,
    onSecondary = Neutral205,
    secondaryContainer = Neutral269,
    onSecondaryContainer = Neutral985,
    tertiary = Violet862,
    onTertiary = Neutral205,
    tertiaryContainer = Violet500,
    onTertiaryContainer = Neutral985,
    background = Neutral145,
    onBackground = Neutral985,
    surface = Neutral145,
    onSurface = Neutral985,
    surfaceVariant = Neutral269,
    onSurfaceVariant = Neutral708,
    surfaceTint = Neutral922,
    inverseSurface = Neutral985,
    inverseOnSurface = Neutral145,
    error = DestructiveDark,
    onError = Neutral205,
    errorContainer = DestructiveTintDark,
    onErrorContainer = DestructiveDark,
    outline = Neutral556,
    outlineVariant = BorderDark,
    scrim = Color.Black,
    surfaceBright = Neutral269,
    surfaceContainer = Neutral205,
    surfaceContainerHigh = Neutral269,
    surfaceContainerHighest = InputDark,
    surfaceContainerLow = Neutral205,
    surfaceContainerLowest = Neutral145,
    surfaceDim = Neutral145,
    primaryFixed = Neutral922,
    primaryFixedDim = Neutral708,
    onPrimaryFixed = Neutral145,
    onPrimaryFixedVariant = Neutral269,
    secondaryFixed = Neutral970,
    secondaryFixedDim = Neutral922,
    onSecondaryFixed = Neutral145,
    onSecondaryFixedVariant = Neutral556,
    tertiaryFixed = Violet862,
    tertiaryFixedDim = Violet580,
    onTertiaryFixed = Neutral145,
    onTertiaryFixedVariant = Violet500
)

val ReadanaTypography = Typography()

/**
 * App-wide Material 3 Expressive theme: the mtgfmax palette, expressive motion for every
 * transition, and stock Material 3 components on every screen.
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
