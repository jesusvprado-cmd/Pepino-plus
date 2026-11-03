package com.suso.miiptv.ui

import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.suso.miiptv.data.Episode
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.MovieDetail
import com.suso.miiptv.data.PlayItem
import com.suso.miiptv.data.Prefs
import com.suso.miiptv.data.Repo
import com.suso.miiptv.data.SeriesDetail
import com.suso.miiptv.player.PlayQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------- Piezas comunes ----------------

/** Lo que se muestra en la cabecera de una ficha (la película o serie, o un título relacionado). */
internal data class HeaderInfo(
    val key: String,
    val title: String,
    val meta: String,
    val rating: String?,
    val plot: String?,
    val director: String?,
    val cast: String?,
    val image: String?,
)

private fun plotLines(text: String?): String? = text?.replace("\\n", "\n")

private fun MovieDetail.header() = HeaderInfo(
    key = item.key,
    title = title,
    meta = listOfNotNull(year, duration, genre?.replace(",", " /")).joinToString(" · "),
    rating = rating?.takeIf { it != "0" && it != "0.0" },
    plot = plotLines(plot),
    director = director,
    cast = cast,
    image = backdrop ?: poster ?: item.image,
)

private fun SeriesDetail.header() = HeaderInfo(
    key = item.key,
    title = title,
    meta = listOfNotNull(year, genre?.replace(",", " /")).joinToString(" · "),
    rating = rating?.takeIf { it != "0" && it != "0.0" },
    plot = plotLines(plot),
    director = null,
    cast = cast,
    image = backdrop ?: poster ?: item.image,
)

private fun basicHeader(item: Item) = HeaderInfo(item.key, item.name, "", item.rating, null, null, null, item.image)

internal suspend fun loadHeader(item: Item): HeaderInfo = try {
    if (item.kind == Kind.SERIES) Repo.seriesDetail(item).header() else Repo.movieDetail(item).header()
} catch (e: Exception) {
    if (e is kotlinx.coroutines.CancellationException) throw e
    basicHeader(item)
}

/** Imagen grande a la derecha que se funde con el negro; cambia con un fundido. */
@Composable
private fun BoxScope.HeroImage(url: String?) {
    val compact = compactDetail()
    Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(if (compact) 1f else 0.5f).fillMaxHeight(if (compact) 0.5f else 0.82f)) {
        Crossfade(targetState = url, animationSpec = tween(400), label = "hero") { u ->
            if (u != null) {
                AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(C.bg, C.bg.copy(alpha = 0.55f), Color.Transparent, Color.Transparent))
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, C.bg.copy(alpha = 0.7f), C.bg))
            )
        )
        // En el móvil el texto va encima de la imagen: se oscurece para que se lea.
        if (compact) Box(Modifier.fillMaxSize().background(C.bg.copy(alpha = 0.55f)))
    }
}

/** Móvil en vertical: la ficha ocupa todo el ancho. */
@Composable
internal fun compactDetail(): Boolean = isNarrowTouch() // móvil, o tablet en vertical

/** Ancho del texto de la ficha (62 % en TV, todo en el móvil). */
@Composable
private fun detailFrac() = if (compactDetail()) 1f else 0.62f

@Composable
private fun detailStart() = if (compactDetail()) 18.dp else 48.dp

/** Fila de botones: en el móvil se puede deslizar si no caben. */
@Composable
private fun buttonsScroll(): Modifier =
    if (compactDetail()) Modifier.horizontalScroll(rememberScrollState()) else Modifier

/**
 * Fila de títulos relacionados. Al pasar por encima de uno se avisa con [onPreview]
 * para que la cabecera muestre sus datos sin pulsar OK.
 */
@Composable
private fun RelatedRow(item: Item, onPreview: (Item) -> Unit) {
    var related by remember(item.key) { mutableStateOf<List<Item>>(emptyList()) }
    LaunchedEffect(item.key) {
        if (item.categoryId.isBlank()) return@LaunchedEffect
        related = try {
            Repo.items(item.kind, item.categoryId).filter { it.key != item.key && Repo.visible(it) }.take(30)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emptyList()
        }
    }
    if (related.isEmpty()) return
    Column(Modifier.fillMaxWidth().fadeInOnChange(related.size)) {
        Text(
            "Títulos relacionados", color = Color.White, fontSize = Sz.emph, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(related, key = { i, x -> "$i:${x.key}" }) { _, x ->
                PosterCard(
                    x.name, x.image, "rel_${x.key}", Modifier.width(90.dp),
                    onFocus = { f -> if (f) onPreview(x) },
                ) { Actions.open(x) }
            }
        }
    }
}

