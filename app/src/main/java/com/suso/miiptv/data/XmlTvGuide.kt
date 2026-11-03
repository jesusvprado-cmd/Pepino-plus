package com.suso.miiptv.data

import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Guía de programación en formato XMLTV: un único archivo con la programación de todos los canales.
 * Se descarga de una vez (y se guarda en el aparato), en vez de pedir la guía canal a canal, y se empareja
 * con los canales de la lista por su id de guía o por el nombre (sin «HD», «FHD», prefijos de país...).
 *
 * [pastMs]: cuánto de lo ya emitido se guarda (la guía en parrilla deja ver hasta un día atrás).
 */
class XmlTvGuide(private val label: String, private val cacheName: String, private val pastMs: Long, private val maxAgeMs: Long) {
    private val mutex = Mutex()
    private var loadedUrl: String? = null
    private var loadedAt = 0L
    private var failedAt = 0L
    private var byKey: Map<String, List<EpgEntry>> = emptyMap()

    /** Para volver a descargarla («Actualizar la guía ahora» o al cambiar de lista). */
    fun reset() {
        loadedUrl = null
        loadedAt = 0
        failedAt = 0
        byKey = emptyMap()
        runCatching { cacheFile()?.delete() }
    }

    private fun cacheFile(): File? = DiskCache.dirOrNull()?.let { File(it, cacheName) }

    private fun nameKey(name: String) = "n:" + Logos.clean(ChannelNames.display(name)).key

