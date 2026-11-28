package com.shieldstream.tv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.ProfileTheme

/**
 * Colores de Pepino+: fondo casi negro con un toque verde, el foco invierte a blanco y el verde pepino
 * marca progreso, selección, novedades y los indicadores activos.
 */
object AppColors {
    /** Full-screen canvas. */
    val Background = Color(0xFF0D100E)

    /** Top stop of subtle full-screen gradients and image placeholders behind backdrops. */
    val BackgroundRaised = Color(0xFF131B15)

    /** Panels, cards, dialogs, and unfocused controls. */
    val Surface = Color(0xFF18211B)

    /** Focused panel rows when the control does not invert to white. */
    val SurfaceFocused = Color(0xFF27342A)

    /** Text fields and inset areas inside a [Surface]. */
    val Field = Color(0xFF0E120F)

    /** Image and skeleton placeholders. */
    val Placeholder = Color(0xFF1F2621)

    /** Acento del perfil activo: progreso, selección e indicadores activos. */
    var Accent by mutableStateOf(Color(0xFF62C33F))
        private set

    /** Variante oscura del acento (degradados y estados secundarios). */
    var AccentDark by mutableStateOf(Color(0xFF2D6B1E))
        private set

    /** Content drawn on [Accent]. */
    var OnAccent by mutableStateOf(Color(0xFF0D1F08))
        private set

    /**
     * The single accent: "new" dots and counts, progress bars, the active rail marker and small
     * section labels. Keep it to small details; large accent areas compete with the artwork.
     */
    var Highlight by mutableStateOf(Color(0xFF62C33F))
        private set

    /** Avisos que llaman la atención (punto de novedades). Rojo anaranjado: con la marca verde, el rojo Netflix chocaba. */
    val Alert = Color(0xFFFF5A36)

    /** Tres niveles de texto, y nada más: principal, secundario (datos, descripciones) y terciario (pistas, notas). */
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.75f)
    val TextTertiary = Color.White.copy(alpha = 0.55f)

    /** Translucent backing for pills drawn over artwork. */
    val PillScrim = Color.Black.copy(alpha = 0.62f)

    /** Estado correcto (activado, guardado). Verde agua, para no confundirse con el verde pepino de la marca. */
    val Success = Color(0xFF35D0BA)
    val Warning = Color(0xFFF5B83D)
    val Error = Color(0xFFFF5A5F)

    /**
     * Pepino+ solo tiene un color: el verde pepino. El tema guardado en cada perfil se ignora (la elección de tema
     * se quitó de Editar perfil), así que da igual lo que tuviera un perfil antiguo.
     */
    @Suppress("UNUSED_PARAMETER")
    fun applyTheme(ignored: ProfileTheme) {
        val theme = ProfileTheme.BAMBI
        Accent = Color(theme.accent)
        AccentDark = Color(theme.accentDark)
        OnAccent = Color(theme.onAccent)
        Highlight = Color(theme.accent)
    }
}

/** Shared corner radii. Use these instead of ad-hoc `RoundedCornerShape` values. */
object AppShapes {
    /** Small labels: age ratings, media badges, "new" tags, checkboxes, skeleton text. */
    val Badge = RoundedCornerShape(4.dp)

    /** Buttons, cards, posters, list rows, keys, and text fields. */
    val Control = RoundedCornerShape(14.dp)

    /** Panels, sheets, and dialogs. */
    val Panel = RoundedCornerShape(16.dp)

    /** Chips, pills, and progress bars. */
    val Pill = RoundedCornerShape(50)
}

/** Shared spacing tokens. */
object AppSpacing {
    /** Horizontal inset from the screen edge for browse screens. */
    val ScreenEdge = 48.dp
}

/**
 * Una sola regla de foco en toda la app: lo elegible crece un 5 % y lleva borde blanco de 3 dp. Los botones se
 * rellenan de blanco. El verde marca estado (activado, elegido, visto), nunca el foco.
 */
object FocusStyle {
    const val Scale = 1.05f
    val Border = 3.dp
}
