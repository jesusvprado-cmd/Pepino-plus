package com.shieldstream.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** Reduces image and animation work on low-power TV boxes. Provided by MainActivity. */
val LocalLightweightUi = staticCompositionLocalOf { false }

/** Blur the backdrop behind Home rows. Provided by MainActivity from the app-wide setting. */
val LocalSoftBackdrops = staticCompositionLocalOf { false }

/** Load sharp full-screen backdrops at TMDB's original size. Provided by MainActivity. */
val LocalHighResBackdrops = staticCompositionLocalOf { false }

@Composable
fun ShieldStreamTheme(
    content: @Composable () -> Unit,
) {
    // Se leen dentro de la composición para que un cambio de acento (perfil) repinte la app.
    val accent = AppColors.Accent
    val onAccent = AppColors.OnAccent
    val accentDark = AppColors.AccentDark
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = accentDark,
            background = AppColors.Background,
            onBackground = Color.White,
            surface = AppColors.Surface,
            onSurface = Color.White,
        ),
        typography = PepinoTypography,
        content = content,
    )
}

/**
 * Estilos de Material asignados a la escala de Pepino+ ([TypeScale]). Los textos sin estilo heredan bodyLarge,
 * así que también salen en Manrope.
 */
private val PepinoTypography = Typography(
    displayLarge = TypeScale.Cover,
    displayMedium = TypeScale.Cover,
    displaySmall = TypeScale.Cover,
    headlineLarge = TypeScale.ScreenTitle,
    headlineMedium = TypeScale.ScreenTitle,
    headlineSmall = TypeScale.ScreenTitle,
    titleLarge = TypeScale.Section,
    titleMedium = TypeScale.Body.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = TypeScale.Body,
    bodyLarge = TypeScale.Body.copy(fontWeight = FontWeight.Normal),
    bodyMedium = TypeScale.Secondary,
    bodySmall = TypeScale.Secondary,
    labelLarge = TypeScale.Secondary.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = TypeScale.Label,
    labelSmall = TypeScale.Label,
)
