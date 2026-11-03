package com.suso.miiptv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.Normalizer

data class SearchResult(val live: List<Item>, val movies: List<Item>, val series: List<Item>)

/** Punto único de acceso a los datos. Se usa siempre desde el hilo principal. */
object Repo {
    lateinit var prefs: Prefs
        private set
    var config: Config? = null
        private set
    var source: Source? = null
        private set

    /** Cambia al recargar o cambiar de lista, para que las pantallas vuelvan a cargar. */
    var dataVersion by mutableIntStateOf(0)
        private set

    private val catCache = HashMap<Kind, List<Category>>()
    private val itemCache = HashMap<String, List<Item>>()
    private val epgCache = HashMap<String, Pair<Long, List<EpgEntry>>>()
    private var searchIndex: List<Pair<String, Item>>? = null
    private val searchMutex = Mutex()

    private val ADULT = Regex(
        "(adult|adulto|xxx|\\+ ?18|18 ?\\+|\\bporn|erotic|erótic|erotik|\\bsexy?\\b|\\bhot\\b|playboy|hustler|brazzers|onlyfans)",
        RegexOption.IGNORE_CASE,
    )

    fun isAdult(name: String): Boolean = ADULT.containsMatchIn(name)

    fun isReady() = ::prefs.isInitialized

    fun init(ctx: Context) {
        if (isReady()) return
        DiskCache.init(ctx.applicationContext)
        com.suso.miiptv.debrid.DebridStore.init(ctx.applicationContext)
        prefs = Prefs(ctx.applicationContext)
        prefs.loadConfig()?.let { activate(it, createSource(it)) }
    }

    fun createSource(cfg: Config): Source = if (cfg.type == "xtream") XtreamSource(cfg) else M3uSource(cfg)

    private fun activate(cfg: Config, src: Source) {
        config = cfg
        Http.userAgent = cfg.userAgent.ifBlank { Http.DEFAULT_UA }
        source = src
        clear()
    }

    fun save(cfg: Config, src: Source = createSource(cfg)) {
        // Lista nueva o cambiada: lo guardado de la anterior ya no sirve.
        DiskCache.clear()
        prefs.saveConfig(cfg)
        activate(cfg, src)
    }

    /** Vuelve a descargar la lista desde cero (también lo guardado en el aparato). */
    fun reload() {
        DiskCache.clear()
        config?.let { activate(it, createSource(it)) }
    }

    private fun clear() {
        catCache.clear()
        itemCache.clear()
        epgCache.clear()
        searchIndex = null
        dataVersion++
    }

    private fun src(): Source = source ?: throw IOException("No hay ninguna lista configurada.")

    /** Todas las categorías, incluidas las ocultas (para la pantalla de Ajustes). */
    suspend fun allCategories(kind: Kind): List<Category> {
        catCache[kind]?.let { return it }
        val l = src().categories(kind)
        // Las categorías nuevas con nombre de adultos se ocultan solas (una única vez).
        val keys = l.map { prefs.catKey(kind, it.id) }
        val fresh = prefs.unseen(keys).toSet()
        if (fresh.isNotEmpty()) {
            val adult = l.filter { prefs.catKey(kind, it.id) in fresh && isAdult(it.name) }.map { prefs.catKey(kind, it.id) }
            if (adult.isNotEmpty()) prefs.setHidden(adult, true)
            prefs.markSeen(fresh)
        }
        catCache[kind] = l
        return l
    }

    /** Categorías visibles. */
    suspend fun categories(kind: Kind): List<Category> =
        allCategories(kind).filter { !prefs.isHidden(kind, it.id) }

    fun visible(item: Item): Boolean = item.categoryId.isEmpty() || !prefs.isHidden(item.kind, item.categoryId)

