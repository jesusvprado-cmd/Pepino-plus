package com.suso.miiptv.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.Request
import org.json.JSONObject
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * Logos de canales en alta calidad sacados de bancos abiertos:
 *  - tv-logo/tv-logos (PNG de 512 px pensados para fondo oscuro), fuente principal.
 *  - iptv-org (miles de canales con sus nombres alternativos), de respaldo.
 * Solo se cambia la imagen; el nombre del canal se muestra siempre tal cual viene en la lista.
 * Solo se usa un logo nuevo si el nombre coincide de forma exacta (tras limpiarlo).
 */
object Logos {
    private const val TV_LOGOS_RAW = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/"
    private const val TV_LOGOS_TREE = "https://api.github.com/repos/tv-logo/tv-logos/git/trees/main?recursive=1"
    private const val TV_LOGOS_JSDELIVR = "https://data.jsdelivr.com/v1/packages/gh/tv-logo/tv-logos@main?structure=flat"
    private const val IPTV_CHANNELS = "https://iptv-org.github.io/api/channels.json"
    private const val IPTV_LOGOS = "https://iptv-org.github.io/api/logos.json"
    private const val MAX_AGE = 7L * 24 * 3600 * 1000
    private const val COMMONS_API = "https://commons.wikimedia.org/w/api.php"
    private const val COMMONS_ROOT = "Category:SVG logos of television channels of Spain"
    /** Wikimedia pide identificarse con un nombre de app propio. */
    const val WIKI_UA = "MiIPTV/1.0 (https://github.com/jesusvprado-cmd/MI-IPTV; Android TV)"

    /** Países de iptv-org que se guardan (los habituales en listas españolas). */
    private val IPTV_COUNTRIES = setOf("ES", "AD", "PT", "GB", "UK", "US", "FR", "IT", "DE", "MX", "AR", "INT")

    /** Prefijo de país en el nombre del canal → carpeta preferida en tv-logos. */
    private val PREFIX_FOLDER = mapOf(
        "es" to "spain", "esp" to "spain", "spa" to "spain",
        "uk" to "united-kingdom", "gb" to "united-kingdom",
        "us" to "united-states", "usa" to "united-states",
        "pt" to "portugal", "por" to "portugal",
        "fr" to "france", "it" to "italy", "de" to "germany",
        "mx" to "mexico", "ar" to "argentina",
    )

    /** Cambia cuando el índice de logos está listo (las pantallas se vuelven a dibujar). */
    var version by mutableIntStateOf(0)
        private set

    /** clave → lista de (carpeta, url) del banco tv-logos (las versiones sin «HD» primero). */
    @Volatile
    private var tvLogos: Map<String, List<Pair<String, String>>> = emptyMap()

    /** Igual que [tvLogos] pero con claves sin la palabra «tv» (respaldo: «Be Mad» ↔ «Be Mad TV»). */
    @Volatile
    private var tvLoose: Map<String, List<Pair<String, String>>> = emptyMap()

    /** clave → url del banco iptv-org. */
    @Volatile
    private var iptvOrg: Map<String, String> = emptyMap()

    /** clave → url de Wikimedia Commons (logos actuales de los canales españoles). */
    @Volatile
    private var commons: Map<String, String> = emptyMap()

