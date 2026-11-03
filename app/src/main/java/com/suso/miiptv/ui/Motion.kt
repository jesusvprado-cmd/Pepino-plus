package com.suso.miiptv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Duraciones comunes de las animaciones de la interfaz. */
object Motion {
    const val FOCUS_MS = 220
    const val SCROLL_MS = 520
    const val FADE_MS = 260

    fun <T> focusSpec(): AnimationSpec<T> = tween(FOCUS_MS, easing = FastOutSlowInEasing)
}

/**
 * Desplazamiento de las listas al moverse con el mando: el elemento con el foco se coloca
 * a un 30 % del borde y la lista se desliza suavemente hasta él (como en Android TV).
 */
@OptIn(ExperimentalFoundationApi::class)
val TvBringIntoView = object : BringIntoViewSpec {
    // Curva suave (arranca y frena con delicadeza) para que los desplazamientos no se noten bruscos.
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(Motion.SCROLL_MS, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f))

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val target = containerSize * 0.3f
        val available = containerSize - target
        val leading = if (size <= containerSize && available < size) containerSize - size else target
        return offset - leading
    }
}

/** Aparición suave (fundido y pequeño deslizamiento) cada vez que cambia [key]. */
@Composable
fun Modifier.fadeInOnChange(key: Any?): Modifier {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        a.animateTo(1f, tween(Motion.FADE_MS, easing = FastOutSlowInEasing))
    }
    val shift = with(LocalDensity.current) { 14.dp.toPx() }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * shift
    }
}

/**
 * Desplazamiento de Inicio: solo se mueve si la tarjeta con el foco no se ve entera,
 * y entonces la coloca a un 30 % del borde. Así los cambios de fila son suaves y no hay saltos innecesarios.
 */
@OptIn(ExperimentalFoundationApi::class)
val HomeBringIntoView = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = TvBringIntoView.scrollAnimationSpec

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (offset >= 0f && offset + size <= containerSize) return 0f
        return TvBringIntoView.calculateScrollDistance(offset, size, containerSize)
    }
}

/**
 * Cuadrículas (Películas, Series, canales): mientras la tarjeta se ve entera no se mueve nada;
 * al llegar al borde se desplaza solo lo justo, con un margen, y con la curva suave.
 */
@OptIn(ExperimentalFoundationApi::class)
val RevealBringIntoView = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = TvBringIntoView.scrollAnimationSpec

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = (containerSize * 0.14f).coerceAtMost(containerSize - size).coerceAtLeast(0f)
        return when {
            offset < margin -> offset - margin
            offset + size > containerSize - margin -> offset + size - (containerSize - margin)
            else -> 0f
        }
    }
}
