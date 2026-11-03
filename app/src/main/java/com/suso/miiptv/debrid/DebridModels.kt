package com.suso.miiptv.debrid

/** Un archivo de vídeo de la cuenta de AllDebrid. */
data class DFile(
    /** Enlace de AllDebrid (se desbloquea al reproducir). */
    val link: String,
    val name: String,
    /** Carpeta dentro del torrent ("" si está suelto). */
    val folder: String,
    val size: Long,
    /** Fecha en que se añadió a la cuenta (ms). */
    val added: Long,
    /** Nombre del torrent o «Enlaces guardados». */
    val source: String,
)

/** Una copia concreta de una película o de un capítulo. */
class Version(val file: DFile, val info: ReleaseName.Info) {
    /** Pistas leídas del propio archivo (null si aún no se han leído o no se puede). */
    @Volatile
    var probe: MkvTracks.Result? = null

    val res: Int
        get() = probe?.let { resOf(it.width, it.height) }?.takeIf { it > 0 } ?: info.res

    val hdr: String?
        get() = probe?.hdr ?: info.hdr

    /** Idiomas del audio: del archivo si se ha leído; si no, de lo que dice el nombre. */
    val audio: List<String>
        get() = probe?.audio?.map { it.lang }?.filter { it != "?" }?.distinct()?.takeIf { it.isNotEmpty() } ?: info.audio.toList()

    val subs: List<String>
        get() = probe?.subs?.map { it.lang }?.filter { it != "?" }?.distinct() ?: info.subs.toList()

    val probed: Boolean get() = probe != null

    fun hasAudio(l: String): Boolean {
        val a = audio
        return when (l) {
            "spa" -> "es" in a || "lat" in a || (!probed && "multi" in a)
            "vo" -> "vo" in a || "en" in a || "ja" in a || "fr" in a || "it" in a || "de" in a || "ko" in a
            else -> l in a || (!probed && "multi" in a && (l == "es" || l == "en"))
        }
    }

    /** Etiquetas cortas para la carátula y la lista de versiones: «4K», «DV», «ES/EN», «SUB ES». */
    fun tags(withSubs: Boolean = true): List<String> {
        val t = ArrayList<String>()
        resLabel(res)?.let { t += it }
        hdr?.let { t += if (it == "HDR10+") "HDR10+" else it }
        val a = audio.filter { it != "multi" && it != "vo" }
        when {
            a.isNotEmpty() -> t += a.take(3).joinToString("/") { langShort(it) } + if (a.size > 3) "+" else ""
            "multi" in audio -> t += "MULTI"
            "vo" in audio -> t += "VO"
        }
        if (withSubs && "es" in subs) t += "SUB ES"
        return t
    }

    companion object {
        fun resOf(w: Int, h: Int): Int = when {
            w <= 0 && h <= 0 -> 0
            w >= 3200 || h >= 1800 -> 2160
            w >= 1800 || h >= 1000 -> 1080
            w >= 1200 || h >= 700 -> 720
            else -> 480
        }

        fun resLabel(r: Int): String? = when (r) {
            2160 -> "4K"
            1080 -> "1080p"
            720 -> "720p"
            480 -> "SD"
            else -> null
        }

        fun langShort(l: String): String = when (l) {
            "es" -> "ES"
            "lat" -> "LAT"
            "en" -> "EN"
            else -> l.uppercase().take(3)
        }

        fun langName(l: String): String = when (l) {
            "es" -> "Castellano"
            "lat" -> "Latino"
            "en" -> "Inglés"
            "fr" -> "Francés"
            "de" -> "Alemán"
            "it" -> "Italiano"
            "ja" -> "Japonés"
            "pt" -> "Portugués"
            "ca" -> "Catalán"
            "gl" -> "Gallego"
            "eu" -> "Euskera"
            "ko" -> "Coreano"
            "zh" -> "Chino"
            "ru" -> "Ruso"
            "multi" -> "Varios idiomas"
            "vo" -> "Versión original"
            "?" -> "Sin indicar"
            else -> l.uppercase()
        }
    }
}

enum class DKind { MOVIE, SERIES }

/** Película o serie (con todas sus copias y capítulos). */
class DTitle(
    val key: String,
    val kind: DKind,
    /** Título sacado del nombre del archivo. */
    val parsedName: String,
    val year: Int?,
    val versions: List<Version>,
) {
    val added: Long = versions.maxOfOrNull { it.file.added } ?: 0L
    val size: Long = versions.sumOf { it.file.size }

    /** Capítulos: temporada → capítulo → copias. */
    val episodes: Map<Int, Map<Int, List<Version>>> by lazy {
        if (kind != DKind.SERIES) emptyMap()
        else versions.filter { it.info.episode != null }
            .groupBy { it.info.season ?: 1 }
            .toSortedMap()
            .mapValues { (_, l) -> l.groupBy { it.info.episode!! }.toSortedMap() }
    }

    val episodeCount: Int get() = episodes.values.sumOf { it.size }
}

