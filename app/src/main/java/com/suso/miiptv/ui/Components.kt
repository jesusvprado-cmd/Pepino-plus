package com.suso.miiptv.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Logos
import com.suso.miiptv.data.Repo
import kotlinx.coroutines.delay

@Composable
fun Logo(dp: Dp) {
    BrandMark(dp)
}

/** Pestaña del menú superior: con borde si está elegida, blanca si tiene el foco. */
@Composable
fun TabChip(
    text: String,
    selected: Boolean,
    focusKey: String,
    focusRequester: FocusRequester? = null,
    requestInitial: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(if (focused) Color.White else Color.Transparent, Motion.focusSpec(), label = "tabbg")
    val fr = focusRequester ?: remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (requestInitial) {
            delay(80)
            runCatching { fr.requestFocus() }
        }
    }
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .focusRequester(fr)
            .memFocus(focusKey) {
                focused = it
                onFocusChange?.invoke(it)
            }
            .tvClick(onClick = onClick)
            .clip(shape)
            .background(bgColor)
            .then(if (selected && !focused) Modifier.border(1.5.dp, Color.White, shape) else Modifier)
            .padding(horizontal = if (icon != null) 10.dp else 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = text, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(24.dp))
        }
        if (text.isNotEmpty() && icon == null) {
            Text(
                text,
                color = if (focused) Color.Black else Color.White,
                fontSize = Sz.emph,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Botón de las fichas (REPRODUCIR, corazón, TRÁILER...). */
@Composable
fun TvButton(
    text: String?,
    focusKey: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    filled: Boolean = false,
    focusRequester: FocusRequester? = null,
    vertical: Boolean = false,
    onClick: () -> Unit,
) {
    // Sin recuadros. Botones en fila (horizontal): como el menú de arriba, con el cursor el texto se pone verde y en negrita.
    // Botones en columna (vertical): línea verde a la izquierda, como los menús que bajan.
    // El principal («Reproducir») siempre en verde; con el cursor, un verde más vivo y en negrita.
    var focused by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val principal = filled // «Reproducir» se comporta como los demás.
    // Verde vivo para el botón con el cursor (el verde pastel se veía apagado sobre fondo oscuro).
    val fg = if (focused) C.greenStrong else Color(0xFFBDBDBD) // mismo verde que la sección abierta en Ajustes
    Row(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .memFocus(focusKey) { focused = it }
            .tvClick(onClick = onClick)
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vertical) FocusBar(focused) else Spacer(Modifier.width(10.dp))
        if (icon != null) Icon(icon, contentDescription = text, tint = iconTint ?: fg, modifier = Modifier.size(24.dp))
        if (icon != null && text != null) Spacer(Modifier.width(8.dp))
        if (text != null) {
            Box {
                // Texto invisible en negrita: reserva el ancho para que los botones no se muevan al pasar el cursor.
                Text(text, color = Color.Transparent, fontWeight = FontWeight.ExtraBold, fontSize = Sz.emph, maxLines = 1)
                Text(text, color = fg, fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = Sz.emph, maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
    }
}

/** Línea verde vertical que marca dónde está el cursor en los menús que bajan (ocupa siempre 10 dp de ancho). */
@Composable
fun FocusBar(active: Boolean, height: androidx.compose.ui.unit.Dp = 26.dp, color: Color = C.greenSoft) {
    val bar = if (active) 3.dp else 0.dp
    Box(Modifier.width(bar).height(height).clip(RoundedCornerShape(2.dp)).background(color))
    Spacer(Modifier.width(10.dp - bar))
}

/** Carátula vertical de película o serie. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: String,
    image: String?,
    focusKey: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    subtitle: String? = null,
    item: Item? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    favBadge: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var loaded by remember(image) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, Motion.focusSpec(), label = "poster")
    val borderA by animateFloatAsState(if (focused) 1f else 0f, Motion.focusSpec(), label = "pb")
    val shape = RoundedCornerShape(8.dp)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .memFocus(focusKey) {
                    focused = it
                    if (it && image != null) Ambient.image = image
                    onFocus?.invoke(it)
                }
                .tvClick(onLong, onClick)
                .clip(shape)
                .background(C.card)
                .border(2.dp, focusFrame(borderA), shape)
        ) {
            if (image != null) {
                if (!loaded) Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(0.dp)))
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { st -> loaded = st !is AsyncImagePainter.State.Loading && st !is AsyncImagePainter.State.Empty },
                )
                // Degradado inferior para que las insignias se lean sobre cualquier carátula.
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.3f)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000))))
                )
            } else {
                Text(
                    title,
                    color = C.text2,
                    fontSize = Sz.small,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            PosterBadges(item, favBadge)
            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0x99000000))
                ) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(C.gold))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            title,
            color = if (focused) Color.White else C.text3,
            // En el móvil, un punto menos: el título no le quita protagonismo a la carátula.
            fontSize = if (isCompactPhone()) (Sz.small.value - 1f).sp else Sz.small,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = safeMarquee(focused),
        )
        if (subtitle != null) {
            Text(subtitle, color = C.text2, fontSize = Sz.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Tarjeta de canal: logo sobre gris oscuro y nombre debajo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(
    title: String,
    image: String?,
    focusKey: String,
    modifier: Modifier = Modifier,
    onFocus: ((Boolean) -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    nameSize: androidx.compose.ui.unit.TextUnit = Sz.body,
    /** Línea pequeña bajo el nombre (p. ej. el programa que se emite ahora). */
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    // Con el cursor, la tarjeta crece bastante: con eso ya se ve qué canal está seleccionado.
    val scale by animateFloatAsState(if (focused) 1.2f else 1f, Motion.focusSpec(), label = "chan")
    val borderA by animateFloatAsState(if (focused) 1f else 0f, Motion.focusSpec(), label = "cb")
    val shape = RoundedCornerShape(8.dp)
    // Logo en alta calidad si se encuentra; el nombre de debajo se deja tal cual.
    val sv = Repo.prefs.settingsVersion
    val lv = Logos.version
    val logo = remember(title, image, sv, lv) { Logos.url(title, image) }
    // Los logos oscuros (Telecinco, La 1...) van sobre un fondo claro para que se vean bien.
    var shownLogo by remember(logo) { mutableStateOf(logo) }
    // Todos los canales sobre blanco (el logo se retoca solo para verse bien sobre él).
    val cardBg = if (logo != null) Color.White else C.card
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10.5f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .memFocus(focusKey) {
                    focused = it
                    onFocus?.invoke(it)
                }
                .tvClick(onLong, onClick)
                .clip(shape)
                .background(cardBg)
                // Sin logo (fondo oscuro), un marco para ver la selección; con logo basta con que la tarjeta crezca.
                .then(if (logo == null) Modifier.border(2.dp, focusFrame(borderA), shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                ChannelLogoImage(logo, Modifier.fillMaxSize().padding(10.dp), fallback = image) { shownLogo = it }
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF4A4A4A), modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            title,
            color = if (focused) Color.White else C.text3,
            fontSize = nameSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = safeMarquee(focused),
        )
        if (subtitle != null) {
            Text(
                subtitle, color = if (focused) C.gold else C.text2, fontSize = Sz.small,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Fila de la columna de categorías de la izquierda. */
@Composable
fun CategoryRow(name: String, selected: Boolean, focusKey: String, onFocused: () -> Unit, cursorInMenu: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // Como en Ajustes: sin recuadro, con una línea verde a la izquierda. Con el cursor en la lista solo se marca
    // la categoría con el cursor (nunca dos a la vez); si estás en la cuadrícula, la categoría abierta, en verde más intenso.
    val active = if (cursorInMenu) focused else selected
    val accent = if (cursorInMenu) C.greenSoft else C.greenStrong
    val bar = if (active) (if (cursorInMenu) 3.dp else 4.dp) else 0.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .memFocus(focusKey) {
                focused = it
                if (it) onFocused()
            }
            .tvClick(onClick = onClick)
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(bar).height(24.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(14.dp - bar))
        Text(
            name,
            color = if (active) accent else Color(0xFF9A9A9A),
            fontSize = Sz.body,
            fontWeight = when {
                active && focused -> FontWeight.ExtraBold
                active -> FontWeight.SemiBold
                else -> FontWeight.Medium
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun Loading(text: String = "Cargando…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = C.gold)
            Spacer(Modifier.height(14.dp))
            Text(text, color = C.text2, fontSize = Sz.body)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(message, color = Color.White, fontSize = Sz.emph, textAlign = TextAlign.Center)
            TvButton("Reintentar", "err_retry", filled = true, onClick = onRetry)
        }
    }
}

@Composable
fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = C.text2, fontSize = Sz.emph, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    // En el móvil, más pequeño: así los títulos de las filas de Inicio caben en una línea.
    val phone = isCompactPhone()
    Text(
        text, color = Color.White, fontSize = if (phone) Sz.emph else Sz.sub, fontWeight = FontWeight.SemiBold,
        maxLines = if (phone) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

/** Marco de la miniatura con el cursor: degradado en diagonal del verde claro (arriba a la izquierda) a blanco,
 *  para que abajo no se confunda con la barra verde de lo ya visto. */
fun focusFrame(alpha: Float): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(C.gold.copy(alpha = alpha), Color.White.copy(alpha = alpha)),
    )


/**
 * Título que se desplaza cuando no cabe (sustituye a basicMarquee, que en listas largas a veces cerraba la app
 * con «LayoutNode should be attached to an owner» al dibujar una tarjeta que ya se había retirado).
 */
@Composable
fun safeMarquee(on: Boolean): Modifier {
    if (!on) return Modifier
    val s = androidx.compose.foundation.rememberScrollState()
    androidx.compose.runtime.LaunchedEffect(s.maxValue) {
        val max = s.maxValue
        if (max <= 0) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(1200)
            s.animateScrollTo(max, androidx.compose.animation.core.tween(max * 16, easing = androidx.compose.animation.core.LinearEasing))
            kotlinx.coroutines.delay(1500)
            s.scrollTo(0)
        }
    }
    return Modifier.clipToBounds().horizontalScroll(s, enabled = false)
}


/**
 * Destino de foco seguro para focusProperties: si la tarjeta o fila ya no está en pantalla, su FocusRequester
 * no está enganchado y Compose cerraría la app («FocusRequester is not initialized»); entonces se usa el normal.
 */
fun androidx.compose.ui.focus.FocusRequester?.orDefault(): androidx.compose.ui.focus.FocusRequester {
    val fr = this ?: return androidx.compose.ui.focus.FocusRequester.Default
    if (fr === androidx.compose.ui.focus.FocusRequester.Default || fr === androidx.compose.ui.focus.FocusRequester.Cancel) return fr
    // freeFocus() no mueve nada (no usamos captureFocus) y lanza error si el requester no está enganchado.
    return if (runCatching { fr.freeFocus() }.isSuccess) fr else androidx.compose.ui.focus.FocusRequester.Default
}