    suspend fun items(kind: Kind, cat: String?): List<Item> {
        val k = "${kind.name}|${cat ?: "*"}"
        itemCache[k]?.let { return it }
        val everything = itemCache["${kind.name}|*"]
        val raw = if (cat != null && everything != null) everything.filter { it.categoryId == cat } else src().items(kind, cat)
        // Canales: nombre sin «Movistar» delante de los autonómicos y familias numeradas en orden (1, 2, 3...).
        val l = if (kind == Kind.LIVE) withContext(Dispatchers.Default) { ChannelNames.tidy(raw, sortGroups = cat != null) } else raw
        // Sección de cada canal: algunos logos se retocan según la sección (Liga RFEF, Fanplay...).
        if (kind == Kind.LIVE) runCatching {
            val names = allCategories(kind).associate { it.id to it.name }
            Logos.setSections(l.associate { it.name to (names[it.categoryId] ?: "") })
        }
        itemCache[k] = l
        // Logos de la categoría calculados por adelantado, en segundo plano: al pintar las tarjetas ya están
        // (en la Mi Box se notaba un tirón al abrir categorías grandes).
        if (kind == Kind.LIVE && cat != null) repoScope.launch(Dispatchers.Default) {
            for (it in l) runCatching { Logos.url(it.name, it.image) }
        }
        return l
    }

    suspend fun latest(kind: Kind): List<Item> {
        val k = "${kind.name}|__recent"
        val sorted = itemCache[k] ?: run {
            allCategories(kind)
            val everything = items(kind, null)
            val s = withContext(Dispatchers.Default) { everything.sortedByDescending { it.added } }
            itemCache[k] = s
            s
        }
        return withContext(Dispatchers.Default) { sorted.asSequence().filter { visible(it) }.take(300).toList() }
    }

    fun favorites(kind: Kind): List<Item> = prefs.favorites().filter { it.kind == kind && visible(it) }
        .map { if (it.kind == Kind.LIVE) it.copy(name = ChannelNames.display(it.name)) else it }

    suspend fun movieDetail(item: Item): MovieDetail = src().movieDetail(item)

    suspend fun seriesDetail(item: Item): SeriesDetail = src().seriesDetail(item)

    suspend fun epg(item: Item): List<EpgEntry> {
        if (!prefs.epgEnabled || item.kind != Kind.LIVE) return emptyList()
        val now = System.currentTimeMillis()
        val c = epgCache[item.key]
        val raw = if (c != null && now - c.first < 10 * 60_000L) {
            c.second
        } else {
            val provider = suspend {
                try {
                    // Primero la guía completa (un solo archivo para todos los canales); si el proveedor no la da,
                    // la de cada canal, salvo si el servidor ha pedido esperar.
                    providerXml(item)?.filter { it.end > now }
                        ?: if (Http.busy()) emptyList() else src().epg(item)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    ErrorLog.once("epg-proveedor", "Guía del proveedor: ${e.message ?: e.javaClass.simpleName}")
                    emptyList()
                }
            }
            val external = suspend {
                try {
                    ExternalEpg.epg(item)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    ErrorLog.once("epg-externa", "Guía externa: ${e.message ?: e.javaClass.simpleName}")
                    emptyList()
                }
            }
            val l = when (prefs.epgMode) {
                "external" -> external()
                "both" -> provider().ifEmpty { external() }
                else -> provider()
            }
            epgCache[item.key] = now to l
            l
        }
        // Ajuste de hora elegido en Ajustes → Guía.
        val shift = prefs.epgOffset * 60_000L
        val l = if (shift == 0L) raw else raw.map { it.copy(start = it.start + shift, end = it.end + shift) }
        return l.filter { it.end > now }
    }

    /** Guía completa del proveedor para este canal; null si no está disponible. */
    private suspend fun providerXml(item: Item): List<EpgEntry>? {
        val url = src().xmltvUrl() ?: return null
        return ProviderEpg.entries(item, url)
    }

    /** Vuelve a pedir la guía (Ajustes → Guía → Actualizar). */
    fun refreshEpg() {
        epgCache.clear()
        ExternalEpg.reset()
        ProviderEpg.reset()
        synchronized(guideCache) { guideCache.clear() }
    }

    fun streamUrl(item: Item): String = item.url ?: src().streamUrl(item)

    private val guideCache = HashMap<String, Pair<Long, List<EpgEntry>>>()

    /** Programación para la guía en parrilla: la completa del proveedor (si la da) o, si no, la de siempre. */
    suspend fun guideEpg(item: Item): List<EpgEntry> {
        if (!prefs.epgEnabled || item.kind != Kind.LIVE) return emptyList()
        val now = System.currentTimeMillis()
        synchronized(guideCache) { guideCache[item.key] }?.let { if (now - it.first < 15 * 60_000L) return it.second }
        // Si ya se está pidiendo (la carga anticipada de la guía y la fila en pantalla a la vez), se espera a esa.
        val running = guideInFlight[item.key]
        if (running != null) return running.await()
        val job = repoScope.async {
            try {
                loadGuide(item)
            } finally {
                guideInFlight.remove(item.key)
            }
        }
        guideInFlight[item.key] = job
        return job.await()
    }