    /**
     * Programación de un canal (también lo emitido en las últimas [pastMs]).
     * null = la guía no está disponible (no se pudo descargar o leer): quien llama usa otra fuente.
     */
    suspend fun entries(item: Item, url: String): List<EpgEntry>? {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (loadedUrl != url || now - loadedAt > maxAgeMs) {
                // Si falló hace poco, no se insiste (se usa la guía canal a canal mientras tanto).
                if (loadedUrl == url && failedAt > 0 && now - failedAt < 30 * 60_000L) return null
                try {
                    byKey = withContext(Dispatchers.IO) { load(url) }
                    loadedUrl = url
                    loadedAt = now
                    failedAt = 0
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Throwable: también si la guía es tan grande que no cabe en la memoria del aparato.
                    ErrorLog.once("xmltv-$cacheName", "$label: no se pudo cargar (${e.message ?: e.javaClass.simpleName})")
                    byKey = emptyMap()
                    loadedUrl = url
                    failedAt = now
                    return null
                }
            }
        }
        if (byKey.isEmpty()) return null
        return item.epgId?.let { byKey["i:" + it.lowercase()] } ?: byKey[nameKey(item.name)] ?: emptyList()
    }

    private val fmtTz = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val fmtPlain = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    private fun parseTime(s: String?): Long {
        if (s == null) return 0
        var t = s.trim()
        if (t.length > 14 && t[14] != ' ') t = t.substring(0, 14) + " " + t.substring(14)
        return try {
            if (t.length > 15) fmtTz.parse(t)?.time ?: 0 else fmtPlain.parse(t.take(14))?.time ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /** El archivo se guarda en el aparato: al abrir la app otra vez no hay que descargarlo de nuevo. */
    private fun download(url: String): File {
        val f = cacheFile()
        if (f != null && f.exists() && f.length() > 0 && System.currentTimeMillis() - f.lastModified() < maxAgeMs) return f
        f?.parentFile?.mkdirs()
        val tmp = File((f ?: File.createTempFile("xmltv", null)).path + ".part")
        // Se guarda comprimida (ocupa unas 10 veces menos: importante en aparatos con poco espacio, como el Chromecast).
        Http.open(url).use { r ->
            val body = r.body ?: throw java.io.IOException("Respuesta vacía")
            val input = java.io.BufferedInputStream(body.byteStream(), 65536)
            input.mark(4)
            val gz = input.read() == 0x1f && input.read() == 0x8b
            input.reset()
            tmp.outputStream().use { raw ->
                if (gz) input.copyTo(raw, 65536)
                else java.util.zip.GZIPOutputStream(raw, 65536).use { out -> input.copyTo(out, 65536) }
            }
        }
        if (tmp.length() < 100) {
            tmp.delete()
            throw java.io.IOException("El servidor no da la guía completa")
        }
        if (f == null) return tmp
        f.delete()
        tmp.renameTo(f)
        return f
    }

    private suspend fun load(url: String): Map<String, List<EpgEntry>> {
        // Solo se guardan los canales que hay en tu lista (la guía completa ocupa mucho).
        val live = try {
            Repo.items(Kind.LIVE, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val wantIds = HashSet<String>()
        val wantNames = HashSet<String>()
        for (it in live) {
            it.epgId?.let { id -> wantIds.add(id.lowercase()) }
            wantNames.add(nameKey(it.name))
        }
        val now = System.currentTimeMillis()
        val from = now - pastMs
        val to = now + 24 * 3_600_000L
        // La sinopsis solo de lo cercano (ahora y las próximas horas): así la guía ocupa mucha menos memoria.
        val descTo = now + 12 * 3_600_000L
        val chKeys = HashMap<String, MutableSet<String>>()
        val out = HashMap<String, MutableList<EpgEntry>>()
        val titles = HashMap<String, String>()

        val file = download(url)
        file.inputStream().use { raw ->
            var input: InputStream = BufferedInputStream(raw, 65536)
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
            var pKeys: Set<String>? = null
            var pStart = 0L
            var pEnd = 0L
            var pTitle: String? = null
            var pDesc: String? = null
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (xp.name) {
                        "channel" -> {
                            val c = xp.getAttributeValue(null, "id")
                            curChan = c
                            if (c != null) {
                                val set = chKeys.getOrPut(c) { mutableSetOf() }
                                if (c.lowercase() in wantIds) set += "i:" + c.lowercase()
                                // El id de muchas guías ya es el nombre («La 1 HD», «Antena3.es»...).
                                val nk = nameKey(c.substringBeforeLast('.'))
                                if (nk in wantNames) set += nk
                            }
                        }
                        "display-name" -> if (curChan != null) field = 3
                        "programme" -> {
                            val c = xp.getAttributeValue(null, "channel")
                            pStart = parseTime(xp.getAttributeValue(null, "start"))
                            pEnd = parseTime(xp.getAttributeValue(null, "stop"))
                            val keys = c?.let { chKeys[it] }?.takeIf { it.isNotEmpty() }
                                ?: c?.lowercase()?.takeIf { it in wantIds }?.let { setOf("i:$it") }
                            pKeys = if (keys != null && pEnd > from && pStart < to) keys else null
                            pTitle = null
                            pDesc = null
                        }
                        "title" -> if (pKeys != null && pTitle == null) field = 1
                        "desc" -> if (pKeys != null && pDesc == null && pStart < descTo && pEnd > now - 3_600_000L) field = 2
                    }
                    XmlPullParser.TEXT -> when (field) {
                        1 -> pTitle = xp.text
                        2 -> pDesc = xp.text?.take(600)
                        3 -> {
                            val n = xp.text?.trim()
                            val c = curChan
                            if (!n.isNullOrEmpty() && c != null) {
                                val nk = nameKey(n)
                                if (nk in wantNames) chKeys.getOrPut(c) { mutableSetOf() } += nk
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> when (xp.name) {
                        "title", "desc", "display-name" -> field = 0
                        "channel" -> curChan = null
                        "programme" -> {
                            val ks = pKeys
                            if (ks != null) {
                                // Títulos repetidos (informativos, series) compartidos en memoria.
                                val t = (pTitle?.trim() ?: "").let { titles.getOrPut(it) { it } }
                                val e = EpgEntry(t, pDesc?.trim(), pStart, pEnd)
                                for (k in ks) out.getOrPut(k) { mutableListOf() }.add(e)
                            }
                            pKeys = null
                        }
                    }
                }
                ev = xp.next()
            }
        }
        for (l in out.values) l.sortBy { it.start }
        return out
    }
}

/** Guía externa elegida en Ajustes → Guía (por ejemplo, la de dobleM). */
object ExternalEpg {
    private val guide = XmlTvGuide("Guía externa", "xmltv_externa", 3_600_000L, 6 * 3_600_000L)

    fun reset() = guide.reset()

    suspend fun epg(item: Item): List<EpgEntry> {
        val url = Repo.prefs.epgUrl.trim()
        if (url.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return guide.entries(item, url)?.filter { it.end > now } ?: emptyList()
    }
}

/** Guía completa del propio proveedor (Xtream: xmltv.php), descargada de una vez en vez de canal a canal. */
object ProviderEpg {
    private val guide = XmlTvGuide("Guía completa del proveedor", "xmltv_proveedor", 26 * 3_600_000L, 6 * 3_600_000L)

    fun reset() = guide.reset()

    /** null = no disponible (el proveedor no la da o no se pudo leer): se usa la guía canal a canal. */
    suspend fun entries(item: Item, url: String): List<EpgEntry>? = guide.entries(item, url)
}
