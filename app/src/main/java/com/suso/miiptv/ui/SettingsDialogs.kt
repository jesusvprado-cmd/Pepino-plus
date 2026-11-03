package com.suso.miiptv.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.suso.miiptv.data.AccountInfo
import com.suso.miiptv.data.Http
import com.suso.miiptv.data.Repo
import com.suso.miiptv.data.io
import com.suso.miiptv.vpn.VpnManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

// Pantallas auxiliares de Ajustes: registro de errores, elegir archivo y escribir una dirección.

/** Pantalla con el registro de errores. */
@Composable
internal fun ErrorLogView(onClose: () -> Unit) {
    val text = remember { com.suso.miiptv.data.ErrorLog.read() }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { fr.requestFocus() }
    }
    BackHandler { onClose() }
    Column(Modifier.fillMaxSize().background(C.bg).padding(40.dp)) {
        Text("Registro de errores", color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
        Text(com.suso.miiptv.data.ErrorLog.device(), color = C.text2, fontSize = Sz.small)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Cerrar", "log_close", focusRequester = fr) { onClose() }
            TvButton("Borrar registro", "log_clear") {
                com.suso.miiptv.data.ErrorLog.clear()
                onClose()
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(S.panel)
                .verticalScroll(rememberScrollState(Int.MAX_VALUE)).padding(16.dp)
        ) {
            Text(
                text.ifBlank { "No hay errores registrados." },
                color = Color(0xFFDDDDDD), fontSize = Sz.small, lineHeight = 16.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}

/** Lista de archivos .conf encontrados en la carpeta Download. */
@Composable
internal fun FilePickList(files: List<File>, onCancel: () -> Unit, onPick: (File) -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { fr.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().background(C.bg).verticalScroll(rememberScrollState()).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Elige el archivo WireGuard", color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
        Text("Archivos .conf de la carpeta Download", color = C.text2, fontSize = Sz.body)
        files.forEachIndexed { i, f ->
            TvButton(f.name, "conf_$i", focusRequester = if (i == 0) fr else null, vertical = true) { onPick(f) }
        }
        Spacer(Modifier.height(8.dp))
        TvButton("Cancelar", "conf_cancel") { onCancel() }
    }
}

/** Pantalla para escribir la dirección web de un archivo .conf. */
@Composable
internal fun UrlEntry(
    title: String = "Dirección web del archivo .conf",
    button: String = "Cargar",
    initial: String = "",
    hint: String = "https://…",
    text: Boolean = false,
    onCancel: () -> Unit,
    onDone: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { fr.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 48.dp, start = 32.dp, end = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, color = Color.White, fontSize = Sz.sub, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = value,
                onValueChange = { value = if (text) it.take(20) else it.trim() },
                singleLine = true,
                label = { Text(hint) },
                keyboardOptions = KeyboardOptions(keyboardType = if (text) KeyboardType.Text else KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onDone(value) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.gold,
                    unfocusedBorderColor = Color(0xFF555555),
                    focusedLabelColor = C.gold,
                    cursorColor = C.gold,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(button, "url_ok", filled = true) { if (value.isNotBlank()) onDone(value) }
                TvButton("Cancelar", "url_cancel") { onCancel() }
            }
        }
    }
}
