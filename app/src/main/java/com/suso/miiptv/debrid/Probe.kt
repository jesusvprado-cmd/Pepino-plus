package com.suso.miiptv.debrid

import com.suso.miiptv.data.Http
import okhttp3.Request
import java.io.IOException

/** Lee las pistas de un MKV descargando solo su principio (unos cientos de KB). */
object Probe {
    private const val FIRST = 512 * 1024
    private const val SECOND = 256 * 1024

    /** null si no es un MKV o no se encuentran las pistas. Lanza IOException si falla la red. */
    fun read(link: String): MkvTracks.Result? {
        val url = AllDebrid.unlock(link)
        val (b, n) = range(url, 0, FIRST) ?: return null
        return when (val o = MkvTracks.parse(b, n)) {
            is MkvTracks.Outcome.Found -> o.r
            is MkvTracks.Outcome.At -> {
                val (b2, n2) = range(url, o.offset.coerceAtLeast(0), SECOND) ?: return null
                MkvTracks.parseAt(b2, n2)
            }
            else -> null
        }
    }

    private fun range(url: String, from: Long, len: Int): Pair<ByteArray, Int>? {
        val req = Request.Builder().url(url)
            .header("User-Agent", "PepinoPlus-Android")
            .header("Range", "bytes=$from-${from + len - 1}")
            .build()
        Http.client.newCall(req).execute().use { r ->
            if (r.code == 404 || r.code == 410) return null
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            // Si el servidor no hace caso del «Range» y manda desde el principio, solo vale la primera lectura.
            if (r.code != 206 && from > 0) return null
            val buf = ByteArray(len)
            var n = 0
            r.body?.byteStream()?.use { s ->
                while (n < len) {
                    val k = s.read(buf, n, len - n)
                    if (k < 0) break
                    n += k
                }
            }
            return buf to n
        }
    }
}
