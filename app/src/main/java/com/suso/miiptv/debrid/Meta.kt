package com.suso.miiptv.debrid

import org.json.JSONArray
import org.json.JSONObject

/**
 * Carátulas y sinopsis. Con clave de TMDB (gratis) salen en español; sin ella se usa Cinemeta
 * (el catálogo de Stremio, sin clave, en inglés).
 */
object Meta {
    private const val TMDB = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/"
    private const val CINEMETA = "https://v3-cinemeta.strem.io"

    fun find(t: DTitle): DMeta? {
        val q = t.parsedName.trim()
        if (q.length < 2) return null
        val key = AllDebrid.tmdbKey
        return if (key.isNotBlank()) tmdbFind(key, q, t.year, t.kind) ?: cinemetaFind(q, t.year, t.kind)
        else cinemetaFind(q, t.year, t.kind)
    }

    fun season(m: DMeta, s: Int): Map<Int, EpMeta> {
        val id = m.id ?: return emptyMap()
        return when {
            id.startsWith("tmdb:") -> tmdbSeason(AllDebrid.tmdbKey, id.removePrefix("tmdb:"), s)
            id.startsWith("imdb:") -> cinemetaSeason(id.removePrefix("imdb:"), s)
            else -> emptyMap()
        }
    }

    // ---------- TMDB ----------

    private fun tmdbGet(key: String, path: String, params: List<Pair<String, String>>): JSONObject {
        // Clave corta (v3) en la dirección; el «token de lectura» largo (v4) en la cabecera.
        val bearer = key.length > 40
        val all = params + ("language" to "es-ES") + (if (bearer) emptyList() else listOf("api_key" to key))
        val url = TMDB + path + "?" + all.joinToString("&") { (k, v) -> k + "=" + DebridStore.enc(v) }
        val h = if (bearer) mapOf("Authorization" to "Bearer $key") else emptyMap()
        return JSONObject(DebridStore.http(url, h))
    }

    private fun tmdbFind(key: String, q: String, year: Int?, kind: DKind): DMeta? {
        val tv = kind == DKind.SERIES
        val path = if (tv) "/search/tv" else "/search/movie"
        fun search(withYear: Boolean): JSONArray? {
            val p = mutableListOf("query" to q, "include_adult" to "false")
            if (withYear && year != null) p += (if (tv) "first_air_date_year" else "primary_release_year") to year.toString()
            return tmdbGet(key, path, p).optJSONArray("results")
        }
        var res = search(true)
        if ((res == null || res.length() == 0) && year != null) res = search(false)
        if (res == null || res.length() == 0) return null
        val o = pickByYear(res, year) { it.optString(if (tv) "first_air_date" else "release_date").take(4).toIntOrNull() }
        val genres = JSONArrayInts(o.optJSONArray("genre_ids")).mapNotNull { GENRES[it] }.take(3).joinToString(", ").ifBlank { null }
        val vote = o.optDouble("vote_average", 0.0)
        return DMeta(
            title = o.optString(if (tv) "name" else "title").ifBlank { null },
            year = o.optString(if (tv) "first_air_date" else "release_date").take(4).toIntOrNull(),
            poster = o.optString("poster_path").takeIf { it.startsWith("/") }?.let { IMG + "w342" + it },
            backdrop = o.optString("backdrop_path").takeIf { it.startsWith("/") }?.let { IMG + "w1280" + it },
            overview = o.optString("overview").ifBlank { null },
            rating = if (vote > 0) String.format(java.util.Locale.US, "%.1f", vote) else null,
            genres = genres,
            id = "tmdb:" + o.optLong("id"),
        )
    }

