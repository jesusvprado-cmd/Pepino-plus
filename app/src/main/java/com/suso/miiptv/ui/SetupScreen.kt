package com.suso.miiptv.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suso.miiptv.data.Config
import com.suso.miiptv.data.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val EYE =
    "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z"
private const val SLASH = "M2.1,3.51L3.51,2.1 21.9,20.49 20.49,21.9z"

private fun eyeIcon(crossed: Boolean): ImageVector {
    val b = ImageVector.Builder(
        name = if (crossed) "ojo_tachado" else "ojo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    b.addPath(pathData = addPathNodes(EYE), fill = SolidColor(Color.White))
    if (crossed) b.addPath(pathData = addPathNodes(SLASH), fill = SolidColor(Color.White))
    return b.build()
}

private val EyeOpen by lazy { eyeIcon(false) }
private val EyeCrossed by lazy { eyeIcon(true) }

@Composable
fun SetupScreen(canCancel: Boolean, onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val existing = Repo.config
    var mode by remember { mutableStateOf(existing?.type ?: "xtream") }
    var server by remember { mutableStateOf(existing?.server ?: "") }
    var user by remember { mutableStateOf(existing?.user ?: "") }
    var pass by remember { mutableStateOf(existing?.pass ?: "") }
    var m3u by remember { mutableStateOf(existing?.m3uUrl ?: "") }
    var epg by remember { mutableStateOf(existing?.epgUrl ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (canCancel) BackHandler { onCancel() }

    fun connect() {
        if (busy) return
        focus.clearFocus()
        var cfg = if (mode == "xtream") {
            Config("xtream", server = server.trim(), user = user.trim(), pass = pass.trim())
        } else {
            Config("m3u", m3uUrl = m3u.trim(), epgUrl = epg.trim())
        }
        // Si la URL M3U es de un panel Xtream (get.php?username=...&password=...), usamos Xtream:
        // así salen las carátulas, sinopsis y temporadas de películas y series.
        if (mode == "m3u") {
            val uri = Uri.parse(cfg.m3uUrl)
            val u = uri.getQueryParameter("username")
            val p = uri.getQueryParameter("password")
            if (uri.path?.endsWith("get.php") == true && !u.isNullOrBlank() && !p.isNullOrBlank() && uri.host != null) {
                cfg = Config("xtream", server = "${uri.scheme}://${uri.encodedAuthority}", user = u, pass = p, epgUrl = cfg.epgUrl)
                Actions.toast(ctx, "Lista Xtream detectada: se usará el modo Xtream Codes")
            }
        }
        if (cfg.type == "xtream" && (cfg.server.isBlank() || cfg.user.isBlank() || cfg.pass.isBlank())) {
            error = "Rellena servidor, usuario y contraseña."
            return
        }
        if (cfg.type == "m3u" && cfg.m3uUrl.isBlank()) {
            error = "Escribe la URL de la lista."
            return
        }
        cfg = cfg.copy(liveFormat = existing?.liveFormat ?: "ts", userAgent = existing?.userAgent ?: "")
        val finalCfg = cfg
        scope.launch {
            busy = true
            error = null
            try {
                val src = Repo.createSource(finalCfg)
                src.check()
                Repo.save(finalCfg, src)
                UiState.resetAll()
                onDone()
            } catch (e: Exception) {
                error = "No se pudo conectar: ${e.message ?: "error desconocido"}"
            } finally {
                busy = false
            }
        }
    }

    // imePadding + desplazamiento: al abrir el teclado la pantalla sube y el campo queda a la vista.
    Box(Modifier.fillMaxSize().background(C.bg).imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logo(40.dp)
                Spacer(Modifier.width(14.dp))
                Text(BrandName(), color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
            }
            Text(
                "Introduce los datos de tu lista. Se guardan solo en este dispositivo.",
                color = C.text2, fontSize = Sz.body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TabChip("Xtream Codes", mode == "xtream", "setup_x", requestInitial = mode == "xtream") { mode = "xtream" }
                TabChip("Lista M3U", mode == "m3u", "setup_m", requestInitial = mode == "m3u") { mode = "m3u" }
            }
            val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
            val go = KeyboardActions(onDone = { connect() })
            if (mode == "xtream") {
                Field("Servidor (ej. http://servidor.com:8080)", server, KeyboardType.Uri, next) { server = it }
                Field("Usuario", user, KeyboardType.Text, next) { user = it }
                Field("Contraseña", pass, KeyboardType.Password, go, password = true, last = true) { pass = it }
            } else {
                Field("URL de la lista M3U", m3u, KeyboardType.Uri, next) { m3u = it }
                Field("URL de la guía EPG (opcional)", epg, KeyboardType.Uri, go, last = true) { epg = it }
            }
            error?.let { Text(it, color = C.error, fontSize = Sz.body) }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(if (busy) "Conectando…" else "Conectar", "setup_go", filled = true) { connect() }
                if (canCancel) TvButton("Cancelar", "setup_cancel") { onCancel() }
            }
            // Hueco extra para que el último campo pueda subir por encima del teclado.
            Spacer(Modifier.height(240.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Field(
    label: String,
    value: String,
    type: KeyboardType,
    actions: KeyboardActions,
    password: Boolean = false,
    last: Boolean = false,
    onChange: (String) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password && show) KeyboardType.Text else type,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = actions,
        trailingIcon = if (password) {
            {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        if (show) EyeCrossed else EyeOpen,
                        contentDescription = if (show) "Ocultar contraseña" else "Mostrar contraseña",
                        tint = if (show) C.gold else C.text2,
                    )
                }
            }
        } else {
            null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.gold,
            unfocusedBorderColor = Color(0xFF555555),
            focusedLabelColor = C.gold,
            cursorColor = C.gold,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bring)
            .onFocusEvent { st ->
                if (st.isFocused) {
                    scope.launch {
                        // Esperamos a que el teclado termine de abrirse y llevamos el campo a la vista.
                        delay(350)
                        bring.bringIntoView()
                        delay(400)
                        bring.bringIntoView()
                    }
                }
            },
    )
}
