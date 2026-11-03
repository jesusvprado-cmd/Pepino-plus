package com.suso.miiptv.data

import android.content.Context
import android.os.Build
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro de errores: lo último que ha fallado (cierres de la app y fallos al reproducir), para poder enseñarlo. */
object ErrorLog {
    private const val MAX_BYTES = 60_000L
    private var file: File? = null

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "errores.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            add("CIERRE de la app: ${e.javaClass.simpleName}: ${e.message}\n" + e.stackTrace.take(8).joinToString("\n") { "   en $it" })
            previous?.uncaughtException(t, e)
        }
    }

    private val seen = HashSet<String>()

    /** Como [add], pero solo la primera vez en cada sesión para esa [key] (fallos que se repetirían en cada canal). */
    fun once(key: String, msg: String) {
        if (synchronized(seen) { seen.add(key) }) add(msg)
    }

    @Synchronized
    fun add(msg: String) {
        // Tareas canceladas a propósito (al salir de una pantalla): no son errores.
        if (msg.contains("left the composition") || msg.contains("was cancelled") || msg.contains("Job was cancelled")) return
        val f = file ?: return
        try {
            val stamp = SimpleDateFormat("dd/MM HH:mm:ss", Locale("es", "ES")).format(Date())
            f.appendText("[$stamp] $msg\n")
            if (f.length() > MAX_BYTES) {
                val keep = f.readText().takeLast((MAX_BYTES / 2).toInt())
                f.writeText(keep.substringAfter('\n'))
            }
        } catch (e: Exception) {
            // Sin importancia.
        }
    }

    fun read(): String = try {
        file?.takeIf { it.exists() }?.readText().orEmpty()
    } catch (e: Exception) {
        ""
    }

    fun clear() {
        try {
            file?.delete()
        } catch (e: Exception) {
            // Sin importancia.
        }
    }

    /** Datos del aparato, para acompañar al registro. */
    fun device(): String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
}

/**
 * Copia de seguridad de ajustes, lista, favoritos y perfiles en un archivo de la carpeta Download,
 * cifrada con un PIN de 4 números (sin él no se puede leer ni restaurar).
 */
object Backup {
    const val FILE_NAME = "pepino-iptv-copia.json"

    fun file(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)

    fun export(ctx: Context, pin: String): File {
        val sp = ctx.getSharedPreferences("miiptv", Context.MODE_PRIVATE)
        val o = JSONObject()
        for ((k, v) in sp.all) {
            val t = when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                // La cuenta y el perfil WireGuard van cifrados con la llave de este aparato, que no sirve en otro:
                // en la copia van en claro, pero dentro del cifrado con el PIN.
                is String -> JSONObject().put("t", "s").put("v", if (k == "config" || k == "wgConf") Secure.open(v) ?: continue else v)
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            o.put(k, t)
        }
        val root = JSONObject().put("app", "pepino-iptv").put("fecha", System.currentTimeMillis())
            .put("cifrado", Secure.encryptBackup(o.toString(), pin))
        val f = file()
        f.parentFile?.mkdirs()
        f.writeText(root.toString(1))
        return f
    }

    /** Lee el archivo de copia de la carpeta Download; null si no existe o Android no deja leerlo. */
    fun readFile(): String? = try {
        file().takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        null
    }

    /** Comprueba que es una copia de Pepino IPTV. Lanza una excepción con un mensaje legible si no. */
    private fun parse(text: String): JSONObject {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalStateException("Ese archivo no es una copia de Pepino IPTV")
        }
        if (root.optString("app") != "pepino-iptv") throw IllegalStateException("Ese archivo no es una copia de Pepino IPTV")
        return root
    }

    fun isEncrypted(text: String): Boolean = runCatching { parse(text).has("cifrado") }.getOrDefault(false)

    fun date(text: String): Long? = runCatching { parse(text).optLong("fecha").takeIf { it > 0 } }.getOrNull()

    /** Restaura desde el contenido de una copia. [pin] solo hace falta si la copia está cifrada. */
    fun restoreText(ctx: Context, text: String, pin: String?) {
        val root = parse(text)
        val d = if (root.has("cifrado")) {
            if (pin == null) throw IllegalStateException("Esta copia necesita su PIN")
            JSONObject(Secure.decryptBackup(root.getJSONObject("cifrado"), pin))
        } else {
            root.getJSONObject("datos")
        }
        val e = ctx.getSharedPreferences("miiptv", Context.MODE_PRIVATE).edit().clear()
        for (k in d.keys()) {
            val o = d.getJSONObject(k)
            when (o.optString("t")) {
                "b" -> e.putBoolean(k, o.getBoolean("v"))
                "i" -> e.putInt(k, o.getInt("v"))
                "l" -> e.putLong(k, o.getLong("v"))
                "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                // Al arrancar de nuevo, la app vuelve a cifrar la cuenta con la llave de este aparato.
                "s" -> e.putString(k, o.getString("v"))
                "set" -> {
                    val a = o.getJSONArray("v")
                    e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
        }
        e.commit()
    }
}

/** Modo ligero: menos animaciones y efectos para aparatos con poca potencia (como la Mi Box). */
object Perf {
    @Volatile
    var light = false
        private set

    fun init(ctx: Context) {
        val mode = ctx.getSharedPreferences("miiptv", Context.MODE_PRIVATE).getString("light_mode", "auto") ?: "auto"
        light = when (mode) {
            "on" -> true
            "off" -> false
            else -> isWeak(ctx)
        }
    }

    /** Aparatos modestos: poca memoria para la app o modelos conocidos (Mi Box). */
    fun isWeak(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val lowRam = am?.isLowRamDevice == true
        val mem = am?.memoryClass ?: 512
        val model = (Build.MODEL ?: "").uppercase()
        return lowRam || mem <= 192 || model.contains("MIBOX") || model.contains("MI BOX")
    }
}

/** Tipo de aparato: tele (mando) o táctil (móvil, tablet). */
object Device {
    /** true en móviles y tablets (pantalla táctil y sin interfaz de tele). */
    @Volatile
    var touch = false
        internal set

    fun init(ctx: Context) {
        val pm = ctx.packageManager
        val tv = pm.hasSystemFeature("android.software.leanback") || pm.hasSystemFeature("android.hardware.type.television")
        touch = !tv && pm.hasSystemFeature("android.hardware.touchscreen")
    }
}
