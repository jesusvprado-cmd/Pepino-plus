package com.shieldstream.tv.crash

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.ui.component.onBackKey
import com.shieldstream.tv.ui.theme.AppColors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ajustes → Registro de errores: últimos cierres de la app y avisos del sistema. ▲▼ desplaza, Atrás sale. */
@Composable
fun ErrorLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val en = Lang.isEnglish
    var text by remember { mutableStateOf(if (en) "Loading…" else "Cargando…") }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.IO) {
            val report = buildReport(context, en)
            // Copia en un archivo que se puede sacar al ordenador con: adb pull <ruta>
            val export = runCatching {
                File(context.getExternalFilesDir(null), EXPORT_NAME).apply { writeText(report) }
            }.getOrNull()
            if (export == null) report else buildString {
                appendLine(if (en) "Saved as a file. To copy it to the computer:" else "Guardado también como archivo. Para copiarlo al ordenador:")
                appendLine("  adb pull ${export.absolutePath}")
                appendLine()
                append(report)
            }
        }
        runCatching { focus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .onBackKey(enabled = true) { onBack() },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
            Text(
                if (en) "Error log" else "Registro de errores",
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (en) "▲▼ to scroll · Back to exit · take a photo of what you need"
                else "▲▼ para desplazarte · Atrás para salir · haz foto de lo que haga falta",
                color = AppColors.TextSecondary,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(AppColors.Field, RoundedCornerShape(14.dp))
                    .border(1.dp, AppColors.SurfaceFocused, RoundedCornerShape(14.dp))
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key.nativeKeyCode) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> { scope.launch { scroll.animateScrollBy(420f) }; true }
                            KeyEvent.KEYCODE_DPAD_UP -> { scope.launch { scroll.animateScrollBy(-420f) }; true }
                            else -> false
                        }
                    }
                    .focusable()
                    .verticalScroll(scroll)
                    .padding(18.dp),
            ) {
                Text(
                    text,
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

private const val EXPORT_NAME = "registro-errores.txt"

private fun buildReport(context: android.content.Context, en: Boolean): String = buildString {
    val folder = File(context.filesDir, "crashes")
    val crashes = folder.listFiles { f -> f.name.startsWith("cierre-") }?.sortedByDescending { it.name }.orEmpty()
    appendLine(if (en) "=== APP ERRORS (latest first) ===" else "=== ERRORES DE LA APP (el más reciente primero) ===")
    if (crashes.isEmpty()) {
        appendLine(if (en) "None saved." else "Ninguno guardado.")
    } else {
        crashes.take(3).forEach { appendLine(CrashLog.summarize(it.readText())); appendLine() }
    }
    appendLine()
    appendLine(if (en) "=== ANDROID LOG ===" else "=== REGISTRO DE ANDROID ===")
    if (!SystemLog.hasPermission(context)) {
        appendLine(
            if (en) "Only the app's own messages are visible. To also see when Android closes the app (memory, video decoder…), run once from a computer:"
            else "Solo se ven los mensajes de la propia app. Para ver también cuándo la cierra Android (memoria, decodificador de vídeo…), ejecuta una vez desde el ordenador:",
        )
        appendLine("  adb shell pm grant ${context.packageName} ${SystemLog.PERMISSION}")
        appendLine(if (en) "and restart Pepino+." else "y vuelve a abrir Pepino+.")
        appendLine()
    }
    SystemLog.previous(context)?.let {
        appendLine(if (en) "--- Saved when the app was opened ---" else "--- Guardado al abrir la app (lo de antes) ---")
        appendLine(it)
        appendLine()
    }
    val now = SystemLog.read(context)
    appendLine(if (en) "--- Now ---" else "--- Ahora ---")
    if (now.isEmpty()) appendLine(if (en) "Nothing relevant." else "Nada relevante.") else now.forEach { appendLine(it) }
}
