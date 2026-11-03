package com.suso.miiptv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.suso.miiptv.R
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Paleta «pepino»: fondo casi negro con un toque verde, verde claro de acento y verde intenso de marca. */
object C {
    val bg = Color(0xFF050906)
    val sel = Color(0xFF18221A)
    val card = Color(0xFF212B23)
    val text2 = Color(0xFFA0A0A0)
    val text3 = Color(0xFFDDDDDD)
    /** Acento (antes dorado): verde pepino claro. */
    val gold = Color(0xFFA6EE90)
    /** Verde intenso de la marca (icono, etiquetas). */
    val brand = Color(0xFF268332)
    /** Verde de «activado» (favoritos, interruptores). */
    val fav = Color(0xFFA6EE90)
    val divider = Color(0xFF334036)

    // Verdes de la interfaz, con nombre (mismos valores que antes; cambiar aquí los cambia en toda la app).
    /** Verde más intenso: opción con el cursor dentro de una sección de Ajustes y menú de Ajustes con el cursor. */
    val greenIntense = Color(0xFF34D640)
    /** Verde fuerte: botones con el cursor, interruptores activados, sección abierta, punto de la VPN. */
    val greenStrong = Color(0xFF4FD65A)
    /** Verde suave: línea vertical del cursor y categoría con el cursor. */
    val greenSoft = Color(0xFF8FE27C)
    /** Verde pálido: sección abierta en el menú de Ajustes. */
    val greenPale = Color(0xFFB8EBAE)
    /** Verde de los iconos del menú de arriba (la lupa), un poco más vivo que [gold] para que se vea igual. */
    val greenIcon = Color(0xFF6FE07A)
    val playBtn = Color(0xFFDDF3F2)
    val error = Color(0xFFFF6B6B)
}

/**
 * Escala de tamaños de letra de toda la app (cambiar uno aquí lo cambia en todas partes).
 * Fuera de la escala solo quedan unos pocos textos grandes únicos (número tecleado, pantalla de arranque…).
 */
object Sz {
    /** Insignias (FHD, 5.1…) y avisos pequeños. */
    val label = 10.sp
    /** Pistas del mando, nombres de canal, datos secundarios. */
    val small = 13.sp
    /** Texto normal: opciones, programas, descripciones. */
    val body = 15.sp
    /** Títulos de fila, programa actual, botones. */
    val emph = 17.sp
    /** Títulos de paneles y secciones. */
    val sub = 22.sp
    /** Títulos grandes (fichas, Ajustes). */
    val title = 28.sp
}

/** Tipografía Manrope en toda la app. */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    Font(R.font.manrope_extrabold, FontWeight.Black),
    Font(R.font.manrope_regular, FontWeight.Light),
)

private fun TextStyle.m() = copy(fontFamily = Manrope)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.m(), displayMedium = t.displayMedium.m(), displaySmall = t.displaySmall.m(),
        headlineLarge = t.headlineLarge.m(), headlineMedium = t.headlineMedium.m(), headlineSmall = t.headlineSmall.m(),
        titleLarge = t.titleLarge.m(), titleMedium = t.titleMedium.m(), titleSmall = t.titleSmall.m(),
        bodyLarge = t.bodyLarge.m(), bodyMedium = t.bodyMedium.m(), bodySmall = t.bodySmall.m(),
        labelLarge = t.labelLarge.m(), labelMedium = t.labelMedium.m(), labelSmall = t.labelSmall.m(),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.gold,
            onPrimary = Color.Black,
            secondary = C.gold,
            background = C.bg,
            onBackground = Color.White,
            surface = C.bg,
            onSurface = Color.White,
            surfaceVariant = C.card,
            onSurfaceVariant = C.text2,
            outline = Color(0xFF777777),
        ),
        typography = AppTypography,
        content = content,
    )
}

private val hmFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

fun hm(ms: Long): String = hmFormat.format(Date(ms))

fun dateStr(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))

fun fmtTime(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

/** Móvil en vertical (pantalla estrecha y táctil). */
@Composable
fun isCompactPhone(): Boolean =
    com.suso.miiptv.data.Device.touch && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600

/** Pantalla táctil estrecha: móvil, o tablet en vertical (se usa el diseño del móvil). */
@Composable
fun isNarrowTouch(): Boolean {
    if (!com.suso.miiptv.data.Device.touch) return false
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    return c.screenWidthDp < 600 || c.screenHeightDp > c.screenWidthDp
}

/** Margen a los lados del contenido: amplio en la tele (zona segura) y estrecho en el móvil en vertical. */
@Composable
fun sideGap(): androidx.compose.ui.unit.Dp {
    if (!com.suso.miiptv.data.Device.touch) return 40.dp
    // Táctil: estrecho en el móvil y en la tablet en vertical; algo más en la tablet en horizontal.
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    return if (c.screenWidthDp < 600 || c.screenHeightDp > c.screenWidthDp) 16.dp else 24.dp
}
