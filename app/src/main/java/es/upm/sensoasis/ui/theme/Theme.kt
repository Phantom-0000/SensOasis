package es.upm.sensoasis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import es.upm.sensoasis.ui.i18n.LocalAppPreferences
import es.upm.sensoasis.ui.i18n.LocalAppStrings
import es.upm.sensoasis.ui.i18n.rememberAppPreferencesState
import es.upm.sensoasis.ui.i18n.stringsFor

private val DarkColorScheme = darkColorScheme(
    primary = OasisDarkPrimary,
    onPrimary = OasisDarkOnPrimary,
    primaryContainer = OasisDarkPrimaryContainer,
    onPrimaryContainer = OasisDarkOnPrimaryContainer,
    secondary = OasisDarkSecondary,
    onSecondary = OasisDarkOnSecondary,
    secondaryContainer = OasisDarkSecondaryContainer,
    onSecondaryContainer = OasisDarkOnSecondaryContainer,
    tertiary = OasisDarkTertiary,
    background = OasisDarkBackground,
    onBackground = OasisDarkOnBackground,
    surface = OasisDarkSurface,
    onSurface = OasisDarkOnSurface,
    surfaceVariant = OasisDarkSurfaceVariant,
    onSurfaceVariant = OasisDarkOnSurfaceVariant,
    outline = OasisDarkOutline,
    outlineVariant = OasisDarkOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = OasisPrimary,
    onPrimary = OasisOnPrimary,
    primaryContainer = OasisPrimaryContainer,
    onPrimaryContainer = OasisOnPrimaryContainer,
    secondary = OasisSecondary,
    onSecondary = OasisOnSecondary,
    secondaryContainer = OasisSecondaryContainer,
    onSecondaryContainer = OasisOnSecondaryContainer,
    tertiary = OasisTertiary,
    onTertiary = OasisOnTertiary,
    tertiaryContainer = OasisTertiaryContainer,
    onTertiaryContainer = OasisOnTertiaryContainer,
    background = OasisBackground,
    onBackground = OasisOnBackground,
    surface = OasisSurface,
    onSurface = OasisOnSurface,
    surfaceVariant = OasisSurfaceVariant,
    onSurfaceVariant = OasisOnSurfaceVariant,
    outline = OasisOutline,
    outlineVariant = OasisOutlineVariant
)

@Composable
fun SensOasisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appPrefs = rememberAppPreferencesState()
    val appStrings = stringsFor(appPrefs.language)
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * appPrefs.fontScale.multiplier
    )

    CompositionLocalProvider(
        LocalAppPreferences provides appPrefs,
        LocalAppStrings provides appStrings,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
