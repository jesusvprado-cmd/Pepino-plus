package com.suso.miiptv.debrid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.suso.miiptv.data.ErrorLog
import com.suso.miiptv.data.Http
import com.suso.miiptv.data.Secure
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Cuenta de AllDebrid: la clave (cifrada en el aparato), las llamadas a la API y el paso de
 * «enlace guardado» a «dirección de vídeo» (desbloquear).
 *
 * La clave va siempre en la cabecera «Authorization: Bearer», nunca en una dirección.
 */
object AllDebrid {
    private const val API = "https://api.alldebrid.com"
    private const val AGENT = "pepinoplus"

    /** Prefijo de las direcciones «perezosas»: el reproductor las desbloquea justo antes de abrirlas. */
    const val LAZY = "alldebrid:"

    private lateinit var appCtx: Context
    private val sp by lazy { appCtx.getSharedPreferences("alldebrid", Context.MODE_PRIVATE) }

    /** Cambia al vincular o desvincular la cuenta y al cambiar un ajuste de esta sección. */
    var version by mutableIntStateOf(0)
        private set

    fun init(ctx: Context) {
        if (::appCtx.isInitialized) return
        appCtx = ctx.applicationContext
    }

    fun bump() {
        version++
    }

    // ---------- Clave y ajustes ----------

    private var keyCache: String? = null

    val apiKey: String?
        get() {
            keyCache?.let { return it }
            val k = Secure.open(sp.getString("key", null))?.takeIf { it.isNotBlank() }
            keyCache = k
            return k
        }

    val linked: Boolean get() = apiKey != null

    fun setKey(k: String?) {
        keyCache = null
        sp.edit().putString("key", k?.let { Secure.seal(it.trim()) }).apply()
        if (k == null) sp.edit().remove("user").apply()
        version++
    }

    /** Nombre de usuario y fecha de fin de la suscripción (lo último que se leyó). */
    var userName: String?
        get() = sp.getString("user", null)
        set(v) {
            sp.edit().putString("user", v).apply()
        }
    var premiumUntil: Long
        get() = sp.getLong("premium", 0)
        set(v) {
            sp.edit().putLong("premium", v).apply()
        }

    fun str(k: String, def: String): String = sp.getString(k, def) ?: def
    fun putStr(k: String, v: String) {
        sp.edit().putString(k, v).apply()
        version++
    }
    fun bool(k: String, def: Boolean): Boolean = sp.getBoolean(k, def)
    fun putBool(k: String, v: Boolean) {
        sp.edit().putBoolean(k, v).apply()
        version++
    }

    /** Clave de TMDB (opcional): con ella las fichas salen en español. */
    var tmdbKey: String
        get() = Secure.open(sp.getString("tmdb", null)).orEmpty()
        set(v) {
            sp.edit().putString("tmdb", if (v.isBlank()) null else Secure.seal(v.trim())).apply()
            version++
        }

    // ---------- Llamadas ----------

    class ApiError(val code: String, msg: String) : IOException(msg)

    private fun readable(code: String, msg: String): String = when (code) {
        "AUTH_BAD_APIKEY", "AUTH_MISSING_APIKEY" -> "La clave de AllDebrid no es válida. Vuelve a vincular la cuenta."
        "AUTH_BLOCKED" -> "AllDebrid ha bloqueado el acceso: entra en alldebrid.com y confirma el aviso de seguridad."
        "AUTH_USER_BANNED" -> "La cuenta de AllDebrid está bloqueada."
        "MUST_BE_PREMIUM" -> "Hace falta una cuenta premium de AllDebrid."
        "LINK_HOST_NOT_SUPPORTED", "LINK_DOWN" -> "Ese archivo ya no está disponible."
        "LINK_IS_MISSING", "LINK_NOT_SUPPORTED" -> "Enlace no válido."
        "FREE_TRIAL_LIMIT_REACHED" -> "Se ha acabado la prueba gratuita de AllDebrid."
        else -> msg.ifBlank { code }
    }

