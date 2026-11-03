package com.suso.miiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suso.miiptv.data.Category
import com.suso.miiptv.data.Repo
import com.suso.miiptv.debrid.AllDebrid
import com.suso.miiptv.debrid.DFilters
import com.suso.miiptv.debrid.DKind
import com.suso.miiptv.debrid.DTitle
import com.suso.miiptv.debrid.DebridStore
import kotlinx.coroutines.delay

/** Sección del menú elegida (Películas / Series / Todo); se recuerda al ir y volver de las fichas. */
private var debridCat by mutableStateOf("movies")

/** Categoría del IPTV elegida dentro de Películas o Series ("" = todas). */
private var debridGroup by mutableStateOf("")

/** Pregunta con varias respuestas a la vez (filtros). */
private class MultiAsk(val title: String, val options: List<Pair<String, String>>, val selected: Set<String>, val onDone: (Set<String>) -> Unit)

/** Sección «AllDebrid»: lo que hay en tu cuenta, con filtros de calidad, audio y subtítulos. */
@Composable
fun DebridScreen() {
    @Suppress("UNUSED_VARIABLE") val av = AllDebrid.version
    if (!AllDebrid.linked) {
        DebridLinkPrompt()
        return
    }
    LaunchedEffect(Unit) { DebridStore.open() }
    @Suppress("UNUSED_VARIABLE") val v = DebridStore.version
    val f = DebridStore.filters
    var choice by remember { mutableStateOf<Choice<*>?>(null) }
    var multi by remember { mutableStateOf<MultiAsk?>(null) }

    val kinds = listOf(Category("movies", "Películas"), Category("series", "Series"), Category("all", "Todo"))
    val kind = when (debridCat) {
        "movies" -> DKind.MOVIE
        "series" -> DKind.SERIES
        else -> null
    }
    // Categorías del IPTV para Películas o Series (con cuántos títulos de AllDebrid hay en cada una).
    val groups: List<Category> = remember(DebridStore.version, kind) {
        val l = when (kind) {
            DKind.MOVIE -> DebridStore.movieCats
            DKind.SERIES -> DebridStore.seriesCats
            null -> emptyList()
        }
        if (kind == null || l.isEmpty()) emptyList()
        else {
            val total = DebridStore.titles.count { it.kind == kind }
            val none = DebridStore.uncategorized(kind)
            listOf(Category("", "Todas ($total)")) + l.map { Category(it.key, "${it.name} (${it.count})") } +
                (if (none > 0) listOf(Category(DebridStore.NO_CAT, "Sin categoría ($none)")) else emptyList())
        }
    }
    val group = debridGroup.takeIf { g -> g.isEmpty() || groups.any { it.id == g } } ?: ""
    val list = remember(DebridStore.version, debridCat, group, f) { DebridStore.list(kind, f, group) }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val chips = com.suso.miiptv.data.Device.touch && (maxWidth < 600.dp || maxHeight > maxWidth)
            val narrow = maxWidth < 700.dp
            Row(Modifier.fillMaxSize()) {
                if (!chips) LazyColumn(
                    Modifier.width(if (narrow) 160.dp else if (com.suso.miiptv.data.Device.touch) 240.dp else 280.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(start = if (narrow) 6.dp else 22.dp, end = 6.dp, top = 14.dp, bottom = 24.dp),
                ) {
                    items(kinds, key = { "k_" + it.id }) { c ->
                        CategoryRow(
                            name = c.name,
                            selected = c.id == debridCat,
                            focusKey = "adcat_${c.id}",
                            onFocused = {},
                        ) {
                            if (debridCat != c.id) debridCat = c.id
                            debridGroup = ""
                        }
                    }
                    if (groups.isNotEmpty()) {
                        item(key = "g_label") {
                            Text(
                                "CATEGORÍAS", color = C.text2, fontSize = Sz.label, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(groups, key = { "g_" + it.id }) { c ->
                            CategoryRow(
                                name = c.name,
                                selected = c.id == group,
                                focusKey = "adgrp_${debridCat}_${c.id}",
                                // Como en Películas y Series: la categoría se elige al pasar por encima.
                                onFocused = { if (debridGroup != c.id) debridGroup = c.id },
                            ) { debridGroup = c.id }
                        }
                    }
                    item(key = "status") { DebridStatus() }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (chips) {
                        CategoryChips(kinds, debridCat) {
                            debridCat = it
                            debridGroup = ""
                        }
                        if (groups.isNotEmpty()) CategoryChips(groups, group) { debridGroup = it }
                        DebridStatus(Modifier.padding(horizontal = 16.dp))
                    }
                    FilterBar(
                        f, chips,
                        askMulti = { t, o, sel, done -> multi = MultiAsk(t, o, sel, done) },
                        askOne = { t, o, sel, done -> choice = Choice(t, o, sel, done) },
                    )
                    val err = DebridStore.error
                    when {
                        list.isEmpty() && DebridStore.titles.isEmpty() && DebridStore.syncing -> Loading("Leyendo tu cuenta de AllDebrid…")
                        list.isEmpty() && DebridStore.titles.isEmpty() && err != null -> ErrorBox(err) { DebridStore.sync() }
                        list.isEmpty() && f.active() -> EmptyText("Nada cumple estos filtros.\nCambia la calidad, el audio o los subtítulos.")
                        list.isEmpty() -> EmptyText(
                            if (kind == DKind.SERIES) "No hay series aquí." else if (kind == DKind.MOVIE) "No hay películas aquí." else "Tu cuenta de AllDebrid está vacía."
                        )
                        else -> DebridGrid(list, f, chips)
                    }
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        val c = choice as Choice<Any?>?
        if (c != null) {
            ChoiceDialog(c.title, c.options, c.selected, onPick = { v2 ->
                choice = null
                c.onPick(v2)
            }, onClose = { choice = null })
        }
        val m = multi
        if (m != null) MultiChoiceDialog(m.title, m.options, m.selected, onDone = { sel ->
            multi = null
            m.onDone(sel)
        })
    }
}

/** Recuento de la cuenta y botón para actualizar. */
@Composable
private fun DebridStatus(modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val v = DebridStore.version
    Column(modifier.padding(top = 18.dp)) {
        val ts = DebridStore.titles
        val movies = ts.count { it.kind == DKind.MOVIE }
        val series = ts.filter { it.kind == DKind.SERIES }
        val eps = series.sumOf { it.episodeCount }
        Text(
            when {
                DebridStore.syncing -> "Actualizando…"
                ts.isEmpty() -> ""
                else -> "$movies películas · ${series.size} series ($eps capítulos)"
            },
            color = C.text2, fontSize = Sz.small,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
        )
        TvButton("Actualizar", "ad_refresh", vertical = true) { DebridStore.open(force = true) }
    }
}

private fun setLabel(o: List<Pair<String, String>>, sel: Set<String>, all: String): String {
    if (sel.isEmpty()) return all
    val names = o.filter { it.second in sel }.map { it.first }
    return if (names.size <= 2) names.joinToString(", ") else names.take(2).joinToString(", ") + " +${names.size - 2}"
}

@Composable
private fun FilterBar(
    f: DFilters,
    touch: Boolean,
    askMulti: (String, List<Pair<String, String>>, Set<String>, (Set<String>) -> Unit) -> Unit,
    askOne: (String, List<Pair<String, String>>, String, (String) -> Unit) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = if (touch) 8.dp else 4.dp, end = 16.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterButton("Calidad", setLabel(DFilters.QUALITY, f.quality, "Todas"), f.quality.isNotEmpty(), "adf_q") {
            askMulti("Calidad", DFilters.QUALITY, f.quality) { DebridStore.filters = DebridStore.filters.copy(quality = it) }
        }
        FilterButton("Audio", setLabel(DFilters.AUDIO, f.audio, "Todos"), f.audio.isNotEmpty(), "adf_a") {
            askMulti("Idioma del audio", DFilters.AUDIO, f.audio) { DebridStore.filters = DebridStore.filters.copy(audio = it) }
        }
        FilterButton("Subtítulos", setLabel(DFilters.SUBS, f.subs, "Todos"), f.subs.isNotEmpty(), "adf_s") {
            askMulti("Subtítulos", DFilters.SUBS, f.subs) { DebridStore.filters = DebridStore.filters.copy(subs = it) }
        }
        FilterButton("Orden", DFilters.SORT.firstOrNull { it.second == f.sort }?.first ?: f.sort, false, "adf_o") {
            askOne("Ordenar por", DFilters.SORT, f.sort) { DebridStore.filters = DebridStore.filters.copy(sort = it) }
        }
        if (f.active()) TvButton("Quitar filtros", "adf_clear") {
            DebridStore.filters = DFilters(sort = f.sort)
        }
    }
}

/**
 * Ventana para marcar varias opciones: OK marca o desmarca; «Aplicar» (o Atrás) guarda lo marcado.
 * Sin nada marcado = todas.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun MultiChoiceDialog(title: String, options: List<Pair<String, String>>, initial: Set<String>, onDone: (Set<String>) -> Unit) {
    var sel by remember { mutableStateOf(initial) }
    BackHandler { onDone(sel) }
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .focusProperties { exit = { androidx.compose.ui.focus.FocusRequester.Cancel } }
                .focusGroup()
                .widthIn(min = 360.dp, max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141414))
                .padding(vertical = 18.dp),
        ) {
            Text(title, color = Color.White, fontSize = Sz.sub, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "Marca una o varias. Sin ninguna marcada = todas.", color = C.text2, fontSize = Sz.small,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 380.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(options.size) { i ->
                    val (label, value) = options[i]
                    val on = value in sel
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                            .memFocus("multi_$value") { focused = it }
                            .tvClick { sel = if (on) sel - value else sel + value }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (focused) Color(0xFF1F2A21) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                                .background(if (on) C.greenStrong else Color(0xFF333333)),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Text("✓", color = Color.Black, fontSize = Sz.body, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(14.dp))
                        Text(
                            label, color = if (focused) C.greenStrong else Color.White, fontSize = Sz.emph,
                            fontWeight = if (focused || on) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
            Row(Modifier.padding(start = 14.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton("Aplicar", "multi_ok", filled = true) { onDone(sel) }
                TvButton("Todas", "multi_all") { onDone(emptySet()) }
            }
        }
    }
}

/** Botón de filtro: «Calidad: 4K» (el valor en verde si el filtro está puesto). */
@Composable
private fun FilterButton(label: String, value: String, on: Boolean, key: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(end = 8.dp)
            .memFocus(key) { focused = it }
            .tvClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) Color(0xFF2A3A2D) else Color(0xFF172019))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label: ", color = if (focused) Color.White else C.text2, fontSize = Sz.body, maxLines = 1)
        Text(
            value, color = if (on || focused) C.greenStrong else Color.White, fontSize = Sz.body, maxLines = 1,
            fontWeight = if (on || focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun DebridGrid(list: List<DTitle>, f: DFilters, touch: Boolean) {
    val tv = !com.suso.miiptv.data.Device.touch
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (tv) 118.dp else if (isCompactPhone()) 100.dp else 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = if (touch) PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp)
        else PaddingValues(start = 14.dp, end = 30.dp, top = 14.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isCompactPhone()) 12.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(list, key = { it.key }) { t -> DebridPoster(t, f, Modifier) }
    }
}

/** Carátula con etiquetas de la mejor copia (calidad, HDR, idiomas). */
@Composable
fun DebridPoster(t: DTitle, f: DFilters?, modifier: Modifier, focusPrefix: String = "ad") {
    @Suppress("UNUSED_VARIABLE") val v = DebridStore.version
    @Suppress("UNUSED_VARIABLE") val pv = Repo.prefs.version
    val best = remember(DebridStore.version, t.key, f) { DebridStore.best(t.versions, f) }
    val tags = best?.tags(withSubs = false).orEmpty().take(3)
    val progress = if (t.kind == DKind.MOVIE) Repo.prefs.progress(DebridStore.movieKey(t))?.let { p ->
        if (p.dur > 0) p.pos.toFloat() / p.dur else null
    } else null
    val sub = when (t.kind) {
        DKind.SERIES -> t.episodeCount.let { if (it == 1) "1 capítulo" else "$it capítulos" }
        DKind.MOVIE -> (DebridStore.meta(t)?.year ?: t.year)?.toString()
    }
    Box(modifier) {
        PosterCard(
            title = DebridStore.displayName(t),
            image = DebridStore.poster(t),
            focusKey = "${focusPrefix}_${t.key}",
            progress = progress,
            subtitle = sub,
            favBadge = false,
        ) { UiState.push(Screen.Debrid(t.key)) }
        if (tags.isNotEmpty()) Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            tags.forEach { TagChip(it, small = true) }
        }
    }
}

/** Etiqueta pequeña («4K», «DV», «ES/EN»). */
@Composable
fun TagChip(text: String, small: Boolean = false) {
    val strong = text == "4K" || text == "DV" || text.startsWith("HDR")
    Text(
        text,
        color = if (strong) Color.Black else Color.White,
        fontSize = if (small) 9.sp else Sz.small,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (strong) C.gold else Color(0xD0101810))
            .padding(horizontal = if (small) 4.dp else 7.dp, vertical = if (small) 1.dp else 3.dp),
    )
}

