package com.suso.miiptv.debrid

/**
 * Lee la lista de pistas (audio, subtítulos, vídeo) del principio de un archivo MKV, sin descargarlo:
 * basta con el primer trozo del archivo.
 */
object MkvTracks {
    data class Track(val type: Int, val lang: String, val codec: String, val name: String, val channels: Int, val default: Boolean, val forced: Boolean)

    data class Result(
        val width: Int,
        val height: Int,
        /** "DV", "HDR" o null. */
        val hdr: String?,
        val videoCodec: String?,
        val audio: List<Track>,
        val subs: List<Track>,
    )

    sealed interface Outcome {
        data class Found(val r: Result) : Outcome
        /** Las pistas están más adelante en el archivo: hay que leer desde esa posición. */
        data class At(val offset: Long) : Outcome
        data object NotMkv : Outcome
        data object Unknown : Outcome
    }

    private const val EBML = 0x1A45DFA3L
    private const val SEGMENT = 0x18538067L
    private const val SEEKHEAD = 0x114D9B74L
    private const val SEEK = 0x4DBBL
    private const val SEEK_ID = 0x53ABL
    private const val SEEK_POS = 0x53ACL
    private const val TRACKS = 0x1654AE6BL
    private const val CLUSTER = 0x1F43B675L
    private const val TRACK_ENTRY = 0xAEL

    private class Reader(val b: ByteArray, var p: Int, val end: Int) {
        fun more() = p < end
        /** Identificador (con sus bits de longitud). */
        fun id(): Long {
            if (p >= end) return -1
            val first = b[p].toInt() and 0xFF
            val len = when {
                first and 0x80 != 0 -> 1
                first and 0x40 != 0 -> 2
                first and 0x20 != 0 -> 3
                first and 0x10 != 0 -> 4
                else -> return -1
            }
            if (p + len > end) return -1
            var v = 0L
            for (i in 0 until len) v = (v shl 8) or (b[p + i].toLong() and 0xFF)
            p += len
            return v
        }
        /** Tamaño (-1 = desconocido, -2 = error). */
        fun size(): Long {
            if (p >= end) return -2
            val first = b[p].toInt() and 0xFF
            var len = 1
            var mask = 0x80
            while (len <= 8 && first and mask == 0) {
                mask = mask shr 1
                len++
            }
            if (len > 8 || p + len > end) return -2
            var v = (first and (mask - 1)).toLong()
            var allOnes = v == (mask - 1).toLong()
            for (i in 1 until len) {
                val x = b[p + i].toLong() and 0xFF
                if (x != 0xFFL) allOnes = false
                v = (v shl 8) or x
            }
            p += len
            return if (allOnes) -1 else v
        }
    }

    private fun uint(b: ByteArray, p: Int, n: Int): Long {
        var v = 0L
        for (i in 0 until n.coerceAtMost(8)) v = (v shl 8) or (b[p + i].toLong() and 0xFF)
        return v
    }

    private fun str(b: ByteArray, p: Int, n: Int): String = String(b, p, n, Charsets.UTF_8).trimEnd('\u0000').trim()

    /**
     * [b]: bytes leídos desde el principio del archivo ([base] = 0) o desde la posición [base] (segunda lectura,
     * entonces [segmentData] es dónde empiezan los datos del Segment, sacado de la primera).
     */
    fun parse(b: ByteArray, len: Int = b.size): Outcome {
        val r = Reader(b, 0, len)
        if (r.id() != EBML) return Outcome.NotMkv
        val hs = r.size()
        if (hs < 0) return Outcome.Unknown
        r.p += hs.toInt()
        if (r.id() != SEGMENT) return Outcome.NotMkv
        r.size() // el Segment suele tener tamaño desconocido o enorme: se recorre por dentro
        val segData = r.p
        var tracksAt = -1L
        while (r.more()) {
            val id = r.id()
            if (id < 0) break
            val sz = r.size()
            if (sz < 0) break
            val start = r.p
            val stop = start + sz
            when (id) {
                TRACKS -> {
                    if (stop > len) return Outcome.At(start.toLong() - 12) // cortado: se vuelve a leer desde ahí
                    return Outcome.Found(tracks(b, start, stop.toInt()))
                }
                SEEKHEAD -> if (stop <= len) tracksAt = seekTracks(b, start, stop.toInt())?.let { segData + it } ?: tracksAt
                CLUSTER -> break
            }
            if (stop > len) break
            r.p = stop.toInt()
        }
        return if (tracksAt > 0) Outcome.At(tracksAt) else Outcome.Unknown
    }

    /** Segunda lectura: [b] empieza justo en el elemento Tracks (o un poco antes). */
    fun parseAt(b: ByteArray, len: Int = b.size): Result? {
        // Se busca la marca de Tracks en lo leído.
        var i = 0
        while (i + 4 < len) {
            if ((b[i].toInt() and 0xFF) == 0x16 && (b[i + 1].toInt() and 0xFF) == 0x54 &&
                (b[i + 2].toInt() and 0xFF) == 0xAE && (b[i + 3].toInt() and 0xFF) == 0x6B
            ) {
                val r = Reader(b, i + 4, len)
                val sz = r.size()
                if (sz <= 0) return null
                val stop = (r.p + sz).coerceAtMost(len.toLong()).toInt()
                return tracks(b, r.p, stop)
            }
            i++
        }
        return null
    }