    private val guideInFlight = HashMap<String, kotlinx.coroutines.Deferred<List<EpgEntry>>>()

    /** Para las cargas compartidas: siguen aunque la pantalla que las pidió ya no esté (el resultado queda guardado). */
    private val repoScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)

    private suspend fun loadGuide(item: Item): List<EpgEntry> {
        val now = System.currentTimeMillis()
        val full = try {
            when {
                prefs.epgMode == "external" -> emptyList()
                // Guía completa en un solo archivo; si no la hay, la de cada canal (salvo si el servidor pide esperar).
                else -> providerXml(item) ?: if (Http.busy()) emptyList() else src().fullEpg(item)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ErrorLog.once("guia-completa", "Guía en parrilla: ${e.message ?: e.javaClass.simpleName}")
            emptyList()
        }
        val shift = prefs.epgOffset * 60_000L
        val l = (if (full.isNotEmpty()) full.map { it.copy(start = it.start + shift, end = it.end + shift) } else epg(item))
            .filter { it.end > now - 26 * 3600_000L }
        synchronized(guideCache) { guideCache[item.key] = now to l }
        return l
    }

    /**
     * ¿Guarda de verdad el servidor lo emitido en este canal? Lo dice la guía completa: si el proveedor marca
     * cada programa (has_archive) y ninguno de los ya emitidos está grabado, no lo guarda aunque el canal diga
     * que sí. null = el proveedor no lo indica (entonces se hace caso a lo que dice el canal).
     */
    suspend fun archiveWorks(ch: Item): Boolean? {
        if (ch.archive <= 0) return false
        val now = System.currentTimeMillis()
        // La guía completa (XMLTV) no dice qué está grabado: se mira la de este canal (una sola petición, guardada 2 h).
        val flags = try {
            src().fullEpg(ch).filter { it.end <= now }.mapNotNull { it.archived }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        }
        return if (flags.isEmpty()) null else flags.any { it }
    }

    /** Dirección para ver un programa ya emitido o empezar uno desde el principio (null si no se puede). */
    fun catchupUrl(item: Item, e: EpgEntry): String? {
        val shift = prefs.epgOffset * 60_000L
        val start = e.start - shift
        val minutes = ((e.end - e.start) / 60_000L).toInt() + 2
        val all = try {
            src().catchupUrls(item, start, minutes)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ErrorLog.add("Ver desde el principio: ${e.message ?: e.javaClass.simpleName}")
            emptyList()
        }
        // Si el servidor rechaza una, el reproductor prueba la siguiente (ver [nextCatchup]).
        for (i in 0 until all.size - 1) catchupNext[all[i]] = all[i + 1]
        return all.firstOrNull()
    }

    private val catchupNext = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Siguiente dirección que probar para un programa emitido cuando el servidor rechaza [url]. */
    fun nextCatchup(url: String): String? = catchupNext[url]

    fun playItem(item: Item): PlayItem = PlayItem(
        key = item.key,
        title = item.name,
        image = item.image,
        url = streamUrl(item),
        live = item.kind == Kind.LIVE,
        channel = if (item.kind == Kind.LIVE) item else null,
    )

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    suspend fun search(q: String): SearchResult {
        val idx = searchMutex.withLock {
            searchIndex ?: run {
                val everything = ArrayList<Item>()
                for (k in Kind.values()) {
                    try {
                        allCategories(k)
                        everything.addAll(items(k, null))
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        // Si un tipo falla, se busca en los demás.
                    }
                }
                val built = withContext(Dispatchers.Default) { everything.map { norm(it.name) to it } }
                searchIndex = built
                built
            }
        }
        val nq = norm(q.trim())
        val found = withContext(Dispatchers.Default) {
            idx.filter { it.first.contains(nq) && visible(it.second) }.map { it.second }
        }
        return SearchResult(
            live = found.filter { it.kind == Kind.LIVE }.take(60),
            movies = found.filter { it.kind == Kind.MOVIE }.take(60),
            series = found.filter { it.kind == Kind.SERIES }.take(60),
        )
    }
}
