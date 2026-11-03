@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.suso.miiptv.ui

import androidx.compose.foundation.focusGroup

import androidx.compose.ui.focus.focusProperties

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.PlayItem
import com.suso.miiptv.data.Repo
import com.suso.miiptv.player.PlayQueue
import kotlinx.coroutines.delay

/** Estado de la portada destacada que se conserva al ir y volver de una ficha. */
internal object HeroState {
    var index by mutableStateOf(0)
    val headers = mutableStateMapOf<String, HeaderInfo>()
}

private const val SLIDE_MS = 8000

/** Elige los títulos destacados: los más recientes de las filas de películas y series, alternados. */
fun heroCandidates(rows: List<HomeRow>): List<Item> {
    val movies = rows.filter { it.kind == Kind.MOVIE }.flatMap { it.items }
        .filter { it.image != null }.sortedByDescending { it.added }.take(5)
    val series = rows.filter { it.kind == Kind.SERIES }.flatMap { it.items }
        .filter { it.image != null }.sortedByDescending { it.added }.take(3)
    val out = ArrayList<Item>()
    val mi = movies.iterator()
    val si = series.iterator()
    while (mi.hasNext() || si.hasNext()) {
        if (mi.hasNext()) out += mi.next()
        if (mi.hasNext()) out += mi.next()
        if (si.hasNext()) out += si.next()
    }
    return out.distinctBy { it.key }.take(8)
}

