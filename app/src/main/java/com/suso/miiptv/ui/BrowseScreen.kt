package com.suso.miiptv.ui

import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suso.miiptv.data.Category
import com.suso.miiptv.data.EpgEntry
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.Repo
import kotlinx.coroutines.delay

const val FAV = "__fav"
const val RECENT = "__recent"

/** Pantallas "TV en directo", "Películas" y "Series": categorías a la izquierda y cuadrícula a la derecha. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(kind: Kind) {
    val ctx = LocalContext.current
    val st = UiState.browseState(kind)
    val dv = Repo.dataVersion
    val pv = Repo.prefs.version
    val hv = Repo.prefs.hiddenVersion

    var cats by remember(kind, dv) { mutableStateOf<List<Category>?>(null) }
    var catErr by remember(kind, dv) { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, dv, hv) {
        try {
            cats = Repo.categories(kind)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            catErr = e.message ?: "No se pudieron cargar las categorías"
        }
    }

    val ce = catErr
    if (ce != null) {
        ErrorBox(ce) { Repo.reload() }
        return
    }
    val list = cats
    if (list == null) {
        if (kind == Kind.LIVE) Loading() else SkeletonGrid(kind)
        return
    }

    val specials = if (kind == Kind.LIVE) listOf(Category(FAV, "Favoritos"))
    else listOf(Category(FAV, "Favoritos"), Category(RECENT, "Últimos añadidos"))
    val allCats = specials + list
    val sel = st.selected?.takeIf { s -> s == FAV || s == RECENT || list.any { it.id == s } }
        ?: (list.firstOrNull()?.id ?: FAV)

    // Con el mando, la categoría se elige al pasar por encima (con una pequeña pausa).
    var pending by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        delay(400)
        if (st.selected != p) st.selected = p
    }

    var content by remember(kind) { mutableStateOf(if (st.loadedSel == sel) st.lastItems else null) }
    var contentErr by remember(kind) { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, sel, dv, hv, if (sel == FAV) pv else -1) {
        val changed = st.loadedSel != sel
        if (changed) content = null
        contentErr = null
        val res: List<Item> = try {
            when (sel) {
                FAV -> Repo.favorites(kind)
                RECENT -> Repo.latest(kind)
                else -> Repo.items(kind, sel)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            contentErr = e.message ?: "No se pudo cargar el contenido"
            emptyList()
        }
        if (changed && st.loadedSel != null) {
            st.grid = LazyGridState()
            st.lastIdx = -1
        }
        st.loadedSel = sel
        st.lastItems = res
        content = res
    }

    // ▶ desde una categoría lleva siempre a la cuadrícula: a la última tarjeta usada si se ve, o a la primera visible.
    // (Se calcula solo al pulsar ▶, sin redibujar la pantalla en cada movimiento.)
    val ready = content?.isNotEmpty() == true
    fun gridTarget(): FocusRequester {
        if (!ready) return FocusRequester.Cancel
        val visible = st.grid.layoutInfo.visibleItemsInfo.map { it.index }
        val i = if (st.lastIdx in visible) st.lastIdx else visible.firstOrNull()
        return i?.let { st.reqs[it] }.orDefault()
    }

    // ¿Está el cursor en la lista de categorías? (para marcar solo una categoría a la vez)
    var catsFocused by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 700.dp
        // Móvil, o tablet en vertical: categorías en una fila deslizable arriba en vez de la columna de la izquierda
        // (así la cuadrícula usa todo el ancho).
        val chips = com.suso.miiptv.data.Device.touch && (maxWidth < 600.dp || maxHeight > maxWidth)
        Row(Modifier.fillMaxSize()) {
            if (!chips) LazyColumn(
                state = st.cats,
                modifier = Modifier.width(if (narrow) 140.dp else if (com.suso.miiptv.data.Device.touch) 240.dp else 290.dp).fillMaxHeight()
                    .onFocusChanged { catsFocused = it.hasFocus },
                contentPadding = PaddingValues(start = if (narrow) 6.dp else 22.dp, end = 6.dp, top = 14.dp, bottom = 24.dp),
            ) {
                itemsIndexed(allCats, key = { i, c -> "$i:${c.id}" }) { _, c ->
                    val cfr = remember { FocusRequester() }
                    DisposableEffect(c.id, cfr) {
                        st.catReqs[c.id] = cfr
                        onDispose { if (st.catReqs[c.id] === cfr) st.catReqs.remove(c.id) }
                    }
                    Box(Modifier.focusRequester(cfr).focusProperties { right = gridTarget() }) {
                    CategoryRow(
                        name = catLabel(c.name, kind),
                        selected = c.id == sel,
                        focusKey = "cat_${kind.name}_${c.id}",
                        onFocused = { pending = c.id },
                        cursorInMenu = catsFocused,
                    ) {
                        pending = null
                        st.selected = c.id
                    }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (chips) CategoryChips(allCats, sel, kind) { id -> st.selected = id }
                if (kind == Kind.LIVE && !narrow && !com.suso.miiptv.data.Device.touch) LiveHeader(st.focused) // en táctil, sin previsualización: más sitio para los canales
                val its = content
                val ie = contentErr
                // Táctil: deslizar el dedo a izquierda/derecha sobre las carátulas pasa a la categoría siguiente/anterior.
                val swipeX = remember { androidx.compose.animation.core.Animatable(0f) }
                // Transición al cambiar de categoría deslizando: la actual se desvanece hacia un lado y la nueva aparece desde el otro.
                val fade = remember { androidx.compose.animation.core.Animatable(1f) }
                val swipeScope = rememberCoroutineScope()
                val density = androidx.compose.ui.platform.LocalDensity.current
                var swipeW by remember { mutableStateOf(1f) }
                val swipeMod = if (!com.suso.miiptv.data.Device.touch || allCats.size < 2) Modifier else Modifier
                    .onSizeChanged { swipeW = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(allCats, sel) {
                        val limit = with(density) { 70.dp.toPx() }
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val dx = swipeX.value
                                val i = allCats.indexOfFirst { it.id == sel }
                                val next = when {
                                    dx < -limit -> i + 1
                                    dx > limit -> i - 1
                                    else -> i
                                }
                                if (next != i && next in allCats.indices) {
                                    val dir = if (next > i) -1f else 1f
                                    val target = allCats[next].id
                                    swipeScope.launch {
                                        // Sale: se desliza un poco más hacia el lado del dedo mientras se desvanece.
                                        launch { swipeX.animateTo(dir * swipeW * 0.5f, androidx.compose.animation.core.tween(170)) }
                                        fade.animateTo(0f, androidx.compose.animation.core.tween(170))
                                        pending = null
                                        st.selected = target
                                        swipeX.snapTo(-dir * swipeW * 0.35f)
                                        // Espera a que esté el contenido nuevo (casi siempre al momento) y entra desde el otro lado.
                                        kotlinx.coroutines.withTimeoutOrNull(1500) {
                                            while (st.loadedSel != target) kotlinx.coroutines.delay(16)
                                        }
                                        kotlinx.coroutines.delay(30)
                                        launch { swipeX.animateTo(0f, androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                                        fade.animateTo(1f, androidx.compose.animation.core.tween(260))
                                    }
                                } else swipeScope.launch { swipeX.animateTo(0f) }
                            },
                            onDragCancel = { swipeScope.launch { swipeX.animateTo(0f) } },
                        ) { change, amount ->
                            change.consume()
                            swipeScope.launch { swipeX.snapTo(swipeX.value + amount) }
                        }
                    }
                    .graphicsLayer {
                        translationX = swipeX.value * 0.35f
                        alpha = fade.value * (1f - (kotlin.math.abs(swipeX.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 0.5f))
                    }
                Box(Modifier.fillMaxSize().then(swipeMod)) {
                when {
                    its == null -> SkeletonGrid(kind)
                    ie != null && its.isEmpty() -> ErrorBox(ie) { Repo.reload() }
                    its.isEmpty() -> EmptyText(
                        if (sel == FAV) "Aún no tienes favoritos aquí.\nAñádelos con el botón ♥ de la ficha o manteniendo pulsado OK."
                        else "No hay contenido en esta categoría"
                    )
                    else -> CompositionLocalProvider(LocalBringIntoViewSpec provides RevealBringIntoView) {
                    val tvGrid = !com.suso.miiptv.data.Device.touch
                    // Móvil: canales 3 por fila, con los márgenes iguales a ambos lados.
                    val compact = isCompactPhone()
                    val phoneGrid = kind == Kind.LIVE && compact
                    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                    // Móvil o tableta: carátulas del tamaño justo para que quepan enteras con su título
                    // (dos filas en vertical, una en horizontal), con el alto real y el tamaño de letra del teléfono.
                    val gapH = if (compact) 14.dp else 18.dp
                    // Táctil: márgenes iguales a los dos lados (16 en el móvil, 24 en la tablet).
                    val touchPad = if (compact) 16.dp else 24.dp
                    // En la tele: dos filas enteras con título y al menos 5 carátulas por fila.
                    val posterFit: androidx.compose.ui.unit.Dp? = if (kind != Kind.LIVE) {
                        // Filas enteras que caben: 2 en la tele; en táctil según el alto (móvil 2 / 1, tablet 2-4).
                        val rows = if (tvGrid) 2 else (maxHeight / 260.dp).toInt().coerceIn(1, 4)
                        val titleH = 7.dp + (Sz.small.value * 1.5f * fontScale).dp + 4.dp
                        val h = (maxHeight - 12.dp - (titleH.value * rows).dp - (20 * (rows - 1)).dp - 8.dp) / rows
                        val w = h * 2f / 3f
                        val pads = if (tvGrid) 44.dp else touchPad * 2
                        val n = ((maxWidth - pads + gapH) / (128.dp + gapH)).toInt().coerceAtLeast(1)
                        val natural = (maxWidth - pads - (gapH.value * (n - 1)).dp) / n
                        if (tvGrid) {
                            // 5 por fila como mínimo (con margen para el zoom de la seleccionada).
                            val five = (maxWidth - pads - (gapH.value * 4).dp - 12.dp) / 5
                            minOf(w, five).takeIf { it > 60.dp }
                        } else if (w < natural && w > 60.dp) w else null
                    } else null
                    LazyVerticalGrid(
                        // Canales pequeños para ver más de un vistazo (en la tele, 6 por fila).
                        columns = if (phoneGrid) GridCells.Fixed(3)
                        else if (posterFit != null) GridCells.FixedSize(posterFit)
                        else GridCells.Adaptive(if (kind == Kind.LIVE) (if (tvGrid) 92.dp else 112.dp) else 128.dp),
                        state = st.grid,
                        modifier = Modifier
                            .fillMaxSize()
                            // ◀ desde la cuadrícula vuelve a la categoría elegida, no a la que quede a la misma altura.
                            .focusProperties {
                                exit = { d -> if (d == FocusDirection.Left) st.catReqs[sel].orDefault() else FocusRequester.Default }
                                // ▼ desde el menú de arriba: a la primera carátula visible (arriba a la izquierda), no a la más cercana.
                                enter = { d ->
                                    if (d == FocusDirection.Down) st.grid.layoutInfo.visibleItemsInfo.minOfOrNull { it.index }?.let { st.reqs[it] }.orDefault()
                                    else FocusRequester.Default
                                }
                            }
                            .focusGroup()
                            .fadeInOnChange(its),
                        contentPadding = if (!tvGrid) PaddingValues(start = touchPad, end = touchPad, top = 12.dp, bottom = 40.dp)
                        else PaddingValues(start = 14.dp, end = 30.dp, top = 14.dp, bottom = 40.dp),
                        horizontalArrangement = if (posterFit != null) Arrangement.spacedBy(gapH, androidx.compose.ui.Alignment.CenterHorizontally)
                        else Arrangement.spacedBy(if (phoneGrid) 10.dp else if (compact) 14.dp else if (kind == Kind.LIVE) (if (tvGrid) 12.dp else 14.dp) else 18.dp),
                        verticalArrangement = Arrangement.spacedBy(if (phoneGrid) 10.dp else if (kind == Kind.LIVE) (if (tvGrid) 12.dp else 14.dp) else 20.dp),
                    ) {
                        itemsIndexed(its, key = { i, x -> "$i:${x.key}" }) { i, x ->
                            val fk = "g_${kind.name}_${sel}_${x.key}"
                            val fr = remember { FocusRequester() }
                            DisposableEffect(i, fr) {
                                st.reqs[i] = fr
                                onDispose { if (st.reqs[i] === fr) st.reqs.remove(i) }
                            }
                            val m = Modifier.focusRequester(fr)
                            if (kind == Kind.LIVE) {
                                ChannelCard(
                                    x.name, x.image, fk, m,
                                    onFocus = { f -> if (f) { st.focused = x; st.lastIdx = i } },
                                    onLong = { ItemMenu.open(x) },
                                    nameSize = if (tvGrid) (Sz.body.value - 1.5f).sp else if (phoneGrid) Sz.small else Sz.body,
                                ) { Actions.playLive(ctx, its, i, fromPreview = !narrow && !com.suso.miiptv.data.Device.touch) }
                            } else {
                                PosterCard(
                                    x.name, x.image, fk, m,
                                    item = x,
                                    onFocus = { f -> if (f) st.lastIdx = i },
                                    onLong = { ItemMenu.open(x) },
                                    favBadge = sel != FAV,
                                ) { Actions.open(x) }
                            }
                        }
                    }
                    }
                    }
                }
                }
            }
        }
    }
}


/** Categorías como fila de «píldoras» deslizable (móvil). */
@Composable
fun CategoryChips(cats: List<Category>, selected: String?, kind: com.suso.miiptv.data.Kind? = null, onPick: (String) -> Unit) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    // Al cambiar de categoría (también deslizando las carátulas) la píldora elegida se queda a la vista.
    LaunchedEffect(selected) {
        val i = cats.indexOfFirst { it.id == selected }
        if (i < 0) return@LaunchedEffect
        val vis = state.layoutInfo.visibleItemsInfo
        val fully = vis.any { it.index == i && it.offset >= 0 && it.offset + it.size <= state.layoutInfo.viewportEndOffset }
        if (!fully) state.animateScrollToItem((i - 1).coerceAtLeast(0))
    }
    androidx.compose.foundation.lazy.LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(cats.size) { i ->
            val c = cats[i]
            val sel = c.id == selected
            Text(
                catLabel(c.name, kind), maxLines = 1, fontSize = Sz.small,
                color = if (sel) Color.Black else Color(0xFFDDDDDD),
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(if (sel) C.gold else Color(0xFF1F2A21))
                    .clickable { onPick(c.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