/** Sin cuenta vinculada: explicación y botón para vincularla con un código. */
@Composable
private fun DebridLinkPrompt() {
    var linking by remember { mutableStateOf(false) }
    if (linking) {
        DebridPinPanel(onClose = { linking = false })
        return
    }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 620.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Tu cuenta de AllDebrid", color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
            Text(
                "Vincula tu cuenta para ver aquí las películas y series que tienes en AllDebrid, con carátulas y filtros de calidad e idioma.",
                color = C.text2, fontSize = Sz.body, textAlign = TextAlign.Center,
            )
            val fr = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) {
                delay(150)
                runCatching { fr.requestFocus() }
            }
            TvButton("Vincular cuenta", "ad_link", filled = true, focusRequester = fr) { linking = true }
            Text("También puedes escribir la clave API en Ajustes › AllDebrid.", color = C.text2, fontSize = Sz.small)
        }
    }
}

/**
 * Vincular con código: AllDebrid da un código de 4 letras; se escribe en alldebrid.com/pin desde el móvil
 * o el ordenador y la app recibe la clave sola.
 */
@Composable
fun DebridPinPanel(onClose: () -> Unit) {
    BackHandler { onClose() }
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf<AllDebrid.Pin?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        msg = null
        pin = null
        val p = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AllDebrid.pinGet() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            msg = e.message ?: "No se pudo pedir el código"
            return@LaunchedEffect
        }
        pin = p
        while (System.currentTimeMillis() < p.expiresAt) {
            delay(5000)
            val key = try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AllDebrid.pinCheck(p) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is AllDebrid.ApiError && e.code.startsWith("PIN_")) {
                    msg = "El código ha caducado. Pide otro."
                    return@LaunchedEffect
                }
                null
            }
            if (key != null) {
                AllDebrid.setKey(key)
                val name = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AllDebrid.checkUser() } }.getOrNull()
                Actions.toast(ctx, "Cuenta vinculada" + (name?.let { ": $it" } ?: ""))
                DebridStore.clear()
                DebridStore.open(force = true)
                onClose()
                return@LaunchedEffect
            }
        }
        msg = "El código ha caducado. Pide otro."
    }
    Box(Modifier.fillMaxSize().background(C.bg).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Vincular AllDebrid", color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
            val p = pin
            when {
                msg != null -> Text(msg!!, color = C.error, fontSize = Sz.emph, textAlign = TextAlign.Center)
                p == null -> Text("Pidiendo un código…", color = C.text2, fontSize = Sz.body)
                else -> {
                    Text("En el móvil o el ordenador, entra en", color = C.text2, fontSize = Sz.body)
                    Text("alldebrid.com/pin", color = C.gold, fontSize = Sz.sub, fontWeight = FontWeight.Bold)
                    Text("y escribe este código:", color = C.text2, fontSize = Sz.body)
                    Text(
                        p.pin, color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 10.sp,
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF172019)).padding(horizontal = 28.dp, vertical = 10.dp),
                    )
                    Text("Esta pantalla se cerrará sola al confirmarlo.", color = C.text2, fontSize = Sz.small)
                }
            }
            Spacer(Modifier.height(4.dp))
            val fr = remember { androidx.compose.ui.focus.FocusRequester() }
            LaunchedEffect(Unit) {
                delay(150)
                runCatching { fr.requestFocus() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (msg != null) TvButton("Pedir otro código", "ad_pin_retry", filled = true) { attempt++ }
                TvButton("Cancelar", "ad_pin_cancel", focusRequester = fr) { onClose() }
            }
        }
    }
}

/** Fila de resultados de AllDebrid en «Buscar». */
@Composable
fun DebridSearchRow(results: List<DTitle>) {
    if (results.isEmpty()) return
    val gap = sideGap()
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        SectionTitle("AllDebrid (${results.size})", Modifier.padding(start = gap, bottom = 8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = gap, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(results, key = { it.key }) { t -> DebridPoster(t, null, Modifier.width(if (isCompactPhone()) 100.dp else 118.dp), "ads") }
        }
    }
}
