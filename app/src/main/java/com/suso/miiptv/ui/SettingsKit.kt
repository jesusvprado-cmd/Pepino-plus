package com.suso.miiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Superficies de los ajustes. */
object S {
    val panel = Color(0xFF0B120C)
    val card = Color(0xFF161616)
    val line = Color(0xFF262626)
    val ok = Color(0xFF3DDC6A)
    val warn = Color(0xFFE8C46A)
    val bad = Color(0xFFFF6B6B)
}

/** Icono a partir de un trazado SVG de 24×24. */
fun pathIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.White))
        .build()

object SIcons {
    val account by lazy { pathIcon("cuenta", "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z") }
    val shield by lazy { pathIcon("vpn", "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4zM10,17l-4,-4 1.41,-1.41L10,14.17l6.59,-6.59L18,9l-8,8z") }
    val video by lazy { pathIcon("video", "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z") }
    val audio by lazy { pathIcon("audio", "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z") }
    val subs by lazy { pathIcon("subs", "M20,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM4,12h4v2H4v-2zM14,18H4v-2h10v2zM20,18h-4v-2h4v2zM20,14H10v-2h10v2z") }
    val content by lazy { pathIcon("contenido", "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2z") }
    val mic by lazy { pathIcon("voz", "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z") }
    val people by lazy { pathIcon("perfiles", "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3zM8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3zM8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5zM16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z") }
    val guide by lazy { pathIcon("guia", "M21,3H3C1.9,3 1,3.9 1,5v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5C23,3.9 22.1,3 21,3zM21,17H3V5h18V17zM5,7h9v2H5V7zM5,11h9v2H5V11zM16,7h3v6h-3V7z") }
    /** Solo la «v» del escudo de la VPN (para pintarla de verde cuando está conectada). */
    val shieldCheck by lazy { pathIcon("vpncheck", "M10,17l-4,-4 1.41,-1.41L10,14.17l6.59,-6.59L18,9l-8,8z") }
    val info by lazy { pathIcon("info", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z") }
}

/** Elemento del menú lateral de secciones. */
@Composable
fun SideItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    focusKey: String,
    requester: FocusRequester,
    iconColor: Color? = null,
    overlay: ImageVector? = null,
    overlayColor: Color = Color.Unspecified,
    cursorInMenu: Boolean = false,
    onClick: (() -> Unit)? = null,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Sin recuadro: la sección activa se marca con la línea, el icono en verde y el texto en blanco.
    // Con el cursor en el menú solo se marca la que tiene el cursor (nunca dos a la vez); si no, la sección abierta.
    val active = if (cursorInMenu) focused else selected
    val fgBase = Color(0xFF8A8A8A)
    // Verde intenso con el cursor en el menú; más claro cuando ya estás dentro de las opciones de esa sección.
    val accent = if (cursorInMenu) PaneFocus else C.greenPale
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .focusRequester(requester)
            .memFocus(focusKey) { f ->
                focused = f
                if (f) onFocused()
            }
            .tvClick(onClick = onClick ?: onFocused)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Línea vertical verde a la izquierda de la sección activa (donde está el cursor).
        val bar = if (active) (if (cursorInMenu) 3.dp else 4.dp) else 0.dp
        Box(Modifier.width(bar).height(26.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(14.dp - bar))
        Box(Modifier.size(22.dp)) {
            Icon(icon, contentDescription = null, tint = iconColor ?: if (active) accent else Color(0xFF8A8A8A), modifier = Modifier.size(22.dp))
            if (overlay != null) Icon(overlay, contentDescription = null, tint = overlayColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label, color = if (active) accent else fgBase, fontSize = Sz.emph, maxLines = 1,
            fontWeight = when {
                active && focused -> FontWeight.ExtraBold
                active -> FontWeight.SemiBold
                else -> FontWeight.Medium
            },
        )
    }
}

/** Fila de ajuste: título, explicación opcional y valor actual a la derecha. */
@Composable
fun SettingRow(
    title: String,
    focusKey: String,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = true,
    enabled: Boolean = true,
    trailing: (@Composable (focused: Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    // Sin fondo blanco: con el cursor, línea verde a la izquierda y el título en negrita.
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .memFocus(focusKey) { focused = it }
            .tvClick { if (enabled) onClick() }
            .clip(RoundedCornerShape(12.dp))
            .background(S.card)
            .heightIn(min = 60.dp)
            .padding(end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusBar(focused, height = 30.dp, color = PaneFocus)
        Spacer(Modifier.width(10.dp))
        // En el móvil el valor va debajo del texto: al lado no cabe y el título se parte en varias líneas.
        val valueBelow = value != null && isCompactPhone()
        Column(Modifier.weight(1f)) {
            Text(title, color = if (focused) PaneFocus else Color.White, fontSize = Sz.emph, fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Medium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = C.text2, fontSize = Sz.small, lineHeight = 17.sp)
            }
            if (valueBelow) {
                Spacer(Modifier.height(4.dp))
                Text(value!!, color = C.gold, fontSize = Sz.body, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null && !valueBelow) {
            Spacer(Modifier.width(16.dp))
            Text(
                value,
                color = C.gold,
                fontSize = Sz.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing(focused)
        } else if (chevron) {
            Spacer(Modifier.width(10.dp))
            Text("›", color = if (focused) PaneFocus else C.text2, fontSize = Sz.sub)
        }
    }
}

/** Fila con interruptor (sí / no). */
@Composable
fun ToggleRow(title: String, focusKey: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    SettingRow(
        title, focusKey, subtitle,
        trailing = { f -> Switch(checked, f) },
    ) { onChange(!checked) }
}

@Composable
private fun Switch(checked: Boolean, focused: Boolean) {
    // Dibujado a mano (Canvas) para que el círculo quede siempre entero y centrado en el carril.
    // Apagado: círculo blanco a la izquierda. Encendido: círculo verde vivo a la derecha.
    val track = if (focused) Color(0xFFB9B9B9) else Color(0xFF6E6E6E)
    val on = C.greenStrong
    val knob by animateColorAsState(if (checked) on else Color.White, Motion.focusSpec(), label = "knobc")
    val pos by androidx.compose.animation.core.animateFloatAsState(if (checked) 1f else 0f, Motion.focusSpec(), label = "knob")
    androidx.compose.foundation.Canvas(Modifier.width(46.dp).height(26.dp)) {
        val h = size.height
        val w = size.width
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2))
        val r = h / 2 - 3.dp.toPx()
        val cx = h / 2 + (w - h) * pos
        val c = androidx.compose.ui.geometry.Offset(cx, h / 2)
        // Sombra suave alrededor para separar el círculo del carril.
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = r + 1.dp.toPx(), center = c)
        drawCircle(knob, radius = r, center = c)
    }
}

/** Bloque informativo (no seleccionable) con pares etiqueta / valor. */
@Composable
fun InfoCard(rows: List<Pair<String, String>>, status: Pair<String, Color>? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(C.bg.copy(alpha = 0.55f))
            .border(1.dp, S.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (status != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(status.second))
                Spacer(Modifier.width(10.dp))
                Text(status.first, color = Color.White, fontSize = Sz.emph, fontWeight = FontWeight.SemiBold)
            }
        }
        rows.forEach { (k, v) ->
            Row {
                Text(k, color = C.text2, fontSize = Sz.body, modifier = Modifier.width(130.dp))
                Text(v, color = Color(0xFFE6E6E6), fontSize = Sz.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Título pequeño para agrupar filas dentro de una sección. */
@Composable
fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        color = C.text2,
        fontSize = Sz.small,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** Ventana para elegir una opción de una lista. Atrás la cierra sin cambiar nada. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onPick: (T) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val start = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    val frs = remember(options.size) { List(options.size) { FocusRequester() } }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { frs[start].requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).fadeInOnChange(title),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                // El foco no sale de la ventana mientras está abierta.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .widthIn(min = 360.dp, max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141414))
                .border(1.dp, S.line, RoundedCornerShape(16.dp))
                .padding(vertical = 18.dp),
        ) {
            Text(title, color = Color.White, fontSize = Sz.sub, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                Modifier.heightIn(max = 380.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                itemsIndexed(options) { i, (label, value) ->
                    ChoiceItem(label, value == selected, frs[i]) { onPick(value) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceItem(label: String, selected: Boolean, fr: FocusRequester, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .focusRequester(fr)
            .memFocus("choice_$label") { focused = it }
            .tvClick(onClick = onClick)
            .padding(end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusBar(focused, color = PaneFocus)
        Spacer(Modifier.width(4.dp))
        // Botón de opción: círculo, relleno si está elegida.
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) C.gold else C.text2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(C.gold))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (focused) PaneFocus else Color.White, fontSize = Sz.emph, fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.Medium)
    }
}

/** Verde intenso de la opción con el cursor dentro de una sección de Ajustes. */
val PaneFocus = C.greenIntense
