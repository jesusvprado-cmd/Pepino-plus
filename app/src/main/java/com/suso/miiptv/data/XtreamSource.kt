package com.suso.miiptv.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

fun normalizeServer(s: String): String {
    var x = s.trim()
    if (!x.startsWith("http://", true) && !x.startsWith("https://", true)) x = "http://$x"
    x = x.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("/xmltv.php")
    return x.trimEnd('/')
}

class XtreamSource(private val cfg: Config) : Source {
    private val base = normalizeServer(cfg.server)
    private val u = Uri.encode(cfg.user.trim())
    private val p = Uri.encode(cfg.pass.trim())

    private fun api(action: String?, extra: String = ""): String =
        "$base/player_api.php?username=$u&password=$p" +
            (if (action != null) "&action=$action" else "") + extra

    override fun xmltvUrl(): String = "$base/xmltv.php?username=$u&password=$p"

    private fun json(url: String): JSONObject = try {
        JSONObject(Http.text(url))
    } catch (e: JSONException) {
        JSONObject()
    }

    override suspend fun check(): AccountInfo = io {
        val o = try {
            JSONObject(Http.text(api(null)))
        } catch (e: JSONException) {
            throw IOException("El servidor no responde como Xtream Codes. Revisa la dirección y el puerto.")
        }
        val ui = o.optJSONObject("user_info")
            ?: throw IOException("El servidor no responde como Xtream Codes. Revisa la dirección y el puerto.")
        if (ui.optString("auth") != "1") throw IOException("Usuario o contraseña incorrectos.")
        learnServerClock(o)
        AccountInfo(
            status = ui.str("status"),
            expiry = ui.str("exp_date")?.toLongOrNull()?.times(1000),
            maxConnections = ui.str("max_connections"),
            activeConnections = ui.str("active_cons"),
        )
    }

