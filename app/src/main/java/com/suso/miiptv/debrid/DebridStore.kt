package com.suso.miiptv.debrid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.suso.miiptv.data.ErrorLog
import com.suso.miiptv.data.Http
import com.suso.miiptv.data.PlayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale

/** Ficha de una película o serie (de TMDB, o de Cinemeta si no hay clave de TMDB). */
data class DMeta(
    val title: String?,
    val year: Int?,
    val poster: String?,
    val backdrop: String?,
    val overview: String?,
    val rating: String?,
    val genres: String?,
    /** "tmdb:123" o "imdb:tt123". */
    val id: String?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        putOpt("t", title); putOpt("y", year); putOpt("p", poster); putOpt("b", backdrop)
        putOpt("o", overview); putOpt("r", rating); putOpt("g", genres); putOpt("id", id)
    }

    companion object {
        fun fromJson(o: JSONObject) = DMeta(
            o.optString("t").ifBlank { null }, o.optInt("y").takeIf { it > 0 }, o.optString("p").ifBlank { null },
            o.optString("b").ifBlank { null }, o.optString("o").ifBlank { null }, o.optString("r").ifBlank { null },
            o.optString("g").ifBlank { null }, o.optString("id").ifBlank { null },
        )
    }
}

data class EpMeta(val name: String?, val overview: String?, val still: String?)

/** Biblioteca de AllDebrid: lo que hay en la cuenta, agrupado en películas y series, con fichas y pistas. */
object DebridStore {
    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Cambia cuando cambia la biblioteca, las fichas o las pistas leídas (para volver a dibujar). */
    var version by mutableIntStateOf(0)
        private set

    var titles by mutableStateOf<List<DTitle>>(emptyList())
        private set
    var syncing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var lastSync = 0L
        private set

    private var files: List<DFile> = emptyList()
    private val meta = HashMap<String, DMeta?>()
    private val metaTs = HashMap<String, Long>()
    private val epMeta = HashMap<String, Map<Int, EpMeta>>()
    private val probes = HashMap<String, MkvTracks.Result?>()

    fun init(ctx: Context) {
        if (::dir.isInitialized) return
        AllDebrid.init(ctx)
        dir = File(ctx.filesDir, "alldebrid").apply { mkdirs() }
    }

    // ---------- Ajustes de la sección ----------

    var filters: DFilters
        get() = DFilters(
            AllDebrid.str("f_q", "all"), AllDebrid.str("f_a", "all"), AllDebrid.str("f_s", "all"), AllDebrid.str("f_o", "recent"),
        )
        set(f) {
            AllDebrid.putStr("f_q", f.quality); AllDebrid.putStr("f_a", f.audio)
            AllDebrid.putStr("f_s", f.subs); AllDebrid.putStr("f_o", f.sort)
            version++
        }

    var prefs: DPrefs
        get() = DPrefs(
            AllDebrid.str("p_audio", "es"),
            AllDebrid.str("p_res", if (com.suso.miiptv.data.Device.touch) "1080" else "2160").toIntOrNull() ?: 2160,
        )
        set(p) {
            AllDebrid.putStr("p_audio", p.audio); AllDebrid.putStr("p_res", p.maxRes.toString())
            version++
        }

    /** Leer las pistas de los MKV (idiomas reales). */
    var probeOn: Boolean
        get() = AllDebrid.bool("probe", true)
        set(v) {
            AllDebrid.putBool("probe", v)
            if (v) startProbing()
        }

    // ---------- Carga ----------

    /** Se llama al abrir la sección: lo guardado al momento y, si es viejo, se actualiza. */
    fun open(force: Boolean = false) {
        if (!AllDebrid.linked) return
        if (!loaded) {
            loaded = true
            scope.launch {
                withContext(Dispatchers.IO) { readDisk() }
                rebuild()
                if (force || System.currentTimeMillis() - lastSync > SYNC_AGE) sync()
            }
            return
        }
        if (force || System.currentTimeMillis() - lastSync > SYNC_AGE) sync()
    }

    private const val SYNC_AGE = 15 * 60_000L

    private var syncJob: Job? = null

