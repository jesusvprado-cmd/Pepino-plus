package com.suso.miiptv.data

import org.json.JSONObject

enum class Kind { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

data class Item(
    val kind: Kind,
    val id: String,
    val name: String,
    val image: String? = null,
    val categoryId: String = "",
    val epgId: String? = null,
    val num: Int = 0,
    val added: Long = 0,
    val ext: String? = null,
    val url: String? = null,
    val rating: String? = null,
    /** Días de programas pasados que guarda el proveedor (0 = no se puede ver lo ya emitido). */
    val archive: Int = 0,
) {
    val key: String get() = "${kind.name}|$id"

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        put("id", id)
        put("name", name)
        putOpt("image", image)
        put("cat", categoryId)
        putOpt("epg", epgId)
        put("num", num)
        put("added", added)
        putOpt("ext", ext)
        putOpt("url", url)
        putOpt("rating", rating)
        if (archive > 0) put("archive", archive)
    }

    companion object {
        fun fromJson(o: JSONObject) = Item(
            kind = Kind.valueOf(o.getString("kind")),
            id = o.getString("id"),
            name = o.optString("name"),
            image = o.str("image"),
            categoryId = o.optString("cat"),
            epgId = o.str("epg"),
            num = o.optInt("num"),
            added = o.optLong("added"),
            ext = o.str("ext"),
            url = o.str("url"),
            rating = o.str("rating"),
            archive = o.optInt("archive"),
        )
    }
}

/** Lo que se le pasa al reproductor. */
data class PlayItem(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val url: String,
    val live: Boolean,
    val channel: Item? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("title", title)
        putOpt("sub", subtitle)
        putOpt("img", image)
        put("url", url)
        put("live", live)
        channel?.let { put("ch", it.toJson()) }
    }

    companion object {
        fun fromJson(o: JSONObject) = PlayItem(
            key = o.getString("key"),
            title = o.optString("title"),
            subtitle = o.str("sub"),
            image = o.str("img"),
            url = o.getString("url"),
            live = o.optBoolean("live"),
            channel = o.optJSONObject("ch")?.let { Item.fromJson(it) },
        )
    }
}

data class MovieDetail(
    val item: Item,
    val title: String,
    val plot: String?,
    val year: String?,
    val duration: String?,
    val genre: String?,
    val poster: String?,
    val backdrop: String?,
    val trailer: String?,
    val cast: String?,
    val director: String?,
    val rating: String?,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val plot: String?,
    val image: String?,
    val duration: String?,
    val url: String,
)

data class SeriesDetail(
    val item: Item,
    val title: String,
    val plot: String?,
    val year: String?,
    val genre: String?,
    val poster: String?,
    val backdrop: String?,
    val cast: String?,
    val rating: String?,
    val seasons: List<Pair<Int, List<Episode>>>,
)

/** [archived]: si el proveedor dice que ese programa está grabado (true/false); null si no lo indica. */
data class EpgEntry(val title: String, val desc: String?, val start: Long, val end: Long, val archived: Boolean? = null)

data class AccountInfo(
    val status: String?,
    val expiry: Long?,
    val maxConnections: String?,
    val activeConnections: String?,
)

data class Config(
    val type: String,
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val liveFormat: String = "ts",
    val userAgent: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("server", server)
        put("user", user)
        put("pass", pass)
        put("m3u", m3uUrl)
        put("epg", epgUrl)
        put("fmt", liveFormat)
        put("ua", userAgent)
    }

    companion object {
        fun fromJson(o: JSONObject) = Config(
            type = o.optString("type", "xtream"),
            server = o.optString("server"),
            user = o.optString("user"),
            pass = o.optString("pass"),
            m3uUrl = o.optString("m3u"),
            epgUrl = o.optString("epg"),
            liveFormat = o.optString("fmt", "ts"),
            userAgent = o.optString("ua"),
        )
    }
}

interface Source {
    /** Comprueba que la lista funciona. Lanza una excepción con un mensaje legible si no. */
    suspend fun check(): AccountInfo?
    suspend fun categories(kind: Kind): List<Category>
    /** categoryId = null devuelve todo el catálogo de ese tipo. */
    suspend fun items(kind: Kind, categoryId: String?): List<Item>
    suspend fun movieDetail(item: Item): MovieDetail
    suspend fun seriesDetail(item: Item): SeriesDetail
    suspend fun epg(item: Item): List<EpgEntry>
    /** Programación completa (también lo ya emitido, si el proveedor lo guarda), para la guía en parrilla. */
    suspend fun fullEpg(item: Item): List<EpgEntry> = epg(item)
    fun streamUrl(item: Item): String
    /** Dirección para ver un programa ya emitido (o desde el principio); null si el proveedor no lo permite. */
    fun catchupUrls(item: Item, start: Long, minutes: Int): List<String> = emptyList()
    /** Guía completa de todos los canales en un solo archivo (XMLTV); null si la lista no la da. */
    fun xmltvUrl(): String? = null
}
