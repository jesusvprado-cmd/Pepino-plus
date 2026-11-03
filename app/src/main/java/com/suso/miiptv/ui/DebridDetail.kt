package com.suso.miiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.suso.miiptv.data.PlayItem
import com.suso.miiptv.data.Repo
import com.suso.miiptv.debrid.AllDebrid
import com.suso.miiptv.debrid.DKind
import com.suso.miiptv.debrid.DTitle
import com.suso.miiptv.debrid.DebridStore
import com.suso.miiptv.debrid.EpMeta
import com.suso.miiptv.debrid.Version
import com.suso.miiptv.player.PlayQueue
import kotlinx.coroutines.delay
import java.util.Locale

/** Ficha de una película o serie de AllDebrid: sinopsis, copias disponibles y capítulos. */
@Composable
fun DebridDetailScreen(key: String) {
    @Suppress("UNUSED_VARIABLE") val v = DebridStore.version
    @Suppress("UNUSED_VARIABLE") val pv = Repo.prefs.version
    val t = DebridStore.byKey(key)
    if (t == null) {
        EmptyText("Este título ya no está en tu cuenta.")
        return
    }
    LaunchedEffect(t.key) { DebridStore.probeFirst(t) }
    val m = DebridStore.meta(t)
    // Versión elegida a mano para un capítulo: (temporada, capítulo) → elegir copia.
    var pickFor by remember { mutableStateOf<Pair<String, List<Version>>?>(null) }
    var onPicked by remember { mutableStateOf<((Version) -> Unit)?>(null) }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        // Imagen de fondo a la derecha, fundida con el negro.
        val bg = m?.backdrop ?: m?.poster
        if (bg != null) Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.62f).fillMaxHeight(0.75f)) {
            AsyncImage(model = bg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(C.bg, C.bg.copy(alpha = 0.6f), Color.Transparent))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, C.bg))))
            if (isNarrowTouch()) Box(Modifier.fillMaxSize().background(C.bg.copy(alpha = 0.6f)))
        }
        val side = if (isNarrowTouch()) 18.dp else 48.dp
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, top = 34.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "head") { Header(t) }
            if (t.kind == DKind.MOVIE) movieBody(t) { title, list, cb -> pickFor = title to list; onPicked = cb }
            else seriesBody(t) { title, list, cb -> pickFor = title to list; onPicked = cb }
        }
        val pf = pickFor
        if (pf != null) {
            val prefsBest = DebridStore.best(pf.second)
            ChoiceDialog(
                pf.first,
                pf.second.map { x -> versionLabel(x, x === prefsBest) to x },
                prefsBest,
                onPick = { x ->
                    pickFor = null
                    x?.let { onPicked?.invoke(it) }
                },
                onClose = { pickFor = null },
            )
        }
    }
}

private fun versionLabel(v: Version, best: Boolean): String =
    (v.tags().joinToString(" · ").ifBlank { "Sin datos" }) + " · " + gb(v.file.size) + if (best) "  ★" else ""

private fun gb(b: Long): String = if (b <= 0) "?" else if (b >= 1L shl 30) String.format(Locale.US, "%.1f GB", b / 1073741824.0) else "${b / 1048576} MB"