    /** Llama a la API (POST con formulario) y devuelve el bloque «data». */
    fun call(path: String, form: List<Pair<String, String>> = emptyList(), auth: Boolean = true): JSONObject {
        val key = if (auth) apiKey ?: throw ApiError("AUTH_MISSING_APIKEY", "No has vinculado tu cuenta de AllDebrid.") else null
        val body = FormBody.Builder().apply {
            add("agent", AGENT)
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        val b = Request.Builder().url(API + path).post(body).header("User-Agent", "PepinoPlus-Android")
        if (key != null) b.header("Authorization", "Bearer $key")
        val text = try {
            Http.client.newCall(b.build()).execute().use { r ->
                val t = r.body?.string().orEmpty()
                if (!r.isSuccessful && !t.trimStart().startsWith("{")) {
                    if (r.code == 429) throw IOException("AllDebrid pide esperar un poco (demasiadas peticiones).")
                    throw IOException("AllDebrid respondió con un error (HTTP ${r.code}).")
                }
                t
            }
        } catch (e: java.net.UnknownHostException) {
            throw IOException("Sin conexión con AllDebrid. Revisa internet.")
        } catch (e: java.net.SocketTimeoutException) {
            throw IOException("AllDebrid tarda demasiado en responder.")
        }
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("Respuesta de AllDebrid no válida.")
        }
        if (o.optString("status") != "success") {
            val err = o.optJSONObject("error")
            val code = err?.optString("code").orEmpty()
            val msg = err?.optString("message").orEmpty()
            if (code == "AUTH_BAD_APIKEY") ErrorLog.add("AllDebrid: clave rechazada")
            throw ApiError(code, readable(code, msg))
        }
        return o.optJSONObject("data") ?: JSONObject()
    }

    // ---------- Vincular con PIN ----------

    data class Pin(val pin: String, val check: String, val url: String, val expiresAt: Long)

    fun pinGet(): Pin {
        val d = call("/v4/pin/get", auth = false)
        val pin = d.optString("pin")
        val url = d.optString("user_url").ifBlank { "https://alldebrid.com/pin/?pin=$pin" }
        return Pin(pin, d.optString("check"), url, System.currentTimeMillis() + d.optLong("expires_in", 600) * 1000)
    }

    /** Devuelve la clave cuando el usuario ya ha confirmado el PIN en la web; null si aún no. */
    fun pinCheck(p: Pin): String? {
        val d = call("/v4/pin/check", listOf("check" to p.check, "pin" to p.pin), auth = false)
        return if (d.optBoolean("activated")) d.optString("apikey").takeIf { it.isNotBlank() } else null
    }

    /** Comprueba la clave y guarda usuario y fecha de fin. Devuelve un texto para mostrar. */
    fun checkUser(): String {
        val u = call("/v4/user").optJSONObject("user") ?: JSONObject()
        userName = u.optString("username").ifBlank { null }
        premiumUntil = u.optLong("premiumUntil") * 1000
        version++
        return userName ?: "Cuenta vinculada"
    }

    // ---------- Contenido de la cuenta ----------

    data class Magnet(
        val id: Long,
        val name: String,
        val size: Long,
        val date: Long,
        val ready: Boolean,
        val code: Int = 4,
        /** Texto de estado de AllDebrid (en inglés). */
        val status: String = "",
        val downloaded: Long = 0,
    )

