package com.shieldstream.tv.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.shieldstream.tv.data.security.TokenStore
import com.shieldstream.tv.domain.ApiService
import com.shieldstream.tv.domain.ConfigBackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Copia toda la configuración guardada en SharedPreferences (salvo lo propio de cada aparato) y las claves de los
 * servicios guardadas cifradas en [TokenStore]. Las claves viajan en claro dentro del archivo de copia.
 */
@Singleton
class ConfigBackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: TokenStore,
) : ConfigBackupRepository {

    override fun export(): String {
        val prefs = JSONObject()
        for (name in copyablePrefsFiles()) {
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            if (values.isEmpty()) continue
            val file = JSONObject()
            for ((key, value) in values) {
                val entry = encode(value) ?: continue
                file.put(key, entry)
            }
            prefs.put(name, file)
        }
        val secrets = JSONObject()
        for (name in secretNames()) {
            tokens.getSecret(name)?.takeIf { it.isNotBlank() }?.let { secrets.put(name, it) }
        }
        return JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("created", System.currentTimeMillis())
            .put("prefs", prefs)
            .put("secrets", secrets)
            .toString(2)
    }

    override fun restore(raw: String): Int {
        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw IllegalArgumentException("El archivo no es una copia de Pepino+")
        require(root.optString("app") == APP) { "El archivo no es una copia de Pepino+" }
        require(root.optInt("format") in 1..FORMAT) { "Copia de una versión de Pepino+ más nueva: actualiza esta tele" }
        val prefs = root.optJSONObject("prefs") ?: JSONObject()
        val secrets = root.optJSONObject("secrets") ?: JSONObject()
        var restored = 0

        for (name in prefs.keys()) {
            if (!copyable(name)) continue
            val values = prefs.optJSONObject(name) ?: continue
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (key in values.keys()) {
                val entry = values.optJSONObject(key) ?: continue
                decode(editor, key, entry, sanitize = name == PROFILES)
            }
            editor.commit()
            restored++
        }
        // Las claves: se sustituyen todas (las que no vienen en la copia se borran).
        for (name in secretNames()) {
            tokens.setSecret(name, secrets.optString(name).takeIf { it.isNotBlank() })
        }
        if (secrets.length() > 0) restored++
        return restored
    }

    private fun secretNames(): List<String> = ApiService.entries.map(TokenStore::keyNameFor).distinct()

    private fun copyablePrefsFiles(): List<String> {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        return dir.listFiles { f -> f.name.endsWith(".xml") }
            .orEmpty()
            .map { it.name.removeSuffix(".xml") }
            .filter(::copyable)
            .sorted()
    }

    private fun copyable(name: String): Boolean =
        name.isNotBlank() &&
            name !in EXCLUDED &&
            EXCLUDED_PREFIXES.none { name.startsWith(it) } &&
            !name.contains('/') && !name.contains("..")

    private fun encode(value: Any?): JSONObject? = when (value) {
        is String -> JSONObject().put("t", "s").put("v", value)
        is Boolean -> JSONObject().put("t", "b").put("v", value)
        is Int -> JSONObject().put("t", "i").put("v", value)
        is Long -> JSONObject().put("t", "l").put("v", value)
        is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
        is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.filterIsInstance<String>()))
        else -> null
    }

    private fun decode(editor: SharedPreferences.Editor, key: String, entry: JSONObject, sanitize: Boolean) {
        when (entry.optString("t")) {
            "s" -> editor.putString(key, entry.optString("v").let { if (sanitize) withoutTraktUser(it) else it })
            "b" -> editor.putBoolean(key, entry.optBoolean("v"))
            "i" -> editor.putInt(key, entry.optInt("v"))
            "l" -> editor.putLong(key, entry.optLong("v"))
            "f" -> editor.putFloat(key, entry.optDouble("v").toFloat())
            "set" -> {
                val array = entry.optJSONArray("v") ?: JSONArray()
                editor.putStringSet(key, (0 until array.length()).map { array.optString(it) }.toSet())
            }
        }
    }

    /** Los perfiles llegan sin la cuenta de Trakt: la sesión no se copia y en esta tele hay que vincularla de nuevo. */
    private fun withoutTraktUser(value: String): String = runCatching {
        val array = JSONArray(value)
        for (i in 0 until array.length()) array.optJSONObject(i)?.remove("traktUser")
        array.toString()
    }.getOrElse {
        runCatching { JSONObject(value).apply { remove("traktUser") }.toString() }.getOrDefault(value)
    }

    private companion object {
        const val APP = "pepino+"
        const val FORMAT = 1
        const val PROFILES = "profiles"

        /** Propio de cada aparato o que se rehace solo. */
        val EXCLUDED = setOf(
            "secure_prefs", // las claves van aparte, descifradas
            "trakt_sync", // marcas de sincronización de Trakt de esta tele
            "updates",
            "downloads", // rutas de archivos de esta tele
            "featured", // caché del carrusel
            "WorkManager",
        )
        val EXCLUDED_PREFIXES = listOf("androidx.", "__androidx", "com.google.", "WorkManager", "profileinstaller")
    }
}