    private fun seekTracks(b: ByteArray, from: Int, to: Int): Long? {
        val r = Reader(b, from, to)
        while (r.more()) {
            val id = r.id()
            val sz = r.size()
            if (id < 0 || sz < 0) return null
            val stop = (r.p + sz).toInt()
            if (id == SEEK) {
                val s = Reader(b, r.p, stop)
                var sid = 0L
                var pos = -1L
                while (s.more()) {
                    val cid = s.id()
                    val csz = s.size()
                    if (cid < 0 || csz < 0) break
                    when (cid) {
                        SEEK_ID -> sid = uint(b, s.p, csz.toInt())
                        SEEK_POS -> pos = uint(b, s.p, csz.toInt())
                    }
                    s.p += csz.toInt()
                }
                if (sid == TRACKS && pos >= 0) return pos
            }
            r.p = stop
        }
        return null
    }

    private fun tracks(b: ByteArray, from: Int, to: Int): Result {
        val r = Reader(b, from, to)
        val audio = ArrayList<Track>()
        val subs = ArrayList<Track>()
        var w = 0
        var h = 0
        var hdr: String? = null
        var vcodec: String? = null
        while (r.more()) {
            val id = r.id()
            val sz = r.size()
            if (id < 0 || sz < 0) break
            val stop = (r.p + sz).coerceAtMost(to.toLong()).toInt()
            if (id == TRACK_ENTRY) {
                var type = 0
                var lang = "eng" // valor por defecto de Matroska si no lo dice
                var bcp: String? = null
                var codec = ""
                var name = ""
                var ch = 0
                var def = true
                var forced = false
                var tw = 0
                var th = 0
                var transfer = 0
                var dv = false
                val e = Reader(b, r.p, stop)
                while (e.more()) {
                    val cid = e.id()
                    val csz = e.size()
                    if (cid < 0 || csz < 0) break
                    val cs = e.p
                    val ce = (cs + csz).coerceAtMost(stop.toLong()).toInt()
                    val n = ce - cs
                    when (cid) {
                        0x83L -> type = uint(b, cs, n).toInt()
                        0x22B59CL -> lang = str(b, cs, n)
                        0x22B59DL -> bcp = str(b, cs, n)
                        0x86L -> codec = str(b, cs, n)
                        0x536EL -> name = str(b, cs, n)
                        0x88L -> def = uint(b, cs, n) != 0L
                        0x55AAL -> forced = uint(b, cs, n) != 0L
                        0xE1L -> { // Audio
                            val a = Reader(b, cs, ce)
                            while (a.more()) {
                                val aid = a.id(); val asz = a.size()
                                if (aid < 0 || asz < 0) break
                                if (aid == 0x9FL) ch = uint(b, a.p, asz.toInt()).toInt()
                                a.p += asz.toInt()
                            }
                        }
                        0xE0L -> { // Vídeo
                            val v = Reader(b, cs, ce)
                            while (v.more()) {
                                val vid = v.id(); val vsz = v.size()
                                if (vid < 0 || vsz < 0) break
                                val vs = v.p
                                when (vid) {
                                    0xB0L -> tw = uint(b, vs, vsz.toInt()).toInt()
                                    0xBAL -> th = uint(b, vs, vsz.toInt()).toInt()
                                    0x55B0L -> { // Colour
                                        val c = Reader(b, vs, vs + vsz.toInt())
                                        while (c.more()) {
                                            val kid = c.id(); val ksz = c.size()
                                            if (kid < 0 || ksz < 0) break
                                            if (kid == 0x55BAL) transfer = uint(b, c.p, ksz.toInt()).toInt()
                                            c.p += ksz.toInt()
                                        }
                                    }
                                }
                                v.p += vsz.toInt()
                            }
                        }
                        0x41E4L -> { // BlockAdditionMapping (Dolby Vision)
                            val m = Reader(b, cs, ce)
                            while (m.more()) {
                                val mid = m.id(); val msz = m.size()
                                if (mid < 0 || msz < 0) break
                                if (mid == 0x41E7L) {
                                    val t = uint(b, m.p, msz.toInt())
                                    if (t == 0x64766343L || t == 0x64767643L || t == 0x64767745L) dv = true
                                }
                                m.p += msz.toInt()
                            }
                        }
                    }
                    e.p = ce
                }
                when (type) {
                    1 -> {
                        if (w == 0) {
                            w = tw; h = th; vcodec = codec
                            hdr = when {
                                dv -> "DV"
                                transfer == 16 || transfer == 18 -> "HDR"
                                else -> null
                            }
                        }
                    }
                    2 -> audio += Track(2, langCode(bcp ?: lang, name), codec, name, ch, def, forced)
                    17 -> subs += Track(17, langCode(bcp ?: lang, name), codec, name, 0, def, forced)
                }
            }
            r.p = stop
        }
        return Result(w, h, hdr, vcodec, audio, subs)
    }

    /** Idioma de la pista en el formato de la app: "es" (castellano), "lat", "en", "fr"... */
    fun langCode(raw: String, name: String): String {
        val l = raw.lowercase().replace('_', '-')
        val n = name.lowercase()
        val base = l.substringBefore('-')
        val spanish = base == "spa" || base == "es" || base == "esp"
        if (spanish) {
            if (l.contains("419") || l.endsWith("-mx") || l.endsWith("-ar") || l.endsWith("-co") ||
                Regex("""latino|latin|lat\b|latam|méxico|mexico|hispanoam""").containsMatchIn(n)
            ) return "lat"
            return "es"
        }
        return when (base) {
            "eng", "en" -> "en"
            "fre", "fra", "fr" -> "fr"
            "ger", "deu", "de" -> "de"
            "ita", "it" -> "it"
            "jpn", "ja" -> "ja"
            "por", "pt" -> "pt"
            "cat", "ca" -> "ca"
            "glg", "gl" -> "gl"
            "baq", "eus", "eu" -> "eu"
            "kor", "ko" -> "ko"
            "chi", "zho", "zh" -> "zh"
            "rus", "ru" -> "ru"
            "und", "" -> "?"
            else -> base.take(3)
        }
    }
}