    override suspend fun categories(kind: Kind): List<Category> = io {
        // La hora del servidor hace falta para «Ver desde el principio»: se pregunta una vez.
        if (kind == Kind.LIVE && serverOffset[base] == null) runCatching { learnServerClock(JSONObject(Http.text(api(null)))) }
        val action = when (kind) {
            Kind.LIVE -> "get_live_categories"
            Kind.MOVIE -> "get_vod_categories"
            Kind.SERIES -> "get_series_categories"
        }
        // Guardadas en el aparato (6 h): la app arranca sin esperar al servidor.
        val arr = DiskCache.read(api(action), DiskCache.CATALOG_AGE, valid = { it.length() > 0 }) { input ->
            try {
                JSONArray(input.bufferedReader(Charsets.UTF_8).readText())
            } catch (e: JSONException) {
                JSONArray()
            }
        }
        val out = ArrayList<Category>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("category_id") ?: continue
            out.add(Category(id, o.str("category_name") ?: id))
        }
        out
    }

    override suspend fun items(kind: Kind, categoryId: String?): List<Item> = io {
        val action = when (kind) {
            Kind.LIVE -> "get_live_streams"
            Kind.MOVIE -> "get_vod_streams"
            Kind.SERIES -> "get_series"
        }
        val extra = if (categoryId != null) "&category_id=${Uri.encode(categoryId)}" else ""
        // Guardados en el aparato (6 h): la app arranca sin esperar al servidor.
        DiskCache.read(api(action, extra), DiskCache.CATALOG_AGE, valid = { it.isNotEmpty() }) { input ->
            val out = ArrayList<Item>()
            try {
                readFlatArray(input) { m ->
                    val cat = m["category_id"] ?: ""
                    val item = when (kind) {
                        Kind.LIVE -> m["stream_id"]?.let { id ->
                            Item(
                                Kind.LIVE, id, m["name"]?.trim() ?: id,
                                image = m["stream_icon"].clean(),
                                categoryId = cat,
                                epgId = m["epg_channel_id"].clean(),
                                num = m["num"]?.toIntOrNull() ?: 0,
                                added = m["added"]?.toLongOrNull() ?: 0,
                                archive = if (m["tv_archive"] == "1") (m["tv_archive_duration"]?.toIntOrNull() ?: 1).coerceAtLeast(1) else 0,
                            )
                        }
                        Kind.MOVIE -> m["stream_id"]?.let { id ->
                            Item(
                                Kind.MOVIE, id, m["name"]?.trim() ?: id,
                                image = m["stream_icon"].clean(),
                                categoryId = cat,
                                added = m["added"]?.toLongOrNull() ?: 0,
                                ext = m["container_extension"].clean(),
                                rating = m["rating"].clean(),
                            )
                        }
                        Kind.SERIES -> m["series_id"]?.let { id ->
                            Item(
                                Kind.SERIES, id, m["name"]?.trim() ?: id,
                                image = m["cover"].clean(),
                                categoryId = cat,
                                added = m["last_modified"]?.toLongOrNull() ?: 0,
                                rating = m["rating"].clean(),
                            )
                        }
                    }
                    if (item != null) out.add(item)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (out.isEmpty()) throw IOException("El servidor devolvió una respuesta no válida.")
            }
            out
        }
    }

    override suspend fun movieDetail(item: Item): MovieDetail = io {
        val o = json(api("get_vod_info", "&vod_id=${item.id}"))
        val info = o.optJSONObject("info") ?: JSONObject()
        val md = o.optJSONObject("movie_data")
        val secs = info.str("duration_secs")?.toLongOrNull()
        val dur = secs?.takeIf { it > 0 }?.let { fmtDuration(it) } ?: info.str("duration")?.let { fmtHms(it) }
        MovieDetail(
            item = item.copy(ext = md?.str("container_extension") ?: item.ext),
            title = item.name,
            plot = info.str("plot") ?: info.str("description"),
            year = yearOf(info.str("releasedate") ?: info.str("release_date") ?: info.str("year")),
            duration = dur,
            genre = info.str("genre"),
            poster = info.str("movie_image") ?: info.str("cover_big") ?: item.image,
            backdrop = info.firstStr("backdrop_path"),
            trailer = info.str("youtube_trailer"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            rating = info.str("rating") ?: item.rating,
        )
    }

    override suspend fun seriesDetail(item: Item): SeriesDetail = io {
        val o = json(api("get_series_info", "&series_id=${item.id}"))
        val info = o.optJSONObject("info") ?: JSONObject()
        val seasons = sortedMapOf<Int, MutableList<Episode>>()

        fun addEp(e: JSONObject, seasonKey: Int?) {
            val id = e.str("id") ?: return
            val season = e.str("season")?.toIntOrNull() ?: seasonKey ?: 1
            val num = e.str("episode_num")?.toIntOrNull() ?: 0
            val ei = e.optJSONObject("info")
            val ext = e.str("container_extension") ?: "mp4"
            val dur = ei?.str("duration_secs")?.toLongOrNull()?.takeIf { it > 0 }?.let { fmtDuration(it) }
                ?: ei?.str("duration")?.let { fmtHms(it) }
            val ep = Episode(
                id = id,
                season = season,
                number = num,
                title = cleanEpisodeTitle(e.str("title") ?: "Episodio $num"),
                plot = ei?.str("plot"),
                image = ei?.str("movie_image"),
                duration = dur,
                url = "$base/series/$u/$p/$id.$ext",
            )
            seasons.getOrPut(season) { mutableListOf() }.add(ep)
        }

        when (val eps = o.opt("episodes")) {
            is JSONObject -> {
                val keys = eps.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = eps.optJSONArray(k) ?: continue
                    for (i in 0 until arr.length()) {
                        val e = arr.optJSONObject(i) ?: continue
                        addEp(e, k.toIntOrNull())
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until eps.length()) {
                    val x = eps.opt(i)
                    if (x is JSONArray) {
                        for (j in 0 until x.length()) {
                            val e = x.optJSONObject(j) ?: continue
                            addEp(e, null)
                        }
                    } else if (x is JSONObject) {
                        addEp(x, null)
                    }
                }
            }
        }

        SeriesDetail(
            item = item,
            title = item.name,
            plot = info.str("plot"),
            year = yearOf(info.str("releaseDate") ?: info.str("release_date")),
            genre = info.str("genre"),
            poster = info.str("cover") ?: item.image,
            backdrop = info.firstStr("backdrop_path"),
            cast = info.str("cast"),
            rating = info.str("rating") ?: item.rating,
            seasons = seasons.entries.map { it.key to it.value.sortedBy { e -> e.number } },
        )
    }

    override suspend fun epg(item: Item): List<EpgEntry> = io {
        if (item.kind != Kind.LIVE) return@io emptyList()
        val o = json(api("get_short_epg", "&stream_id=${item.id}&limit=8"))
        val arr = o.optJSONArray("epg_listings") ?: return@io emptyList()
        val now = System.currentTimeMillis()
        val out = ArrayList<EpgEntry>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val s = e.str("start_timestamp")?.toLongOrNull() ?: continue
            val en = e.str("stop_timestamp")?.toLongOrNull() ?: continue
            if (en * 1000 <= now) continue
            out.add(EpgEntry(b64(e.str("title")) ?: "", b64(e.str("description")), s * 1000, en * 1000))
        }
        out.sortedBy { it.start }
    }

    override suspend fun fullEpg(item: Item): List<EpgEntry> = io {
        if (item.kind != Kind.LIVE) return@io emptyList()
        // Guardada en el aparato (2 h): al volver a la guía no hay que pedirla otra vez.
        val o = DiskCache.read(api("get_simple_data_table", "&stream_id=${item.id}"), DiskCache.GUIDE_AGE, valid = { it.has("epg_listings") }) { input ->
            try {
                JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
            } catch (e: JSONException) {
                JSONObject()
            }
        }
        val arr = o.optJSONArray("epg_listings") ?: return@io emptyList()
        val out = ArrayList<EpgEntry>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val s = e.str("start_timestamp")?.toLongOrNull() ?: continue
            val en = e.str("stop_timestamp")?.toLongOrNull() ?: continue
            out.add(
                EpgEntry(
                    b64(e.str("title")) ?: "", b64(e.str("description")), s * 1000, en * 1000,
                    // Grabado de verdad en el servidor (si el proveedor lo indica en cada programa).
                    archived = if (e.has("has_archive") && !e.isNull("has_archive")) e.optString("has_archive") == "1" else null,
                )
            )
        }
        out.sortedBy { it.start }.distinctBy { it.start }
    }

    /** Diferencia entre la hora del servidor y la UTC (cada proveedor usa la de su país). */
    private fun learnServerClock(o: JSONObject) {
        val si = o.optJSONObject("server_info") ?: return
        val ts = si.str("timestamp_now")?.toLongOrNull()
        val wall = si.str("time_now")
        if (ts != null && wall != null) {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val w = runCatching { f.parse(wall)?.time }.getOrNull()
            if (w != null) {
                val q = 15 * 60_000L
                serverOffset[base] = Math.round((w - ts * 1000) / q.toDouble()) * q
                return
            }
        }
        si.str("timezone")?.let { tz ->
            val z = java.util.TimeZone.getTimeZone(tz)
            if (z.id == tz) serverOffset[base] = z.getOffset(System.currentTimeMillis()).toLong()
        }
    }

    /**
     * Direcciones posibles para ver un programa ya emitido (o desde el principio), en orden de preferencia.
     * Cada proveedor acepta una: /timeshift/… o timeshift.php, con la hora del servidor (o la local).
     */
    override fun catchupUrls(item: Item, start: Long, minutes: Int): List<String> {
        if (item.kind != Kind.LIVE || item.archive <= 0) return emptyList()
        val min = minutes.coerceAtLeast(1)
        fun fmt(offset: Long?): String {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US)
            if (offset != null) {
                f.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return f.format(java.util.Date(start + offset))
            }
            return f.format(java.util.Date(start))
        }
        val times = listOfNotNull(serverOffset[base]?.let { fmt(it) }, fmt(null)).distinct()
        // Programa que aún se está emitiendo: algunos servidores solo aceptan la parte ya grabada (hasta ahora).
        val elapsed = ((System.currentTimeMillis() - start) / 60_000L).toInt()
        val durations = if (elapsed in 1 until min) listOf(min, elapsed) else listOf(min)
        val out = ArrayList<String>()
        for (d in durations) for (t in times) {
            out += "$base/timeshift/$u/$p/$d/$t/${item.id}.ts"
            out += "$base/streaming/timeshift.php?username=$u&password=$p&stream=${item.id}&start=$t&duration=$d"
            out += "$base/timeshift/$u/$p/$d/$t/${item.id}.m3u8"
        }
        return out
    }

    companion object {
        /** Desfase horario de cada servidor (milisegundos respecto a UTC). */
        private val serverOffset = java.util.concurrent.ConcurrentHashMap<String, Long>()
    }

    override fun streamUrl(item: Item): String = when (item.kind) {
        Kind.LIVE -> "$base/live/$u/$p/${item.id}.${if (cfg.liveFormat == "m3u8") "m3u8" else "ts"}"
        Kind.MOVIE -> "$base/movie/$u/$p/${item.id}.${item.ext ?: "mp4"}"
        Kind.SERIES -> ""
    }
}
