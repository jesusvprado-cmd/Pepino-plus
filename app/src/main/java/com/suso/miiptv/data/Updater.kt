package com.suso.miiptv.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Actualizaciones desde la propia app: mira la última versión publicada en GitHub,
 * la descarga y abre el instalador de Android (que la instala encima, sin perder nada).
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/jesusvprado-cmd/Pepino-plus/releases/latest"
    private const val CHECK_EVERY = 6L * 3600 * 1000

    data class Release(val build: Int, val name: String, val notes: String, val apkUrl: String, val size: Long)

    /** Versión nueva disponible (null si no hay o aún no se ha mirado). */
    var available by mutableStateOf<Release?>(null)
        private set

    /** Progreso de la descarga de 0 a 1 (null si no se está descargando). */
    var progress by mutableStateOf<Float?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    /** Si el usuario ha dicho «Más tarde» en esta sesión. */
    var dismissed by mutableStateOf(false)

    fun currentBuild(ctx: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    } catch (e: Exception) {
        0
    }

    fun currentName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    /** Comprueba si hay versión nueva. Sin [force], como mucho cada 6 horas. Devuelve true si la hay. */
    suspend fun check(ctx: Context, force: Boolean = false): Boolean {
        val sp = ctx.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - sp.getLong("last", 0) < CHECK_EVERY) return available != null
        error = null
        val rel = withContext(Dispatchers.IO) {
            // 1) La API de GitHub (trae las novedades). Con VPN o IP compartida a veces responde 403 por límite de consultas.
            // 2) Si falla, la página pública de la última versión, que no tiene ese límite.
            try {
                fromApi()
            } catch (e: Exception) {
                null
            } ?: fromWeb()
        }
        sp.edit().putLong("last", now).apply()
        available = rel?.takeIf { it.build > currentBuild(ctx) }
        return available != null
    }

    private const val PAGE_LATEST = "https://github.com/jesusvprado-cmd/Pepino-plus/releases/latest"
    private const val DOWNLOAD = "https://github.com/jesusvprado-cmd/Pepino-plus/releases/download"

    private fun get(url: String, accept: String? = null): okhttp3.Response {
        val b = Request.Builder().url(url).header("User-Agent", "MiIPTV-Android")
        if (accept != null) b.header("Accept", accept)
        return Http.client.newCall(b.build()).execute()
    }

    private fun fromApi(): Release? = get(LATEST, "application/vnd.github+json").use { r ->
        if (!r.isSuccessful) throw IOException("GitHub HTTP ${r.code}")
        val o = JSONObject(r.body?.string() ?: "{}")
        val build = o.optString("tag_name").removePrefix("v").toIntOrNull() ?: return@use null
        val assets = o.optJSONArray("assets")
        var url: String? = null
        var size = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", true)) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
        }
        if (url.isNullOrBlank()) return@use null
        Release(build, o.optString("name", "Pepino+ $build"), cleanNotes(o.optString("body")), url, size)
    }

    /** «…/releases/latest» redirige a «…/releases/tag/v23»: de ahí sale el número de la última versión. */
    private fun fromWeb(): Release? = get(PAGE_LATEST).use { r ->
        if (!r.isSuccessful) throw IOException("No se pudo consultar GitHub (HTTP ${r.code})")
        val tag = r.request.url.pathSegments.lastOrNull() ?: return@use null
        val build = tag.removePrefix("v").toIntOrNull() ?: return@use null
        Release(build, "Pepino+ $build", "", "$DOWNLOAD/$tag/PepinoPlus.apk", 0)
    }

    /** Quita las líneas técnicas del texto de la versión (autoría, enlaces de sesión...). */
    private fun cleanNotes(body: String): String =
        body.lines()
            .filter { l ->
                val t = l.trim()
                !t.startsWith("Co-Authored-By", true) && !t.startsWith("Claude-Session", true) &&
                    !t.startsWith("**Full Changelog", true)
            }
            .joinToString("\n")
            .trim()

    /** Descarga el APK y abre el instalador. */
    suspend fun downloadAndInstall(ctx: Context) {
        val rel = available ?: return
        if (progress != null) return
        error = null
        progress = 0f
        try {
            // En Android 6 y anteriores el instalador solo lee archivos de la memoria compartida.
            val base = if (Build.VERSION.SDK_INT >= 24) ctx.cacheDir else (ctx.getExternalFilesDir(null) ?: ctx.cacheDir)
            val dir = File(base, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "PepinoPlus-${rel.build}.apk")
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url(rel.apkUrl).header("User-Agent", "MiIPTV").build()
                Http.client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("No se pudo descargar (HTTP ${r.code})")
                    val body = r.body ?: throw IOException("Descarga vacía")
                    val total = body.contentLength().takeIf { it > 0 } ?: rel.size
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastShown = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0 && done - lastShown > 128 * 1024) {
                                    lastShown = done
                                    val f = (done.toFloat() / total).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) { progress = f }
                                }
                            }
                        }
                    }
                }
            }
            progress = 1f
            install(ctx, file)
        } catch (e: Exception) {
            error = e.message ?: "No se pudo descargar la actualización"
        } finally {
            progress = null
        }
    }

    private fun install(ctx: Context, file: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(ctx, ctx.packageName + ".updates", file)
        } else {
            Uri.fromFile(file.apply { setReadable(true, false) })
        }
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            error = "Este equipo no tiene instalador de apps"
        }
    }
}
