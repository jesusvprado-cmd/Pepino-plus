package com.shieldstream.tv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.ui.R

/** Tipografía de Pepino+ (la misma del rótulo). Tiene tildes, ñ y ¿¡; los símbolos ✓ ★ ▶ salen de la fuente del sistema. */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

/**
 * Antes era Bebas Neue (solo mayúsculas). Ahora es Manrope ExtraBold: los textos que la usan ponen el peso por
 * defecto de esta familia, que solo tiene ExtraBold.
 */
val DisplayFont = FontFamily(Font(R.font.manrope_extrabold, FontWeight.ExtraBold))

/**
 * Escala de Pepino+, pensada para leerse desde el sofá a 1080p. Seis tamaños; nada que haya que leer baja de 15 sp,
 * y 13 sp queda solo para etiquetas cortas.
 */
object TypeScale {
    /** Portada: título grande de la ficha sin logo, «¿Quién está viendo?». */
    val Cover = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 50.sp)

    /** Título de pantalla: Ajustes, Buscar, Editar perfil. */
    val ScreenTitle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp)

    /** Título de fila o sección: «Seguir viendo», «Episodios». */
    val Section = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)

    /** Texto principal: opciones de menú, nombres, sinopsis. */
    val Body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 25.sp)

    /** Texto secundario: duración, año, descripciones. */
    val Secondary = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)

    /** Etiqueta corta: 4K, HDR, NUEVO, +16. */
    val Label = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp)
}
