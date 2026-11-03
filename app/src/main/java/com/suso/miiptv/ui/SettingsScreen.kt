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

internal enum class Sec(val label: String, val desc: String) {
    ACCOUNT("Cuenta", "Tu lista IPTV y el estado de la suscripción."),
    ALLDEBRID("AllDebrid", "Tu cuenta de AllDebrid y qué copia se elige."),
    VPN("VPN", "Se conecta cuando tú quieras."),
    VIDEO("Vídeo", "Imagen, carga y forma de reproducir."),
    AUDIO("Audio", "Sonido de canales, películas y series."),
    SUBS("Subtítulos", "Idioma y aspecto de los subtítulos."),
    CONTENT("Contenido", "Qué se muestra en la app."),
    EPG("Guía", "Guía de programación de los canales (EPG)."),
    ABOUT("Acerca de", ""),
}

/** Una pregunta abierta en la ventana de elección. */
internal class Choice<T>(val title: String, val options: List<Pair<String, T>>, val selected: T, val onPick: (T) -> Unit)

/** Última sección abierta (para volver a ella al regresar de «Categorías ocultas»). */
internal var lastSec: Sec = Sec.ACCOUNT

internal fun <T> label(options: List<Pair<String, T>>, v: T): String = options.firstOrNull { it.second == v }?.first ?: ""

