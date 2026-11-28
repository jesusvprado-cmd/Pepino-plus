package com.shieldstream.tv.ui.component

import com.shieldstream.tv.ui.theme.AppColors
import com.shieldstream.tv.ui.theme.FocusStyle

import com.shieldstream.tv.ui.theme.AppShapes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shieldstream.tv.ui.theme.LocalLightweightUi

/**
 * Botón con el estilo de Pepino IPTV: sin recuadro. Con el cursor, el texto y el icono se ponen en verde vivo y en
 * negrita; sin él, en gris claro (la acción principal, en verde pepino; las de borrar, en rojo).
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    variant: TvButtonVariant = TvButtonVariant.Neutral,
) {
    var focused by remember { mutableStateOf(false) }
    val duration = if (LocalLightweightUi.current) 0 else 150
    val content by animateColorAsState(
        when {
            !enabled -> FocusStyle.Idle.copy(alpha = 0.5f)
            variant == TvButtonVariant.Danger -> if (focused) Color(0xFFFF7A7E) else AppColors.Error
            focused -> FocusStyle.Content
            variant == TvButtonVariant.Primary -> AppColors.Accent
            else -> FocusStyle.Idle
        },
        tween(duration),
        label = "buttonContent",
    )
    val style = MaterialTheme.typography.titleMedium

    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Box {
            // Texto invisible en negrita: reserva el ancho para que los botones no se muevan al pasar el cursor.
            Text(text = text, color = Color.Transparent, style = style.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
            Text(
                text = text,
                color = content,
                style = style.copy(fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.SemiBold),
                maxLines = 1,
            )
        }
    }
}

/** Neutral (por defecto); Primary = la acción principal, en el color del tema; Danger = borrar o algo sin vuelta atrás. */
enum class TvButtonVariant { Neutral, Primary, Danger }