    fun magnets(): List<Magnet> {
        val d = call("/v4.1/magnet/status")
        val arr = d.optJSONArray("magnets") ?: d.optJSONObject("magnets")?.let { JSONArray().put(it) } ?: JSONArray()
        val l = ArrayList<Magnet>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val date = m.optLong("completionDate").takeIf { it > 0 } ?: m.optLong("uploadDate")
            val code = m.optInt("statusCode", -1)
            l += Magnet(
                m.optLong("id"), m.optString("filename"), m.optLong("size"), date * 1000, code == 4,
                code, m.optString("status"), m.optLong("downloaded"),
            )
        }
        return l
    }

    /** Vuelve a intentar torrents que fallaron. */
    fun restart(ids: List<Long>) {
        if (ids.isEmpty()) return
        if (ids.size == 1) call("/v4/magnet/restart", listOf("id" to ids[0].toString()))
        else ids.chunked(50).forEach { c -> call("/v4/magnet/restart", c.map { "ids[]" to it.toString() }) }
    }

    /** Qué le pasa a un torrent que no está listo, en castellano. */
    fun statusText(code: Int): String = when (code) {
        0 -> "En cola"
        1 -> "Descargando"
        2 -> "Preparando (comprimiendo o moviendo)"
        3 -> "Subiendo"
        5 -> "Falló al subir"
        6 -> "Error al descomprimir"
        7 -> "Sin fuentes: no se descargó en 20 minutos"
        8 -> "Archivo demasiado grande"
        9 -> "Error interno de AllDebrid"
        10 -> "La descarga tardó más de 72 horas"
        11 -> "Borrado en el servidor de origen"
        12, 13 -> "Falló al procesarlo"
        14 -> "Error al contactar con el tracker"
        15 -> "Sin fuentes (nadie lo comparte)"
        else -> "Estado desconocido ($code)"
    }

    /** true si el torrent ha fallado (se puede reintentar); false si sigue en marcha. */
    fun failed(code: Int): Boolean = code >= 5

    data class RawFile(val name: String, val path: String, val size: Long, val link: String)

    /** Archivos (con su enlace) de varios torrents a la vez. */
    fun magnetFiles(ids: List<Long>): Map<Long, List<RawFile>> {
        if (ids.isEmpty()) return emptyMap()
        val d = call("/v4/magnet/files", ids.map { "id[]" to it.toString() })
        val arr = d.optJSONArray("magnets") ?: JSONArray()
        val out = HashMap<Long, List<RawFile>>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            if (m.has("error")) continue
            val files = ArrayList<RawFile>()
            flatten(m.optJSONArray("files"), "", files)
            out[m.optLong("id")] = files
        }
        return out
    }

    private fun flatten(a: JSONArray?, parent: String, out: MutableList<RawFile>) {
        if (a == null) return
        for (i in 0 until a.length()) {
            val f = a.optJSONObject(i) ?: continue
            val n = f.optString("n")
            val path = if (parent.isEmpty()) n else "$parent/$n"
            val e = f.optJSONArray("e")
            if (e != null) flatten(e, path, out)
            else {
                val link = f.optString("l")
                if (link.isNotBlank()) out += RawFile(n, path, f.optLong("s"), link)
            }
        }
    }

    data class Saved(val link: String, val name: String, val size: Long, val date: Long)

    /** Enlaces guardados en la cuenta (no torrents). */
    fun savedLinks(): List<Saved> {
        val a = call("/v4/user/links").optJSONArray("links") ?: return emptyList()
        val l = ArrayList<Saved>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val link = o.optString("link")
            if (link.isBlank()) continue
            l += Saved(link, o.optString("filename"), o.optString("size").toLongOrNull() ?: 0L, o.optLong("date") * 1000)
        }
        return l
    }

    // ---------- Desbloquear ----------

    private class Unlocked(val url: String, val at: Long)

    private val unlocked = HashMap<String, Unlocked>()

    /** Las direcciones desbloqueadas valen unas horas; se guardan 3 h para no pedirlas otra vez. */
    private const val UNLOCK_AGE = 3 * 3600_000L

    fun forget(link: String) {
        synchronized(unlocked) { unlocked.remove(link) }
    }

    /** Dirección directa del vídeo (se llama fuera del hilo principal). */
    fun unlock(link: String): String {
        synchronized(unlocked) {
            unlocked[link]?.let { if (System.currentTimeMillis() - it.at < UNLOCK_AGE) return it.url }
        }
        val d = call("/v4/link/unlock", listOf("link" to link))
        var url = d.optString("link")
        val delayed = d.optLong("delayed")
        if (url.isBlank() && delayed > 0) {
            // Archivo que AllDebrid tiene que preparar: se pregunta cada 2 s, como mucho 1 minuto.
            var tries = 0
            while (url.isBlank() && tries < 30) {
                tries++
                Thread.sleep(2000)
                val dd = call("/v4/link/delayed", listOf("id" to delayed.toString()))
                when (dd.optInt("status")) {
                    2 -> url = dd.optString("link")
                    3 -> throw IOException("AllDebrid no ha podido preparar el archivo.")
                }
            }
        }
        if (url.isBlank()) throw IOException("AllDebrid no ha dado la dirección del vídeo.")
        synchronized(unlocked) { unlocked[link] = Unlocked(url, System.currentTimeMillis()) }
        return url
    }

    fun lazyUrl(link: String): String = LAZY + URLEncoder.encode(link, "UTF-8")

    fun isLazy(url: String?): Boolean = url != null && url.startsWith(LAZY)

    fun linkOf(lazy: String): String = URLDecoder.decode(lazy.removePrefix(LAZY), "UTF-8")

    /** Para el reproductor: si es una dirección «perezosa», la desbloquea; si no, la devuelve igual. */
    fun resolve(url: String): String = if (isLazy(url)) unlock(linkOf(url)) else url
}
