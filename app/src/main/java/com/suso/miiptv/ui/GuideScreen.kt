package com.suso.miiptv.ui

import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateDecay
import androidx.compose.foundation.clickable
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suso.miiptv.data.Category
import com.suso.miiptv.data.EpgEntry
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.Logos
import com.suso.miiptv.data.PlayItem
import com.suso.miiptv.data.Prefs
import com.suso.miiptv.data.Repo
import com.suso.miiptv.player.PlayQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Categoría abierta en la guía (se recuerda al volver). */
internal object GuideState {
    var sel: String? = null
}

private const val WIN_MIN = 150L // minutos visibles a la vez
private const val HALF_HOUR = 30 * 60_000L

private fun floorHalfHour(t: Long): Long = t - (t % HALF_HOUR)

private val HM = SimpleDateFormat("HH:mm", Locale("es", "ES"))
private val DAY = SimpleDateFormat("EEE d", Locale("es", "ES"))

/** Programa con el cursor, para el panel de arriba. */
private data class Focused(val ch: Item, val e: EpgEntry?, val loading: Boolean = false)

/** Canales de la categoría cuya guía se carga por adelantado (los demás, al aparecer en pantalla). */
private const val PREFETCH_MAX = 150

/**
 * Guía en parrilla: canales en filas y programas a lo largo del tiempo.
 * OK sobre lo que se emite ahora = ver el canal; sobre algo futuro = recordatorio; sobre algo pasado = verlo (si el canal lo guarda).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun GuideScreen() {
    val ctx = LocalContext.current
    var cats by remember { mutableStateOf<List<Category>>(emptyList()) }
    LaunchedEffect(Unit) {
        cats = listOf(Category(FAV, "Favoritos")) + runCatching { Repo.categories(Kind.LIVE) }.getOrDefault(emptyList())
        if (GuideState.sel == null) GuideState.sel = cats.getOrNull(1)?.id ?: FAV
    }
    var sel by remember { mutableStateOf(GuideState.sel) }
    LaunchedEffect(cats) { if (sel == null) sel = GuideState.sel }
    var channels by remember { mutableStateOf<List<Item>?>(null) }
    LaunchedEffect(sel) {
        val s = sel ?: return@LaunchedEffect
        GuideState.sel = s
        channels = null
        channels = runCatching { if (s == FAV) Repo.favorites(Kind.LIVE) else Repo.items(Kind.LIVE, s) }.getOrDefault(emptyList())
    }
    // La guía de todos los canales de la categoría se pide por adelantado, de 3 en 3 y de arriba abajo:
    // al bajar rápido las filas ya están listas, en vez de pedirse una a una al aparecer en pantalla.
    LaunchedEffect(channels) {
        val l = channels ?: return@LaunchedEffect
        val permits = kotlinx.coroutines.sync.Semaphore(2)
        kotlinx.coroutines.coroutineScope {
            l.take(PREFETCH_MAX).forEach { ch ->
                launch {
                    permits.acquire()
                    try {
                        // Si el servidor ha pedido esperar (HTTP 429), no se adelanta nada más.
                        if (!com.suso.miiptv.data.Http.busy()) Repo.guideEpg(ch)
                    } finally {
                        permits.release()
                    }
                }
            }
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    var winStart by remember { mutableLongStateOf(floorHalfHour(System.currentTimeMillis())) }
    // Táctil: canal cuya programación del día se muestra en lista (al tocar su logo o nombre).
    var dayCh by remember { mutableStateOf<Pair<List<Item>, Int>?>(null) }
    var focused by remember { mutableStateOf<Focused?>(null) }
    var catsFocused by remember { mutableStateOf(false) }
    // Como en TV en directo: la categoría se abre al ponerse encima (con una pausa corta para poder pasar de largo).
    var pending by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        delay(350)
        sel = p
    }
    val pv = Repo.prefs.version
    val touch = com.suso.miiptv.data.Device.touch
    // Móvil, o tablet en vertical: categorías como píldoras arriba y la parrilla a todo el ancho.
    val gcfg = androidx.compose.ui.platform.LocalConfiguration.current
    val phoneW = touch && gcfg.screenWidthDp < 600
    val compact = touch && (gcfg.screenWidthDp < 600 || gcfg.screenHeightDp > gcfg.screenWidthDp)

    // Volver al menú de grupos: siempre al grupo abierto, no al que quede a la altura del cursor.
    val catsState = rememberLazyListState()
    val catReqs = remember { HashMap<String, androidx.compose.ui.focus.FocusRequester>() }
    val menuScope = rememberCoroutineScope()
    // ▶ desde el menú: a la fila del último canal usado si se ve, si no a la primera visible (no a la que quede a la misma altura).
    val gridEntry = remember { arrayOfNulls<() -> androidx.compose.ui.focus.FocusRequester>(1) }
    fun toMenu() {
        val i = cats.indexOfFirst { it.id == sel }
        menuScope.launch {
            if (i >= 0 && catsState.layoutInfo.visibleItemsInfo.none { it.index == i }) catsState.scrollToItem(maxOf(0, i - 3))
            kotlinx.coroutines.delay(30)
            catReqs[sel]?.let { runCatching { it.requestFocus() } }
        }
    }
    dayCh?.let { (l, i) ->
        ChannelDaySheet(l[i], now, pv, onPick = { e -> pick(ctx, l, i, l[i], e, now); if (e == null || (e.start <= now && e.end > now)) dayCh = null }) { dayCh = null }
    }
    Row(Modifier.fillMaxSize()) {
        // Categorías (en el móvil, como píldoras arriba)
        if (!compact) LazyColumn(
            state = catsState,
            modifier = Modifier.width(250.dp).fillMaxHeight().onFocusChanged { catsFocused = it.hasFocus }
                // Tras saltar al menú manteniendo ◀, las repeticiones de ◀ no hacen nada.
                .onPreviewKeyEvent { e ->
                    e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0
                },
            contentPadding = PaddingValues(start = 22.dp, end = 6.dp, top = 14.dp, bottom = 24.dp),
        ) {
            itemsIndexed(cats, key = { i, c -> "$i:${c.id}" }) { _, c ->
                val cfr = remember { androidx.compose.ui.focus.FocusRequester() }
                androidx.compose.runtime.DisposableEffect(c.id, cfr) {
                    catReqs[c.id] = cfr
                    onDispose { if (catReqs[c.id] === cfr) catReqs.remove(c.id) }
                }
                Box(Modifier.focusRequester(cfr).focusProperties {
                    right = gridEntry[0]?.invoke().orDefault()
                }) {
                CategoryRow(
                    name = catLabel(c.name),
                    selected = c.id == sel,
                    focusKey = "guide_cat_${c.id}",
                    onFocused = { pending = c.id },
                    cursorInMenu = catsFocused,
                ) { sel = c.id }
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight().padding(end = if (compact) 6.dp else 24.dp)) {
            if (compact) CategoryChips(cats, sel) { id -> sel = id }
            InfoPanel(focused, now, touch)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Táctil: «Ahora» flotante, solo cuando te has alejado de la hora actual.
                if (touch) androidx.compose.animation.AnimatedVisibility(
                    visible = winStart != floorHalfHour(now),
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).zIndex(2f).padding(end = 14.dp, bottom = 18.dp),
                ) {
                    Text(
                        "Ahora", color = Color.Black, fontSize = Sz.small, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(C.gold)
                            .clickable { winStart = floorHalfHour(now) }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
                val chW = if (phoneW) 116.dp else if (compact) 170.dp else 200.dp
                val areaW = maxWidth - chW
                val perMin: Dp = areaW / WIN_MIN.toFloat()
                val perMinPx = with(androidx.compose.ui.platform.LocalDensity.current) { perMin.toPx() }
                // Táctil: toda la parrilla se desliza con el dedo, con inercia al soltar.
                val flingScope = rememberCoroutineScope()
                val flingJob = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
                fun moveBy(px: Float): Boolean {
                    val arch = channels.orEmpty().maxOfOrNull { it.archive } ?: 0
                    val base = floorHalfHour(System.currentTimeMillis())
                    val min = if (arch > 0) base - minOf(arch, 1) * 24 * 3600_000L else base
                    val n = (winStart - (px / perMinPx * 60_000f).toLong()).coerceIn(min, base + 24 * 3600_000L)
                    val moved = n != winStart
                    winStart = n
                    return moved
                }
                val dragMod = if (!touch) Modifier else Modifier.pointerInput(perMinPx, channels) {
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { flingJob[0]?.cancel(); tracker.resetTracking() },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            flingJob[0] = flingScope.launch {
                                var last = 0f
                                androidx.compose.animation.core.AnimationState(0f, v).animateDecay(androidx.compose.animation.core.exponentialDecay(frictionMultiplier = 1.5f)) {
                                    val d = value - last
                                    last = value
                                    if (!moveBy(d)) cancelAnimation()
                                }
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        moveBy(amount)
                    }
                }
                val winEnd = winStart + WIN_MIN * 60_000L
                Column(Modifier.fillMaxSize().then(dragMod)) {
                    TimeHeader(winStart, chW, perMin, now)
                    val list = channels
                    when {
                        list == null -> SkeletonGrid(Kind.LIVE)
                        list.isEmpty() -> EmptyText("No hay canales en esta categoría.")
                        else -> {
                            val state = rememberLazyListState()
                            // Casilla «en emisión» de cada fila: al subir o bajar, el cursor va siempre a lo que se emite ahora.
                            val liveFrs = remember(list) { HashMap<Int, androidx.compose.ui.focus.FocusRequester>() }
                            val lastRow = remember(list) { intArrayOf(-1) }
                            gridEntry[0] = {
                                val vis = state.layoutInfo.visibleItemsInfo.map { it.index }
                                val i = if (lastRow[0] in vis) lastRow[0] else vis.firstOrNull()
                                i?.let { liveFrs[it] } ?: androidx.compose.ui.focus.FocusRequester.Default
                            }
                            fun jump(to: Int): Boolean {
                                val fr = liveFrs[to] ?: return false
                                return runCatching { fr.requestFocus() }.isSuccess
                            }
                            LazyColumn(
                                state = state,
                                modifier = Modifier.fillMaxSize().focusProperties {
                                    exit = { d ->
                                        if (d == androidx.compose.ui.focus.FocusDirection.Left) catReqs[sel].orDefault()
                                        else androidx.compose.ui.focus.FocusRequester.Default
                                    }
                                },
                            ) {
                                itemsIndexed(list, key = { _, ch -> ch.key }) { i, ch ->
                                    GuideRow(
                                        ch = ch,
                                        chW = chW,
                                        compact = compact,
                                        perMin = perMin,
                                        winStart = winStart,
                                        winEnd = winEnd,
                                        onChannelTap = if (touch) ({ dayCh = list to i }) else null,
                                        now = now,
                                        pv = pv,
                                        onFocus = { e, loading -> focused = Focused(ch, e, loading); lastRow[0] = i },
                                        onShift = { delta ->
                                            val min = minWindow(ch, now)
                                            val max = floorHalfHour(now) + 24 * 3600_000L
                                            val n = (winStart + delta).coerceIn(min, max)
                                            if (n != winStart) {
                                                winStart = n
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                        onPick = { e -> pick(ctx, list, i, ch, e, now) },
                                        toMenu = { if (!compact) toMenu() },
                                        liveFr = remember(ch.key) { androidx.compose.ui.focus.FocusRequester() }.also { liveFrs[i] = it },
                                        // Mantener ▲ en la primera fila no se sale al menú de arriba (una pulsación suelta, sí).
                                        onVertical = { d, repeat -> jump(i + d) || (repeat && i + d < 0) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Hasta dónde se puede ir hacia atrás: lo que guarde el canal (máx. 1 día) o, si no guarda nada, hasta ahora. */
