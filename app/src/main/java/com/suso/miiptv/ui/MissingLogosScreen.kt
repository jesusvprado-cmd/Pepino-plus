package com.suso.miiptv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suso.miiptv.data.Category
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.Logos
import com.suso.miiptv.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lista de canales sin logo de calidad, para poder revisarlos y mandar sus nombres. */
@Composable
fun MissingLogosScreen() {
    var rows by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var total by remember { mutableStateOf(0) }
    // Todos los canales (sin adultos ni Latinos) con su logo, para mandarlos a revisar.
    var export by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lv = Logos.version
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(lv) {
        Logos.ensureLoaded()
        if (!Logos.ready()) return@LaunchedEffect
        val cats: List<Category> = try {
            Repo.categories(Kind.LIVE)
        } catch (e: Exception) {
            emptyList()
        }
        val names = cats.associate { it.id to it.name }
        val all = try {
            Repo.items(Kind.LIVE, null)
        } catch (e: Exception) {
            emptyList()
        }
        val res = withContext(Dispatchers.Default) {
            val vis = all.filter { Repo.visible(it) }
            total = vis.size
            vis.filter { !Logos.has(it.name) }
                .map { (names[it.categoryId] ?: "") to it.name }
                .distinct()
        }
        rows = res
        export = withContext(Dispatchers.Default) {
            all.filter { Repo.visible(it) }
                .filter { ch -> val c = names[ch.categoryId] ?: ""; !Repo.isAdult(c) && !c.contains("latino", ignoreCase = true) }
                .distinctBy { it.name }
                .joinToString("\n") { ch -> listOf(ch.name, names[ch.categoryId] ?: "", ch.image ?: "", Logos.url(ch.name, ch.image) ?: "").joinToString("|") }
        }
        delay(80)
        runCatching { firstFr.requestFocus() }
    }
    Column(Modifier.fillMaxSize().background(C.bg).padding(horizontal = 48.dp, vertical = 28.dp)) {
        Text("Canales sin logo de calidad", color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val r = rows
        Text(
            when {
                r == null -> "Buscando…"
                r.isEmpty() -> "Todos los canales tienen logo de calidad."
                else -> "${r.size} de $total canales sin logo de calidad. Haz una foto y mándamela para añadirlos."
            },
            color = C.text2, fontSize = Sz.body,
        )
        Spacer(Modifier.height(10.dp))
        val ex = export
        if (ex != null) {
            TvButton("Enviar lista de canales para revisar los logos", "ml_send", filled = true) {
                try {
                    val dir = java.io.File(ctx.cacheDir, "copia").apply { mkdirs() }
                    val f = java.io.File(dir, "canales-logos.txt").apply { writeText(ex) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".updates", f)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(android.content.Intent.createChooser(send, "Enviar lista de canales"))
                } catch (e: Exception) {
                    Actions.toast(ctx, "No se pudo enviar: ${e.message ?: "error"}")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (r == null) {
            Loading()
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
                itemsIndexed(r) { i, (cat, name) ->
                    var f by remember { mutableStateOf(false) }
                    val bg by animateColorAsState(if (f) Color.White else Color.Transparent, label = "ml")
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(if (i == 0) Modifier.focusRequester(firstFr) else Modifier)
                            .onFocusChanged { f = it.isFocused }
                            .focusable()
                            .clip(RoundedCornerShape(6.dp))
                            .background(bg)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (cat.isNotEmpty()) "$name   ·   $cat" else name,
                            color = if (f) Color.Black else Color(0xFFDDDDDD), fontSize = Sz.body,
                        )
                    }
                }
            }
        }
    }
}
