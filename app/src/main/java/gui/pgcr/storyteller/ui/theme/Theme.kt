package gui.pgcr.storyteller.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import gui.pgcr.storyteller.appThemeState

/**
 * O esquema do Material segue o tema escolhido no app (e não o do sistema nem as cores dinâmicas),
 * para campos de texto, chips, menus etc. ficarem legíveis também nos temas claros.
 */
@Composable
fun MyComposeApplicationTheme(content: @Composable () -> Unit) {
    val t = appThemeState.value
    val colorScheme = if (t.isDark) {
        darkColorScheme(
            primary = t.accent, onPrimary = t.onAccent,
            secondary = t.accent, onSecondary = t.onAccent,
            tertiary = t.secondaryText,
            secondaryContainer = lerp(t.card, t.accent, 0.3f), onSecondaryContainer = t.primaryText,
            background = t.background, onBackground = t.primaryText,
            surface = t.card, onSurface = t.primaryText,
            surfaceVariant = t.card, onSurfaceVariant = t.secondaryText,
            outline = t.secondaryText.copy(alpha = 0.7f), outlineVariant = t.secondaryText.copy(alpha = 0.3f),
            surfaceTint = t.accent
        )
    } else {
        lightColorScheme(
            primary = t.accent, onPrimary = t.onAccent,
            secondary = t.accent, onSecondary = t.onAccent,
            tertiary = t.secondaryText,
            secondaryContainer = lerp(t.card, t.accent, 0.3f), onSecondaryContainer = t.primaryText,
            background = t.background, onBackground = t.primaryText,
            surface = t.card, onSurface = t.primaryText,
            surfaceVariant = t.card, onSurfaceVariant = t.secondaryText,
            outline = t.secondaryText.copy(alpha = 0.7f), outlineVariant = t.secondaryText.copy(alpha = 0.3f),
            surfaceTint = t.accent
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = t.background.toArgb()
            window.navigationBarColor = t.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !t.isDark
                isAppearanceLightNavigationBars = !t.isDark
            }
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
