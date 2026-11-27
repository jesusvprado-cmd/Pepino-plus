package com.shieldstream.tv.crash

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Lectura del registro de Android (logcat) para averiguar por qué se cerró Pepino+ cuando no fue un error de la app:
 * el sistema la cerró por memoria, falló un decodificador nativo, etc.
 *
 * Sin permiso, Android solo deja leer lo que escribió la propia app. Con el permiso READ_LOGS (se concede una vez por
 * ADB: `adb shell pm grant com.suso.pepinoplus android.permission.READ_LOGS`) se ven también los avisos del sistema.
 * Al arrancar la app se guarda una copia filtrada, porque el registro de Android es pequeño y se borra pronto.
 */
object SystemLog {
    const val PERMISSION = "android.permission.READ_LOGS"
    private const val SNAPSHOT = "sistema-anterior.txt"
    private const val MAX_LINES = 250

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun file(context: Context) = File(File(context.filesDir, "crashes").apply { mkdirs() }, SNAPSHOT)

    /** Copia lo relevante del registro actual (llamar al arrancar, en segundo plano). */
    fun snapshotAtStartup(context: Context) {
        val lines = read(context)
        if (lines.isNotEmpty()) file(context).writeText(lines.joinToString("\n"))
    }

    /** Lo guardado al arrancar esta vez (lo que pasó antes de abrir la app). */
    fun previous(context: Context): String? = file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    /** Líneas relevantes del registro de Android ahora mismo. */
    fun read(context: Context): List<String> {
        val pkg = context.packageName
        val raw = runLogcat(listOf("logcat", "-d", "-v", "time", "-b", "main", "-b", "system", "-b", "crash"))
            ?: runLogcat(listOf("logcat", "-d", "-v", "time"))
            ?: return emptyList()
        return raw.filter { line -> relevant(line, pkg) }.takeLast(MAX_LINES)
    }

    private fun relevant(line: String, pkg: String): Boolean =
        line.contains(pkg) ||
            line.contains("AndroidRuntime") ||
            line.contains("FATAL") ||
            line.contains("F/DEBUG") ||
            line.contains("F/libc") ||
            line.contains("lowmemorykiller", ignoreCase = true) ||
            line.contains("lmkd") ||
            line.contains("OutOfMemory") ||
            line.contains("ANR in")

    private fun runLogcat(command: List<String>): List<String>? = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val lines = process.inputStream.bufferedReader().readLines()
        process.waitFor(5, TimeUnit.SECONDS)
        if (process.exitValue() != 0 && lines.size < 3) null else lines
    }.getOrNull()
}
