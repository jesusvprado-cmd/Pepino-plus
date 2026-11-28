package com.shieldstream.tv.ui.component

import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Valor que se cambia con ◀ ▶ del mando: flechas vectoriales a los lados (sin flechas si solo hay una opción). */
@Composable
fun ValueSelector(
    value: String,
    modifier: Modifier = Modifier,
    showArrows: Boolean = true,
    color: Color = Color.White,
) {
    // Pepino+ (como Pepino IPTV): el valor en verde claro a la derecha con «›». ◀ ▶ y OK lo siguen cambiando.
    val shown = if (color == Color.White) Color(0xFFA6EE90) else color
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = shown,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = 6.dp),
        )
        if (showArrows) Icon(AppIcons.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(24.dp))
    }
}
