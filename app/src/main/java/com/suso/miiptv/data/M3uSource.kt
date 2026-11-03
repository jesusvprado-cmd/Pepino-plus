package com.suso.miiptv.data

import android.util.Xml
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

class M3uSource(private val cfg: Config) : Source {
    private val mutex = Mutex()
    private var loaded = false
    private val cats = HashMap<Kind, MutableList<Category>>()
    private val all = HashMap<Kind, MutableList<Item>>()
    private val episodes = HashMap<String, MutableList<Episode>>()
    private var tvgUrl: String? = null

    private val epgMutex = Mutex()
    private var epgLoaded = false
    private val epg = HashMap<String, MutableList<EpgEntry>>()

    private suspend fun ensure() {
        mutex.withLock {
            if (!loaded) {
                io { parse() }
                loaded = true
            }
        }
    }

    // ---------- Lista M3U ----------

    private fun parse() {
        // La lista se guarda en el aparato (6 h): la app arranca sin esperar a descargarla.
        DiskCache.read(cfg.m3uUrl, DiskCache.CATALOG_AGE, valid = { it }) { input ->
            for (k in Kind.values()) {
                cats[k] = mutableListOf()
                all[k] = mutableListOf()
            }
            episodes.clear()
            val catSeen = HashMap<Kind, HashSet<String>>()
            val seriesIdx = HashMap<String, Item>()

            fun addCat(kind: Kind, group: String) {
                val seen = catSeen.getOrPut(kind) { HashSet() }
                if (seen.add(group)) cats[kind]!!.add(Category(group, group))
            }

            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            var attrs: Map<String, String> = emptyMap()
            var name = ""
            var pending = false
            var lineNo = 0L
            while (true) {
                val raw = reader.readLine() ?: break
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#EXTM3U")) {
                    val a = parseAttrs(line)
                    tvgUrl = (a["url-tvg"] ?: a["x-tvg-url"])?.split(',')?.firstOrNull()?.trim()?.ifEmpty { null }
                    continue
                }
                if (line.startsWith("#EXTINF")) {
                    attrs = parseAttrs(line)
                    name = extinfName(line)
                    pending = true
                    continue
                }
                if (line.startsWith("#EXTGRP:")) {
                    if (attrs["group-title"].isNullOrBlank()) attrs = attrs + ("group-title" to line.substringAfter(':').trim())
                    continue
                }
                if (line.startsWith("#")) continue
                if (!pending) continue
                pending = false

                val url = line
                val group = attrs["group-title"]?.trim()?.takeIf { it.isNotEmpty() } ?: "Sin categoría"
                val title = name.ifBlank { attrs["tvg-name"] ?: url }
                val logo = attrs["tvg-logo"].clean()
                var kind = classify(url)
                val se = SE_REGEX.find(title)
                if (kind == Kind.MOVIE && se != null) kind = Kind.SERIES
                lineNo++

                when (kind) {
                    Kind.LIVE, Kind.MOVIE -> {
                        addCat(kind, group)
                        val list = all[kind]!!
                        list.add(
                            Item(
                                kind, url, title, logo, group,
                                epgId = attrs["tvg-id"].clean(),
                                num = list.size + 1,
                                added = lineNo,
                                url = url,
                            )
                        )
                    }
                    Kind.SERIES -> {
                        val sName = se?.groupValues?.get(1)?.trim()?.trimEnd('-', '|', ':', '.', ' ')?.trim()
                            ?.ifEmpty { null } ?: title
                        val sid = "$group|$sName"
                        if (!seriesIdx.containsKey(sid)) {
                            addCat(Kind.SERIES, group)
                            val s = Item(Kind.SERIES, sid, sName, logo, group, added = lineNo)
                            seriesIdx[sid] = s
                            all[Kind.SERIES]!!.add(s)
                        }
                        val epList = episodes.getOrPut(sid) { mutableListOf() }
                        val season = se?.groupValues?.get(2)?.toIntOrNull() ?: 1
                        val number = se?.groupValues?.get(3)?.toIntOrNull() ?: (epList.size + 1)
                        val epTitle = if (se != null) cleanEpisodeTitle(title).takeIf { it != title } else null
                        epList.add(Episode(url, season, number, epTitle ?: "Episodio $number", null, logo, null, url))
                    }
                }
            }
            all.values.any { it.isNotEmpty() }
        }
        if (all.values.all { it.isEmpty() }) throw IOException("La lista está vacía o no es una lista M3U válida.")
    }

    private fun classify(url: String): Kind {
        val l = url.lowercase()
        return when {
            "/series/" in l -> Kind.SERIES
            "/movie/" in l -> Kind.MOVIE
            VOD_EXT.containsMatchIn(l) -> Kind.MOVIE
            else -> Kind.LIVE
        }
    }

    private fun parseAttrs(line: String): Map<String, String> {
        val m = HashMap<String, String>()
        ATTR_REGEX.findAll(line).forEach { m[it.groupValues[1].lowercase()] = it.groupValues[2] }
        return m
    }

    private fun extinfName(line: String): String {
        var inQuote = false
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuote = !inQuote
            else if (c == ',' && !inQuote) return line.substring(i + 1).trim()
        }
        return ""
    }

    // ---------- Source ----------

    override suspend fun check(): AccountInfo? {
        ensure()
        return null
    }

    override suspend fun categories(kind: Kind): List<Category> {
        ensure()
        return cats[kind] ?: emptyList()
    }

    override suspend fun items(kind: Kind, categoryId: String?): List<Item> {
        ensure()
        val l = all[kind] ?: return emptyList()
        return if (categoryId == null) l else l.filter { it.categoryId == categoryId }
    }

    override suspend fun movieDetail(item: Item): MovieDetail = MovieDetail(
        item = item, title = item.name, plot = null, year = yearOf(item.name), duration = null,
        genre = null, poster = item.image, backdrop = null, trailer = null, cast = null,
        director = null, rating = null,
    )

    override suspend fun seriesDetail(item: Item): SeriesDetail {
        ensure()
        val eps = episodes[item.id] ?: emptyList()
        val bySeason = eps.groupBy { it.season }.toSortedMap().map { (s, l) -> s to l.sortedBy { it.number } }
        return SeriesDetail(item, item.name, null, yearOf(item.name), null, item.image, null, null, null, bySeason)
    }

    override fun streamUrl(item: Item): String = item.url ?: ""

    override suspend fun epg(item: Item): List<EpgEntry> {
        if (item.kind != Kind.LIVE) return emptyList()
        ensure()
        epgMutex.withLock {
            if (!epgLoaded) {
                epgLoaded = true
                val url = cfg.epgUrl.ifBlank { tvgUrl ?: "" }
                if (url.isNotBlank()) {
                    try {
                        io { loadXmltv(url) }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        // Sin guía: no es un error grave.
                    }
                }
            }
        }
        val now = System.currentTimeMillis()
        val l = item.epgId?.let { epg["id:" + it.lowercase()] } ?: epg["name:" + item.name.lowercase()] ?: return emptyList()
        return l.filter { it.end > now }
    }

    // ---------- Guía XMLTV ----------

    private val fmtTz = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val fmtPlain = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    private fun parseTime(s: String?): Long {
        if (s == null) return 0
        var t = s.trim()
        if (t.length > 14 && t[14] != ' ') t = t.substring(0, 14) + " " + t.substring(14)
        return try {
            if (t.length > 15) fmtTz.parse(t)?.time ?: 0 else fmtPlain.parse(t.take(14))?.time ?: 0
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            0
        }
    }

    private fun loadXmltv(url: String) {
        val live = all[Kind.LIVE] ?: return
        val wantIds = HashSet<String>()
        val wantNames = HashSet<String>()
        for (it in live) {
            it.epgId?.let { id -> wantIds.add(id.lowercase()) }
            wantNames.add(it.name.lowercase())
        }
        val now = System.currentTimeMillis()
        val from = now - 3_600_000L
        val to = now + 14 * 3_600_000L
        val chMap = HashMap<String, String>()

        Http.open(url).use { r ->
            val body = r.body ?: return
            var input: InputStream = BufferedInputStream(body.byteStream(), 65536)
            input.mark(4)
            val b1 = input.read()
            val b2 = input.read()
            input.reset()
            if (b1 == 0x1f && b2 == 0x8b) input = GZIPInputStream(input, 65536)

            val xp = Xml.newPullParser()
            xp.setInput(input, null)
            var ev = xp.eventType
            var curChan: String? = null
            var field = 0
            var pKey: String? = null
            var pStart = 0L
            var pEnd = 0L
            var pTitle: String? = null
            var pDesc: String? = null

            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (xp.name) {
                        "channel" -> {
                            val c = xp.getAttributeValue(null, "id")
                            curChan = c
                            if (c != null && c.lowercase() in wantIds) chMap[c] = "id:" + c.lowercase()
                        }
                        "display-name" -> if (curChan != null) field = 3
                        "programme" -> {
                            val c = xp.getAttributeValue(null, "channel")
                            val key = if (c == null) null else chMap[c] ?: (if (c.lowercase() in wantIds) "id:" + c.lowercase() else null)
                            pStart = parseTime(xp.getAttributeValue(null, "start"))
                            pEnd = parseTime(xp.getAttributeValue(null, "stop"))
                            pKey = if (key != null && pEnd > from && pStart < to) key else null
                            pTitle = null
                            pDesc = null
                        }
                        "title" -> if (pKey != null && pTitle == null) field = 1
                        "desc" -> if (pKey != null && pDesc == null) field = 2
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    when (field) {
                        1 -> pTitle = xp.text
                        2 -> pDesc = xp.text
                        3 -> {
                            val n = xp.text?.trim()?.lowercase()
                            val c = curChan
                            if (n != null && c != null && n in wantNames && !chMap.containsKey(c)) chMap[c] = "name:$n"
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    when (xp.name) {
                        "title", "desc", "display-name" -> field = 0
                        "channel" -> curChan = null
                        "programme" -> {
                            val k = pKey
                            if (k != null) epg.getOrPut(k) { mutableListOf() }.add(EpgEntry(pTitle?.trim() ?: "", pDesc?.trim(), pStart, pEnd))
                            pKey = null
                        }
                    }
                }
                ev = xp.next()
            }
        }
        for (l in epg.values) l.sortBy { it.start }
    }

    companion object {
        private val ATTR_REGEX = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
        private val SE_REGEX = Regex("^(.*?)[\\s._\\-|:]*\\b[Ss](\\d{1,2})[\\s._\\-]*[Ee](\\d{1,3})")
        private val VOD_EXT = Regex("\\.(mp4|mkv|avi|mov|wmv|flv|m4v)(\\?|$)")
    }
}