/**
 * Cabecera con título, datos y sinopsis. Cambia con un fundido cuando se pasa por un
 * título relacionado. [onPlotOverflow] avisa si la sinopsis no cabe entera.
 */
@Composable
private fun HeaderText(
    info: HeaderInfo,
    titleColor: Color,
    maxPlotLines: Int,
    modifier: Modifier,
    onPlotOverflow: (String, Boolean) -> Unit,
) {
    AnimatedContent(
        targetState = info,
        transitionSpec = { fadeIn(tween(Motion.FADE_MS)) togetherWith fadeOut(tween(Motion.FADE_MS / 2)) },
        contentKey = { it.key },
        label = "header",
        modifier = modifier,
    ) { h ->
        Column(Modifier.fillMaxWidth()) {
            Text(
                h.title, color = titleColor, fontSize = Sz.title, fontWeight = FontWeight.Bold,
                lineHeight = 36.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            if (h.meta.isNotBlank()) Text(h.meta, color = C.text2, fontSize = Sz.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            h.rating?.let {
                Spacer(Modifier.height(2.dp))
                Text("★ $it", color = C.gold, fontSize = Sz.body)
            }
            Spacer(Modifier.height(10.dp))
            PlotAndCredits(h, maxPlotLines, Modifier.weight(1f, fill = false)) { o -> onPlotOverflow(h.key, o) }
        }
    }
}

/**
 * Sinopsis y, debajo, dirección y reparto. La sinopsis tiene preferencia: si el hueco es justo (p. ej. un título de dos líneas),
 * se quitan dirección y reparto para que se vean al menos un par de líneas de sinopsis; todo sigue en «Leer más».
 */
@Composable
private fun PlotAndCredits(h: HeaderInfo, maxPlotLines: Int, modifier: Modifier, onOverflow: (Boolean) -> Unit) {
    BoxWithConstraints(modifier) {
        val base = androidx.compose.material3.LocalTextStyle.current
        val plotStyle = base.merge(androidx.compose.ui.text.TextStyle(color = Color(0xFFE6E6E6), fontSize = Sz.body, lineHeight = 21.sp))
        val creditStyle = base.merge(androidx.compose.ui.text.TextStyle(color = C.text2, fontSize = Sz.small))
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val p1 = remember(plotStyle) { measurer.measure("Ág", plotStyle, maxLines = 1).size.height }
        val p2 = remember(plotStyle) { measurer.measure("Ág\nÁg", plotStyle, maxLines = 2).size.height }
        val per = (p2 - p1).coerceAtLeast(1)
        val c1 = remember(creditStyle) { measurer.measure("Ág", creditStyle, maxLines = 1).size.height }
        val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
        val credits = listOfNotNull(h.director?.let { "Dirección: $it" }, h.cast?.let { "Reparto: $it" })
        val creditsH = if (credits.isEmpty()) 0 else gap + credits.size * c1
        val plot = h.plot
        // Líneas que ocupa la sinopsis entera con este ancho.
        val fullLines = remember(plot, constraints.maxWidth) {
            if (plot.isNullOrBlank()) 0 else measurer.measure(plot, plotStyle, constraints = androidx.compose.ui.unit.Constraints(maxWidth = constraints.maxWidth)).lineCount
        }
        fun fit(space: Int): Int = if (space < p1) 0 else 1 + (space - p1) / per
        val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE / 2
        // Se muestran dirección y reparto solo si aun así caben al menos 3 líneas de sinopsis (o la sinopsis entera).
        val wantPlot = minOf(fullLines, 3)
        val showCredits = credits.isNotEmpty() && fit(maxH - creditsH) >= wantPlot
        val lines = minOf(fit(maxH - if (showCredits) creditsH else 0), maxPlotLines, fullLines)
        val overflow = lines < fullLines || (credits.isNotEmpty() && !showCredits)
        LaunchedEffect(h.key, overflow) { onOverflow(overflow) }
        Column {
            if (plot != null && lines > 0) {
                Text(plot, style = plotStyle, maxLines = lines, overflow = TextOverflow.Ellipsis)
            }
            if (showCredits) {
                Spacer(Modifier.height(6.dp))
                credits.forEach { Text(it, style = creditStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Sinopsis que ocupa el hueco disponible y se recorta con «…» si no cabe. */
@Composable
private fun FittingPlot(text: String, modifier: Modifier, maxLinesCap: Int, onOverflow: (Boolean) -> Unit) {
    BoxWithConstraints(modifier) {
        val style = androidx.compose.material3.LocalTextStyle.current.merge(
            androidx.compose.ui.text.TextStyle(color = Color(0xFFE6E6E6), fontSize = Sz.body, lineHeight = 21.sp)
        )
        // Se mide de verdad cuánto ocupa una línea y dos líneas con esta letra, y solo se muestran las que caben enteras.
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val h1 = remember(style) { measurer.measure("Ág", style, maxLines = 1).size.height }
        val h2 = remember(style) { measurer.measure("Ág\nÁg", style, maxLines = 2).size.height }
        val per = (h2 - h1).coerceAtLeast(1)
        val maxH = constraints.maxHeight
        val lines = when {
            !constraints.hasBoundedHeight -> maxLinesCap
            maxH < h1 -> 0
            else -> (1 + (maxH - h1) / per).coerceIn(1, maxLinesCap)
        }
        // Si no cabe ni una línea entera, mejor no mostrarla que verla cortada (queda «Leer más»).
        if (lines == 0) {
            LaunchedEffect(text) { onOverflow(true) }
        } else {
            Text(
                text, style = style, maxLines = lines, overflow = TextOverflow.Ellipsis,
                onTextLayout = { r -> onOverflow(r.hasVisualOverflow) },
            )
        }
    }
}

/** Panel con la sinopsis completa, el reparto y la dirección. Se cierra con Atrás. */
@Composable
private fun FullPlotPanel(info: HeaderInfo, onClose: () -> Unit) {
    BackHandler { onClose() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { fr.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xF2000000)).fadeInOnChange(info.key),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 32.dp)
                .focusRequester(fr)
                .onPreviewKeyEvent { e ->
                    // OK no hace nada aquí (se cierra solo con Atrás); se consume para que no llegue a los botones de detrás.
                    if (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter) return@onPreviewKeyEvent true
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> {
                            scope.launch { scroll.animateScrollBy(180f) }
                            true
                        }
                        Key.DirectionUp -> {
                            scope.launch { scroll.animateScrollBy(-180f) }
                            true
                        }
                        // Izquierda/derecha no salen del panel.
                        Key.DirectionLeft, Key.DirectionRight -> true
                        else -> false
                    }
                }
                .focusable()
                .verticalScroll(scroll),
        ) {
            Text(info.title, color = C.gold, fontSize = Sz.title, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
            if (info.meta.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(info.meta, color = C.text2, fontSize = Sz.body)
            }
            Spacer(Modifier.height(16.dp))
            Text(info.plot ?: "", color = Color(0xFFEDEDED), fontSize = Sz.emph, lineHeight = 25.sp)
            info.director?.let {
                Spacer(Modifier.height(14.dp))
                Text("Dirección: $it", color = C.text2, fontSize = Sz.body)
            }
            info.cast?.let {
                Spacer(Modifier.height(6.dp))
                Text("Reparto: $it", color = C.text2, fontSize = Sz.body, lineHeight = 21.sp)
            }
            Spacer(Modifier.height(20.dp))
            Text("▲▼ desplazar · Atrás para cerrar", color = C.gold, fontSize = Sz.small)
        }
    }
}

/**
 * Lógica común: qué cabecera se ve (la propia o la de un relacionado enfocado)
 * y si la sinopsis propia se ha recortado.
 */
private class HeaderState {
    var previewItem by mutableStateOf<Item?>(null)
    var previewInfo by mutableStateOf<HeaderInfo?>(null)
    var overflow by mutableStateOf(false)
    var fullOpen by mutableStateOf(false)
    val cache = HashMap<String, HeaderInfo>()
    val moreFr = FocusRequester()
}

@Composable
private fun rememberHeaderState(key: String): HeaderState {
    val st = remember(key) { HeaderState() }
    // Al cerrar el panel de la sinopsis, el foco vuelve a «Leer más».
    var wasOpen by remember(key) { mutableStateOf(false) }
    LaunchedEffect(st.fullOpen) {
        if (st.fullOpen) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            delay(60)
            runCatching { st.moreFr.requestFocus() }
        }
    }
    LaunchedEffect(st.previewItem?.key) {
        val p = st.previewItem
        if (p == null) {
            st.previewInfo = null
            return@LaunchedEffect
        }
        delay(350)
        val h = st.cache[p.key] ?: loadHeader(p).also { st.cache[p.key] = it }
        if (st.previewItem?.key == p.key) st.previewInfo = h
    }
    return st
}

@Composable
private fun SmallLoading() {
    CircularProgressIndicator(color = C.gold, modifier = Modifier.size(32.dp))
}

// ---------------- Ficha de película ----------------

@Composable
fun MovieDetailScreen(item: Item) {
    val ctx = LocalContext.current
    var d by remember(item.key) { mutableStateOf<MovieDetail?>(null) }
    LaunchedEffect(item.key) {
        d = try {
            Repo.movieDetail(item)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            MovieDetail(item, item.name, null, null, null, null, item.image, null, null, null, null, null)
        }
    }
    val pv = Repo.prefs.version
    val fav = remember(pv) { Repo.prefs.isFavorite(item.key) }
    val prog = remember(pv) { Repo.prefs.progress(item.key) }
    val playFr = remember { FocusRequester() }
    val restoring = remember { FocusMemory.restore != null }
    val det = d
    LaunchedEffect(det != null) {
        if (det != null && !restoring) {
            delay(60)
            runCatching { playFr.requestFocus() }
        }
    }
    val hs = rememberHeaderState(item.key)
    val own = det?.header() ?: basicHeader(item)
    val shown = hs.previewInfo ?: own
    val previewing = hs.previewInfo != null

    fun play(from: Long) {
        val m = d ?: return
        val sub = listOfNotNull(m.year, m.genre).joinToString(" · ").ifBlank { null }
        try {
            PlayQueue.start(
                ctx,
                listOf(PlayItem(item.key, m.title, sub, m.poster ?: item.image, Repo.streamUrl(m.item), false)),
                0,
                from,
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Actions.toast(ctx, e.message ?: "No se puede reproducir")
        }
    }

    // Todo cabe en la pantalla: el foco en «Reproducir» no desplaza la ficha ni corta el título.
    Box(Modifier.fillMaxSize().background(C.bg)) {
        HeroImage(shown.image)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showRelated = maxHeight >= 470.dp
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxWidth(detailFrac())
                        .weight(1f)
                        .padding(start = detailStart(), top = 28.dp, end = 16.dp, bottom = 6.dp),
                ) {
                    HeaderText(shown, C.gold, 8, Modifier.weight(1f, fill = false)) { k, o ->
                        if (k == own.key) hs.overflow = o
                    }
                    Spacer(Modifier.height(16.dp))
                    if (det == null) {
                        SmallLoading()
                    } else {
                        Row(
                            Modifier
                                .then(buttonsScroll())
                                .graphicsLayer { alpha = if (previewing) 0.35f else 1f }
                                .onFocusChanged { if (it.hasFocus) hs.previewItem = null },
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TvButton(
                                if (prog != null) "Continuar ${fmtTime(prog.pos)}" else "Reproducir",
                                "d_play",
                                icon = Icons.Filled.PlayArrow,
                                filled = true,
                                focusRequester = playFr,
                            ) { play(prog?.pos ?: 0L) }
                            if (prog != null) TvButton("Empezar", "d_restart") { play(0L) }
                            if (hs.overflow) TvButton("Leer más", "d_more", focusRequester = hs.moreFr) { hs.fullOpen = true }
                            TvButton(null, "d_fav", icon = if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, iconTint = if (fav) C.fav else null) {
                                Actions.toggleFav(ctx, item)
                            }
                            det.trailer?.let { t -> TvButton("Tráiler", "d_trailer") { Actions.openTrailer(ctx, t) } }
                        }
                    }
                }
                if (showRelated) {
                    RelatedRow(item) { x -> hs.previewItem = x }
                    Spacer(Modifier.height(22.dp))
                }
            }
        }
        if (hs.fullOpen) FullPlotPanel(own) { hs.fullOpen = false }
    }
}

// ---------------- Ficha de serie ----------------

private fun epKey(seriesId: String, ep: Episode) = "EP|$seriesId|${ep.id}"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesDetailScreen(item: Item) {
    val ctx = LocalContext.current
    var d by remember(item.key) { mutableStateOf<SeriesDetail?>(null) }
    var failed by remember(item.key) { mutableStateOf(false) }
    LaunchedEffect(item.key) {
        d = try {
            Repo.seriesDetail(item)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            failed = true
            SeriesDetail(item, item.name, null, null, null, item.image, null, null, null, emptyList())
        }
    }
    // Todas las temporadas empiezan plegadas; se despliegan con OK.
    var open by remember(item.key) { mutableStateOf<Int?>(null) }
    val seasonList = rememberLazyListState()
    val seasonFrs = remember(item.key) { HashMap<Int, FocusRequester>() }
    // Al abrir una temporada: se cierran las demás, la temporada sube arriba de la lista y conserva el foco.
    LaunchedEffect(open) {
        val o = open ?: return@LaunchedEffect
        val idx = d?.seasons?.indexOfFirst { it.first == o } ?: -1
        if (idx < 0) return@LaunchedEffect
        delay(30)
        runCatching { seasonList.animateScrollToItem(idx) }
        runCatching { seasonFrs[o]?.requestFocus() }
    }
    val det = d
    val pv = Repo.prefs.version
    val fav = remember(pv) { Repo.prefs.isFavorite(item.key) }
    val lastEp: Prefs.Progress? = remember(pv) { Repo.prefs.progressList().firstOrNull { it.play.key.startsWith("EP|${item.id}|") } }
    val firstFr = remember { FocusRequester() }
    val restoring = remember { FocusMemory.restore != null }
    val hs = rememberHeaderState(item.key)
    // Sin nada empezado: botón para ver el primer episodio de la serie.
    val firstSeason = det?.seasons?.firstOrNull { it.second.isNotEmpty() }
    val hasPlay = lastEp != null || firstSeason != null
    // Cursor al entrar: en el primer botón de la fila («Continuar» / «Reproducir»; si no, «Leer más»; si no, ♥).
    LaunchedEffect(det != null) {
        if (det != null && !restoring) {
            delay(160) // da tiempo a saber si la sinopsis cabe entera (y por tanto si hay «Leer más»)
            val target = if (!hasPlay && hs.overflow) hs.moreFr else firstFr
            runCatching { target.requestFocus() }
        }
    }
    val own = det?.header() ?: basicHeader(item)
    val shown = hs.previewInfo ?: own
    val previewing = hs.previewInfo != null
    val clearPreview = Modifier.onFocusChanged { if (it.hasFocus) hs.previewItem = null }

    fun playEpisodes(eps: List<Episode>, index: Int) {
        val m = d ?: return
        // Toda la serie en la cola (todas las temporadas): al terminar el último de una, sigue la siguiente.
        val list = Actions.episodeQueue(item.id, m, item.image)
        val start = list.indexOfFirst { it.key == epKey(item.id, eps[index]) }.coerceAtLeast(0)
        val pos = Repo.prefs.progress(list[start].key)?.pos ?: 0L
        PlayQueue.start(ctx, list, start, pos)
    }

    fun continueLast() {
        val m = d ?: return
        val p = lastEp ?: return
        for ((_, eps) in m.seasons) {
            val i = eps.indexOfFirst { epKey(item.id, it) == p.play.key }
            if (i >= 0) {
                playEpisodes(eps, i)
                return
            }
        }
        Actions.resume(ctx, p)
    }

    // Cabecera fija arriba; solo se desplaza la lista de temporadas y episodios.
    Box(Modifier.fillMaxSize().background(C.bg)) {
        HeroImage(shown.image)
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // Cabecera de altura fija: al pasar por los relacionados, la lista de abajo no sube ni baja.
        val headerH = (maxHeight * 0.46f).coerceIn(210.dp, 320.dp)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth(detailFrac()).height(headerH).padding(start = detailStart(), top = 28.dp, end = 16.dp)) {
                HeaderText(shown, C.gold, 4, Modifier.fillMaxWidth().weight(1f)) { k, o ->
                    if (k == own.key) hs.overflow = o
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .then(buttonsScroll())
                        .graphicsLayer { alpha = if (previewing) 0.35f else 1f }
                        .then(clearPreview),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (lastEp != null && det != null) {
                        TvButton(
                            // «Ver» si es el siguiente a uno terminado (aún sin empezar); «Continuar» si va a medias.
                            (if (lastEp.pos > 0L) "Continuar " else "Ver ") + (lastEp.play.subtitle?.substringBefore(" - ") ?: ""),
                            "s_cont",
                            icon = Icons.Filled.PlayArrow,
                            filled = true,
                            focusRequester = firstFr,
                        ) { continueLast() }
                    } else if (firstSeason != null) {
                        val ep = firstSeason.second.first()
                        TvButton(
                            "Reproducir T${firstSeason.first} E${ep.number}",
                            "s_play",
                            icon = Icons.Filled.PlayArrow,
                            filled = true,
                            focusRequester = firstFr,
                        ) { playEpisodes(firstSeason.second, 0) }
                    }
                    if (hs.overflow) TvButton("Leer más", "s_more", focusRequester = hs.moreFr) { hs.fullOpen = true }
                    TvButton(
                        null,
                        "s_fav",
                        icon = if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        iconTint = if (fav) C.fav else null,
                        focusRequester = if (!hasPlay) firstFr else null,
                    ) { Actions.toggleFav(ctx, item) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Solo se desplaza cuando lo que tiene el foco no se ve entero: los relacionados no bailan.
            CompositionLocalProvider(LocalBringIntoViewSpec provides RevealBringIntoView) {
            LazyColumn(
                state = seasonList,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when {
                    det == null -> this.item(key = "loading") {
                        Box(Modifier.padding(start = detailStart())) { SmallLoading() }
                    }
                    det.seasons.isEmpty() -> this.item(key = "empty") {
                        Text(
                            if (failed) "No se pudieron cargar los episodios." else "No se han encontrado episodios.",
                            color = C.text2, fontSize = Sz.body, modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                    else -> det.seasons.forEach { (season, eps) ->
                        this.item(key = "s$season") {
                            val sfr = seasonFrs.getOrPut(season) { FocusRequester() }
                            Box(
                                Modifier.animateItem().fillMaxWidth(detailFrac()).padding(start = detailStart(), end = 16.dp)
                                    .focusRequester(sfr).then(clearPreview)
                            ) {
                                SeasonRow(season, eps.size, open == season) { open = if (open == season) null else season }
                            }
                        }
                        if (open == season) {
                            this.itemsIndexed(eps, key = { _, ep -> "e${ep.id}" }) { i, ep ->
                                val p = Repo.prefs.progress(epKey(item.id, ep))
                                Box(Modifier.animateItem().fillMaxWidth(detailFrac()).padding(start = detailStart(), end = 16.dp).then(clearPreview)) {
                                    val k = epKey(item.id, ep)
                                    EpisodeRow(
                                        ep, p, "ep_${ep.id}",
                                        watched = remember(pv, k) { Repo.prefs.isWatched(k) },
                                        onLong = {
                                            val on = !Repo.prefs.isWatched(k)
                                            Repo.prefs.setWatched(k, on)
                                            Actions.toast(ctx, if (on) "Marcado como visto" else "Marcado como no visto")
                                        },
                                    ) { playEpisodes(eps, i) }
                                }
                            }
                        }
                    }
                }
                this.item(key = "related") {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(24.dp))
                        CompositionLocalProvider(LocalBringIntoViewSpec provides TvBringIntoView) {
                            RelatedRow(item) { x -> hs.previewItem = x }
                        }
                    }
                }
            }
            }
        }
        }
        if (hs.fullOpen) FullPlotPanel(own) { hs.fullOpen = false }
    }
}

@Composable
private fun SeasonRow(season: Int, count: Int, expanded: Boolean, onClick: () -> Unit) {
    // Como los menús que bajan: línea verde a la izquierda con el cursor; la temporada abierta en verde.
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .memFocus("season_$season") { focused = it }
            .tvClick(onClick = onClick)
            .padding(end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusBar(focused)
        val tc = if (expanded || focused) C.gold else Color.White
        Text(if (expanded) "▾" else "▸", color = tc, fontSize = Sz.emph, modifier = Modifier.width(22.dp))
        Text(
            "Temporada $season", color = tc, fontSize = Sz.sub,
            fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f),
        )
        Text("$count episodios", color = C.text2, fontSize = Sz.small)
    }
}

@Composable
private fun EpisodeRow(ep: Episode, progress: Prefs.Progress?, focusKey: String, watched: Boolean = false, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 26.dp)
            .memFocus(focusKey) { focused = it }
            .tvClick(onLongClick = onLong, onClick = onClick)
            .padding(end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusBar(focused, height = 22.dp)
        Text("${ep.number}", color = C.gold, fontSize = Sz.body, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sentenceLabel(ep.title), color = when { focused -> C.gold; watched -> C.text2; else -> Color.White }, fontSize = Sz.body, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (watched) Text("  ✓", color = C.gold, fontSize = Sz.body, fontWeight = FontWeight.SemiBold)
            }
            if (progress != null && progress.dur > 0) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(3.dp).background(Color(0xFF444444))) {
                    Box(Modifier.fillMaxWidth((progress.pos.toFloat() / progress.dur).coerceIn(0f, 1f)).fillMaxHeight().background(C.gold))
                }
            }
        }
        ep.duration?.let { Text(it, color = C.text2, fontSize = Sz.small) }
    }
}