    fun sync() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            syncing = true
            error = null
            try {
                val l = withContext(Dispatchers.IO) { fetchAll() }
                files = l
                lastSync = System.currentTimeMillis()
                withContext(Dispatchers.IO) { saveLibrary() }
                rebuild()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message ?: "No se pudo leer la cuenta de AllDebrid"
                ErrorLog.add("AllDebrid: ${error}")
            } finally {
                syncing = false
            }
        }
    }

    /** Al desvincular la cuenta. */
    fun clear() {
        syncJob?.cancel()
        files = emptyList()
        titles = emptyList()
        loaded = false
        lastSync = 0
        runCatching { File(dir, "biblioteca.json").delete() }
        version++
    }

    private fun fetchAll(): List<DFile> {
        val out = ArrayList<DFile>()
        val mags = AllDebrid.magnets().filter { it.ready }
        mags.chunked(40).forEach { chunk ->
            val fm = AllDebrid.magnetFiles(chunk.map { it.id })
            for (m in chunk) {
                val fl = fm[m.id] ?: continue
                for (f in fl) {
                    if (!ReleaseName.isVideo(f.name)) continue
                    val folder = f.path.substringBeforeLast('/', "").substringAfterLast('/')
                    out += DFile(f.link, f.name, folder.ifBlank { if (fl.size > 1) m.name else "" }, f.size, m.date, m.name)
                }
            }
        }
        runCatching { AllDebrid.savedLinks() }.getOrDefault(emptyList()).forEach { s ->
            if (ReleaseName.isVideo(s.name) && out.none { it.link == s.link }) out += DFile(s.link, s.name, "", s.size, s.date, Grouping.SAVED)
        }
        return out
    }

    private suspend fun rebuild() {
        val f = files
        val built = withContext(Dispatchers.Default) { Grouping.build(f) }
        built.forEach { t -> t.versions.forEach { v -> probes[v.file.link]?.let { v.probe = it } } }
        titles = built
        version++
        startMeta()
        startProbing()
    }

    // ---------- Disco ----------

    private fun readDisk() {
        runCatching {
            val o = JSONObject(File(dir, "biblioteca.json").readText())
            lastSync = o.optLong("t")
            val a = o.optJSONArray("files") ?: JSONArray()
            files = (0 until a.length()).mapNotNull { i ->
                val x = a.optJSONObject(i) ?: return@mapNotNull null
                DFile(x.optString("l"), x.optString("n"), x.optString("f"), x.optLong("s"), x.optLong("a"), x.optString("src"))
            }
        }
        runCatching {
            val o = JSONObject(File(dir, "fichas.json").readText())
            o.keys().forEach { k ->
                val x = o.optJSONObject(k) ?: return@forEach
                metaTs[k] = x.optLong("ts")
                meta[k] = if (x.optBoolean("none")) null else DMeta.fromJson(x)
            }
        }
        runCatching {
            val o = JSONObject(File(dir, "pistas.json").readText())
            o.keys().forEach { k -> probes[k] = o.optJSONObject(k)?.let { probeFromJson(it) } }
        }
    }

    private fun saveLibrary() {
        val a = JSONArray()
        files.forEach { f ->
            a.put(JSONObject().put("l", f.link).put("n", f.name).put("f", f.folder).put("s", f.size).put("a", f.added).put("src", f.source))
        }
        writeAtomic("biblioteca.json", JSONObject().put("t", lastSync).put("files", a).toString())
    }

    private fun writeAtomic(name: String, text: String) {
        runCatching {
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, name))
        }
    }

    private var saveJob: Job? = null

    /** Guarda fichas y pistas (agrupando: como mucho cada 3 s). */
    private fun saveLater() {
        if (saveJob?.isActive == true) return
        saveJob = scope.launch {
            delay(3000)
            val m = JSONObject()
            meta.forEach { (k, v) ->
                val o = v?.toJson() ?: JSONObject().put("none", true)
                m.put(k, o.put("ts", metaTs[k] ?: 0L))
            }
            val p = JSONObject()
            probes.forEach { (k, v) -> p.put(k, v?.let { probeToJson(it) } ?: JSONObject().put("x", 1)) }
            val ms = m.toString()
            val ps = p.toString()
            withContext(Dispatchers.IO) {
                writeAtomic("fichas.json", ms)
                writeAtomic("pistas.json", ps)
            }
        }
    }

    private fun trackJson(t: MkvTracks.Track) = JSONArray().put(t.lang).put(t.codec).put(t.name).put(t.channels).put(t.default).put(t.forced)

    private fun probeToJson(r: MkvTracks.Result): JSONObject = JSONObject().apply {
        put("w", r.width); put("h", r.height); putOpt("hdr", r.hdr); putOpt("vc", r.videoCodec)
        put("a", JSONArray().apply { r.audio.forEach { put(trackJson(it)) } })
        put("s", JSONArray().apply { r.subs.forEach { put(trackJson(it)) } })
    }

    private fun probeFromJson(o: JSONObject): MkvTracks.Result? {
        if (o.has("x")) return null
        fun tracks(a: JSONArray?, type: Int) = (0 until (a?.length() ?: 0)).mapNotNull { i ->
            val t = a!!.optJSONArray(i) ?: return@mapNotNull null
            MkvTracks.Track(type, t.optString(0), t.optString(1), t.optString(2), t.optInt(3), t.optBoolean(4), t.optBoolean(5))
        }
        return MkvTracks.Result(
            o.optInt("w"), o.optInt("h"), o.optString("hdr").ifBlank { null }, o.optString("vc").ifBlank { null },
            tracks(o.optJSONArray("a"), 2), tracks(o.optJSONArray("s"), 17),
        )
    }

    // ---------- Fichas (TMDB / Cinemeta) ----------

    fun meta(t: DTitle): DMeta? = meta[t.key]

    fun displayName(t: DTitle): String = meta[t.key]?.title ?: t.parsedName

    fun poster(t: DTitle): String? = meta[t.key]?.poster

    private var metaJob: Job? = null
    private var metaBump: Job? = null

    private fun bumpSoon() {
        if (metaBump?.isActive == true) return
        metaBump = scope.launch {
            delay(1200)
            version++
            saveLater()
        }
    }

    /** Vuelve a buscar todas las fichas (p. ej. al poner la clave de TMDB). */
    fun refreshMeta() {
        meta.clear()
        metaTs.clear()
        epMeta.clear()
        version++
        metaJob?.cancel()
        startMeta()
    }

    private fun startMeta() {
        if (metaJob?.isActive == true) return
        val now = System.currentTimeMillis()
        val todo = titles.filter { t ->
            val ts = metaTs[t.key]
            ts == null || (meta[t.key] == null && now - ts > 3 * 24 * 3600_000L)
        }.sortedByDescending { it.added }
        if (todo.isEmpty()) return
        metaJob = scope.launch {
            val sem = Semaphore(3)
            val jobs = todo.map { t ->
                launch {
                    sem.withPermit {
                        val m = withContext(Dispatchers.IO) { runCatching { Meta.find(t) }.getOrNull() }
                        meta[t.key] = m
                        metaTs[t.key] = System.currentTimeMillis()
                        bumpSoon()
                    }
                }
            }
            jobs.forEach { it.join() }
        }
    }

    /** Nombres e imágenes de los capítulos de una temporada (se piden al abrir la serie). */
    suspend fun episodes(t: DTitle, season: Int): Map<Int, EpMeta> {
        val k = "${t.key}|$season"
        epMeta[k]?.let { return it }
        val m = meta[t.key] ?: return emptyMap()
        val r = withContext(Dispatchers.IO) { runCatching { Meta.season(m, season) }.getOrDefault(emptyMap()) }
        epMeta[k] = r
        return r
    }

    // ---------- Pistas de los MKV ----------

    private var probeJob: Job? = null
    private val priority = ArrayDeque<String>()

    /** Lee primero las pistas de este título (al abrir su ficha). */
    fun probeFirst(t: DTitle) {
        if (!probeOn) return
        t.versions.filter { it.probe == null && it.file.link !in probes && ReleaseName.ext(it.file.name) == "mkv" }
            .forEach { if (it.file.link !in priority) priority.addFirst(it.file.link) }
        startProbing()
    }

    private fun startProbing() {
        if (!probeOn || !AllDebrid.linked || probeJob?.isActive == true) return
        probeJob = scope.launch {
            delay(1500)
            while (isActive && probeOn) {
                val byLink = HashMap<String, List<Version>>()
                titles.forEach { t -> t.versions.forEach { v -> byLink[v.file.link] = (byLink[v.file.link] ?: emptyList()) + v } }
                val next = generateSequence { priority.removeFirstOrNull() }.firstOrNull { it !in probes && it in byLink }
                    ?: titles.sortedByDescending { it.added }.asSequence()
                        .flatMap { it.versions.asSequence() }
                        .firstOrNull { it.file.link !in probes && ReleaseName.ext(it.file.name) == "mkv" }?.file?.link
                    ?: break
                val r: MkvTracks.Result? = try {
                    withContext(Dispatchers.IO) { Probe.read(next) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    if (e is AllDebrid.ApiError) {
                        // Ese archivo no se puede abrir (borrado, etc.): no se vuelve a intentar.
                        probes[next] = null
                        bumpSoon()
                    } else {
                        // Sin conexión o AllDebrid ocupado: se espera y se sigue más tarde.
                        priority.addFirst(next)
                        delay(30_000)
                    }
                    continue
                }
                probes[next] = r
                byLink[next]?.forEach { it.probe = r }
                bumpSoon()
                delay(700)
            }
        }
    }

    // ---------- Para las pantallas ----------

    fun byKey(key: String): DTitle? = titles.firstOrNull { it.key == key }

    /** Títulos de un tipo que pasan los filtros, ya ordenados. */
    fun list(kind: DKind?, f: DFilters): List<DTitle> {
        val base = titles.filter { (kind == null || it.kind == kind) && (!f.active() || it.versions.any { v -> f.matches(v) }) }
        return when (f.sort) {
            "title" -> base.sortedBy { displayName(it).lowercase(Locale.getDefault()) }
            "year" -> base.sortedByDescending { meta[it.key]?.year ?: it.year ?: 0 }
            "size" -> base.sortedByDescending { it.size }
            else -> base.sortedByDescending { it.added }
        }
    }

    /** Copia que se pone por defecto (entre las que pasan los filtros, si alguna los pasa). */
    fun best(l: List<Version>, f: DFilters? = null): Version? {
        val ok = if (f != null && f.active()) l.filter { f.matches(it) }.ifEmpty { l } else l
        return Pick.best(ok, prefs)
    }

    fun search(q: String): List<DTitle> {
        val n = ReleaseName.norm(q)
        if (n.length < 2) return emptyList()
        return titles.filter { t ->
            ReleaseName.norm(displayName(t)).contains(n) || ReleaseName.norm(t.parsedName).contains(n)
        }.sortedByDescending { it.added }.take(60)
    }

    // ---------- Reproducir ----------

    fun movieKey(t: DTitle) = "AD|${t.key}"
    fun episodeKey(t: DTitle, s: Int, e: Int) = "ADE|${t.key}|" + String.format(Locale.US, "S%02dE%02d", s, e)

    fun moviePlay(t: DTitle, v: Version): PlayItem = PlayItem(
        key = movieKey(t),
        title = displayName(t),
        subtitle = v.tags().joinToString(" · ").ifBlank { null },
        image = meta[t.key]?.backdrop ?: poster(t),
        url = AllDebrid.lazyUrl(v.file.link),
        live = false,
    )

    /** Todos los capítulos en orden (con la copia preferida de cada uno). */
    fun episodeQueue(t: DTitle, f: DFilters? = null): List<PlayItem> {
        val out = ArrayList<PlayItem>()
        t.episodes.forEach { (s, eps) ->
            val names = epMeta["${t.key}|$s"].orEmpty()
            eps.forEach { (e, vs) ->
                val v = best(vs, f) ?: return@forEach
                val n = names[e]?.name
                out += PlayItem(
                    key = episodeKey(t, s, e),
                    title = displayName(t),
                    subtitle = "T$s E$e" + (n?.let { " - $it" } ?: ""),
                    image = names[e]?.still ?: meta[t.key]?.backdrop ?: poster(t),
                    url = AllDebrid.lazyUrl(v.file.link),
                    live = false,
                )
            }
        }
        return out
    }

    /** «ADE|clave|S01E02» → la serie y la cola de capítulos (para «Continuar viendo»). */
    fun queueForKey(key: String): Pair<List<PlayItem>, Int>? {
        if (!key.startsWith("ADE|")) return null
        val tk = key.removePrefix("ADE|").substringBeforeLast('|')
        val t = byKey(tk) ?: return null
        val q = episodeQueue(t)
        val i = q.indexOfFirst { it.key == key }
        return if (i >= 0) q to i else null
    }

    /** Carga lo guardado (sin red) si aún no se ha hecho: para reanudar desde Inicio. */
    suspend fun ensureLoaded() {
        if (titles.isNotEmpty() || !AllDebrid.linked) return
        if (!loaded) {
            loaded = true
            withContext(Dispatchers.IO) { readDisk() }
            rebuild()
        }
    }

    internal fun http(url: String, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url).header("User-Agent", "PepinoPlus-Android")
        headers.forEach { (k, v) -> b.header(k, v) }
        Http.client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }

    internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
