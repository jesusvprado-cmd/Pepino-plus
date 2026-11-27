package com.shieldstream.tv.intro

import kotlin.math.pow

/**
 * La salida "de película", de 0 a 1: la pantalla se aleja hasta ser un fotograma, la tira avanza al siguiente (la app)
 * y este vuelve a acercarse hasta ocupar la pantalla. La usan la intro (para dibujar) y su anfitrión (para colocar la
 * app justo en el hueco del fotograma siguiente).
 */
object IntroReveal {
    /** Separación entre fotogramas, en fracción de la altura de un fotograma. */
    private const val GAP = 0.07f

    /** Escala de los fotogramas (1 = pantalla completa). */
    fun scale(p: Float): Float {
        val away = easeInOutCubic(segment(p, 0f, 0.30f))
        val back = easeInOutCubic(segment(p, 0.72f, 1f))
        return 1f - 0.2f * away + 0.2f * back
    }

    /** Avance de la tira (0 = la intro en el centro, 1 = la app en el centro). */
    fun advance(p: Float): Float = easeInOutCubic(segment(p, 0.20f, 0.78f))

    /** Distancia entre centros de fotogramas, en fracción de la altura de la pantalla. */
    fun pitch(p: Float): Float = scale(p) * (1f + GAP)

    /** Desplazamiento vertical (fracción de la altura) del fotograma de la app respecto al centro. */
    fun appOffset(p: Float): Float = pitch(p) * (1f - advance(p))
}

private fun segment(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun easeInOutCubic(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
