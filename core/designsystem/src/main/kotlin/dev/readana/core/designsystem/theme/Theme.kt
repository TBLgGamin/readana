package dev.readana.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7A4E2D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC6),
    onPrimaryContainer = Color(0xFF2D1600),
    secondary = Color(0xFF705B4C),
    secondaryContainer = Color(0xFFFBDECB),
    tertiary = Color(0xFF4F6D4A),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF0B58E),
    onPrimary = Color(0xFF482506),
    primaryContainer = Color(0xFF613B1A),
    onPrimaryContainer = Color(0xFFFFDCC6),
    secondary = Color(0xFFDEC2AF),
    secondaryContainer = Color(0xFF574336),
    tertiary = Color(0xFFB5CCAD),
    background = Color(0xFF1A120D),
    surface = Color(0xFF1A120D)
)

val ReadanaTypography = Typography()

/**
 * App-wide Material 3 theme. Uses Material You dynamic colour on Android 12+ (the Pad 8 runs
 * Android 16) and a warm paper-like palette elsewhere.
 */
@Composable
fun ReadanaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
    MaterialTheme(colorScheme = colorScheme, typography = ReadanaTypography, content = content)
}