private fun minWindow(ch: Item, now: Long): Long {
    val base = floorHalfHour(now)
    return if (ch.archive > 0) base - minOf(ch.archive, 1) * 24 * 3600_000L else base
}

private fun pick(ctx: Context, list: List<Item>, i: Int, ch: Item, e: EpgEntry?, now: Long) {
    when {
        e == null || (e.start <= now && e.end > now) -> Actions.playLive(ctx, list, i)
        e.start > now -> {
            val on = Repo.prefs.toggleReminder(Prefs.Reminder(ch, e.title, e.start))
            Actions.toast(ctx, if (on) "Te avisaré cuando empiece «${e.title}»" else "Recordatorio quitado")
        }
        // El proveedor dice que ese programa no está grabado: no se intenta (daría error 404).
        e.archived == false -> Actions.toast(ctx, "Este programa no está grabado en el servidor")
        else -> {
            val url = Repo.catchupUrl(ch, e)
            if (url == null) {
                Actions.toast(ctx, "Este canal no guarda los programas ya emitidos")
            } else {
                PlayQueue.start(
                    ctx,
                    listOf(PlayItem("CU|${ch.key}|${e.start}", e.title, "${ch.name} · ${HM.format(Date(e.start))}", ch.image, url, false)),
                    0, 0,
                )
            }
        }
    }
}

