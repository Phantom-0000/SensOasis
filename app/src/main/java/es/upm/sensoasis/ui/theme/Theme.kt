package es.upm.sensoasis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = OasisSecondary,
    secondary = OasisPrimary,
    tertiary = OasisTertiary
)

private val LightColorScheme = lightColorScheme(
    primary = OasisPrimary,
    secondary = OasisSecondary,
    tertiary = OasisTertiary,
    background = OasisBackground,
    surface = OasisSurface
)

@Composable
fun SensOasisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
