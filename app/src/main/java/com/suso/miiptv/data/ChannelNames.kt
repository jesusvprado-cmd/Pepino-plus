package com.suso.miiptv.data

import java.text.Normalizer

/**
 * Pequeños arreglos de los canales de la lista:
 *  - Quita la palabra «Movistar» delante de los canales que no son de Movistar (autonómicos, etc.):
 *    «MOVISTAR TVG HD» → «TVG HD». Las etiquetas de calidad (UHD, HD, TDT...) se dejan como están.
 *  - Ordena las familias de canales numerados: primero el que no lleva número y después 1, 2, 3...
 *    («Liga de Campeones», «Liga de Campeones 2», «Liga de Campeones 3»...).
 */
object ChannelNames {
    /** Lo que va detrás de «Movistar» en los canales propios de Movistar+ (ahí la palabra se deja). */
    private val OWN = listOf(
        "+", "plus", "laliga", "la liga", "liga", "champions", "deporte", "dep ", "dep.", "vamos", "#", "ellas",
        "cine", "estreno", "serie", "golf", "documental", "comedia", "drama", "accion", "clasico", "musica",
        "indie", "suspense", "original", "f1", "formula", "motor", "moto", "tenis", "copa", "supercopa", "europa",
        "ufc", "nba", "euroliga", "eurocup", "ppv", "multi", "bar", "hits", "españa", "espana", "max", "partidos",
        "toros", "caza", "fiesta", "kids", "infantil", "fanzone", "fest", "hits", "documental", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
    )

    /** ¿Lo que va detrás de «Movistar»/«M+» es un canal propio de Movistar Plus+? */
    fun isOwnMovistar(rest: String): Boolean { val r = norm(rest.trimStart()); return OWN.any { r.startsWith(it) } }

    private val MOVISTAR = Regex("(?i)\\bmovistar\\b\\s*[:|\\-–·]*\\s*")

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Erratas de la lista que se corrigen al mostrar el nombre («DICOVERY» → «DISCOVERY»). */
    private val TYPOS = listOf(Regex("(?i)\\bdicovery\\b") to "Discovery")

    private fun fixTypos(name: String): String {
        var n = name
        for ((re, good) in TYPOS) n = re.replace(n) { m ->
            val v = m.value
            when {
                v == v.uppercase() -> good.uppercase()
                v == v.lowercase() -> good.lowercase()
                else -> good
            }
        }
        return n
    }

    /** Nombre a mostrar: sin «Movistar» delante de los canales que no son suyos. */
    fun display(raw: String): String = displayRaw(raw).uppercase(java.util.Locale("es", "ES"))

    /** Todos los canales en mayúsculas (los autonómicos venían en minúsculas y desentonaban). */
    private fun displayRaw(raw: String): String {
        val name = fixTypos(raw)
        val m = MOVISTAR.find(name) ?: return name
        val rest = name.substring(m.range.last + 1)
        if (rest.isBlank()) return name
        val r = norm(rest.trimStart())
        if (OWN.any { r.startsWith(it) }) return name
        return (name.substring(0, m.range.first) + rest).replace(Regex("\\s{2,}"), " ").trim()
    }

    private val QUALITY = setOf(
        "uhd", "fhd", "hd", "sd", "4k", "hevc", "h265", "1080", "1080p", "720", "720p", "hdr", "tdt",
        "multi", "multiaudio", "backup", "alt", "50fps", "60fps", "vip", "sat", "satelite", "script", "low", "lq",
    )

    /** (familia, número o null). «ES: LIGA DE CAMPEONES 4 FHD» → ("es liga de campeones", 4). */
    private fun family(name: String): Pair<String, Int?> {
        val tokens = norm(name).replace(Regex("[^a-z0-9#+ ]"), " ").split(' ').filter { it.isNotBlank() }.toMutableList()
        while (tokens.isNotEmpty() && tokens.last() in QUALITY) tokens.removeAt(tokens.size - 1)
        val last = tokens.lastOrNull()
        val n = last?.toIntOrNull()
        if (n != null && tokens.size > 1 && n in 0..99) {
            tokens.removeAt(tokens.size - 1)
            while (tokens.isNotEmpty() && tokens.last() in QUALITY) tokens.removeAt(tokens.size - 1)
            return tokens.joinToString(" ") to n
        }
        return tokens.joinToString(" ") to null
    }

    /** Aplica el nombre limpio y, si [sortGroups], ordena las familias numeradas en sus mismos huecos. */
    fun tidy(items: List<Item>, sortGroups: Boolean): List<Item> {
        val named = items.map { if (it.kind == Kind.LIVE) it.copy(name = display(it.name)) else it }
        if (!sortGroups || named.size < 2) return named
        val fams = named.map { family(it.name) }
        // Solo las familias con al menos un canal numerado y dos o más canales.
        val groups = HashMap<String, MutableList<Int>>()
        fams.forEachIndexed { i, f -> if (f.first.isNotEmpty()) groups.getOrPut(f.first) { mutableListOf() }.add(i) }
        val out = named.toMutableList()
        for ((_, idx) in groups) {
            if (idx.size < 2 || idx.none { fams[it].second != null }) continue
            val sorted = idx.sortedWith(compareBy<Int>({ fams[it].second ?: -1 }, { it }))
            idx.forEachIndexed { slot, pos -> out[pos] = named[sorted[slot]] }
        }
        return out
    }
}