/** Portada grande de Inicio: imagen de fondo, título, nota, sinopsis y botones. Pasa sola cada pocos segundos. */
@Composable
fun HeroCarousel(items: List<Item>) {
    if (items.isEmpty()) return
    val ctx = LocalContext.current
    val n = items.size
    val index = HeroState.index.let { if (it in 0 until n) it else 0 }
    val item = items[index]
    var firstFocused by remember { mutableStateOf(false) }
    var lastFocused by remember { mutableStateOf(false) }
    val focused = firstFocused || lastFocused

    // Carga los datos (fondo, sinopsis) del destacado actual y del siguiente.
    LaunchedEffect(index, items) {
        for (x in listOf(items[index], items[(index + 1) % n])) {
            if (!HeroState.headers.containsKey(x.key)) HeroState.headers[x.key] = loadHeader(x)
        }
    }
    // Avance automático; se para mientras tienes el foco en la portada.
    val progress = remember(index) { Animatable(0f) }
    LaunchedEffect(index, focused) {
        if (focused) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        // Con un reloj propio (no una animación): si el aparato tiene las animaciones desactivadas
        // (opciones de desarrollador), una animación dura 0 s y los destacados pasaban a toda velocidad.
        val start = android.os.SystemClock.elapsedRealtime()
        while (true) {
            val f = (android.os.SystemClock.elapsedRealtime() - start).toFloat() / SLIDE_MS
            progress.snapTo(f.coerceAtMost(1f))
            if (f >= 1f) break
            delay(50)
        }
        HeroState.index = (index + 1) % n
    }

    val h = HeroState.headers[item.key]
    val image = h?.image ?: item.image
    LaunchedEffect(image, focused) { if (focused && image != null) Ambient.image = image }
    // Con el foco en la portada, se ve entera (el título no queda cortado por arriba).
    LaunchedEffect(focused) {
        if (focused) {
            delay(40)
            runCatching { UiState.homeList.animateScrollToItem(0) }
        }
    }

    val heroAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (HomeFocus.row != null) 0.5f else 1f, tween(420), label = "heroa",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp)
            .graphicsLayer { alpha = heroAlpha }
            .onPreviewKeyEvent { e ->
                if (!focused || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.DirectionLeft && firstFocused -> {
                        HeroState.index = (index - 1 + n) % n
                        true
                    }
                    e.key == Key.DirectionRight && lastFocused -> {
                        HeroState.index = (index + 1) % n
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Imagen de fondo a la derecha, que se funde con lo que hay detrás (sin bordes duros).
        val compact = isNarrowTouch()
        Box(Modifier.matchParentSize()) {
        Crossfade(
            targetState = image,
            animationSpec = tween(700),
            label = "heroimg",
            modifier = Modifier
                .align(Alignment.TopEnd)
                // En el móvil en vertical la imagen ocupa todo el ancho (el texto va encima, sobre la imagen oscurecida).
                .fillMaxWidth(if (compact) 1f else 0.68f)
                .fillMaxHeight()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (!compact) drawRect(
                        Brush.horizontalGradient(0f to Color.Transparent, 0.45f to Color.Black, 1f to Color.Black),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        Brush.verticalGradient(0f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) { u ->
            if (u != null) {
                AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        // Móvil: se oscurece la imagen para que el texto se lea encima.
        if (compact) Box(Modifier.matchParentSize().background(Color(0x8C000000)))
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = sideGap(), end = sideGap(), top = 14.dp, bottom = 8.dp)
                .fillMaxWidth(if (compact) 1f else 0.52f),
        ) {
            AnimatedContent(
                targetState = item.key,
                transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(200)) },
                label = "herotext",
            ) { key ->
                val it2 = items.firstOrNull { it.key == key } ?: item
                val hh = HeroState.headers[key]
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(if (it2.kind == Kind.SERIES) "SERIE" else "PELÍCULA", Color(0x33FFFFFF), Color.White)
                        if (isNew(it2)) Pill("NUEVO", C.brand, Color.White)
                        qualityOf(it2)?.let { Pill(it, Color(0x33FFFFFF), Color.White) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        hh?.title ?: it2.name,
                        color = C.gold, fontSize = Sz.title, fontWeight = FontWeight.ExtraBold,
                        lineHeight = 35.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    val rating = ratingOf(it2) ?: hh?.rating?.replace(',', '.')?.toFloatOrNull()?.takeIf { it > 0 }
                        ?.let { String.format(java.util.Locale("es", "ES"), "%.1f", if (it > 10) it / 10 else it) }
                    val meta = listOfNotNull(rating?.let { "★ $it" }, hh?.meta?.takeIf { it.isNotBlank() }).joinToString("   ")
                    if (meta.isNotBlank()) Text(meta, color = Color.White, fontSize = Sz.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    hh?.plot?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFDADADA), fontSize = Sz.body, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val playFr = remember { androidx.compose.ui.focus.FocusRequester() }
            Row(
                // Al bajar desde el menú (o subir desde las filas) el cursor entra siempre por «Reproducir».
                Modifier
                    .focusProperties { enter = { playFr.orDefault() } }
                    .focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val playLabel = if (item.kind == Kind.SERIES) "Ver serie" else "Reproducir"
                Box(Modifier.trackFocus { f -> firstFocused = f }) {
                    TvButton(playLabel, "hero_play", icon = Icons.Filled.PlayArrow, filled = true, focusRequester = playFr) {
                        if (item.kind == Kind.MOVIE) playMovie(ctx, item, h) else Actions.open(item)
                    }
                }
                Box(Modifier.trackFocus { f -> lastFocused = f }) {
                    TvButton("Más info", "hero_info") { Actions.open(item) }
                }
            }
            Spacer(Modifier.height(18.dp))
            // Indicador de páginas: la actual se va llenando hasta pasar a la siguiente.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until n) {
                    if (i == index) {
                        Box(Modifier.width(26.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x44FFFFFF))) {
                            Box(
                                Modifier.fillMaxHeight()
                                    .fillMaxWidth(if (focused) 1f else progress.value.coerceIn(0.02f, 1f))
                                    .background(C.gold)
                            )
                        }
                    } else {
                        Box(Modifier.width(6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF)))
                    }
                }
            }
        }
    }
}

private fun playMovie(ctx: android.content.Context, item: Item, h: HeaderInfo?) {
    try {
        val pos = Repo.prefs.progress(item.key)?.pos ?: 0L
        PlayQueue.start(
            ctx,
            listOf(PlayItem(item.key, h?.title ?: item.name, h?.meta?.ifBlank { null }, item.image, Repo.streamUrl(item), false)),
            0,
            pos,
        )
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Actions.toast(ctx, e.message ?: "No se puede reproducir")
    }
}

/** Avisa cuando algo dentro de este bloque gana o pierde el foco. */
private fun Modifier.trackFocus(onChange: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { s -> onChange(s.hasFocus) }
