package dev.mindlink.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun scheme(p: MindLinkColors.Palette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(p.primary), onPrimary = Color(p.onPrimary),
        primaryContainer = Color(p.primaryContainer), onPrimaryContainer = Color(p.onPrimaryContainer),
        secondary = Color(p.secondary), onSecondary = Color(p.onSecondary),
        secondaryContainer = Color(p.secondaryContainer), onSecondaryContainer = Color(p.onSecondaryContainer),
        tertiary = Color(p.tertiary), onTertiary = Color(p.onTertiary),
        tertiaryContainer = Color(p.tertiaryContainer), onTertiaryContainer = Color(p.onTertiaryContainer),
        error = Color(p.error), onError = Color(p.onError),
        errorContainer = Color(p.errorContainer), onErrorContainer = Color(p.onErrorContainer),
        background = Color(p.surface), onBackground = Color(p.onSurface),
        surface = Color(p.surface), onSurface = Color(p.onSurface),
        surfaceTint = Color(p.primary),
        surfaceVariant = Color(p.surfaceVariant), onSurfaceVariant = Color(p.onSurfaceVariant),
        outline = Color(p.outline), outlineVariant = Color(p.outlineVariant),
        inverseSurface = Color(p.inverseSurface), inverseOnSurface = Color(p.inverseOnSurface),
        inversePrimary = Color(p.inversePrimary), scrim = Color.Black,
        surfaceDim = Color(p.surfaceDim), surfaceBright = Color(p.surfaceBright),
        surfaceContainerLowest = Color(p.surfaceContainerLowest),
        surfaceContainerLow = Color(p.surfaceContainerLow), surfaceContainer = Color(p.surfaceContainer),
        surfaceContainerHigh = Color(p.surfaceContainerHigh),
        surfaceContainerHighest = Color(p.surfaceContainerHighest),
    )
}

private val LightScheme = scheme(MindLinkColors.LIGHT, false)
private val DarkScheme = scheme(MindLinkColors.DARK, true)
private val DefaultTypography = Typography()
private val SharedTypography = with(DefaultTypography) {
    copy(
        displayLarge = displayLarge.copy(letterSpacing = 0.sp),
        displayMedium = displayMedium.copy(letterSpacing = 0.sp),
        displaySmall = displaySmall.copy(letterSpacing = 0.sp),
        headlineLarge = headlineLarge.copy(letterSpacing = 0.sp),
        headlineMedium = headlineMedium.copy(letterSpacing = 0.sp),
        headlineSmall = headlineSmall.copy(letterSpacing = 0.sp),
        titleLarge = titleLarge.copy(letterSpacing = 0.sp),
        titleMedium = titleMedium.copy(letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(letterSpacing = 0.sp),
        labelSmall = labelSmall.copy(letterSpacing = 0.sp),
    )
}
private val SharedShapes = Shapes(extraLarge = RoundedCornerShape(MindLinkColors.CARD_RADIUS_DP.dp))

@Composable
fun MindLinkTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = SharedTypography,
        shapes = SharedShapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
