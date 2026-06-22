package ai.feltner.nativeapp.ui

import ai.feltner.nativeapp.api.Theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF4F46E5)
private val IndigoLight = Color(0xFF818CF8)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Color(0xFF6366F1),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2FF),
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    secondary = Color(0xFFA5B4FC),
    background = Color(0xFF0B0F1A),
    surface = Color(0xFF131A2A),
    surfaceVariant = Color(0xFF1E2740),
)

@Composable
fun FeltnerTheme(theme: Theme = Theme.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (theme) {
        Theme.LIGHT -> false
        Theme.DARK -> true
        Theme.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
