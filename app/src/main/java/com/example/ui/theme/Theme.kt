package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SuiviSemanticColors(
    val boy: Color,
    val girl: Color,
    val masteryMax: Color,
    val masteryMin: Color,
    val masteryImprove: Color,
    val masteryReinforce: Color,
)

private val LightSemanticColors = SuiviSemanticColors(
    boy = BoyBlueLight,
    girl = GirlRoseLight,
    masteryMax = MasteryMaxLight,
    masteryMin = MasteryMinLight,
    masteryImprove = MasteryImproveLight,
    masteryReinforce = MasteryReinforceLight
)

private val DarkSemanticColors = SuiviSemanticColors(
    boy = BoyBlueDark,
    girl = GirlRoseDark,
    masteryMax = MasteryMaxDark,
    masteryMin = MasteryMinDark,
    masteryImprove = MasteryImproveDark,
    masteryReinforce = MasteryReinforceDark
)

val LocalSuiviColors = staticCompositionLocalOf { LightSemanticColors }

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldPrimaryContainerDark,
    onPrimaryContainer = EmeraldOnPrimaryContainerDark,
    secondary = OchreSecondaryDark,
    onSecondary = OchreOnSecondaryDark,
    secondaryContainer = OchreSecondaryContainerDark,
    onSecondaryContainer = OchreOnSecondaryContainerDark,
    tertiary = CobaltTertiaryDark,
    onTertiary = CobaltOnTertiaryDark,
    tertiaryContainer = CobaltTertiaryContainerDark,
    onTertiaryContainer = CobaltOnTertiaryContainerDark,
    background = SlateDarkBackground,
    onBackground = InkLightOnDark,
    surface = SlateDarkSurface,
    onSurface = InkLightOnDark,
    surfaceVariant = SlateDarkSurfaceVariant,
    onSurfaceVariant = InkMutedOnDark,
    outline = DarkOutline,
    outlineVariant = SlateDarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = OchreSecondary,
    onSecondary = OchreOnSecondary,
    secondaryContainer = OchreSecondaryContainer,
    onSecondaryContainer = OchreOnSecondaryContainer,
    tertiary = CobaltTertiary,
    onTertiary = CobaltOnTertiary,
    tertiaryContainer = CobaltTertiaryContainer,
    onTertiaryContainer = CobaltOnTertiaryContainer,
    background = IvoryBackground,
    onBackground = InkOnBackground,
    surface = IvorySurface,
    onSurface = InkOnBackground,
    surfaceVariant = IvorySurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    outline = HairlineOutline,
    outlineVariant = HairlineOutlineVariant
)

@Composable
fun SuiviCm2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalSuiviColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
