package com.shieldstream.tv.intro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.ui.component.ProfileAvatar
import com.shieldstream.tv.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Accelerate = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)

/**
 * Tras elegir perfil, si el Inicio aún no está listo: el avatar en el centro con un anillo del color del tema girando, y al terminar
 * se funde con el Inicio. Si ya estaba listo, esta pantalla ni se ve.
 */
@Composable
fun ProfileLoadingScreen(
    initial: String,
    avatar: Int,
    image: String? = null,
    awaitReady: suspend (timeoutMs: Long) -> Boolean,
    onReady: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { appear.animateTo(1f, tween(420, easing = Emphasized)) }
        val started = System.currentTimeMillis()
        awaitReady(10_000)
        // Que no parpadee si se resuelve al momento.
        val shown = System.currentTimeMillis() - started
        if (shown < 600) delay(600 - shown)
        leave.animateTo(1f, tween(320, easing = Accelerate))
        onReady()
    }
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(950, easing = LinearEasing)),
        label = "angle",
    )
    val sweep by spin.animateFloat(
        initialValue = 40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = 1_400
                40f at 0
                250f at 700 using FastOutSlowInEasing
                40f at 1_400 using FastOutSlowInEasing
            },
        ),
        label = "sweep",
    )
    BackHandler { /* se espera a que cargue */ }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                val a = appear.value
                val l = leave.value
                alpha = a * (1f - l)
                val sc = (0.86f + 0.14f * a) * (1f + 0.12f * l)
                scaleX = sc
                scaleY = sc
            },
        ) {
            Canvas(Modifier.size(232.dp)) {
                val stroke = 4.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.08f), style = Stroke(stroke))
                drawArc(
                    color = AppColors.Accent,
                    startAngle = angle - 90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            ProfileAvatar(initial = initial, avatar = avatar, size = 140.dp, shape = RoundedCornerShape(12.dp), image = image)
        }
    }
}