@Composable
private fun Header(t: DTitle) {
    val m = DebridStore.meta(t)
    val narrow = isNarrowTouch()
    Column(Modifier.fillMaxWidth(if (narrow) 1f else 0.6f).padding(bottom = 10.dp)) {
        Text(
            DebridStore.displayName(t), color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold,
            lineHeight = 36.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        val line = listOfNotNull(
            (m?.year ?: t.year)?.toString(),
            m?.genres,
            if (t.kind == DKind.SERIES) "${t.episodes.size} temp. · ${t.episodeCount} cap." else null,
        ).joinToString(" · ")
        if (line.isNotBlank()) Text(line, color = C.text2, fontSize = Sz.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        m?.rating?.let {
            Spacer(Modifier.height(2.dp))
            Text("★ $it", color = C.gold, fontSize = Sz.body)
        }
        m?.overview?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFE6E6E6), fontSize = Sz.body, lineHeight = 21.sp, maxLines = if (narrow) 6 else 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

private typealias AskVersion = (String, List<Version>, (Version) -> Unit) -> Unit

// ---------------- Película ----------------

private fun androidx.compose.foundation.lazy.LazyListScope.movieBody(t: DTitle, askVersion: AskVersion) {
    item(key = "buttons") {
        val ctx = LocalContext.current
        val best = DebridStore.best(t.versions)
        val p = Repo.prefs.progress(DebridStore.movieKey(t))
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            if (FocusMemory.restore == null) {
                delay(120)
                runCatching { fr.requestFocus() }
            }
        }
        Row(Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (best != null) {
                TvButton(if (p != null) "Continuar (${fmtTime(p.pos)})" else "Reproducir", "adm_play", filled = true, focusRequester = fr) {
                    PlayQueue.start(ctx, listOf(DebridStore.moviePlay(t, best)), 0, p?.pos ?: 0L)
                }
                if (p != null) TvButton("Desde el principio", "adm_start") {
                    PlayQueue.start(ctx, listOf(DebridStore.moviePlay(t, best)), 0, 0L)
                }
                if (t.versions.size > 1) TvButton("Otra versión", "adm_other") {
                    askVersion("Elegir versión", t.versions) { v -> PlayQueue.start(ctx, listOf(DebridStore.moviePlay(t, v)), 0, p?.pos ?: 0L) }
                }
            }
        }
        best?.let { Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { it.tags().forEach { tg -> TagChip(tg) } } }
    }
    item(key = "vlabel") { GroupLabel(if (t.versions.size == 1) "Archivo" else "Versiones (${t.versions.size})") }
    val sorted = t.versions.sortedByDescending { com.suso.miiptv.debrid.Pick.score(it, DebridStore.prefs) }
    items(sorted, key = { "v_" + it.file.link }) { v ->
        val ctx = LocalContext.current
        val p = Repo.prefs.progress(DebridStore.movieKey(t))
        VersionRow(v, v === sorted.firstOrNull()) {
            PlayQueue.start(ctx, listOf(DebridStore.moviePlay(t, v)), 0, p?.pos ?: 0L)
        }
    }
}

/** Fila de una copia: etiquetas, idiomas, tamaño y nombre del archivo. */
@Composable
private fun VersionRow(v: Version, recommended: Boolean, onClick: () -> Unit) {
    val audio = v.audio.filter { it != "?" }.joinToString(", ") { Version.langName(it) }.ifBlank { "Audio sin indicar" }
    val subs = v.subs.filter { it != "?" }.joinToString(", ") { Version.langName(it) }
    val detail = buildString {
        append("Audio: ").append(audio)
        if (subs.isNotBlank()) append("  ·  Subtítulos: ").append(subs)
        if (!v.probed && AllDebrid.linked && DebridStore.probeOn && com.suso.miiptv.debrid.ReleaseName.ext(v.file.name) == "mkv") append("  (comprobando…)")
        append("\n").append(listOfNotNull(v.info.source, v.info.codec ?: v.probe?.videoCodec?.substringAfterLast('/')).joinToString(" · ").let { if (it.isBlank()) "" else "$it  ·  " })
        append(v.file.name)
    }
    DRow(
        title = (v.tags(withSubs = false).joinToString(" · ").ifBlank { "Calidad sin indicar" }) + if (recommended) "   ★ recomendada" else "",
        subtitle = detail,
        value = gb(v.file.size),
        focusKey = "adv_" + v.file.link,
        onClick = onClick,
    )
}

/** Fila con la línea verde del cursor (como en Ajustes), con pulsación larga opcional. */
@Composable
private fun DRow(
    title: String,
    subtitle: String?,
    value: String?,
    focusKey: String,
    progress: Float? = null,
    image: String? = null,
    onLong: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .memFocus(focusKey) { focused = it }
            .tvClick(onLong, onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF1B261D) else Color(0xCC111811))
            .heightIn(min = 60.dp)
            .padding(end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusBar(focused, height = 30.dp, color = PaneFocus)
        Spacer(Modifier.width(6.dp))
        if (image != null && !isCompactPhone()) {
            Box(Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(8.dp)).background(C.card)) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (progress != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x99000000))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(C.gold))
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, color = if (focused) PaneFocus else Color.White, fontSize = Sz.emph,
                fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = C.text2, fontSize = Sz.small, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null && (image == null || isCompactPhone())) {
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x44FFFFFF))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(C.gold))
                }
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(value, color = C.gold, fontSize = Sz.body, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

// ---------------- Serie ----------------

private val seasonOf = HashMap<String, Int>()

private fun androidx.compose.foundation.lazy.LazyListScope.seriesBody(t: DTitle, askVersion: AskVersion) {
    item(key = "series") {
        val ctx = LocalContext.current
        val seasons = t.episodes.keys.toList()
        var season by remember(t.key) { mutableStateOf(seasonOf[t.key] ?: continueSeason(t) ?: seasons.firstOrNull() ?: 1) }
        var names by remember(t.key, season) { mutableStateOf<Map<Int, EpMeta>>(emptyMap()) }
        LaunchedEffect(t.key, season, DebridStore.meta(t)) { names = DebridStore.episodes(t, season) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            if (FocusMemory.restore == null) {
                delay(120)
                runCatching { fr.requestFocus() }
            }
        }
        fun playFrom(s: Int, e: Int, override: Version? = null, fromStart: Boolean = false) {
            val q = DebridStore.episodeQueue(t).toMutableList()
            val k = DebridStore.episodeKey(t, s, e)
            val i = q.indexOfFirst { it.key == k }
            if (i < 0) return
            if (override != null) q[i] = q[i].copy(url = AllDebrid.lazyUrl(override.file.link))
            val pos = if (fromStart) 0L else Repo.prefs.progress(k)?.pos ?: 0L
            PlayQueue.start(ctx, q, i, pos)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Botón principal: seguir por donde ibas (o empezar).
            val next = nextEpisode(t)
            Row(Modifier.padding(vertical = 6.dp).horizontalScroll(rememberScrollState())) {
                if (next != null) {
                    val (s, e, started) = next
                    TvButton(
                        (if (started) "Continuar " else "Reproducir ") + "T$s E$e", "ads_play", filled = true, focusRequester = fr,
                    ) { playFrom(s, e) }
                }
            }
            // Temporadas.
            if (seasons.size > 1) Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                seasons.forEach { s ->
                    TabChip("Temporada $s", selected = s == season, focusKey = "ads_season_$s") {
                        season = s
                        seasonOf[t.key] = s
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
            GroupLabel(if (seasons.size > 1) "Temporada $season" else "Capítulos")
            val eps = t.episodes[season].orEmpty()
            eps.forEach { (e, vs) ->
                val k = DebridStore.episodeKey(t, season, e)
                val best = DebridStore.best(vs)
                val p = Repo.prefs.progress(k)
                val watched = Repo.prefs.isWatched(k)
                val em = names[e]
                val endEp = vs.firstNotNullOfOrNull { it.info.episodeEnd }
                val title = "$e" + (endEp?.let { "-$it" } ?: "") + ". " + (em?.name ?: "Capítulo $e") + if (watched) "  ✓" else ""
                val sub = listOfNotNull(
                    best?.tags()?.joinToString(" · ")?.ifBlank { null },
                    if (vs.size > 1) "${vs.size} versiones (mantén OK para elegir)" else null,
                    em?.overview,
                ).joinToString("\n").ifBlank { null }
                DRow(
                    title = title,
                    subtitle = sub,
                    value = null,
                    focusKey = "ade_${t.key}_${season}_$e",
                    progress = p?.let { if (it.dur > 0) it.pos.toFloat() / it.dur else null },
                    image = em?.still,
                    onLong = {
                        if (vs.size > 1) askVersion("T$season E$e · elegir versión", vs) { v -> playFrom(season, e, v) }
                        else if (p != null) playFrom(season, e, fromStart = true)
                    },
                ) { playFrom(season, e) }
            }
        }
    }
}

/** Temporada del capítulo que estabas viendo (para abrir la serie por ahí). */
private fun continueSeason(t: DTitle): Int? = nextEpisode(t)?.first

/** Siguiente capítulo: el que está a medias, o el primero sin ver. (temporada, capítulo, ya empezado) */
private fun nextEpisode(t: DTitle): Triple<Int, Int, Boolean>? {
    val prefix = "ADE|${t.key}|"
    Repo.prefs.progressList().firstOrNull { it.play.key.startsWith(prefix) }?.let { p ->
        val m = Regex("""S(\d+)E(\d+)$""").find(p.play.key)
        if (m != null) return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), p.pos > 0)
    }
    t.episodes.forEach { (s, eps) ->
        eps.keys.forEach { e -> if (!Repo.prefs.isWatched(DebridStore.episodeKey(t, s, e))) return Triple(s, e, false) }
    }
    val s = t.episodes.keys.firstOrNull() ?: return null
    val e = t.episodes[s]?.keys?.firstOrNull() ?: return null
    return Triple(s, e, false)
}

@Suppress("unused")
private fun unusedPlay(p: PlayItem) = p