    private val cache = HashMap<String, String>()
    private var loading = false
    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var appCtx: Context? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        dir = ctx.applicationContext.filesDir
        if (Repo.prefs.hqLogos) ensureLoaded()
    }

    /** Solo para la prueba automática de logos: prepara el índice al momento, sin hilos ni red. */
    internal fun loadForTest(ctx: Context) {
        appCtx = ctx.applicationContext
        dir = ctx.applicationContext.filesDir
        load()
        synchronized(cache) { cache.clear() }
    }

    fun ensureLoaded() {
        if (loading || !::dir.isInitialized || tvLogos.isNotEmpty()) return
        loading = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { load() }
                synchronized(cache) { cache.clear() }
                version++
            } catch (e: Exception) {
                // Sin datos de logos: se usan los de la lista (y queda apuntado).
                ErrorLog.add("Logos: no se pudo preparar la lista de logos (${e.message ?: e.javaClass.simpleName})")
            } finally {
                loading = false
            }
        }
    }

    /** Logo a mostrar para un canal: el de alta calidad si se encuentra, si no el de la lista. */
    fun url(name: String, original: String?): String? {
        if (!Repo.prefs.hqLogos) return original
        val cached = synchronized(cache) { cache[name] }
        val r = when {
            cached != null -> cached.ifEmpty { original }
            // Los logos elegidos a mano no dependen de que se haya descargado el índice.
            !ready() -> curated(name) ?: original
            else -> find(name).also { synchronized(cache) { cache[name] = it ?: "" } } ?: original
        }
        return r?.let { bySection(name, it) }?.let { if (onWhiteByName(name) && !hasWhite(it)) white(it) else it }
            ?.let { if (darkenByName(name)) darken(it) else it }
    }

    /**
     * Canales con logo blanco que van sobre fondo blanco: lo blanco del logo pasa a negro y lo que tiene color
     * se respeta (TCM, AXN, Star Channel, Eurosport, Nat Geo, BBC Earth, Disney Channel, Mezzo...).
     */
    private val DARKEN_NAMES = Regex(
        "^(tcm|axn( movies| white| black)?|star( channel)?|comedy central|eurosport ?[0-9]*|" +
            "nat ?geo( wild)?|national geographic( wild)?|bbc lifestyle|disney channel|mezzo( live)?)\\b"
    )

    private fun darkenByName(name: String): Boolean {
        val n = Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase()
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        val words = n.split(' ').filter { it.isNotBlank() }.toMutableList()
        while (words.size > 1 && (words.first() in setOf("es", "esp", "spain", "m", "movistar") || words.first().all { it.isDigit() })) words.removeAt(0)
        return DARKEN_NAMES.containsMatchIn(words.joinToString(" "))
    }

    /** Fondo blanco y lo blanco del logo a negro («x=d»), si el logo no tiene ya otro retoque. */
    private fun darken(url: String): String {
        val frag = url.substringAfter('#', "").split('&')
        if (frag.any { it.startsWith("x=") }) return url
        val extra = if ("w=1" in frag) "x=d" else "x=d&w=1"
        return if (url.contains('#')) "$url&$extra" else "$url#$extra"
    }

    private val sections = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Sección (categoría) de cada canal, por nombre. */
    fun setSections(map: Map<String, String>) {
        var changed = false
        for ((k, v) in map) if (v.isNotEmpty() && sections.put(k, v) != v) changed = true
        if (changed) version++
    }

    /**
     * Retoques por sección para los logos de la lista:
     *  - Liga RFEF: colores invertidos (lo rojo pasa a blanco y lo blanco a rojo) sobre fondo blanco.
     *  - Fanplay TV: lo blanco/gris («FAN») pasa a negro y el resto igual, sobre fondo blanco.
     */
    private fun bySection(name: String, url: String): String {
        if (hasWhite(url)) return url
        val sec = Normalizer.normalize(sections[name] ?: return url, Normalizer.Form.NFKD).lowercase()
            .replace(Regex("\\p{Mn}+"), "")
        val x = when {
            Regex("rfef").containsMatchIn(sec) -> "swap"
            // Fanplay: logo rehecho en alta calidad (misma forma y colores).
            Regex("fan ?play").containsMatchIn(sec) -> return OWN_LOGOS + "fanplay.png#w=1"
            else -> return url
        }
        var extra = "x=$x&w=1"
        if (x == "swap") {
            // Número del canal («LIGA RFEF 3 FHD» → 3), en rojo abajo a la derecha.
            val num = Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase().replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("([a-z])(\\d)"), "$1 $2").split(' ')
                .filter { it.isNotBlank() && it !in QUALITY && it !in FEED }
                .lastOrNull { it.toIntOrNull()?.let { n -> n in 1..99 } == true }
            if (num != null) extra += "&b=$num&c=E4002B&p=br"
        }
        return (if (url.contains('#')) "$url&" else "$url#") + extra
    }

    private fun hasWhite(u: String) = u.substringAfter('#', "").split('&').contains("w=1")

    /** Canales que van sobre fondo blanco aunque su logo venga de la lista (Movistar Plus+, FlixOlé, Cosmo, SyFy, FDF, Neox...). */
    private val WHITE_NAMES = Regex("^(r?tve )?(fl?ix ?ole|cosmo|syfy|sy fy|sci ?fi|fdf|factoria de ficcion|neox|a 3 ?series|a3 ?series|atreseries|" +
        "eurosport|gol|teledeporte|tdp|d ?max|nova|mega|ten|baby ?tv|nick|boing|clan|mtv|24 ?h|24 horas|canal 24|" +
        "deportes|amc|odisea|u ?beat|bar ?tv)\\b|" +
        // Autonómicos
        "^(tvg|galicia|telemadrid|la otra|tv ?3|3 ?cat|3 ?24|super ?3|sx ?3|33\\b|esport ?3|canal ?sur|andalucia|etb|eitb|" +
        "a ?punt|aragon|cmm|castilla|tpa|rtpa|canarias|tv ?canaria|television canaria|canal extremadura|extremadura|" +
        "7 ?rm|7 region|la ?7\\b|la ?8\\b|cyl|navarra|natv|ib ?3|la rioja|tvr\\b|8 ?tv|levante|bete?ve|murcia)")

    private fun onWhiteByName(name: String): Boolean {
        val n = Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase()
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("\\+(?=[a-z0-9])"), "+ ")
            .replace(Regex("[^a-z0-9+ ]"), " ").replace(Regex("\\s+"), " ").trim()
        val words = n.split(' ').filter { it.isNotBlank() }.toMutableList()
        while (words.size > 1 && (words.first() in setOf("es", "esp", "spain") || words.first().all { it.isDigit() })) words.removeAt(0)
        val first = words.firstOrNull() ?: return false
        if (first in setOf("m", "m+", "movistar", "movistar+", "mplus")) {
            val rest = words.drop(1).filter { it !in setOf("+", "plus", "por") }.joinToString(" ")
            return rest.isEmpty() || ChannelNames.isOwnMovistar(rest) || Regex("^(los |las )?(romance|baloncesto|basket|primera|1 ?a federacion|federacion|documentales)").containsMatchIn(rest)
        }
        return WHITE_NAMES.containsMatchIn(words.joinToString(" "))
    }

    /** ¿Está cargado el índice de logos? */
    fun ready(): Boolean = tvLogos.isNotEmpty() || iptvOrg.isNotEmpty() || commons.isNotEmpty()

    /** ¿Hay logo de alta calidad para este canal? */
    fun has(name: String): Boolean = ready() && find(name) != null

    private fun pick(list: List<Pair<String, String>>, folder: String): String =
        (list.firstOrNull { it.first == folder } ?: list.firstOrNull { it.first == "spain" } ?: list.first()).second

    /** Marca un logo para que se muestre sobre fondo blanco («…#w=1»). */
    private fun white(url: String): String = if (url.contains('#')) "$url&w=1" else "$url#w=1"

    /** Logos elegidos a mano (no necesitan el índice de los bancos). Los de Movistar Plus+ van sobre fondo blanco. */
    private fun curated(name: String): String? =
        (movistarLaLiga(name) ?: movistarChampions(name) ?: movistarPlus(name))?.let { white(it) }
            ?: picked(name) ?: special(name)

    private fun find(name: String): String? {
        curated(name)?.let { return it }
        // La 1 UHD tiene su propio logo.
        clean(name).let { c -> if ((ALIAS[c.key] ?: c.key) == "tve1" && UHD_WORD.containsMatchIn(name)) return LA1_UHD }
        findOne(clean(name))?.let { return it }
        // «Movistar TVG», «M+ Telemadrid»...: se prueba también sin la marca delante.
        val n = ChannelNames.display(name)
        if (n != name) findOne(clean(n))?.let { return it }
        return null
    }

    // ---------------- LaLiga por Movistar Plus+ (logos actuales, 2025) ----------------

    private const val LOGOPEDIA = "https://static.wikia.nocookie.net/logopedia/images/"
    private val LALIGA_MAIN = LOGOPEDIA + "0/06/LaLiga_por_Movistar_Plus%2B_2025.svg/revision/latest/scale-to-width-down/600"
    private val LALIGA_HDR = LOGOPEDIA + "0/07/LaLiga_HDR_por_Movistar_Plus%2B_2025.svg/revision/latest/scale-to-width-down/600"
    private val LALIGA_2_HDR = LOGOPEDIA + "e/e5/LaLiga_2_HDR_por_Movistar_Plus%2B_2025.svg/revision/latest/scale-to-width-down/600"
    private val LALIGA_N = mapOf(
        2 to "6/68/LaLiga_2_por_Movistar_Plus%2B_2025.png",
        3 to "0/0e/LaLiga_3_por_Movistar_Plus%2B_2025.png",
        4 to "8/89/LaLiga_4_por_Movistar_Plus%2B_2025.png",
        5 to "f/fd/LaLiga_5_por_Movistar_Plus%2B_2025.png",
        6 to "e/e5/LaLiga_6_por_Movistar_Plus%2B_2025.png",
        7 to "b/b7/LaLiga_7_por_Movistar_Plus%2B_2025.png",
    ).mapValues { LOGOPEDIA + it.value + "/revision/latest" }
    private val NOT_MOVISTAR_LALIGA = Regex("\\b(hypermotion|dazn|bar|smartbank|ea|tv\\s*bar|femenina|f|liga\\s*f)\\b")

    /**
     * Canales de LaLiga de Movistar Plus+ («M+ LaLiga TV 2 FHD», «Movistar LaLiga UHD», «LALIGA TV 3»...)
     * con el logo actual: el principal, el HDR o el del número que corresponda.
     */
    private fun movistarLaLiga(name: String): String? {
        val n = Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase()
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9+ ]"), " ")
            // «LALIGA2» → «LALIGA 2»
            .replace(Regex("([a-z])(\\d)"), "$1 $2").replace(Regex("\\s+"), " ").trim()
        val m = Regex("\\bla\\s*liga\\b").find(n) ?: return null
        if (NOT_MOVISTAR_LALIGA.containsMatchIn(n)) return null
        // Lo de delante solo puede ser el país o la marca Movistar / M+.
        val before = n.substring(0, m.range.first).trim().split(' ').filter { it.isNotBlank() }
        if (before.any { it !in setOf("es", "esp", "spain", "m", "m+", "movistar", "movistar+", "plus", "por", "+") }) return null
        val words = n.substring(m.range.last + 1).trim().split(' ').filter { it.isNotBlank() }
        val hdr = words.any { it == "hdr" || it == "uhd" || it == "4k" || it == "2160" || it == "2160p" }
        val after = words.filter { it !in setOf("tv", "por", "movistar", "movistar+", "plus", "m+", "es", "+") && it !in QUALITY && it !in FEED }
        val num = after.firstNotNullOfOrNull { it.toIntOrNull() }
        if (after.any { it.toIntOrNull() == null }) return null
        return when {
            num == 2 && hdr -> LALIGA_2_HDR
            hdr -> LALIGA_HDR
            num == null || num == 1 -> LALIGA_MAIN
            else -> LALIGA_N[num] ?: LALIGA_MAIN
        }
    }

    // ---------------- Canales «por Movistar Plus+» (logos actuales de Logopedia, en vectorial) ----------------

    private class MPlus(val words: Regex, val main: String, val numbered: Map<Int, String> = emptyMap())

    /** Orden importante: lo más concreto primero («cine español» antes que «cine»). */
    private val MPLUS = listOf(
        MPlus(Regex("^(cine )?espanol\\b"), "d/d2/Cine_Espa%C3%B1ol_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^ellas( vamos)?\\b"), "1/1d/Ellas_Vamos_por_Movistar_Plus%2B.svg"),
        MPlus(Regex("^(estrenos|estreno|cine)\\b"), "8/88/Estrenos_por_Movistar_Plus%2B_2025.svg", mapOf(2 to "3/31/Estrenos_2_por_Movistar_Plus%2B.svg")),
        MPlus(Regex("^deportes\\b"), "d/d7/Deportes_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^vamos\\b"), "b/be/Vamos_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^(documentales|documental|docu|docs)\\b"), "7/7c/Documentales_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^(hits|series)\\b"), "3/3c/Hits_por_M%2B.svg", mapOf(2 to "3/3f/Series_2_por_Movistar_Plus%2B.svg")),
        MPlus(Regex("^(originales|original)\\b"), "0/09/Originales_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^accion\\b"), "b/b6/Acci%C3%B3n_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^drama\\b"), "0/0d/Drama_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^suspense\\b"), "4/4d/Suspense_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^(clasicos|clasico)\\b"), "2/2f/Cl%C3%A1sicos_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^comedia\\b"), "2/27/Comedia_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^musica\\b"), "3/38/M%C3%BAsica_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^fest\\b"), "6/67/Fest_por_Movistar_Plus%2B.svg"),
        MPlus(Regex("^fanzone\\b"), "c/ca/Fanzone_por_Movistar_Plus%2B_2026.svg"),
        MPlus(Regex("^indie\\b"), "2/22/Indie_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^golf\\b"), "2/21/Golf_por_Movistar_Plus%2B_2023.svg"),
        MPlus(Regex("^(# ?0|0|cero)$"), "0/07/Movistar_Plus%2B.svg"),
    )
    private val MPLUS_BRAND = setOf("m", "m+", "movistar", "movistar+", "plus", "+", "es", "esp", "spain")
    private val MPLUS_GREEN = "1AC93A"
    /** Nombres que en las listas españolas son de Movistar Plus+ aunque no lleven la marca («DOCUMENTALES SAT», «GOLF 2»). */
    private val UNBRANDED_MPLUS = Regex("^(golf|documentales|documental|vamos|ellas vamos|ellas|originales|estrenos|cine espanol|baloncesto|suspense|clasicos|fanzone|deportes|portada)( \\d+)?$")

    /**
     * «M+ Deportes 3 FHD», «Movistar Estrenos», «#Vamos», «#0»...: logo actual en alta calidad.
     * Los numerados que en Logopedia solo existen diminutos se muestran con el logo principal y el número en verde.
     */
    private fun movistarPlus(name: String): String? {
        var n = Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase()
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9+# ]"), " ")
            .replace(Regex("([a-z])(\\d)"), "$1 $2").replace(Regex("\\s+"), " ").trim()
        // «M+DOCUMENTALES» → «M+ DOCUMENTALES»
        n = n.replace(Regex("\\+(?=[a-z0-9])"), "+ ")
        val hashtag = n.contains("#")
        n = n.replace(Regex("\\s*por (movistar ?plus|movistar ?\\+|m ?\\+)\\s*"), " ").trim()
        val words = n.split(' ').filter { it.isNotBlank() }.toMutableList()
        var branded = hashtag
        // Número de canal delante («105 M. DOCUMENTALES»).
        while (words.size > 1 && words.first().all { it.isDigit() }) words.removeAt(0)
        while (words.isNotEmpty() && words.first() in MPLUS_BRAND) {
            if (words.first() !in setOf("es", "esp", "spain")) branded = true
            words.removeAt(0)
        }
        var rest = words.filter { it !in QUALITY && it !in FEED }.joinToString(" ").replace("#", "").trim()
        // «GOLF 2 FHD» sin marca: en las listas españolas es el de Movistar Plus+.
        if (!branded && !name.contains("movistar", true) && !UNBRANDED_MPLUS.matches(rest)) return null
        if (rest.isEmpty() && hashtag) return null
        // «M. LOS DOCUMENTALES SAT» → «documentales»
        rest = rest.removePrefix("los ").removePrefix("las ")
        // «M.PLUS SAT», «M+ HEVC»: el canal principal de Movistar Plus+.
        if (rest.isEmpty() || Regex("^portada\\b").containsMatchIn(rest)) return LOGOPEDIA + "0/07/Movistar_Plus%2B.svg/revision/latest/scale-to-width-down/700"
        // Baloncesto (logo pasado por el usuario, con el número en verde) y Romance (hecho a partir del de Clásicos).
        Regex("^(baloncesto|basket|basquet)( (\\d{1,2}))?\\b").find(rest)?.let { m ->
            val num = m.groupValues[3].toIntOrNull()
            return when {
                num == null -> OWN_LOGOS + "baloncesto.png"
                num in 1..5 -> OWN_LOGOS + "baloncesto$num.png"
                else -> OWN_LOGOS + "baloncesto.png#b=$num&c=$MPLUS_GREEN"
            }
        }
        if (Regex("^romance\\b").containsMatchIn(rest)) return OWN_LOGOS + "romance.png"
        for (m in MPLUS) {
            val hit = m.words.find(rest) ?: continue
            val tail = rest.removePrefix(hit.value).trim()
            val num = tail.split(' ').firstOrNull()?.toIntOrNull()?.takeIf { it in 2..20 }
            val own = num?.let { m.numbered[it] }
            val base = LOGOPEDIA + (own ?: m.main) + "/revision/latest/scale-to-width-down/700"
            return if (num != null && own == null) "$base#b=$num&c=$MPLUS_GREEN" else base
        }
        return null
    }

    // ---------------- Logos pedidos uno a uno ----------------

    /** Logos subidos al propio repositorio de la app (los que el usuario ha pasado). */
    /** Logos hechos a mano (carpeta «logos» del proyecto): van dentro de la app, no se descargan. */
    private const val OWN_LOGOS = "file:///android_asset/logos/"
    private fun tvl(path: String) = TV_LOGOS_RAW + "countries/spain/" + path
    private fun lp(path: String, w: Int = 600) = LOGOPEDIA + path + "/revision/latest/scale-to-width-down/$w"

    /** (expresión sobre el nombre limpio, logo, número opcional en ese color). Se prueba en orden. */
    private class Pick(val re: Regex, val url: String, val numberColor: String? = null, val onWhite: Boolean = false, val alwaysNumber: Boolean = false)

    private val PICKS by lazy {
        listOf(
            Pick(Regex("^amc$"), OWN_LOGOS + "amc2.png", onWhite = true),
            Pick(Regex("^(canal )?crimen(es)? reales?\\b"), OWN_LOGOS + "crimenesreales.png", onWhite = true),
            // Liga RFEF: emblema rojo sobre blanco con el número del canal en rojo.
            Pick(Regex("^((la ?)?liga )?rfef( tv)?$"), lp("6/61/RFEF_icon_%282021%29.svg", 500), "E4002B", onWhite = true, alwaysNumber = true),
            Pick(Regex("^bbc food$"), lp("5/51/BBC-FOOD.svg", 800), onWhite = true),
            // BBC News: logo oficial en negro sobre fondo blanco.
            Pick(Regex("^bbc news\\b"), OWN_LOGOS + "bbcnews.png", onWhite = true),
            // Canal Sur y Canal Sur Andalucía: logo propio («Canal Sur Televisión Andalucía»); el 2, el suyo de tv-logos.
            Pick(Regex("^canal ?sur ?2\\b"), TV_LOGOS_RAW + "countries/spain/canal-sur-2-es.png", onWhite = true),
            Pick(Regex("^canal ?sur andalucia\\b"), OWN_LOGOS + "canalsur.png", onWhite = true),
            Pick(Regex("^canal ?sur\\b"), OWN_LOGOS + "canalsur.png", onWhite = true),
            // Sundance TV y BBC Earth: logos propios en alta resolución, en negro sobre blanco (el «TV» de Sundance en su color).
            Pick(Regex("^sundance( tv| channel)?\\b"), OWN_LOGOS + "sundancetv.png", onWhite = true),
            Pick(Regex("^bbc earth\\b"), OWN_LOGOS + "bbcearth.png", onWhite = true),
            // DAZN F1 (también FHD, 4K, «script»...): logo propio, recuadro DAZN en negro y F1 en rojo, sobre blanco.
            Pick(Regex("^dazn f ?1\\b"), OWN_LOGOS + "dazn-f1.png", onWhite = true),
            // DAZN 1-4: logos propios en alta resolución (recuadro DAZN y número, en blanco sobre la tarjeta oscura).
            *(1..4).map { n -> Pick(Regex("^(m |m\\+ |movistar )?dazn ?$n$"), OWN_LOGOS + "dazn$n.png", onWhite = true) }.toTypedArray(),
            // DAZN Baloncesto 1, 2, 3...: logo propio (DAZN + «Baloncesto» y el número debajo), negro sobre blanco.
            *(1..10).map { n ->
                Pick(Regex("^dazn (baloncesto|basket|basketball) ?$n$"), OWN_LOGOS + "dazn-baloncesto$n.png", onWhite = true)
            }.toTypedArray(),
            // TVG: solo la «G» azul, centrada (el texto «GALICIA» es blanco y no se ve sobre el fondo blanco).
            Pick(Regex("^(tvg|tv galicia|television de galicia|galicia)( europa| america| internacional| int)?$"), OWN_LOGOS + "tvg.png", onWhite = true),
            Pick(Regex("^caza (y|and|&)? ?pesca\\b"), OWN_LOGOS + "cazaypesca.png", onWhite = true),
            Pick(Regex("^(canal )?hollywood\\b"), OWN_LOGOS + "hollywood.png", onWhite = true),
            Pick(Regex("^(nickelodeon|nick)$"), OWN_LOGOS + "nickelodeon.png", onWhite = true),
            Pick(Regex("^(telecinco|tele 5|t 5)$"), OWN_LOGOS + "telecinco2.png", onWhite = true),
            Pick(Regex("^(cuatro|canal cuatro)$"), OWN_LOGOS + "cuatro.png", onWhite = true),
            Pick(Regex("^(antena 3|antena tres|a 3)$"), lp("1/11/Antena_3_2025.svg"), onWhite = true),
            Pick(Regex("^(la ?sexta|la 6|sexta)$"), lp("0/08/LaSexta_2024.svg"), onWhite = true),
            Pick(Regex("^(la 1|tve 1|tve la 1|tve)$"), lp("e/e4/La_1_Flat_logo_2026.svg"), onWhite = true),
            Pick(Regex("^(la 2|tve 2|tve la 2|la dos)$"), OWN_LOGOS + "la2.png", onWhite = true),
            Pick(Regex("^aragon( tv| television)?( int| internacional)?$"), OWN_LOGOS + "aragon-tv.png"),
            Pick(Regex("^hbo ?max\\b"), lp("b/b3/HBO_Max_%282025%29.svg")),
            Pick(Regex("^fl?ix ?ole\\b"), lp("5/5a/FlixOl%C3%A9.svg"), "EC0032", onWhite = true),
            Pick(Regex("^bbc top ?gear\\b|^top ?gear\\b"), lp("b/b1/BBC_TopGear_2021.svg")),
            Pick(Regex("^bbc (series|history|historia|drama|documentales)$"), lp("d/d7/BBC_2021.svg")),
            Pick(Regex("^warner( tv)?$"), lp("c/c7/WarnerTV_2021.svg")),
            Pick(Regex("^(fdf|factoria de ficcion)$"), lp("4/42/Factor%C3%ADa_de_Ficci%C3%B3n.svg"), onWhite = true),
            Pick(Regex("^(a ?3 ?series|atreseries|a ?3 ?s)$"), OWN_LOGOS + "a3series.png", onWhite = true),
            Pick(Regex("^neox$"), lp("a/ac/Neox_2023.svg"), onWhite = true),
            Pick(Regex("^cosmo( tv)?$"), lp("0/05/COSMO_Espa%C3%B1a_2024.svg"), onWhite = true),
            Pick(Regex("^(sy ?fy|sci ?fi)$"), OWN_LOGOS + "syfy.png", onWhite = true),
            Pick(Regex("^real madrid( tv)?$"), OWN_LOGOS + "realmadrid.png", onWhite = true),
            Pick(Regex("^iberalia( tv)? pesca\\b"), OWN_LOGOS + "iberalia-pesca.png", onWhite = true),
            Pick(Regex("^iberalia( tv)? caza\\b"), OWN_LOGOS + "iberalia-caza.png", onWhite = true),
            Pick(Regex("^iberalia\\b"), OWN_LOGOS + "iberalia.png", onWhite = true),
            Pick(Regex("^(canal )?cocina\\b"), OWN_LOGOS + "canalcocina.png", onWhite = true),
            Pick(Regex("^u ?beat( tv)?$"), OWN_LOGOS + "ubeat.png", onWhite = true),
            Pick(Regex("^di?s?c[ou]very( channel)?$"), OWN_LOGOS + "discovery.png", onWhite = true),
            Pick(Regex("^dreamworks( channel)?$"), OWN_LOGOS + "dreamworks.png", onWhite = true),
        )
    }

    private fun picked(name: String): String? {
        val n = Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase()
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("([a-z])(\\d)"), "$1 $2").replace(Regex("\\s+"), " ").trim()
        val words = n.split(' ').filter { it.isNotBlank() }.toMutableList()
        while (words.isNotEmpty() && words.first() in setOf("es", "esp", "spain", "m", "movistar")) words.removeAt(0)
        val all = words.filter { it !in QUALITY && it !in FEED && it != "avances" }
        val num = all.lastOrNull()?.toIntOrNull()
        val base = (if (num != null) all.dropLast(1) else all).joinToString(" ")
        // La 1 UHD: su logo propio («1» azul con UHD), también sobre blanco.
        if (Regex("^(la|tve)( la)? 1$").matches((if (num != null) "$base $num" else base).trim()) && Regex("\\b(uhd|4k|2160p?)\\b").containsMatchIn(n)) {
            return white(lp("3/3b/La_1_UHD_2026.svg"))
        }
        val full = if (num != null) "$base $num".trim() else base
        for (p in PICKS) {
            // Primero con el número como parte del nombre («la 1», «antena 3»), luego sin él («flixole 2» → número aparte).
            val whole = p.numberColor == null && p.re.containsMatchIn(full)
            if (!whole && !p.re.containsMatchIn(base)) continue
            var u = p.url
            if (!whole && num != null && (num > 1 || p.alwaysNumber) && p.numberColor != null) u = "$u#b=$num&c=${p.numberColor}"
            return if (p.onWhite) white(u) else u
        }
        return null
    }

    // ---------------- Casos especiales pedidos ----------------

    private val NBA_WORD = Regex("(?i)\\bnba\\b")
    private val HYPERMOTION = Regex("(?i)hypermotion")
    /** LaLiga TV Hypermotion (2023–hoy, Logopedia): cada canal con su número. */
    private val HYPERMOTION_N = mapOf(
        2 to "6/6f/La_Liga_Hypermotion_2.svg",
        3 to "2/23/La_Liga_Hypermotion_3.svg",
        4 to "3/38/La_Liga_TV_Hypermotion_4.svg",
        5 to "3/37/La_Liga_TV_Hypermotion_5.svg",
        6 to "c/c6/La_Liga_TV_Hypermotion_6.svg",
        7 to "7/76/La_Liga_TV_Hypermotion_7.svg",
        8 to "2/27/La_Liga_TV_Hypermotion_8.svg",
        9 to "d/d5/La_Liga_TV_Hypermotion_9.svg",
        10 to "a/a2/La_Liga_TV_Hypermotion_10.svg",
        11 to "9/92/La_Liga_TV_Hypermotion_11.svg",
        12 to "2/29/La_Liga_TV_Hypermotion_12.svg",
    )
    private val DAZN_LALIGA = Regex("(?i)\\bdazn\\b.*\\bla\\s*liga(?:\\b|\\d)")

    /** Todos los canales NBA con el logo de la NBA; todos los DAZN LaLiga (2, 3, script...) con el de DAZN LaLiga. */
    private fun special(name: String): String? {
        val n = Normalizer.normalize(name, Normalizer.Form.NFKD).replace(Regex("\\p{Mn}+"), "")
        if (NBA_WORD.containsMatchIn(n)) return LOGOPEDIA + "4/4c/NBA_2017.svg/revision/latest/scale-to-width-down/600"
        if (HYPERMOTION.containsMatchIn(n)) {
            val num = Regex("(?i)hypermotion\\s*(\\d{1,2})(?!\\d)").find(n)?.groupValues?.get(1)?.toIntOrNull()
            val file = num?.let { HYPERMOTION_N[it] } ?: "b/b5/LaLiga_TV_Hypermotion.svg"
            // «x=c»: el turquesa claro del logo se oscurece para que contraste con el fondo blanco.
            return white(LOGOPEDIA + file + "/revision/latest/scale-to-width-down/600") + "&x=c"
        }
        if (DAZN_LALIGA.containsMatchIn(n)) {
            // Logos actuales (2024) de Logopedia: el de DAZN LaLiga 2 lleva el «2» en blanco sobre fondo negro.
            val num = Regex("(?i)la\\s*liga\\s*(\\d)").find(n)?.groupValues?.get(1)
            return if (num == "2") LOGOPEDIA + "f/fa/DAZN_LaLiga_2_2024.svg/revision/latest/scale-to-width-down/600"
            else LOGOPEDIA + "1/18/DAZN_LALIGA_2024.svg/revision/latest/scale-to-width-down/600"
        }
        return null
    }

    // ---------------- Liga de Campeones por Movistar Plus+ (logo actual, 2023) ----------------

    private val LDC_MAIN = LOGOPEDIA + "c/c9/Liga_de_Campeones_por_Movistar_Plus%2B_2023.svg/revision/latest/scale-to-width-down/1000"

    /**
     * Canales de Liga de Campeones de Movistar Plus+. Los logos numerados del banco son diminutos (unos 200 px),
     * así que se usa el logo principal en alta calidad y la app dibuja encima el número (o UHD).
     */
    private fun movistarChampions(name: String): String? {
        val n = Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase()
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9+ ]"), " ")
            // «LALIGA2» → «LALIGA 2»
            .replace(Regex("([a-z])(\\d)"), "$1 $2").replace(Regex("\\s+"), " ").trim()
        val m = Regex("\\b(liga de campeones|champions league|champions)\\b").find(n) ?: return null
        if (Regex("\\b(dazn|tnt|bein|sky|rtl|tf1|canal\\s*plus|femenina|youth)\\b").containsMatchIn(n)) return null
        val before = n.substring(0, m.range.first).trim().split(' ').filter { it.isNotBlank() }
        if (before.any { it !in setOf("es", "esp", "spain", "m", "m+", "movistar", "movistar+", "plus", "+") }) return null
        val words = n.substring(m.range.last + 1).trim().split(' ').filter { it.isNotBlank() }
        val uhd = words.any { it == "uhd" || it == "4k" || it == "hdr" || it == "2160" || it == "2160p" }
        val after = words.filter { it !in setOf("tv", "por", "movistar", "movistar+", "plus", "m+", "es", "+") && it !in QUALITY && it !in FEED }
        if (after.any { it.toIntOrNull() == null }) return null
        val num = after.firstNotNullOfOrNull { it.toIntOrNull() }?.takeIf { it > 1 }
        val badge = listOfNotNull(num?.toString(), if (uhd) "UHD" else null).joinToString(" ")
        // El número va en el mismo verde del logo.
        return if (badge.isEmpty()) LDC_MAIN else "$LDC_MAIN#b=${java.net.URLEncoder.encode(badge, "UTF-8")}&c=1AC93A"
    }

    /** Logos elegidos a mano para canales cuyo logo del banco está anticuado. */
    private val OVERRIDE = mapOf(
        "telecinco" to OWN_LOGOS + "telecinco2.png#w=1",
        // RTVE estrenó logos en 2026 (Logopedia).
        "tve1" to LOGOPEDIA + "b/b6/La_1_2026.svg/revision/latest/scale-to-width-down/600",
        "tve2" to OWN_LOGOS + "la2.png#w=1",
    )
    private val LA1_UHD = LOGOPEDIA + "3/3b/La_1_UHD_2026.svg/revision/latest/scale-to-width-down/600"
    private val UHD_WORD = Regex("(?i)\\b(uhd|4k|2160p?)\\b")

    private fun findOne(c: Cleaned): String? {
        val folder = c.prefix?.let { PREFIX_FOLDER[it] } ?: "spain"
        val key = ALIAS[c.key] ?: c.key
        OVERRIDE[key]?.let { return it }
        val spanish = folder == "spain"
        if (spanish && key.length >= 2) {
            commons[key]?.let { return it }
            commons[c.key]?.let { return it }
        }
        if (key.length >= 2) {
            tvLogos[key]?.let { return pick(it, folder) }
            iptvOrg[key]?.let { return it }
            iptvOrg[c.key]?.let { return it }
        }
        val loose = ALIAS[c.loose] ?: c.loose
        if (loose.length >= 3) {
            tvLogos[loose]?.let { return pick(it, folder) }
            tvLoose[loose]?.let { return pick(it, folder) }
            iptvOrg[loose]?.let { return it }
            if (spanish) commons[loose]?.let { return it }
        }
        return null
    }

    // ---------------- Limpieza de nombres ----------------

    /** Nombres habituales en las listas españolas → nombre del archivo en tv-logos. */
    private val ALIAS: Map<String, String> = buildMap {
        fun a(target: String, vararg names: String) = names.forEach { put(it, target) }
        a("tve1", "la1", "tvela1", "la1tve", "launo", "tvelauno", "rtvela1", "la1rtve", "tve")
        a("tve2", "la2", "tvela2", "la2tve", "lados", "rtvela2", "la2rtve")
        a("antena3", "a3", "antena3tv", "antenatres")
        a("telecinco", "t5", "tele5", "telecincotv")
        a("lasexta", "la6", "sexta", "la6sexta", "lasextatv")
        a("cuatro", "cuatrotv", "canalcuatro")
        a("24h", "canal24horas", "24horas", "tve24h", "canal24h", "24htve", "el24horas", "tve24horas")
        a("tdp", "teledeporte", "tvetdp", "tdptve", "teledeportetve")
        a("clan", "clantve", "tveclan")
        a("fdf", "factoriadeficcion", "fdftelecinco", "factoria")
        a("trece", "13tv", "trecetv", "13")
        a("realmadridtv", "realmadrid", "rmtv")
        a("barcatv", "barca", "barcelonatv")
        a("galicia", "tvg", "tvgeuropa", "galiciatv", "tvgalicia", "tvg1", "tvgamerica", "galiciatvamerica")
        a("galicia2", "tvg2", "galicia2tv")
        a("tv3cat", "tv3", "tv3cat")
        a("cero", "0", "movistar0", "mplus0", "movistarplus0", "m0")
        a("movistarplus", "movistar", "mplus", "movistarplusplus")
        a("eurosport1", "eurosport")
        a("nationalgeographic", "natgeo", "nationalgeo")
        a("nationalgeographicwild", "natgeowild", "nationalgeowild")
        a("discoverychannel", "discovery")
        a("disneyjr", "disneyjunior")
        a("disneychannel", "disney")
        a("nickelodeon", "nick")
        a("canalhollywood", "hollywood")
        a("canalcocina", "cocina")
        a("paramountnetwork", "paramount", "paramountchannel")
        a("telemadrid", "telemadridtv")
        a("andaluciatv", "canalsurandalucia")
        a("bemadtv", "bemad")
        a("comedycentral", "comedy")
        a("dazn", "dazn1es")
        // Autonómicos
        a("castillademancha", "cmm", "cmmtv", "castillalamancha", "castillalamanchatv", "cmmtelevision", "castillalamanchamedia")
        a("tpa7", "tpa", "rtpa", "tpaasturias", "asturiastv")
        a("televisioncanaria", "canarias", "tvcanaria", "canariastv", "televisioncanarias", "tvcanarias", "rtvc")
        a("canalextremadura", "extremadura", "canalextremaduratv", "extremaduratv")
        a("7televisionregiondemurcia", "7rm", "7regiondemurcia", "la7murcia", "7murcia", "7tvmurcia")
        a("navarratelevision", "navarratv", "natv", "navarra")
        a("aragontv", "aragon", "aragontelevision", "cartvaragon")
        a("tv3cat", "3cat", "tv3hd", "tv3catalunya")
        a("324", "3 24", "canal324")
        a("ib3", "ib3televisio", "ib3global", "ib3tv")
        a("apunt", "apuntmedia", "punt", "apunttv", "apuntvalencia")
        a("la7", "la7cyl", "la7castillayleon", "cyl7", "cyltv")
        a("la8", "la8cyl", "cyl8")
        a("etb1", "etb", "eitb", "etb1hd")
        a("canalsurandalucia", "canalsurhd")
        a("galicia", "televisiondegalicia", "agalega", "tvgalega")
    }

    /** Prefijos de país que se quitan del principio del nombre. */
    private val COUNTRY = setOf(
        "es", "esp", "spa", "spain", "uk", "gb", "us", "usa", "pt", "por", "fr", "it", "de",
        "mx", "ar", "lat", "latam", "latino", "int",
    )

    private val PREFIX = Regex("^[^a-z0-9]*([a-z]{2,6})\\s*([^a-z0-9\\s]+|\\s)\\s*")
    private val BRACKETS = Regex("[\\[({][^\\])}]*[\\])}]")
    private val QUALITY_PLUS = Regex("\\b(uhd|fhd|hd|sd|4k|hq)\\s*\\+")
    private val QUALITY = setOf(
        "uhd", "fhd", "hd", "sd", "hq", "lq", "4k", "8k", "hevc", "h265", "h264", "x265", "1080", "1080p", "720", "720p",
        "2160", "2160p", "1080i", "50fps", "60fps", "25fps", "fps", "tdt", "multi", "multiaudio", "vip", "backup", "bk",
        "alt", "raw", "ott", "hdr", "hdr10", "dolby", "atmos", "directo", "ppv", "dual", "vose", "orig", "original",
    )
    private val TRAILING_COUNTRY = setOf("es", "esp", "spain", "espana")

    /** Variantes de emisión que no cambian el canal (satélite, «script», calidad baja...). */
    private val FEED = setOf("sat", "satelite", "script", "low", "lq", "lite", "mobile", "movil", "opc", "bkp", "copia", "ref")

    class Cleaned(val prefix: String?, val key: String, val loose: String)

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFKD).lowercase()
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\x00-\\x7F]"), " ")

    /**
     * Limpia un nombre de canal para buscar su logo (solo por dentro; en pantalla se ve el original).
     * «ES| LA 1 FHD» → clave «la1». «ES ➤ ANTENA 3 ᴴᴰ» → «antena3». «M+ LaLiga TV» → «mpluslaligatv».
     */
    fun clean(name: String): Cleaned {
        var s = norm(name)
        var prefix: String? = null
        PREFIX.find(s)?.let { m ->
            val p = m.groupValues[1]
            val sep = m.groupValues[2]
            if (p in COUNTRY && (sep.isNotBlank() || p == "es" || p == "esp")) {
                prefix = p
                s = s.substring(m.range.last + 1)
            }
        }
        s = s.replace(BRACKETS, " ")
            .replace(QUALITY_PLUS, " ")
            .replace("+", " plus ")
            .replace(Regex("[^a-z0-9 ]"), " ")
        val tokens = s.split(' ').filter { it.isNotBlank() && it !in QUALITY && it !in FEED }.toMutableList()
        while (tokens.size > 1 && tokens.last() in TRAILING_COUNTRY) tokens.removeAt(tokens.size - 1)
        return Cleaned(prefix, tokens.joinToString(""), tokens.filter { it != "tv" }.joinToString(""))
    }

    /** Claves de un archivo de tv-logos: «la-1-es.png» → «la1»; los de Movistar+ también como «M+ …». */
    private fun fileKeys(file: String): List<Cleaned> {
        var stem = file.substringAfterLast('/').substringBeforeLast('.')
        stem = stem.replace(Regex("-[a-z]{2}$"), "")
        val text = stem.replace('-', ' ')
        val out = mutableListOf(clean(text))
        val mv = Regex("\\s*por movistar plus$")
        if (mv.containsMatchIn(text)) {
            val b = text.replace(mv, "").trim()
            if (b.isNotEmpty()) {
                listOf("m plus", "movistar plus", "movistar", "m").forEach { out += clean("$it $b") }
                val plain = clean(b)
                if (plain.key.length >= 5 && plain.key !in GENERIC) out += plain
            }
        }
        return out
    }

    private val GENERIC = setOf(
        "deportes", "cine", "series", "musica", "drama", "comedia", "clasicos", "indie", "originales",
        "suspense", "accion", "documentales", "cineespanol",
    )

    private fun hasQualityWord(file: String): Boolean =
        file.substringAfterLast('/').substringBeforeLast('.').split('-').any { it in QUALITY }

    // ---------------- Carga de los índices ----------------

    /**
     * true mientras todo el índice salga de los datos que van dentro de la app. Si algo hubo que pedirlo por
     * internet (porque faltaba), el índice no se guarda: así un fallo de red nunca deja los logos mal guardados.
     */
    @Volatile
    private var allBundled = true

    /** Abre un archivo de datos que va dentro de la app o, si no está, lo pide a internet. */
    private fun <T> openData(asset: String, url: String, block: (java.io.InputStream) -> T): T {
        val a = try {
            appCtx?.assets?.open(asset)
        } catch (e: Exception) {
            null
        }
        if (a != null) return a.use(block)
        allBundled = false
        return Http.open(url).use { r -> block(r.body!!.byteStream()) }
    }

    private fun appVersion(): Long = try {
        val ctx = appCtx!!
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }

    private fun load() {
        // El índice de logos se construye con los datos que van dentro de la app (se descargan al compilarla),
        // así es idéntico en la tele y en el móvil y no depende de que GitHub o Wikimedia respondan ese día.
        // Uno por versión de la app: al actualizar se rehace con los datos nuevos.
        val cacheFile = File(dir, "logos_index_b${appVersion()}.tsv")
        dir.listFiles()?.forEach { f -> if (f.name.startsWith("logos_index") && f.name != cacheFile.name) f.delete() }
        if (cacheFile.exists()) {
            readCache(cacheFile)
            if (ready()) return
        }
        allBundled = true
        val tv = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        val loose = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        // Lista de tv-logos que va dentro de la app; solo si faltara se pide a GitHub.
        val paths = bundledList().ifEmpty {
            allBundled = false
            ErrorLog.add("Logos: la lista de tv-logos no va dentro de la app; se pide a GitHub")
            try {
                githubTree()
            } catch (e: Exception) {
                ErrorLog.add("Logos: GitHub no respondió (${e.message ?: e.javaClass.simpleName})")
                emptyList()
            }
        }
        // Primero los archivos sin «hd», «uhd»...: así se prefiere el logo normal del canal.
        val sorted = paths.filter { it.endsWith(".png", true) }.sortedBy { if (hasQualityWord(it)) 1 else 0 }
        for (p in sorted) {
            val parts = p.split('/')
            val folder = if (parts.size >= 2) parts[parts.size - 2] else ""
            val entry = folder to TV_LOGOS_RAW + p
            for (c in fileKeys(p)) {
                if (c.key.length >= 2) tv.getOrPut(c.key) { mutableListOf() }.add(entry)
                if (c.loose.length >= 3 && c.loose != c.key) loose.getOrPut(c.loose) { mutableListOf() }.add(entry)
            }
        }
        val org = try {
            iptvOrgIndex()
        } catch (e: Exception) {
            ErrorLog.add("Logos: datos de iptv-org no disponibles (${e.message ?: e.javaClass.simpleName})")
            emptyMap()
        }
        val com = try {
            commonsIndex()
        } catch (e: Exception) {
            ErrorLog.add("Logos: datos de Wikimedia no disponibles (${e.message ?: e.javaClass.simpleName})")
            emptyMap()
        }
        if (tv.isEmpty() && org.isEmpty() && com.isEmpty()) throw IllegalStateException("sin índice")
        tvLogos = tv
        tvLoose = loose
        iptvOrg = org
        commons = com
        // Solo se guarda si todo salió de los datos de la app y no falta nada.
        if (allBundled && tv.isNotEmpty() && org.isNotEmpty() && com.isNotEmpty()) writeCache(cacheFile)
    }

    /** Lista de logos de tv-logos que va incluida en la app (por si GitHub no responde). */
    private fun bundledList(): List<String> = try {
        appCtx?.assets?.open("tvlogos.txt")?.bufferedReader()?.use { it.readLines() }?.filter { it.isNotBlank() } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun githubTree(): List<String> {
        val out = ArrayList<String>()
        Http.open(TV_LOGOS_TREE).use { r ->
            JsonReader(InputStreamReader(r.body!!.byteStream(), Charsets.UTF_8)).use { j ->
                j.beginObject()
                while (j.hasNext()) {
                    if (j.nextName() != "tree") {
                        j.skipValue()
                        continue
                    }
                    j.beginArray()
                    while (j.hasNext()) {
                        var path: String? = null
                        var type: String? = null
                        j.beginObject()
                        while (j.hasNext()) {
                            when (j.nextName()) {
                                "path" -> path = j.nextString()
                                "type" -> type = j.nextString()
                                else -> j.skipValue()
                            }
                        }
                        j.endObject()
                        if (type == "blob" && path != null) out.add(path)
                    }
                    j.endArray()
                }
                j.endObject()
            }
        }
        return out
    }

    private fun jsdelivrList(): List<String> {
        val out = ArrayList<String>()
        Http.open(TV_LOGOS_JSDELIVR).use { r ->
            JsonReader(InputStreamReader(r.body!!.byteStream(), Charsets.UTF_8)).use { j ->
                j.beginObject()
                while (j.hasNext()) {
                    if (j.nextName() != "files") {
                        j.skipValue()
                        continue
                    }
                    j.beginArray()
                    while (j.hasNext()) {
                        j.beginObject()
                        while (j.hasNext()) {
                            if (j.nextName() == "name") out.add(j.nextString().trimStart('/')) else j.skipValue()
                        }
                        j.endObject()
                    }
                    j.endArray()
                }
                j.endObject()
            }
        }
        return out
    }

    /** Lee iptv-org: canales (nombres) y logos (el de mayor tamaño de cada canal). */
    private fun iptvOrgIndex(): Map<String, String> {
        // 1) Logos por id de canal, quedándonos con el más grande.
        val best = HashMap<String, Pair<Int, String>>()
        openData("iptv_logos.json", IPTV_LOGOS) { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { j ->
                j.beginArray()
                while (j.hasNext()) {
                    var ch: String? = null
                    var url: String? = null
                    var w = 0
                    var fmt: String? = null
                    j.beginObject()
                    while (j.hasNext()) {
                        when (j.nextName()) {
                            "channel" -> ch = j.nextStringOrNull()
                            "url" -> url = j.nextStringOrNull()
                            "width" -> w = if (j.peek() == JsonToken.NUMBER) j.nextInt() else { j.skipValue(); 0 }
                            "format" -> fmt = j.nextStringOrNull()
                            else -> j.skipValue()
                        }
                    }
                    j.endObject()
                    if (ch == null || url == null || w < 100) continue
                    if (fmt != null && fmt.equals("SVG", true)) continue
                    val cur = best[ch]
                    if (cur == null || w > cur.first) best[ch] = w to url
                }
                j.endArray()
            }
        }
        // 2) Nombres y nombres alternativos de cada canal → logo.
        val out = HashMap<String, String>()
        openData("iptv_channels.json", IPTV_CHANNELS) { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { j ->
                j.beginArray()
                while (j.hasNext()) {
                    var id: String? = null
                    var name: String? = null
                    var country: String? = null
                    val alts = ArrayList<String>()
                    j.beginObject()
                    while (j.hasNext()) {
                        when (j.nextName()) {
                            "id" -> id = j.nextStringOrNull()
                            "name" -> name = j.nextStringOrNull()
                            "country" -> country = j.nextStringOrNull()
                            "alt_names" -> {
                                if (j.peek() == JsonToken.BEGIN_ARRAY) {
                                    j.beginArray()
                                    while (j.hasNext()) j.nextStringOrNull()?.let { alts.add(it) }
                                    j.endArray()
                                } else {
                                    j.skipValue()
                                }
                            }
                            else -> j.skipValue()
                        }
                    }
                    j.endObject()
                    if (id == null || country == null || country !in IPTV_COUNTRIES) continue
                    val logo = best[id]?.second ?: continue
                    (listOfNotNull(name) + alts).forEach { n ->
                        val k = clean(n).key
                        if (k.length >= 2 && !out.containsKey(k)) out[k] = logo
                    }
                }
                j.endArray()
            }
        }
        return out
    }

    private fun JsonReader.nextStringOrNull(): String? =
        if (peek() == JsonToken.NULL) {
            nextNull()
            null
        } else {
            nextString()
        }

    // ---------------- Caché en disco ----------------

    private fun writeCache(f: File) {
        try {
            f.bufferedWriter().use { w ->
                for ((k, list) in tvLogos) for ((folder, url) in list) w.write("t\t$k\t$folder\t$url\n")
                for ((k, list) in tvLoose) for ((folder, url) in list) w.write("l\t$k\t$folder\t$url\n")
                for ((k, url) in iptvOrg) w.write("o\t$k\t\t$url\n")
                for ((k, url) in commons) w.write("c\t$k\t\t$url\n")
            }
        } catch (e: Exception) {
            // No pasa nada: se volverá a descargar.
        }
    }

    private fun readCache(f: File) {
        val tv = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        val org = HashMap<String, String>()
        val com = HashMap<String, String>()
        val loose = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        try {
            f.forEachLine { line ->
                val p = line.split('\t')
                if (p.size < 4) return@forEachLine
                if (p[0] == "t") tv.getOrPut(p[1]) { mutableListOf() }.add(p[2] to p[3])
                else if (p[0] == "l") loose.getOrPut(p[1]) { mutableListOf() }.add(p[2] to p[3])
                else if (p[0] == "o") org[p[1]] = p[3]
                else if (p[0] == "c") com[p[1]] = p[3]
            }
        } catch (e: Exception) {
            return
        }
        tvLogos = tv
        tvLoose = loose
        iptvOrg = org
        commons = com
    }

    // ---------------- Wikimedia Commons ----------------

    private val YEAR_RANGE = Regex("((?:19|20)\\d{2})\\s*[-–—]\\s*((?:19|20)\\d{2})?")
    private val YEAR = Regex("\\b((?:19|20)\\d{2})\\b")
    private val COMMONS_NOISE = setOf(
        "logo", "logos", "logotipo", "logotype", "icon", "isotipo", "isotype", "imagotipo", "version", "variant",
        "alt", "alternate", "alternative", "white", "black", "blanco", "negro", "color", "colour", "horizontal",
        "vertical", "new", "nuevo", "current", "actual", "official", "oficial", "spain", "espana", "es", "svg",
        "wordmark",
    )
    private val COMMONS_KEEP_LA = Regex("^(la|el|canal)\\s")

    /** «File:Telecinco 2024 Logo.svg» → (clave «telecinco», puntuación 2024). Más puntuación = logo más reciente. */
    private fun commonsKey(title: String): Pair<String, Int>? {
        var name = title.substringAfter(':').substringBeforeLast('.')
        name = Normalizer.normalize(name, Normalizer.Form.NFKD).lowercase().replace(Regex("\\p{Mn}+"), "").replace('_', ' ')
        var score = 2015
        val range = YEAR_RANGE.find(name)
        if (range != null) {
            val end = range.groupValues[2]
            score = if (end.isEmpty()) 9999 else end.toInt()
        } else {
            YEAR.findAll(name).map { it.groupValues[1].toInt() }.maxOrNull()?.let { score = it }
        }
        if (Regex("\\b(white|black|blanco|negro|negative|monochrome|mono)\\b").containsMatchIn(name)) score -= 1
        name = name.replace(YEAR_RANGE, " ").replace(YEAR, " ")
        // «La 1», «La Sexta», «Canal Sur»: el artículo inicial forma parte del nombre del canal.
        val keepFirst = COMMONS_KEEP_LA.containsMatchIn(name.trim())
        val words = name.replace(Regex("[\\[(][^\\])]*[\\])]"), " ").split(Regex("[^a-z0-9+]+")).filter { it.isNotBlank() }
        val kept = words.filterIndexed { i, w -> (i == 0 && keepFirst) || w !in COMMONS_NOISE }
        if (kept.isEmpty()) return null
        val key = clean(kept.joinToString(" ")).key
        return if (key.length >= 2) key to score else null
    }

    private fun md5(s: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Miniatura PNG de 500 px de un archivo de Commons (las SVG se convierten en el propio servidor). */
    private fun commonsThumb(title: String): String {
        val name = title.substringAfter(':').replace(' ', '_')
        val h = md5(name)
        val enc = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val png = if (name.endsWith(".svg", true)) ".png" else ""
        return "https://upload.wikimedia.org/wikipedia/commons/thumb/${h[0]}/${h.substring(0, 2)}/$enc/500px-$enc$png"
    }

    private fun commonsGet(params: String): JSONObject {
        val req = Request.Builder().url("$COMMONS_API?format=json&formatversion=2&$params").header("User-Agent", WIKI_UA).build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Commons HTTP ${r.code}")
            return JSONObject(r.body?.string() ?: "{}")
        }
    }

    /** Recorre la categoría de logos de canales españoles (y sus subcategorías) de Wikimedia Commons. */
    private fun commonsIndex(): Map<String, String> {
        val best = HashMap<String, Pair<Int, String>>()
        // Lista de archivos que va dentro de la app (en el mismo orden en que los da Commons).
        val bundled = try {
            appCtx?.assets?.open("commons_titles.txt")?.bufferedReader()?.use { it.readLines() }?.filter { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
        if (!bundled.isNullOrEmpty()) {
            for (t in bundled) {
                if (!t.endsWith(".svg", true) && !t.endsWith(".png", true)) continue
                val (k, sc) = commonsKey(t) ?: continue
                val cur = best[k]
                if (cur == null || sc > cur.first) best[k] = sc to commonsThumb(t)
            }
            return best.mapValues { it.value.second }
        }
        allBundled = false
        val seen = HashSet<String>()
        var level = listOf(COMMONS_ROOT)
        var depth = 0
        val started = System.currentTimeMillis()
        while (level.isNotEmpty() && depth <= 2) {
            val next = ArrayList<String>()
            for (cat in level) {
                if (!seen.add(cat) || seen.size > 120 || System.currentTimeMillis() - started > 60_000) continue
                var cont: String? = null
                do {
                    val q = "action=query&list=categorymembers&cmtype=file|subcat&cmlimit=500&cmtitle=" +
                        java.net.URLEncoder.encode(cat, "UTF-8") + (cont?.let { "&cmcontinue=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
                    val o = commonsGet(q)
                    val members = o.optJSONObject("query")?.optJSONArray("categorymembers")
                    if (members != null) {
                        for (i in 0 until members.length()) {
                            val m = members.getJSONObject(i)
                            val t = m.optString("title")
                            when (m.optInt("ns")) {
                                14 -> next += t
                                6 -> if (t.endsWith(".svg", true) || t.endsWith(".png", true)) {
                                    val (k, sc) = commonsKey(t) ?: continue
                                    val cur = best[k]
                                    if (cur == null || sc > cur.first) best[k] = sc to commonsThumb(t)
                                }
                            }
                        }
                    }
                    cont = o.optJSONObject("continue")?.optString("cmcontinue")?.takeIf { it.isNotBlank() }
                } while (cont != null)
            }
            level = next
            depth++
        }
        return best.mapValues { it.value.second }
    }
}
