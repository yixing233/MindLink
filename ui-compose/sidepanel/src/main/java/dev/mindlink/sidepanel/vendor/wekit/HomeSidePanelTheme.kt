// SPDX-License-Identifier: GPL-3.0-only
// Copied and modified from WeKit, Ujhhgtg and upstream contributors.
// Source: app/src/main/java/dev/ujhhgtg/wekit/ui/utils/theme/InjectedUiTheme.kt
// MindLink adaptations (2026-11-14): retain original WeKit injected default green color schemes and MaterialExpressiveTheme/expressive motion; no module theme preference/HostInfo dependencies
// Original component geometry/typography/icons are retained; host hooks are not copied.
// See thirdparty/sourceprovenance/wekit-sidepanel.json and matching .patch files.
package dev.mindlink.sidepanel.vendor.wekit

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val primaryLight = Color(0xFF336940)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFB5F1BD)
val onPrimaryContainerLight = Color(0xFF19512A)
val secondaryLight = Color(0xFF506352)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD3E8D2)
val onSecondaryContainerLight = Color(0xFF394B3B)
val tertiaryLight = Color(0xFF39656E)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFBDEAF4)
val onTertiaryContainerLight = Color(0xFF204D55)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = Color(0xFFF6FBF2)
val onBackgroundLight = Color(0xFF181D18)
val surfaceLight = Color(0xFFF6FBF2)
val onSurfaceLight = Color(0xFF181D18)
val surfaceVariantLight = Color(0xFFDDE5DA)
val onSurfaceVariantLight = Color(0xFF414941)
val outlineLight = Color(0xFF717970)
val outlineVariantLight = Color(0xFFC1C9BE)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2D322C)
val inverseOnSurfaceLight = Color(0xFFEEF2EA)
val inversePrimaryLight = Color(0xFF99D4A2)
val surfaceDimLight = Color(0xFFD7DBD3)
val surfaceBrightLight = Color(0xFFF6FBF2)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF1F5ED)
val surfaceContainerLight = Color(0xFFEBEFE7)
val surfaceContainerHighLight = Color(0xFFE5EAE1)
val surfaceContainerHighestLight = Color(0xFFDFE4DC)

val primaryDark = Color(0xFF99D4A2)
val onPrimaryDark = Color(0xFF003917)
val primaryContainerDark = Color(0xFF19512A)
val onPrimaryContainerDark = Color(0xFFB5F1BD)
val secondaryDark = Color(0xFFB7CCB7)
val onSecondaryDark = Color(0xFF233426)
val secondaryContainerDark = Color(0xFF394B3B)
val onSecondaryContainerDark = Color(0xFFD3E8D2)
val tertiaryDark = Color(0xFFA2CED8)
val onTertiaryDark = Color(0xFF00363E)
val tertiaryContainerDark = Color(0xFF204D55)
val onTertiaryContainerDark = Color(0xFFBDEAF4)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF101510)
val onBackgroundDark = Color(0xFFDFE4DC)
val surfaceDark = Color(0xFF101510)
val onSurfaceDark = Color(0xFFDFE4DC)
val surfaceVariantDark = Color(0xFF414941)
val onSurfaceVariantDark = Color(0xFFC1C9BE)
val outlineDark = Color(0xFF8B9389)
val outlineVariantDark = Color(0xFF414941)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFDFE4DC)
val inverseOnSurfaceDark = Color(0xFF2D322C)
val inversePrimaryDark = Color(0xFF336940)
val surfaceDimDark = Color(0xFF101510)
val surfaceBrightDark = Color(0xFF353A35)
val surfaceContainerLowestDark = Color(0xFF0B0F0B)
val surfaceContainerLowDark = Color(0xFF181D18)
val surfaceContainerDark = Color(0xFF1C211C)
val surfaceContainerHighDark = Color(0xFF262B26)
val surfaceContainerHighestDark = Color(0xFF313631)

val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)


@Composable
fun HomeSidePanelTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
