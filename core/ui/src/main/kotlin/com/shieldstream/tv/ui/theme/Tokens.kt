package com.shieldstream.tv.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.model.ProfileTheme

/**
 * Colores de Pepino+, los mismos de Pepino IPTV: fondo casi negro con un toque verde y verde pepino claro de acento.
 * El cursor también es verde (ver [FocusStyle]): sin recuadros blancos.
 */
object AppColors {
    /** Fondo de toda la pantalla: el de Pepino IPTV. */
    val Background = Color(0xFF050906)

    /** Parte alta de los degradados de fondo y huecos de imagen detrás de las portadas. */
    val BackgroundRaised = Color(0xFF0D150F)

    /** Panels, cards, dialogs, and unfocused controls. */
    val Surface = Color(0xFF18211B)

    /** Fondo de una tarjeta o panel con el cursor (el cursor nunca rellena de blanco). */
    val SurfaceFocused = Color(0xFF27342A)

    /** Text fields and inset areas inside a [Surface]. */
    val Field = Color(0xFF0E120F)

    /** Image and skeleton placeholders. */
    val Placeholder = Color(0xFF1F2621)

    /** Acento del perfil activo: progreso, selección e indicadores activos. */
    var Accent by mutableStateOf(Color(0xFFA6EE90))
        private set

    /** Variante oscura del acento (degradados y estados secundarios). */
    var AccentDark by mutableStateOf(Color(0xFF268332))
        private set

    /** Content drawn on [Accent]. */
    var OnAccent by mutableStateOf(Color(0xFF0D1F08))
        private set

    /**
     * The single accent: "new" dots and counts, progress bars, the active rail marker and small
     * section labels. Keep it to small details; large accent areas compete with the artwork.
     */
    var Highlight by mutableStateOf(Color(0xFFA6EE90))
        private set

    /** Avisos que llaman la atención (punto de novedades). Rojo anaranjado: con la marca verde, el rojo Netflix chocaba. */
    val Alert = Color(0xFFFF5A36)

    /** Tres niveles de texto, y nada más: principal, secundario (datos, descripciones) y terciario (pistas, notas). */
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.75f)
    val TextTertiary = Color.White.copy(alpha = 0.55f)

    /** Translucent backing for pills drawn over artwork. */
    val PillScrim = Color.Black.copy(alpha = 0.62f)

    /** Estado correcto (activado, guardado): el verde vivo de los interruptores de Pepino IPTV (antes, verde agua). */
    val Success = Color(0xFF4FD65A)
    val Warning = Color(0xFFF5B83D)
    val Error = Color(0xFFFF6B6B)

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
 * El cursor, igual que en Pepino IPTV:
 * - Botones, pestañas, menús y filas: sin recuadro blanco; el texto y el icono se ponen en verde vivo y en negrita.
 * - Carátulas y tarjetas: crecen un poco y llevan un marco verde pepino fino con una línea oscura por dentro
 *   (ver [focusFrame]).
 */
object FocusStyle {
    const val Scale = 1.05f

    /** Grosor del marco verde de las carátulas y tarjetas con el cursor. */
    val Border = 2.dp

    /** Marco de las carátulas con el cursor: el verde pepino claro de Pepino IPTV. */
    val Frame = Color(0xFFA6EE90)

    /** Texto e iconos con el cursor (botones, pestañas, menús, filas): verde vivo. */
    val Content = Color(0xFF4FD65A)

    /** Fondo de una fila o tecla con el cursor: apenas un velo, sin rellenar de blanco. */
    val Fill = Color.White.copy(alpha = 0.10f)

    /** Texto de los botones y opciones sin el cursor. */
    val Idle = Color(0xFFB4B4B4)
}

/**
 * Marco del cursor en carátulas y tarjetas: verde pepino fino por fuera y una línea oscura por dentro que lo separa
 * de la imagen (como en Pepino IPTV). Sin el cursor no dibuja nada.
 */
fun Modifier.focusFrame(focused: Boolean, shape: Shape): Modifier =
    if (focused) {
        this.border(FocusStyle.Border, FocusStyle.Frame, shape).border(3.dp, Color.Black.copy(alpha = 0.6f), shape)
    } else {
        this
    }