@Composable
private fun InfoPanel(f: Focused?, now: Long, touch: Boolean = false) {
    Column(Modifier.fillMaxWidth().height(if (touch) 104.dp else 118.dp).padding(start = 14.dp, top = if (touch) 8.dp else 14.dp, end = 10.dp)) {
        if (f == null) {
            Text("Guía de programación", color = Color.White, fontSize = if (touch) Sz.sub else Sz.title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                if (touch) "Toca un programa para ver su información y vuelve a tocarlo para verlo, poner un recordatorio o verlo desde el principio (si el canal lo guarda)."
                else "OK sobre lo que se emite ahora: ver el canal · sobre un programa futuro: recordatorio · sobre uno pasado: verlo (si el canal lo guarda)",
                color = C.text2, fontSize = Sz.small, maxLines = 2,
            )
            return@Column
        }
        val e = f.e
        Text(e?.title ?: f.ch.name, color = C.gold, fontSize = Sz.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        val whenTxt = if (e == null) (if (f.loading) "Cargando la guía…" else "Sin guía") else {
            val day = if (DAY.format(Date(e.start)) != DAY.format(Date(now))) DAY.format(Date(e.start)) + " · " else ""
            val state = when {
                e.start <= now && e.end > now -> "  ·  EN EMISIÓN"
                e.end <= now && f.ch.archive > 0 && e.archived == false -> "  ·  ya emitido · no está grabado"
                e.end <= now && f.ch.archive > 0 -> "  ·  ya emitido · OK para verlo"
                e.end <= now -> "  ·  ya emitido"
                Repo.prefs.hasReminder("${f.ch.key}@${e.start}") -> "  ·  con recordatorio"
                else -> ""
            }
            "$day${HM.format(Date(e.start))} – ${HM.format(Date(e.end))}  ·  ${f.ch.name}$state"
        }
        Text(whenTxt, color = Color.White, fontSize = Sz.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        e?.desc?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = C.text2, fontSize = Sz.small, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TimeHeader(winStart: Long, chW: Dp, perMin: Dp, now: Long) {
    Box(Modifier.fillMaxWidth().height(30.dp)) {
        Text(
            relDay(winStart + WIN_MIN * 30_000L), color = C.text2, fontSize = Sz.small,
            modifier = Modifier.padding(start = 14.dp, top = 6.dp),
        )
        // Marcas cada media hora (en táctil la franja puede empezar a cualquier minuto al deslizarla).
        Box(Modifier.padding(start = chW).fillMaxWidth().height(30.dp).clipToBounds()) {
            var t = floorHalfHour(winStart)
            while (t < winStart + WIN_MIN * 60_000L) {
                val m = (t - winStart) / 60_000f
                // A medianoche, el nombre del día en verde para que se note el cambio.
                val midnight = HM.format(Date(t)) == "00:00"
                if (m > -20f) Text(
                    if (midnight) "${relDay(t)} 00:00" else HM.format(Date(t)),
                    color = if (midnight) C.gold else Color.White, fontSize = Sz.small, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.offset(x = perMin * m + 6.dp, y = 6.dp),
                )
                t += HALF_HOUR
            }
        }
        val nx = (now - winStart) / 60_000f
        if (nx in 0f..WIN_MIN.toFloat()) {
            Box(Modifier.offset(x = chW + perMin * nx - 1.dp, y = 22.dp).width(2.dp).height(8.dp).background(C.gold))
        }
    }
}

@Composable
private fun GuideRow(
    ch: Item,
    chW: Dp,
    compact: Boolean,
    perMin: Dp,
    winStart: Long,
    winEnd: Long,
    now: Long,
    pv: Int,
    onFocus: (EpgEntry?, Boolean) -> Unit,
    onShift: (Long) -> Boolean,
    onPick: (EpgEntry?) -> Unit,
    liveFr: androidx.compose.ui.focus.FocusRequester,
    onVertical: (Int, Boolean) -> Boolean,
    onChannelTap: (() -> Unit)? = null,
    toMenu: () -> Unit,
) {
    var epg by remember(ch.key) { mutableStateOf<List<EpgEntry>?>(null) }
    // ¿Está el cursor en esta fila? Si estaba en la casilla «Cargando…» cuando llega la guía, pasa a lo que se emite ahora.
    var rowFocused by remember { mutableStateOf(false) }
    LaunchedEffect(ch.key) {
        val l = Repo.guideEpg(ch)
        val had = rowFocused
        epg = l
        if (had) {
            delay(60)
            runCatching { liveFr.requestFocus() }
        }
    }
    val logo = remember(ch.key, Logos.version) { Logos.url(ch.name, ch.image) }
    Row(Modifier.fillMaxWidth().height(58.dp).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        // Canal: logo y nombre
        Row(
            Modifier.width(chW).fillMaxHeight()
                .then(if (onChannelTap != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable { onChannelTap() } else Modifier)
                .padding(start = if (compact) 8.dp else 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mismo fondo que en la lista de canales: blanco para los marcados, claro para los logos oscuros
            // y, si no, el de las tarjetas (antes era un gris translúcido para todos los demás).
            var shown by remember(logo) { mutableStateOf(logo) }
            val white = LogoTone.wantsWhite(logo) && shown?.substringBefore('#') == logo?.substringBefore('#')
            val bg = if (logo != null) Color.White else C.card
            Box(
                Modifier.width(if (compact) 48.dp else 62.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                if (logo != null) ChannelLogoImage(logo, Modifier.fillMaxSize().padding(5.dp), fallback = ch.image) { shown = it }
            }
            Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
            Text(ch.name, color = Color(0xFFCFCFCF), fontSize = if (compact) Sz.label else Sz.small, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        }
        // Programas
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).onFocusChanged { rowFocused = it.hasFocus }) {
            val l = epg
            val visible = l?.filter { it.end > winStart && it.start < winEnd }.orEmpty()
            // Casilla a la que va el cursor al subir o bajar: lo que se emite ahora o, si no está en la franja, la primera.
            val target = visible.firstOrNull { it.start <= now && it.end > now } ?: visible.firstOrNull()
            if (l == null || visible.isEmpty()) {
                // Cargando, o sin guía en esta franja: una sola casilla, para que el cursor pueda pasar por la fila
                // (antes, mientras cargaba, la fila no tenía dónde ponerse y el cursor se iba lejos o al menú).
                ProgramCell(
                    title = when {
                        l == null -> "Cargando la guía…"
                        l.isEmpty() -> "Sin guía"
                        else -> "Sin programación en esta franja"
                    },
                    x = 0.dp, w = perMin * WIN_MIN.toFloat(), live = l != null, past = false, reminder = false, archived = false,
                    onFocus = { onFocus(null, l == null) },
                    onLeft = { onShift(-60 * 60_000L) },
                    onRight = { onShift(60 * 60_000L) },
                    leftToMenu = true,
                    toMenu = toMenu,
                    onClick = { onPick(null) },
                    liveFr = liveFr,
                    onVertical = onVertical,
                )
            }
            visible.forEachIndexed { idx, e ->
                key(e.start) {
                    val s = maxOf(e.start, winStart)
                    val en = minOf(e.end, winEnd)
                    val x = perMin * ((s - winStart) / 60_000f)
                    val w = perMin * ((en - s) / 60_000f)
                    val past = e.end <= now
                    ProgramCell(
                        title = e.title,
                        x = x, w = w,
                        live = e.start <= now && e.end > now,
                        past = past,
                        reminder = remember(pv, e.start) { Repo.prefs.hasReminder("${ch.key}@${e.start}") },
                        // ↺ solo si el programa está grabado (o si el proveedor no lo indica y el canal guarda lo emitido).
                        archived = past && ch.archive > 0 && e.archived != false,
                        onFocus = { onFocus(e, false) },
                        // En el primero/último visible, ◀ ▶ mueven la franja horaria en lugar de salir de la fila.
                        onLeft = { if (idx == 0) onShift(-60 * 60_000L) else false },
                        leftToMenu = idx == 0,
                        toMenu = toMenu,
                        onRight = { if (idx == visible.lastIndex) onShift(60 * 60_000L) || true else false },
                        onClick = { onPick(e) },
                        liveFr = if (e === target) liveFr else null,
                        onVertical = onVertical,
                    )
                }
            }
            // Línea de «ahora»
            val nx = (now - winStart) / 60_000f
            if (nx in 0f..WIN_MIN.toFloat()) {
                Box(Modifier.offset(x = perMin * nx - 1.dp).width(2.dp).fillMaxHeight().background(C.gold.copy(alpha = 0.8f)))
            }
        }
    }
}

@Composable
private fun ProgramCell(
    title: String,
    x: Dp,
    w: Dp,
    live: Boolean,
    past: Boolean,
    reminder: Boolean,
    archived: Boolean,
    onFocus: () -> Unit,
    onLeft: () -> Boolean,
    onRight: () -> Boolean,
    onClick: () -> Unit,
    liveFr: androidx.compose.ui.focus.FocusRequester? = null,
    leftToMenu: Boolean = false,
    toMenu: () -> Unit = {},
    onVertical: (Int, Boolean) -> Boolean = { _, _ -> false },
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var leftArmed by remember { mutableStateOf(false) }
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    val click by androidx.compose.runtime.rememberUpdatedState(onClick)
    val bg = when {
        focused -> C.gold
        live -> Color(0xFF1E3A22)
        past -> Color(0xFF111813)
        else -> Color(0xFF18221A)
    }
    Box(
        Modifier
            .offset(x = x)
            .width((w - 3.dp).coerceAtLeast(6.dp))
            .fillMaxHeight()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus() else pressed = false
            }
            .onPreviewKeyEvent { e ->
                when {
                    // Primera casilla: ◀ corto retrocede una hora (o, si no hay más atrás, va al menú de grupos);
                    // ◀ mantenido va directo al menú de grupos.
                    // ◀ mantenido, en cualquier casilla: al menú de grupos (una sola vez; el resto de repeticiones no hacen nada).
                    e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0 -> {
                        leftArmed = false
                        if (e.nativeKeyEvent.repeatCount == 1) toMenu()
                        true
                    }
                    leftToMenu && e.key == Key.DirectionLeft -> {
                        if (e.type == KeyEventType.KeyDown) leftArmed = true
                        else if (e.type == KeyEventType.KeyUp && leftArmed) {
                            leftArmed = false
                            if (!onLeft()) toMenu()
                        }
                        true
                    }
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft -> onLeft()
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight -> onRight()
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown -> onVertical(1, e.nativeKeyEvent.repeatCount > 0)
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp -> onVertical(-1, e.nativeKeyEvent.repeatCount > 0)
                    e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) pressed = true
                        if (e.type == KeyEventType.KeyUp && pressed) {
                            pressed = false
                            onClick()
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(fr)
            .then(if (liveFr != null) Modifier.focusRequester(liveFr) else Modifier)
            .focusable()
            // Táctil: el primer toque lo selecciona (se ve su información), el segundo hace lo mismo que OK.
            .pointerInput(Unit) {
                detectTapGestures { if (focused) click() else runCatching { fr.requestFocus() } }
            }
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(if (live && !focused) Modifier.border(1.dp, C.gold.copy(alpha = 0.45f), RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (reminder) {
                MiniClock(if (focused) Color.Black else C.gold, 13.dp)
                Spacer(Modifier.width(6.dp))
            }
            if (archived) Text("↺ ", fontSize = Sz.small, fontWeight = FontWeight.Bold, color = if (focused) Color.Black else C.gold)
            Text(
                title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = Sz.small,
                fontWeight = if (focused || live) FontWeight.SemiBold else FontWeight.Medium,
                color = when {
                    focused -> Color.Black
                    past -> Color(0xFF8A8A8A)
                    else -> Color.White
                },
            )
        }
    }
}

/** Aviso de recordatorio: cuando está a punto de empezar un programa marcado, permite verlo con OK. */
@Composable
fun ReminderPopup(onVisible: (Boolean) -> Unit = {}, onWatch: (Prefs.Reminder) -> Unit) {
    var current by remember { mutableStateOf<Prefs.Reminder?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            if (current == null) {
                val now = System.currentTimeMillis()
                current = Repo.prefs.reminders().firstOrNull { it.start - now <= 60_000L && now - it.start < 10 * 60_000L }
                if (current != null) onVisible(true)
            }
            delay(15_000)
        }
    }
    val r = current ?: return
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(r.id) {
        delay(80)
        runCatching { fr.requestFocus() }
        delay(45_000)
        if (current?.id == r.id) {
            Repo.prefs.removeReminder(r.id)
            current = null
            onVisible(false)
        }
    }
    Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.BottomEnd) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xF20B140D))
                .border(1.dp, C.gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniClock(C.gold, 16.dp)
                Spacer(Modifier.width(8.dp))
                Text("Empieza ahora", color = C.gold, fontSize = Sz.body, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(r.title, color = Color.White, fontSize = Sz.sub, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(r.channel.name, color = C.text2, fontSize = Sz.body)
            Spacer(Modifier.height(14.dp))
            Row {
                TvButton("Ver ahora", "rem_watch", filled = true, focusRequester = fr) {
                    Repo.prefs.removeReminder(r.id)
                    current = null
                    onVisible(false)
                    onWatch(r)
                }
                Spacer(Modifier.width(10.dp))
                TvButton("Cerrar", "rem_close") {
                    Repo.prefs.removeReminder(r.id)
                    current = null
                    onVisible(false)
                }
            }
        }
    }
}


@Composable
private fun GuidePill(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color(0xFFDDDDDD), fontSize = Sz.small, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F2A21))
            .tvClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}


/** «Hoy», «Mañana», «Ayer» o el día de la semana con el número. */
private fun relDay(t: Long): String {
    fun dayNum(x: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = x }
        return c.get(java.util.Calendar.YEAR) * 1000L + c.get(java.util.Calendar.DAY_OF_YEAR)
    }
    val today = System.currentTimeMillis()
    return when (t) {
        in 0..Long.MAX_VALUE -> when {
            dayNum(t) == dayNum(today) -> "Hoy"
            dayNum(t) == dayNum(today + 24 * 3600_000L) -> "Mañana"
            dayNum(t) == dayNum(today - 24 * 3600_000L) -> "Ayer"
            else -> DAY.format(Date(t)).replaceFirstChar { it.uppercase() }
        }
        else -> ""
    }
}

/** Táctil: programación del día de un canal en lista vertical (se abre tocando su logo o nombre en la guía). */
@Composable
private fun ChannelDaySheet(ch: Item, now: Long, pv: Int, onPick: (EpgEntry?) -> Unit, onClose: () -> Unit) {
    var epg by remember(ch.key) { mutableStateOf<List<EpgEntry>?>(null) }
    LaunchedEffect(ch.key) { epg = runCatching { Repo.guideEpg(ch) }.getOrDefault(emptyList()) }
    val logo = remember(ch.key, Logos.version) { Logos.url(ch.name, ch.image) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0C140E)),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(54.dp).height(38.dp).clip(RoundedCornerShape(6.dp)).background(if (logo != null) Color.White else C.card),
                    contentAlignment = Alignment.Center,
                ) { if (logo != null) ChannelLogoImage(logo, Modifier.fillMaxSize().padding(4.dp), fallback = ch.image) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(ch.name, color = Color.White, fontSize = Sz.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Programación", color = C.text2, fontSize = Sz.small)
                }
                Text(
                    "✕", color = C.text3, fontSize = 20.sp,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onClose() }.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(C.divider))
            val l = epg
            val base = floorHalfHour(now)
            val items = l?.filter { it.end > minWindow(ch, now) && it.start < base + 24 * 3600_000L }.orEmpty()
            when {
                l == null -> EmptyText("Cargando la guía…")
                items.isEmpty() -> EmptyText("Este canal no tiene guía")
                else -> {
                    val liveIdx = items.indexOfFirst { it.start <= now && it.end > now }.coerceAtLeast(0)
                    val st = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, liveIdx - 1))
                    LazyColumn(state = st, contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(items, key = { _, e -> e.start }) { k, e ->
                            val prevDay = if (k == 0) null else relDay(items[k - 1].start)
                            val day = relDay(e.start)
                            if (day != prevDay) Text(
                                day, color = C.gold, fontSize = Sz.small, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = if (k == 0) 4.dp else 14.dp, bottom = 4.dp),
                            )
                            DayRow(ch, e, now, pv) { onPick(e) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRow(ch: Item, e: EpgEntry, now: Long, pv: Int, onClick: () -> Unit) {
    val live = e.start <= now && e.end > now
    val past = e.end <= now
    val archived = past && ch.archive > 0 && e.archived != false
    val reminder = remember(pv, e.start) { Repo.prefs.hasReminder("${ch.key}@${e.start}") }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(10.dp))
            .background(if (live) Color(0xFF1E3A22) else Color.Transparent)
            .clickable(enabled = live || archived || !past) { onClick() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            HM.format(Date(e.start)), color = if (past && !archived) C.text2.copy(alpha = 0.6f) else if (live) C.gold else C.text3,
            fontSize = Sz.small, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                e.title, color = if (past && !archived) C.text2.copy(alpha = 0.7f) else Color.White,
                fontSize = Sz.small, fontWeight = if (live) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (live) {
                val p = ((now - e.start).toFloat() / (e.end - e.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(C.gold))
                }
            }
        }
        val tag = when {
            live -> "▶ Ver"
            archived -> "↺ Ver"
            else -> null
        }
        if (reminder && tag == null) Box(Modifier.padding(start = 8.dp, top = 2.dp)) { MiniClock(C.gold, 14.dp) }
        if (tag != null) Text(tag, color = C.gold, fontSize = Sz.small, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}


/** Reloj minimalista (círculo fino y dos agujas) para los programas con aviso. */
@Composable
private fun MiniClock(color: Color, size: Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.11f
        val r = this.size.minDimension / 2f - stroke / 2f
        val c = center
        drawCircle(color, radius = r, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        val cap = androidx.compose.ui.graphics.StrokeCap.Round
        // Agujas a las 10 y 10 (minutero arriba, horario hacia la derecha).
        drawLine(color, c, androidx.compose.ui.geometry.Offset(c.x, c.y - r * 0.62f), stroke, cap)
        drawLine(color, c, androidx.compose.ui.geometry.Offset(c.x + r * 0.45f, c.y), stroke, cap)
    }
}