/** Filtros de la sección. */
data class DFilters(
    val quality: String = "all",
    val audio: String = "all",
    val subs: String = "all",
    val sort: String = "recent",
) {
    fun active() = quality != "all" || audio != "all" || subs != "all"

    fun matches(v: Version): Boolean {
        val q = when (quality) {
            "4k" -> v.res == 2160
            "hdr" -> v.hdr != null
            "1080" -> v.res == 1080
            "720" -> v.res in 1..720
            else -> true
        }
        if (!q) return false
        val a = if (audio == "all") true else v.hasAudio(audio)
        if (!a) return false
        return when (subs) {
            "es" -> "es" in v.subs
            else -> true
        }
    }

    companion object {
        val QUALITY = listOf("Todas" to "all", "4K" to "4k", "HDR / Dolby Vision" to "hdr", "1080p" to "1080", "720p o menos" to "720")
        val AUDIO = listOf("Todos" to "all", "Castellano" to "es", "Latino" to "lat", "Español (cualquiera)" to "spa", "Inglés" to "en", "Versión original" to "vo")
        val SUBS = listOf("Todos" to "all", "Con subtítulos en español" to "es")
        val SORT = listOf("Recientes" to "recent", "Título" to "title", "Año" to "year", "Tamaño" to "size")
    }
}

/** Qué copia se elige por defecto. */
data class DPrefs(
    /** "es", "lat", "en" o "vo". */
    val audio: String = "es",
    /** Calidad máxima en este aparato: 2160, 1080 o 720. */
    val maxRes: Int = 2160,
)

object Pick {
    fun score(v: Version, p: DPrefs): Double {
        val a = v.audio
        val lang = when {
            p.audio == "vo" -> if (v.hasAudio("vo")) 3.0 else if (a.isEmpty()) 1.0 else 0.5
            p.audio in a -> 3.0
            !v.probed && "multi" in a && (p.audio == "es" || p.audio == "en") -> 2.0
            p.audio == "es" && "lat" in a -> 1.5
            p.audio == "lat" && "es" in a -> 1.5
            a.isEmpty() -> 1.0
            else -> 0.0
        }
        val r = if (v.res == 0) 700 else v.res
        val resScore = if (r <= p.maxRes) r / 100.0 else p.maxRes / 100.0 - 3 - (r - p.maxRes) / 1000.0
        val hdr = if (v.hdr != null && r <= p.maxRes && p.maxRes >= 2160) 0.5 else 0.0
        val subs = if ("es" in v.subs) 0.2 else 0.0
        val extra = if (v.info.extra) -50.0 else 0.0
        return lang * 100 + resScore + hdr + subs + extra + v.file.size / 1e13
    }

    fun best(l: List<Version>, p: DPrefs): Version? = l.maxByOrNull { score(it, p) }
}

object Grouping {
    /** Agrupa los archivos en películas y series. */
    fun build(files: List<DFile>): List<DTitle> {
        // Torrents con varios capítulos: todo lo de ese torrent es la misma serie.
        val parsed = files.filter { ReleaseName.isVideo(it.name) }.map { f ->
            val folder = f.folder.ifBlank { null } ?: f.source.takeIf { it != f.name && it != SAVED }
            Version(f, ReleaseName.parse(f.name, folder))
        }.filter { v ->
            // Fuera muestras y archivos muy pequeños que no son capítulos.
            !(v.info.extra && v.file.size < 300L * 1024 * 1024) && (v.info.isEpisode || v.file.size == 0L || v.file.size > 40L * 1024 * 1024)
        }
        val bySource = parsed.groupBy { it.file.source }
        val seriesTitleOfSource = HashMap<String, String>()
        bySource.forEach { (src, l) ->
            val eps = l.filter { it.info.isEpisode }
            if (src != SAVED && eps.size >= 2) {
                // El título más repetido entre los capítulos de ese torrent.
                seriesTitleOfSource[src] = eps.groupingBy { it.info.title }.eachCount().maxByOrNull { it.value }!!.key
            }
        }
        val groups = LinkedHashMap<String, MutableList<Version>>()
        val names = HashMap<String, Pair<String, Int?>>()
        for (v in parsed) {
            val i = v.info
            if (i.isEpisode) {
                val t = seriesTitleOfSource[v.file.source] ?: i.title
                val k = "s:" + ReleaseName.norm(t)
                groups.getOrPut(k) { ArrayList() } += v
                if (k !in names) names[k] = t to i.year
            } else {
                val k = "m:" + ReleaseName.norm(i.title) + ":" + (i.year ?: "")
                groups.getOrPut(k) { ArrayList() } += v
                if (k !in names) names[k] = i.title to i.year
            }
        }
        // Película sin año en el nombre: se junta con la misma película que sí lo tiene (si solo hay una).
        val noYear = groups.keys.filter { it.startsWith("m:") && it.endsWith(":") }
        for (k in noYear) {
            val prefix = k
            val withYear = groups.keys.filter { it != k && it.startsWith(prefix) }
            if (withYear.size == 1) {
                groups[withYear[0]]!!.addAll(groups.remove(k)!!)
            }
        }
        return groups.map { (k, l) ->
            val (n, y) = names[k] ?: ("" to null)
            DTitle(k, if (k.startsWith("s:")) DKind.SERIES else DKind.MOVIE, n, y ?: l.firstNotNullOfOrNull { it.info.year }, l)
        }
    }

    const val SAVED = "Enlaces guardados"
}
