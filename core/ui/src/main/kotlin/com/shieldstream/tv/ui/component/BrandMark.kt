package com.shieldstream.tv.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp

/** Rodaja de pepino de la marca (aro y 8 semillas), la misma de la pantalla de arranque. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier, color: Color = Color(0xFF8FE27C)) {
    Canvas(modifier.size(size)) {
        val s = this.size.width
        val r = s * 0.42f
        drawArc(
            color, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(s / 2 - r, s / 2 - r), size = Size(r * 2, r * 2),
            style = Stroke(s * 0.075f, cap = StrokeCap.Round),
        )
        val sw = s * 0.052f
        val sh = s * 0.105f
        for (i in 0 until 8) {
            rotate(i * 45f, Offset(s / 2, s / 2)) {
                drawOval(color, topLeft = Offset(s / 2 - sw / 2, s / 2 - s * 0.145f - sh / 2), size = Size(sw, sh))
            }
        }
    }
}
