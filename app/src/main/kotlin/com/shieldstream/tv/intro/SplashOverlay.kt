package com.shieldstream.tv.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.ui.theme.Manrope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Verde del logo y del nombre (el mismo de Pepino IPTV): fijo, se ve verde sobre cualquier fondo. */
private val BrandGreen = Color(0xFF8FE27C)

/** Se enseña una vez por cada vez que se abre la app (el proceso), no al volver de otra pantalla. */
private var splashShown = false

/** Marca de Pepino+: rodaja de pepino (aro y 8 semillas). [ring] de 0 a 1 dibuja el aro poco a poco. */
@Composable
fun BrandMark(size: Dp, ring: Float = 1f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width
        val stroke = s * 0.075f
        val r = s * 0.42f
        drawArc(
            BrandGreen, startAngle = -90f, sweepAngle = 360f * ring, useCenter = false,
            topLeft = Offset(s / 2 - r, s / 2 - r),
            size = Size(r * 2, r * 2),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val a = ring.coerceIn(0f, 1f)
        val sw = s * 0.052f
        val sh = s * 0.105f
        for (i in 0 until 8) {
            rotate(i * 45f, Offset(s / 2, s / 2)) {
                drawOval(
                    BrandGreen.copy(alpha = a),
                    topLeft = Offset(s / 2 - sw / 2, s / 2 - s * 0.145f - sh / 2),
                    size = Size(sw, sh),
                )
            }
        }
    }
}

/**
 * Pantalla de arranque (de Pepino IPTV): la rodaja aparece y su aro se dibuja, sale «Pepino+» y se funde en
 * menos de dos segundos. Solo se dibuja con Canvas y texto: no carga imágenes ni retrasa la carga del Inicio,
 * que sigue preparándose debajo.
 */
@Composable
fun SplashOverlay() {
    val firstTime = remember { !splashShown }
    if (!firstTime) return
    var visible by remember { mutableStateOf(true) }
    val ring = remember { Animatable(0f) }
    val mark = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { mark.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
        launch { ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        delay(350)
        text.animateTo(1f, tween(550, easing = LinearEasing))
        delay(450)
        splashShown = true
        visible = false
    }
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = fadeOut(tween(450))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .background(Brush.radialGradient(listOf(Color(0x66235C2B), Color.Transparent), radius = 900f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(
                    120.dp, ring.value,
                    Modifier.graphicsLayer {
                        val sc = 0.82f + 0.18f * mark.value
                        scaleX = sc
                        scaleY = sc
                        alpha = mark.value
                    },
                )
                Spacer(Modifier.height(26.dp))
                Text(
                    buildAnnotatedString {
                        append("Pepino")
                        withStyle(SpanStyle(color = BrandGreen)) { append("+") }
                    },
                    color = Color.White,
                    fontFamily = Manrope,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.graphicsLayer {
                        alpha = text.value
                        translationY = (1f - text.value) * 24f
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "CINE  ·  SERIES",
                    color = BrandGreen,
                    fontFamily = Manrope,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                    modifier = Modifier.graphicsLayer { alpha = text.value * 0.9f },
                )
                Spacer(Modifier.height(30.dp))
                Box(Modifier.width(140.dp).height(2.dp).background(Color(0x22FFFFFF))) {
                    Box(Modifier.width(140.dp * ring.value).height(2.dp).background(BrandGreen))
                }
            }
        }
    }
}
