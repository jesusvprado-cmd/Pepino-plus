package com.shieldstream.tv.crash

import android.content.Context
import android.os.Build
import com.shieldstream.tv.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de cierres inesperados de Pepino+.
 *
 * Si la app se cierra por un error de Kotlin/Java, se guarda el detalle en files/crashes/ (los 10 últimos) y, al volver
 * a abrirla, [CrashDialog] lo enseña en pantalla para poder hacerle una foto. No se envía nada a ningún sitio.
 * Los cierres del sistema por falta de memoria o los fallos dentro de librerías nativas no pasan por aquí.
 */
object CrashLog {
    private const val DIR = "crashes"
    private const val SEEN = "ultimo_visto"
    private const val KEEP = 10

    data class Report(val file: File, val summary: String)

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val now = Date()
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("Pepino+ ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine(SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(now))
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Hilo: ${thread.name}")
            appendLine()
            append(trace)
        }
        val folder = dir(context)
        File(folder, "cierre-${now.time}.txt").writeText(text)
        folder.listFiles { f -> f.name.startsWith("cierre-") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
    }

    /** El último cierre que aún no se ha enseñado, o null. */
    fun pending(context: Context): Report? {
        val folder = dir(context)
        val latest = folder.listFiles { f -> f.name.startsWith("cierre-") }?.maxByOrNull { it.name } ?: return null
        val seen = File(folder, SEEN).takeIf { it.exists() }?.readText()?.trim()
        if (seen == latest.name) return null
        return Report(latest, summarize(latest.readText()))
    }

    fun markSeen(context: Context, report: Report) {
        File(dir(context), SEEN).writeText(report.file.name)
    }

    /**
     * Versión corta para la pantalla: cabecera, el error, las primeras líneas de la pila (priorizando las de la app)
     * y cada "Caused by" con un par de líneas.
     */
    internal fun summarize(text: String): String {
        val lines = text.lines()
        val out = ArrayList<String>()
        var frames = 0
        var causeFrames = 0
        var inCause = false
        for (line in lines) {
            val t = line.trim()
            when {
                !t.startsWith("at ") && !t.startsWith("...") && !t.startsWith("Caused by") -> {
                    if (out.size < 12 || !inCause) out += line
                }
                t.startsWith("Caused by") -> {
                    inCause = true
                    causeFrames = 0
                    out += line
                }
                t.startsWith("at ") && !inCause && frames < 10 -> {
                    frames++
                    out += line
                }
                t.startsWith("at ") && inCause && causeFrames < 4 -> {
                    causeFrames++
                    out += line
                }
            }
            if (out.size >= 32) break
        }
        return out.joinToString("\n").trim()
    }
}