internal val RESIZE = listOf("Ajustar" to "fit", "Rellenar (zoom)" to "zoom", "Estirar" to "fill", "16:9" to "16:9", "4:3" to "4:3")
internal val BUFFER = listOf("Bajo · arranca antes" to "low", "Normal" to "normal", "Alto · menos cortes" to "high")
internal val SEEK = listOf("10 segundos" to 10, "15 segundos" to 15, "30 segundos" to 30)
internal val AUDIO_OUT = listOf("Automática" to false, "Decodificar siempre" to true)
internal val BOOST = listOf("Sin aumento" to 0, "+3 dB" to 3, "+6 dB" to 6, "+10 dB" to 10)
internal val AUDIO_LANG = listOf(
    "Español" to "es", "Inglés" to "en", "Gallego" to "gl", "Catalán" to "ca",
    "Francés" to "fr", "Portugués" to "pt", "Versión original" to "orig",
)
internal val SUB_LANG = listOf(
    "Desactivados" to "", "Español" to "es", "Inglés" to "en", "Gallego" to "gl",
    "Catalán" to "ca", "Francés" to "fr", "Portugués" to "pt",
)
internal val SUB_SIZE = listOf("Pequeño" to "s", "Normal" to "m", "Grande" to "l", "Muy grande" to "xl")
internal val SUB_COLOR = listOf("Blanco" to "white", "Amarillo" to "yellow")
internal val SUB_BG = listOf("Sin fondo (contorno)" to "none", "Franja oscura" to "band")
internal val VPN_MODE = listOf("Desactivada" to "off", "WARP de Cloudflare" to "warp", "Mi WireGuard (.conf)" to "wg")
internal val LIGHT = listOf("Automático" to "auto", "Activado" to "on", "Desactivado" to "off")
internal val LIVE_FMT = listOf("TS (recomendado)" to "ts", "HLS (m3u8)" to "m3u8")

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(onChangeList: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = Repo.prefs
    @Suppress("UNUSED_VARIABLE")
    val sv = p.settingsVersion // se vuelve a dibujar al cambiar cualquier ajuste

    var sec by remember { mutableStateOf(lastSec) }
    var pending by remember { mutableStateOf<Sec?>(null) }
    LaunchedEffect(pending) {
        val s = pending ?: return@LaunchedEffect
        delay(180)
        sec = s
        lastSec = s
    }
    val sideFrs = remember { Sec.values().associateWith { FocusRequester() } }
    // Primera opción de la sección abierta (la lista de la derecha): al entrar con ▶ se va ahí.
    val paneFr = remember { FocusRequester() }
    val paneList = androidx.compose.foundation.lazy.rememberLazyListState()
    val firstKey = remember { mutableMapOf<Sec, String?>() }
    var enteringRight by remember { mutableStateOf(false) }
    LaunchedEffect(sec) { runCatching { paneList.scrollToItem(0) } }
    val restoring = remember { FocusMemory.restore != null }
    LaunchedEffect(Unit) {
        if (!restoring) {
            delay(60)
            runCatching { sideFrs.getValue(sec).requestFocus() }
        }
    }
    var inPane by remember { mutableStateOf(false) }
    // Momento en que el foco salió de las opciones: si justo después cae en otra sección del menú
    // (porque desapareció la fila en la que estabas), no se cambia de sección.
    var paneLeftAt by remember { mutableStateOf(0L) }
    var choice by remember { mutableStateOf<Choice<*>?>(null) }

    // Fila desde la que se abrió la ventana de elección: al cerrarla, el foco vuelve a ella.
    var askedFrom by remember { mutableStateOf<String?>(null) }
    fun <T> ask(title: String, options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit) {
        askedFrom = FocusMemory.last
        choice = Choice(title, options, selected, onPick)
    }
    // Justo al cerrar la ventana, Android puede poner el foco un instante en «Cuenta»: eso no cambia de sección.
    var ignoreSideUntil by remember { mutableStateOf(0L) }
    fun closeChoice() {
        ignoreSideUntil = System.currentTimeMillis() + 500
        val k = askedFrom
        FocusMemory.focus(k)
        choice = null
        scope.launch {
            delay(40)
            if (!FocusMemory.focus(k)) runCatching { sideFrs.getValue(sec).requestFocus() }
        }
    }

    // ---- VPN: permiso de Android y carga de archivos .conf ----
    val vpnPerm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) VpnManager.connect()
        else Actions.toast(ctx, "Sin ese permiso no se puede usar la VPN")
    }
    fun connectVpn() {
        val intent = VpnManager.permissionIntent(ctx)
        if (intent == null) {
            VpnManager.connect()
            return
        }
        try {
            vpnPerm.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Actions.toast(ctx, "Este dispositivo no permite que las apps usen VPN")
        }
    }
    fun setVpnMode(m: String) {
        p.vpnMode = m
        if (m == "off") VpnManager.disconnect()
        else if (m == "wg" && p.wgConf == null) Actions.toast(ctx, "Carga primero tu archivo WireGuard (.conf)")
        else connectVpn()
    }
    fun useConf(text: String, from: String) {
        val err = VpnManager.validate(text)
        if (err != null) {
            Actions.toast(ctx, err)
            return
        }
        p.wgConf = text
        p.wgName = from
        Actions.toast(ctx, "Perfil cargado: ${VpnManager.profileName(text)}")
        if (p.vpnMode == "wg" || p.vpnMode == "off") {
            p.vpnMode = "wg"
            connectVpn()
        }
    }
    var downloadsList by remember { mutableStateOf<List<File>?>(null) }
    var urlDialog by remember { mutableStateOf(false) }
    var epgUrlDialog by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf<Int?>(null) }
    var serverDialog by remember { mutableStateOf(false) }
    // AllDebrid: "pin", "key" o "tmdb".
    var debridDialog by remember { mutableStateOf<String?>(null) }
    var serverBusy by remember { mutableStateOf(false) }

    /** Cambia solo la dirección del servidor (mismo usuario y contraseña); antes comprueba que responde. */
    fun changeServer(raw: String) {
        val cfg = Repo.config ?: return
        var srv = raw.trim().trimEnd('/')
        if (!srv.contains("://")) srv = "http://$srv"
        val u = Uri.parse(srv)
        val base = "${u.scheme}://${u.encodedAuthority}"
        val newCfg = if (cfg.type == "xtream") cfg.copy(server = base) else {
            val old = Uri.parse(cfg.m3uUrl)
            cfg.copy(m3uUrl = old.buildUpon().scheme(u.scheme).encodedAuthority(u.encodedAuthority).build().toString())
        }
        val oldBase = if (cfg.type == "xtream") cfg.server else Uri.parse(cfg.m3uUrl).let { "${it.scheme}://${it.encodedAuthority}" }
        if (newCfg == cfg) {
            Actions.toast(ctx, "Ya estás usando ese servidor")
            return
        }
        serverBusy = true
        Actions.toast(ctx, "Comprobando el servidor…")
        scope.launch {
            try {
                val src = Repo.createSource(newCfg)
                src.check()
                p.rememberServer(oldBase)
                p.rememberServer(base)
                Repo.save(newCfg, src)
                Actions.toast(ctx, "Servidor cambiado")
            } catch (e: Exception) {
                Actions.toast(ctx, "Ese servidor no responde (${e.message ?: "error"}). Sigues con el anterior.")
            } finally {
                serverBusy = false
            }
        }
    }
    var logOpen by remember { mutableStateOf(false) }

    // ---- Copia de seguridad (cifrada con un PIN de 4 números) ----
    // backupPin: null = nada; "new" = elegir PIN; "confirm:1234" = repetirlo.
    var backupPin by remember { mutableStateOf<String?>(null) }
    // Al terminar de guardar la copia, enviarla a otro aparato.
    var shareAfter by remember { mutableStateOf(false) }
    // Copia leída que espera su PIN para restaurarse.
    var restoreText by remember { mutableStateOf<String?>(null) }
    var restoreBusy by remember { mutableStateOf(false) }
    fun doBackup(pin: String) {
        scope.launch {
            try {
                val f = io { com.suso.miiptv.data.Backup.export(ctx, pin) }
                if (shareAfter) {
                    shareAfter = false
                    val out = io {
                        val dir = java.io.File(ctx.cacheDir, "copia").apply { mkdirs() }
                        java.io.File(dir, f.name).also { f.copyTo(it, overwrite = true) }
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".updates", out)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("application/json")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(android.content.Intent.createChooser(send, "Enviar copia de Pepino+"))
                } else {
                    Actions.toast(ctx, "Copia cifrada guardada en Download/${f.name}")
                }
            } catch (e: Exception) {
                Actions.toast(ctx, "No se pudo guardar la copia: ${e.message ?: "error"}")
            }
        }
    }
    val writePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) backupPin = "new" else Actions.toast(ctx, "Sin permiso no se puede guardar en la carpeta Download")
    }
    fun backup() {
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) writePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else backupPin = "new"
    }
    fun restartApp() {
        val i = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (i != null) ctx.startActivity(i)
        Runtime.getRuntime().exit(0)
    }
    /** Restaura [text] (con su PIN si está cifrada) y reinicia la app. Con un PIN incorrecto lo vuelve a pedir. */
    fun applyRestore(text: String, pin: String?) {
        restoreBusy = true
        scope.launch {
            try {
                io { com.suso.miiptv.data.Backup.restoreText(ctx, text, pin) }
                restartApp()
            } catch (e: Exception) {
                restoreBusy = false
                Actions.toast(ctx, e.message ?: "No se pudo restaurar la copia")
                if (e.message?.startsWith("PIN incorrecto") != true) restoreText = null
            }
        }
    }
    fun startRestore(text: String) {
        if (com.suso.miiptv.data.Backup.isEncrypted(text)) restoreText = text else applyRestore(text, null)
    }
    // Copia elegida con el selector de archivos: tras reinstalar la app, Android no deja leer directamente
    // la copia que guardó la instalación anterior en Download (móviles y tablets con Android 11 o más).
    val restoreDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IllegalStateException("No se pudo leer el archivo")
                startRestore(text)
            } catch (e: Exception) {
                Actions.toast(ctx, e.message ?: "No se pudo restaurar la copia")
            }
        }
    }
    fun doRestore() {
        val text = com.suso.miiptv.data.Backup.readFile()
        val d = text?.let { com.suso.miiptv.data.Backup.date(it) }
        if (text == null || d == null) {
            try {
                Actions.toast(ctx, "Elige el archivo ${com.suso.miiptv.data.Backup.FILE_NAME} (en Descargas)")
                restoreDoc.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            } catch (e: Exception) {
                Actions.toast(ctx, "No hay ninguna copia en la carpeta Download")
            }
            return
        }
        val fecha = java.text.SimpleDateFormat("d/M/yyyy HH:mm", java.util.Locale("es", "ES")).format(java.util.Date(d))
        ask("¿Restaurar la copia del $fecha?", listOf("Sí, restaurar (la app se reinicia)" to true, "No" to false), false) { yes ->
            if (yes) startRestore(text)
        }
    }
    val readPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) doRestore() else Actions.toast(ctx, "Sin permiso no se puede leer la carpeta Download")
    }
    fun restore() {
        if (Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) readPerm.launch(Manifest.permission.READ_EXTERNAL_STORAGE) else doRestore()
    }
    val bp = backupPin
    if (bp != null) {
        if (bp == "new") {
            PinEntry("Elige un PIN de 4 números para cifrar la copia", onCancel = { backupPin = null; shareAfter = false }) { p ->
                if (p.length != 4) Actions.toast(ctx, "El PIN debe tener 4 números") else backupPin = "confirm:$p"
            }
        } else {
            val first = bp.removePrefix("confirm:")
            PinEntry("Repite el PIN (te lo pedirá al restaurar)", onCancel = { backupPin = null }) { p ->
                if (p == first) {
                    backupPin = null
                    doBackup(p)
                } else {
                    Actions.toast(ctx, "Los PIN no coinciden")
                    backupPin = "new"
                }
            }
        }
        return
    }
    val rt = restoreText
    if (rt != null) {
        PinEntry(
            if (restoreBusy) "Restaurando…" else "PIN de la copia de seguridad",
            onCancel = { restoreText = null },
        ) { p -> if (!restoreBusy) applyRestore(rt, p) }
        return
    }
    if (logOpen) {
        ErrorLogView { logOpen = false }
        return
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val t = try {
                    io { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
                } catch (e: Exception) {
                    null
                }
                if (t == null) Actions.toast(ctx, "No se pudo leer el archivo") else useConf(t, uri.lastPathSegment ?: "archivo")
            }
        }
    }
    fun scanDownloads() {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val l = (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".conf", true) }
        if (l.isEmpty()) Actions.toast(ctx, "No hay archivos .conf en la carpeta Download") else downloadsList = l
    }
    val storagePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) scanDownloads() else Actions.toast(ctx, "Sin permiso no se puede leer la carpeta Download")
    }
    fun pickFile() {
        try {
            openDoc.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            // Muchas teles no tienen selector de archivos: buscamos en la carpeta Download.
            if (Build.VERSION.SDK_INT <= 32 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePerm.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                scanDownloads()
            }
        }
    }

    val dl = downloadsList
    if (dl != null) {
        FilePickList(dl, onCancel = { downloadsList = null }) { f ->
            downloadsList = null
            scope.launch {
                val t = try {
                    io { f.readText() }
                } catch (e: Exception) {
                    null
                }
                if (t == null) Actions.toast(ctx, "No se pudo leer ${f.name}") else useConf(t, f.name)
            }
        }
        return
    }
    when (debridDialog) {
        "pin" -> {
            DebridPinPanel(onClose = { debridDialog = null })
            return
        }
        "key" -> {
            UrlEntry(
                title = "Clave API de AllDebrid", button = "Guardar", hint = "La ves en alldebrid.com/apikeys",
                onCancel = { debridDialog = null },
            ) { k ->
                debridDialog = null
                com.suso.miiptv.debrid.AllDebrid.setKey(k)
                scope.launch {
                    val r = runCatching { com.suso.miiptv.data.io { com.suso.miiptv.debrid.AllDebrid.checkUser() } }
                    if (r.isSuccess) {
                        Actions.toast(ctx, "Cuenta vinculada: ${r.getOrNull()}")
                        com.suso.miiptv.debrid.DebridStore.clear()
                        com.suso.miiptv.debrid.DebridStore.open(force = true)
                    } else {
                        com.suso.miiptv.debrid.AllDebrid.setKey(null)
                        Actions.toast(ctx, r.exceptionOrNull()?.message ?: "Clave no válida")
                    }
                }
            }
            return
        }
        "tmdb" -> {
            UrlEntry(
                title = "Clave de TMDB", button = "Guardar", initial = com.suso.miiptv.debrid.AllDebrid.tmdbKey,
                hint = "Clave API (v3) o token de lectura", onCancel = { debridDialog = null },
            ) { k ->
                debridDialog = null
                com.suso.miiptv.debrid.AllDebrid.tmdbKey = k
                com.suso.miiptv.debrid.DebridStore.refreshMeta()
                Actions.toast(ctx, "Clave guardada. Buscando las fichas en español…")
            }
            return
        }
    }
    if (serverDialog) {
        val cfg = Repo.config
        val cur = if (cfg?.type == "xtream") cfg.server else cfg?.m3uUrl?.let { Uri.parse(it).let { u -> "${u.scheme}://${u.encodedAuthority}" } }.orEmpty()
        UrlEntry(
            title = "Dirección del servidor", button = "Conectar", initial = cur,
            hint = "http://servidor.com:8080", onCancel = { serverDialog = false },
        ) { url ->
            serverDialog = false
            changeServer(url)
        }
        return
    }
    // Nombre de perfil: -1 = nuevo; si no, el id del perfil a renombrar.
    val nd = nameDialog
    if (nd != null) {
        val old = if (nd >= 0) p.profiles().firstOrNull { it.id == nd }?.name.orEmpty() else ""
        UrlEntry(
            title = if (nd < 0) "Nombre del nuevo perfil" else "Nuevo nombre del perfil",
            button = "Guardar", initial = old, hint = "Nombre", text = true,
            onCancel = { nameDialog = null },
        ) { name ->
            nameDialog = null
            if (nd < 0) {
                val np = p.addProfile(name.trim())
                Actions.toast(ctx, "Perfil «${np.name}» creado")
            } else {
                p.renameProfile(nd, name.trim())
            }
        }
        return
    }
    if (epgUrlDialog) {
        UrlEntry(title = "Dirección de la guía XMLTV", button = "Guardar", initial = p.epgUrl, onCancel = { epgUrlDialog = false }) { url ->
            epgUrlDialog = false
            p.epgUrl = url
            if (p.epgMode == "provider") p.epgMode = "both"
            Repo.refreshEpg()
            Actions.toast(ctx, "Guía guardada")
        }
        return
    }
    if (urlDialog) {
        UrlEntry(onCancel = { urlDialog = false }) { url ->
            urlDialog = false
            scope.launch {
                val t = try {
                    io { Http.text(url) }
                } catch (e: Exception) {
                    Actions.toast(ctx, e.message ?: "No se pudo descargar")
                    null
                }
                if (t != null) useConf(t, Uri.parse(url).lastPathSegment ?: url)
            }
        }
        return
    }

    // Móvil, o tablet en vertical: primero la lista de secciones a pantalla completa; al tocar una, sus opciones.
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val compact = com.suso.miiptv.data.Device.touch &&
        (cfg.screenWidthDp < 600 || cfg.screenHeightDp > cfg.screenWidthDp)
    var mobilePane by remember { mutableStateOf(false) }

    // Atrás desde las opciones vuelve primero al menú de secciones.
    BackHandler(enabled = !compact && inPane && choice == null) {
        runCatching { sideFrs.getValue(sec).requestFocus() }
    }
    BackHandler(enabled = compact && mobilePane && choice == null) { mobilePane = false }

    Box(Modifier.fillMaxSize().background(S.panel)) {
        Row(Modifier.fillMaxSize()) {
            // ---------------- Menú lateral (fijo) ----------------
            if (!compact || !mobilePane) Column(
                Modifier
                    .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(280.dp))
                    .fillMaxHeight()
                    // Menú lateral en negro (más contraste para los iconos); las opciones, sobre el verde oscuro.
                    .background(C.bg)
                    // Por si en alguna tele no cabe entero: se desplaza en vez de quedar cortado.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 22.dp)) {
                    Logo(32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Ajustes", color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
                }
                Sec.values().forEach { s ->
                    SideItem(
                        label = s.label,
                        icon = when (s) {
                            Sec.ACCOUNT -> SIcons.account
                            Sec.ALLDEBRID -> DEBRID_ICON
                            Sec.VPN -> SIcons.shield
                            Sec.VIDEO -> SIcons.video
                            Sec.AUDIO -> SIcons.audio
                            Sec.SUBS -> SIcons.subs
                            Sec.CONTENT -> SIcons.content
                            Sec.EPG -> SIcons.guide
                            Sec.ABOUT -> SIcons.info
                        },
                        selected = sec == s,
                        cursorInMenu = !inPane && choice == null,
                        focusKey = "set_side_${s.name}",
                        requester = sideFrs.getValue(s),
                        // «Acerca de»: la «i» en rojo si hay una versión nueva por instalar.
                        iconColor = if (s == Sec.ABOUT && com.suso.miiptv.data.Updater.available != null) Color(0xFFE53935) else null,
                        onClick = if (compact) ({ sec = s; lastSec = s; mobilePane = true }) else null,
                        onFocused = {
                            val now = System.currentTimeMillis()
                            when {
                                now <= ignoreSideUntil -> {}
                                // Salto involuntario desde las opciones (◀ y Atrás siempre llevan a la sección actual).
                                s != sec && now - paneLeftAt < 400 -> scope.launch {
                                    delay(30)
                                    val back = FocusMemory.prev?.takeIf { !it.startsWith("set_side_") }
                                    if (!FocusMemory.focus(back)) runCatching { sideFrs.getValue(sec).requestFocus() }
                                }
                                else -> pending = s
                            }
                        },
                    )
                }
            }

            // ---------------- Opciones de la sección ----------------
            if (!compact || mobilePane) Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged {
                        if (inPane && !it.hasFocus) paneLeftAt = System.currentTimeMillis()
                        inPane = it.hasFocus
                        // Al entrar con ▶: se apunta cuál es la primera opción y se enseña la sección desde arriba.
                        if (it.hasFocus && enteringRight) {
                            enteringRight = false
                            scope.launch {
                                delay(40)
                                firstKey[sec] = FocusMemory.last
                                paneList.scrollToItem(0)
                            }
                        }
                    }
                    // Al subir hasta la primera opción, vuelve a verse la parte de arriba (título y datos).
                    .onPreviewKeyEvent { e ->
                        if (e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && e.key == androidx.compose.ui.input.key.Key.DirectionUp) {
                            scope.launch {
                                delay(60)
                                if (FocusMemory.last != null && FocusMemory.last == firstKey[sec]) paneList.animateScrollToItem(0)
                            }
                        }
                        false
                    }
                    // ◀ desde las opciones vuelve a la sección en la que estabas, no a la que quede a la misma altura.
                    .focusProperties {
                        exit = { d -> if (d == FocusDirection.Left) sideFrs.getValue(sec).orDefault() else FocusRequester.Default }
                        // ▶ desde el menú lateral entra siempre por la primera opción de arriba.
                        enter = { d ->
                            if (d == FocusDirection.Right) {
                                enteringRight = true
                                paneFr.orDefault()
                            } else {
                                FocusRequester.Default
                            }
                        }
                    }
                    .focusGroup(),
            ) {
                // Solo se desplaza al llegar al borde: al entrar en las opciones la pantalla se queda como está.
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides RevealBringIntoView,
                ) {
                LazyColumn(
                    state = paneList,
                    modifier = Modifier.fillMaxSize().fadeInOnChange(sec).focusRequester(paneFr),
                    contentPadding = if (compact) PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
                        else PaddingValues(start = 40.dp, end = 48.dp, top = 30.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "title_${sec.name}") {
                        Column(Modifier.padding(bottom = 8.dp)) {
                            if (compact) Text(
                                "‹  Ajustes", color = C.gold, fontSize = Sz.emph, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.tvClick { mobilePane = false }.padding(vertical = 8.dp),
                            )
                            Text(sec.label, color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
                            if (sec.desc.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(sec.desc, color = C.text2, fontSize = Sz.body)
                            }
                        }
                    }
                    when (sec) {
                        Sec.ACCOUNT -> item(key = "acc") { AccountSection(
                            onChangeList,
                            onServer = { picked -> if (picked == null) serverDialog = true else changeServer(picked) },
                            busy = serverBusy,
                        ) { t, o, s, f -> ask(t, o, s, f) } }
                        Sec.ALLDEBRID -> item(key = "alldebrid") {
                            DebridSection(ask = { t, o, sel, f -> ask(t, o, sel, f) }) { d -> debridDialog = d }
                        }
                        Sec.VPN -> item(key = "vpn") {
                            VpnSection(
                                onMode = { ask("Conexión VPN", VPN_MODE, p.vpnMode) { setVpnMode(it) } },
                                onConnect = { connectVpn() },
                                onFile = { pickFile() },
                                onUrl = { urlDialog = true },
                            )
                        }
                        Sec.VIDEO -> item(key = "video") { VideoSection { t, o, s, f -> ask(t, o, s, f) } }
                        Sec.AUDIO -> item(key = "audio") { AudioSection { t, o, s, f -> ask(t, o, s, f) } }
                        Sec.SUBS -> item(key = "subs") { SubsSection { t, o, s, f -> ask(t, o, s, f) } }
                        Sec.CONTENT -> item(key = "content") { ContentSection() }
                        Sec.EPG -> item(key = "epg") {
                            EpgSection(ask = { t, o, sel, f -> ask(t, o, sel, f) }, onUrl = { epgUrlDialog = true })
                        }
                        Sec.ABOUT -> item(key = "about") {
                            Column { AboutSection(onLog = { logOpen = true }, onBackup = { backup() }, onRestore = { restore() }, onShare = { shareAfter = true; backup() }) }
                        }
                    }
                }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        val c = choice as Choice<Any?>?
        if (c != null) {
            ChoiceDialog(c.title, c.options, c.selected, onPick = { v ->
                closeChoice()
                c.onPick(v)
            }, onClose = { closeChoice() })
        }
    }
}
