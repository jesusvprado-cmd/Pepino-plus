package com.suso.miiptv.data

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).clean()

/** Primer texto de un campo que puede venir como texto o como lista. */
fun JSONObject.firstStr(k: String): String? = when (val v = opt(k)) {
    is JSONArray -> if (v.length() > 0) v.optString(0).clean() else null
    is String -> v.clean()
    else -> null
}

fun fmtDuration(secs: Long): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return if (h > 0) String.format(Locale.US, "%d:%02dh", h, m) else "$m min"
}

fun fmtHms(s: String): String? {
    val p = s.split(":").mapNotNull { it.trim().toLongOrNull() }
    val secs = when (p.size) {
        3 -> p[0] * 3600 + p[1] * 60 + p[2]
        2 -> p[0] * 60 + p[1]
        1 -> p[0] * 60
        else -> return null
    }
    return if (secs > 0) fmtDuration(secs) else null
}

fun yearOf(s: String?): String? = s?.let { Regex("(19|20)\\d{2}").find(it)?.value }

fun b64(s: String?): String? {
    if (s == null) return null
    return try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim().ifEmpty { null }
    } catch (e: Exception) {
        s
    }
}

fun cleanEpisodeTitle(t: String): String {
    val m = Regex("[Ss]\\d{1,2}\\s*[Ee]\\d{1,3}").find(t) ?: return t
    val rest = t.substring(m.range.last + 1).trim().trimStart('-', '|', ':', '.', ' ').trim()
    return rest.ifEmpty { t }
}

/**
 * Lee una lista JSON de objetos "planos" sin cargarla entera en memoria
 * (importante en la Mi Box con catálogos de miles de películas).
 * Los valores que son listas se quedan con su primer texto.
 */
fun readFlatArray(input: InputStream, onObject: (Map<String, String>) -> Unit) {
    JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
        if (r.peek() != JsonToken.BEGIN_ARRAY) {
            r.skipValue()
            return
        }
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() != JsonToken.BEGIN_OBJECT) {
                r.skipValue()
                continue
            }
            val m = HashMap<String, String>()
            r.beginObject()
            while (r.hasNext()) {
                val name = r.nextName()
                when (r.peek()) {
                    JsonToken.STRING, JsonToken.NUMBER -> m[name] = r.nextString()
                    JsonToken.BOOLEAN -> m[name] = r.nextBoolean().toString()
                    JsonToken.BEGIN_ARRAY -> {
                        r.beginArray()
                        var first: String? = null
                        while (r.hasNext()) {
                            if (first == null && r.peek() == JsonToken.STRING) first = r.nextString() else r.skipValue()
                        }
                        r.endArray()
                        if (first != null) m[name] = first
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
            onObject(m)
        }
        r.endArray()
    }
}