    private fun tmdbSeason(key: String, id: String, s: Int): Map<Int, EpMeta> {
        if (key.isBlank()) return emptyMap()
        val a = tmdbGet(key, "/tv/$id/season/$s", emptyList()).optJSONArray("episodes") ?: return emptyMap()
        val out = HashMap<Int, EpMeta>()
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            out[e.optInt("episode_number")] = EpMeta(
                e.optString("name").ifBlank { null },
                e.optString("overview").ifBlank { null },
                e.optString("still_path").takeIf { it.startsWith("/") }?.let { IMG + "w300" + it },
            )
        }
        return out
    }

    // ---------- Cinemeta ----------

    private fun cinemetaFind(q: String, year: Int?, kind: DKind): DMeta? {
        val type = if (kind == DKind.SERIES) "series" else "movie"
        val s = JSONObject(DebridStore.http("$CINEMETA/catalog/$type/top/search=${DebridStore.enc(q).replace("+", "%20")}.json"))
        val metas = s.optJSONArray("metas") ?: return null
        if (metas.length() == 0) return null
        val o = pickByYear(metas, year) { it.optString("releaseInfo").take(4).toIntOrNull() ?: it.optInt("year").takeIf { y -> y > 0 } }
        val id = o.optString("imdb_id").ifBlank { o.optString("id") }
        // Ficha completa: sinopsis, fondo, nota y géneros.
        val full = runCatching {
            JSONObject(DebridStore.http("$CINEMETA/meta/$type/$id.json")).optJSONObject("meta")
        }.getOrNull() ?: o
        return DMeta(
            title = full.optString("name").ifBlank { o.optString("name") }.ifBlank { null },
            year = (full.optString("releaseInfo").ifBlank { full.optString("year") }).take(4).toIntOrNull(),
            poster = full.optString("poster").ifBlank { o.optString("poster") }.ifBlank { null },
            backdrop = full.optString("background").ifBlank { null },
            overview = full.optString("description").ifBlank { null },
            rating = full.optString("imdbRating").ifBlank { null },
            genres = full.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) }.take(3).joinToString(", ") }?.ifBlank { null },
            id = if (id.startsWith("tt")) "imdb:$id" else null,
        )
    }

    private fun cinemetaSeason(id: String, s: Int): Map<Int, EpMeta> {
        val m = JSONObject(DebridStore.http("$CINEMETA/meta/series/$id.json")).optJSONObject("meta") ?: return emptyMap()
        val v = m.optJSONArray("videos") ?: return emptyMap()
        val out = HashMap<Int, EpMeta>()
        for (i in 0 until v.length()) {
            val e = v.optJSONObject(i) ?: continue
            if (e.optInt("season") != s) continue
            val n = e.optInt("episode").takeIf { it > 0 } ?: e.optInt("number")
            out[n] = EpMeta(
                (e.optString("name").ifBlank { e.optString("title") }).ifBlank { null },
                (e.optString("overview").ifBlank { e.optString("description") }).ifBlank { null },
                e.optString("thumbnail").ifBlank { null },
            )
        }
        return out
    }

    // ---------- Comunes ----------

    private fun pickByYear(a: JSONArray, year: Int?, yearOf: (JSONObject) -> Int?): JSONObject {
        val list = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        if (year != null) {
            list.take(8).firstOrNull { yearOf(it) == year }?.let { return it }
            list.take(8).firstOrNull { yearOf(it)?.let { y -> kotlin.math.abs(y - year) <= 1 } == true }?.let { return it }
        }
        return list.first()
    }

    @Suppress("FunctionName")
    private fun JSONArrayInts(a: JSONArray?): List<Int> = (0 until (a?.length() ?: 0)).map { a!!.optInt(it) }

    private val GENRES = mapOf(
        28 to "Acción", 12 to "Aventura", 16 to "Animación", 35 to "Comedia", 80 to "Crimen", 99 to "Documental",
        18 to "Drama", 10751 to "Familia", 14 to "Fantasía", 36 to "Historia", 27 to "Terror", 10402 to "Música",
        9648 to "Misterio", 10749 to "Romance", 878 to "Ciencia ficción", 10770 to "Película de TV", 53 to "Suspense",
        10752 to "Bélica", 37 to "Western", 10759 to "Acción y aventura", 10762 to "Infantil", 10763 to "Noticias",
        10764 to "Reality", 10765 to "Ciencia ficción y fantasía", 10766 to "Telenovela", 10767 to "Entrevistas",
        10768 to "Bélica y política",
    )
}
