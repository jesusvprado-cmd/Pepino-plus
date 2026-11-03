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

// Secciones de Ajustes (cuenta, VPN, guía, vídeo, audio, subtítulos, contenido, perfiles y acerca de).

/** Forma común de abrir la ventana de elección desde cada sección. */
internal typealias Ask = (String, List<Pair<String, Any?>>, Any?, (Any?) -> Unit) -> Unit

@Suppress("UNCHECKED_CAST")
internal fun <T> Ask.of(title: String, options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit) =
    this(title, options as List<Pair<String, Any?>>, selected) { v -> onPick(v as T) }

// ---------------- Secciones ----------------

@Composable
internal fun AccountSection(onChangeList: () -> Unit, onServer: (String?) -> Unit, busy: Boolean, ask: Ask) {
    @Suppress("UNUSED_VARIABLE")
    val sv = Repo.prefs.settingsVersion // se redibuja al momento al cambiar un ajuste
    val ctx = LocalContext.current
    val cfg = Repo.config
    var acc by remember { mutableStateOf<AccountInfo?>(null) }
    var accState by remember { mutableStateOf("Comprobando…") }
    LaunchedEffect(Repo.dataVersion) {
        try {
            acc = Repo.source?.check()
            accState = if (acc == null) "" else ""
        } catch (e: Exception) {
            accState = e.message ?: "Error"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = mutableListOf<Pair<String, String>>()
        if (cfg != null) {
            if (cfg.type == "xtream") {
                rows += "Tipo" to "Xtream Codes"
                rows += "Servidor" to cfg.server
                rows += "Usuario" to cfg.user
            } else {
                rows += "Tipo" to "Lista M3U"
                rows += "Servidor" to (Uri.parse(cfg.m3uUrl).host ?: cfg.m3uUrl)
                if (cfg.epgUrl.isNotBlank()) rows += "Guía EPG" to (Uri.parse(cfg.epgUrl).host ?: cfg.epgUrl)
            }
        }
        val a = acc
        val status = when {
            a != null && a.status.equals("Active", true) -> "Suscripción activa" to S.ok
            a != null -> "Estado: ${a.status ?: "desconocido"}" to S.warn
            accState == "Comprobando…" -> accState to S.warn
            accState.isNotEmpty() -> accState to S.bad
            else -> "Lista cargada" to S.ok
        }
        if (a != null) {
            rows += "Caduca" to (a.expiry?.let { dateStr(it) } ?: "Sin caducidad")
            rows += "Conexiones" to "${a.activeConnections ?: "0"} en uso de ${a.maxConnections ?: "?"}"
        }
        InfoCard(rows, status)
        GroupLabel("Lista")
        SettingRow("Recargar lista", "set_reload", subtitle = "Vuelve a descargar canales, películas y series") {
            Repo.reload()
            Actions.toast(ctx, "Lista recargada")
        }
        if (cfg != null) {
            val cur = if (cfg.type == "xtream") cfg.server else Uri.parse(cfg.m3uUrl).let { "${it.scheme}://${it.encodedAuthority}" }
            SettingRow(
                "Cambiar servidor", "set_server",
                subtitle = if (busy) "Comprobando el servidor…" else "Misma cuenta (usuario y contraseña), otra dirección",
            ) {
                if (busy) return@SettingRow
                // Con servidores usados antes: se elige de la lista o se escribe otro.
                val prev = Repo.prefs.serverHistory().filter { it != cur }
                if (prev.isEmpty()) onServer(null)
                else ask.of("Cambiar servidor", prev.map { it to it } + ("Escribir otra dirección…" to "__custom"), "") { v ->
                    onServer(if (v == "__custom") null else v)
                }
            }
        }
        SettingRow("Cambiar lista o cuenta", "set_change", subtitle = "Otro servidor, usuario o lista M3U") { onChangeList() }
        if (cfg != null && cfg.type == "xtream") {
            GroupLabel("Directo")
            SettingRow(
                "Formato de los canales", "set_fmt",
                subtitle = "Si algún canal no carga, prueba el otro",
                value = label(LIVE_FMT, cfg.liveFormat),
            ) {
                ask.of("Formato de los canales", LIVE_FMT, cfg.liveFormat) { v ->
                    if (v != cfg.liveFormat) Repo.save(cfg.copy(liveFormat = v))
                }
            }
        }
    }
}

@Composable
internal fun VpnSection(onMode: () -> Unit, onConnect: () -> Unit, onFile: () -> Unit, onUrl: () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val sv = Repo.prefs.settingsVersion // se redibuja al momento al cambiar un ajuste
    val p = Repo.prefs
    val vs = VpnManager.state
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val status = when (vs) {
            VpnManager.State.ON -> "Conectada" to S.ok
            VpnManager.State.CONNECTING -> (VpnManager.message ?: "Conectando…") to S.warn
            VpnManager.State.ERROR -> "Error" to S.bad
            VpnManager.State.OFF -> "Desconectada" to Color(0xFF777777)
        }
        // Siempre las mismas filas (con «—» si no hay dato) para que nada cambie de altura al conectar o desconectar.
        val rows = mutableListOf("Tipo" to label(VPN_MODE, p.vpnMode))
        rows += "Salida" to when {
            vs == VpnManager.State.ERROR -> VpnManager.message ?: "—"
            vs == VpnManager.State.ON -> VpnManager.info ?: "—"
            else -> "—"
        }
        rows += "Alcance" to when {
            vs != VpnManager.State.ON -> "—"
            VpnManager.wholeDevice -> "Todo el equipo (no permite limitarla a la app)"
            else -> "Solo esta app"
        }
        if (p.vpnMode == "wg") rows += "Perfil" to (p.wgConf?.let { VpnManager.profileName(it) } ?: "ninguno cargado")
        InfoCard(rows, status)

        GroupLabel("Conexión")
        SettingRow("Tipo de VPN", "set_vpn_mode", value = label(VPN_MODE, p.vpnMode)) { onMode() }
        ToggleRow(
            "Conectar al abrir la app", "set_vpn_auto", p.vpnAuto,
            subtitle = "Desactivado: la VPN solo se conecta cuando tú lo pidas",
        ) { p.vpnAuto = it }
        ToggleRow(
            "Activarla si un canal se corta", "set_vpn_fallback", p.vpnFallback,
            subtitle = "Si un directo se congela 3 veces seguidas, conecta la VPN sola y lo reintenta",
        ) { p.vpnFallback = it }
        if (p.vpnMode != "off") {
            // Una sola fila que cambia de texto: así el foco se queda en ella al conectar o desconectar.
            val connected = vs == VpnManager.State.ON || vs == VpnManager.State.CONNECTING
            SettingRow(if (connected) "Desconectar" else "Conectar", "set_vpn_toggle", chevron = false) {
                if (connected) VpnManager.disconnect() else onConnect()
            }
        }
        SettingRow("Comprobar IP de salida", "set_vpn_ip", chevron = false, enabled = vs == VpnManager.State.ON) { VpnManager.refreshInfo() }
        if (p.vpnMode == "warp") {
            SettingRow("Nueva cuenta WARP", "set_vpn_renew", subtitle = "Si WARP deja de conectar", chevron = false) { VpnManager.renewWarp() }
        }
        GroupLabel("Perfil WireGuard propio")
        SettingRow("Cargar archivo .conf", "set_vpn_file", subtitle = "Envíalo a la tele con Send Files to TV") { onFile() }
        SettingRow("Cargar desde una dirección web", "set_vpn_url") { onUrl() }
        Text(
            "Sirve el archivo de Proton VPN (gratis) o de cualquier VPN que ofrezca WireGuard.",
            color = Color(0xFF777777), fontSize = Sz.small, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }
}

internal val EPG_MODE = listOf(
    "La de mi lista" to "provider",
    "La de mi lista y, si falta, la externa" to "both",
    "Solo la guía externa" to "external",
)
internal val EPG_OFFSET = listOf(
    "−3 horas" to -180, "−2 horas" to -120, "−1 hora" to -60, "−30 minutos" to -30, "Sin ajuste" to 0,
    "+30 minutos" to 30, "+1 hora" to 60, "+2 horas" to 120, "+3 horas" to 180,
)
/** Guías XMLTV públicas para elegir sin escribir la dirección. */
internal val EPG_PRESETS = listOf(
    "Guía de España (dobleM)" to "https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv.xml.gz",
    "Guía de España para IPTV (dobleM)" to "https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiaiptv.xml",
    "Escribir otra dirección…" to "__custom",
    "Quitar la guía externa" to "",
)

@Composable
internal fun EpgSection(ask: Ask, onUrl: () -> Unit) {
    @Suppress("UNUSED_VARIABLE")
    val sv = Repo.prefs.settingsVersion // se redibuja al momento al cambiar un ajuste
    val p = Repo.prefs
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleRow(
            "Mostrar la guía", "set_epg_on", p.epgEnabled,
            subtitle = "Programa actual y siguiente en la lista de canales y en el reproductor",
        ) { p.epgEnabled = it; Repo.refreshEpg() }
        if (p.epgEnabled) {
            GroupLabel("Origen")
            SettingRow("Guía que se usa", "set_epg_mode", value = label(EPG_MODE, p.epgMode)) {
                ask.of("Guía que se usa", EPG_MODE, p.epgMode) { v ->
                    p.epgMode = v
                    Repo.refreshEpg()
                }
            }
            val ext = p.epgUrl
            val extName = EPG_PRESETS.firstOrNull { it.second == ext && ext.isNotEmpty() }?.first
                ?: if (ext.isEmpty()) "Ninguna" else Uri.parse(ext).host ?: ext
            SettingRow("Guía externa (XMLTV)", "set_epg_url", subtitle = "Sirve para canales sin guía o con la guía mal", value = extName) {
                ask.of("Guía externa", EPG_PRESETS, ext) { v ->
                    when (v) {
                        "__custom" -> onUrl()
                        else -> {
                            p.epgUrl = v
                            if (v.isNotEmpty() && p.epgMode == "provider") p.epgMode = "both"
                            if (v.isEmpty() && p.epgMode == "external") p.epgMode = "provider"
                            Repo.refreshEpg()
                        }
                    }
                }
            }
            GroupLabel("Ajustes")
            SettingRow("Ajuste de hora", "set_epg_off", subtitle = "Si la guía va adelantada o atrasada", value = label(EPG_OFFSET, p.epgOffset)) {
                ask.of("Ajuste de hora", EPG_OFFSET, p.epgOffset) { v ->
                    p.epgOffset = v
                }
            }
            SettingRow("Actualizar la guía ahora", "set_epg_refresh", chevron = false) {
                Repo.refreshEpg()
                Actions.toast(ctx, "La guía se volverá a descargar")
            }
        }
    }
}

@Composable
internal fun VideoSection(ask: Ask) {
    @Suppress("UNUSED_VARIABLE")
    val sv = Repo.prefs.settingsVersion // se redibuja al momento al cambiar un ajuste
    val p = Repo.prefs
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel("Imagen")
        SettingRow("Formato de imagen", "set_res", subtitle = "También se cambia desde el reproductor", value = label(RESIZE, p.resizeMode)) {
            ask.of("Formato de imagen", RESIZE, p.resizeMode) { p.resizeMode = it }
        }
        ToggleRow(
            "Ajustar la frecuencia de la pantalla", "set_afr", p.afr,
            subtitle = "Cambia a 24, 25, 50 o 60 Hz según el vídeo, si tu tele lo permite",
        ) { p.afr = it }
        SettingRow(
            "Modo ligero", "set_light",
            subtitle = "Menos animaciones y efectos para que vaya más fluida en aparatos modestos. Se aplica al volver a abrir la app" +
                if (p.lightMode == "auto") (if (com.suso.miiptv.data.Perf.isWeak(LocalContext.current)) " (este aparato: activado)" else " (este aparato: desactivado)") else "",
            value = label(LIGHT, p.lightMode),
        ) {
            ask.of("Modo ligero", LIGHT, p.lightMode) { p.lightMode = it }
        }
        GroupLabel("Reproducción")
        SettingRow("Búfer", "set_buf", subtitle = "Más alto: menos cortes, pero tarda más en arrancar", value = label(BUFFER, p.bufferMode)) {
            ask.of("Búfer", BUFFER, p.bufferMode) { p.bufferMode = it }
        }
        ToggleRow(
            "Reproducir el siguiente episodio", "set_autonext", p.autoNext,
            subtitle = "Al terminar un episodio empieza solo el siguiente, también el de la temporada siguiente",
        ) { p.autoNext = it }
        SettingRow("Salto al avanzar o retroceder", "set_seek", subtitle = "En películas y series", value = label(SEEK, p.seekStep)) {
            ask.of("Salto al avanzar o retroceder", SEEK, p.seekStep) { p.seekStep = it }
        }
        val ctx = LocalContext.current
        fun pickPlayer() {
            val players = com.suso.miiptv.player.PlayQueue.installedPlayers(ctx)
            if (players.isEmpty()) {
                Actions.toast(ctx, "No hay otro reproductor instalado (por ejemplo VLC o MX Player)")
                p.externalPlayer = false
                return
            }
            ask.of("Elige el reproductor", players.map { it.first to it.second }, p.externalPkg) { pkg ->
                p.externalPkg = pkg
                p.externalPlayer = true
            }
        }
        ToggleRow(
            "Usar otro reproductor", "set_ext", p.externalPlayer,
            subtitle = "Abrir los vídeos en VLC, MX Player u otro instalado",
        ) { on ->
            if (on) pickPlayer() else p.externalPlayer = false
        }
        if (p.externalPlayer) {
            val name = com.suso.miiptv.player.PlayQueue.installedPlayers(ctx).firstOrNull { it.second == p.externalPkg }?.first ?: "Elegir…"
            SettingRow("Reproductor elegido", "set_ext_pkg", value = name) { pickPlayer() }
        }
    }
}

@Composable
internal fun AudioSection(ask: Ask) {
    @Suppress("UNUSED_VARIABLE")
    val sv = Repo.prefs.settingsVersion // se redibuja al momento al cambiar un ajuste
    val p = Repo.prefs
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow(
            "Salida de audio", "set_dec",
            subtitle = if (p.audioDecode) "La app decodifica Dolby y DTS. Úsalo si algún canal no se oye."
            else "Dolby y DTS van directos a tu tele o barra de sonido si los admiten",
            value = label(AUDIO_OUT, p.audioDecode),
        ) {
            ask.of("Salida de audio", AUDIO_OUT, p.audioDecode) { p.audioDecode = it }
        }
        SettingRow("Aumentar volumen", "set_boost", subtitle = "Para canales que suenan bajos", value = label(BOOST, p.volumeBoost)) {
            ask.of("Aumentar volumen", BOOST, p.volumeBoost) { p.volumeBoost = it }
        }
        SettingRow("Idioma preferido", "set_alang", value = label(AUDIO_LANG, p.audioLang ?: "es")) {
            ask.of("Idioma de audio preferido", AUDIO_LANG, p.audioLang ?: "es") { p.audioLang = it }
        }
    }
}

@Composable
internal fun SubsSection(ask: Ask) {
    @Suppress("UNUSED_VARIABLE")
    val sv = Repo.prefs.settingsVersion // se redibuja al momento al cambiar un ajuste
    val p = Repo.prefs
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitlePreview()
        SettingRow("Idioma preferido", "set_slang", value = label(SUB_LANG, p.subLang ?: "")) {
            ask.of("Subtítulos", SUB_LANG, p.subLang ?: "") { p.subLang = it }
        }
        GroupLabel("Aspecto")
        SettingRow("Tamaño", "set_ssize", value = label(SUB_SIZE, p.subSize)) {
            ask.of("Tamaño", SUB_SIZE, p.subSize) { p.subSize = it }
        }
        SettingRow("Color", "set_scolor", value = label(SUB_COLOR, p.subColor)) {
            ask.of("Color", SUB_COLOR, p.subColor) { p.subColor = it }
        }
        SettingRow("Fondo", "set_sbg", value = label(SUB_BG, p.subBg)) {
            ask.of("Fondo", SUB_BG, p.subBg) { p.subBg = it }
        }
    }
}

/** Muestra cómo se verán los subtítulos con los ajustes actuales. */
@Composable
internal fun SubtitlePreview() {
    val p = Repo.prefs
    val size = when (p.subSize) {
        "s" -> 15.sp
        "l" -> 22.sp
        "xl" -> 26.sp
        else -> 18.sp
    }
    val fg = if (p.subColor == "yellow") Color(0xFFFFE650) else Color.White
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1B3A20), Color(0xFF268332)))),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val textMod = if (p.subBg == "band") {
            Modifier.padding(bottom = 14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xAA000000)).padding(horizontal = 10.dp, vertical = 4.dp)
        } else {
            Modifier.padding(bottom = 14.dp)
        }
        Text(
            "Así se verán los subtítulos",
            color = fg,
            fontSize = size,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            style = if (p.subBg == "band") androidx.compose.ui.text.TextStyle.Default
            else androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(1.5f, 1.5f), 3f),
            ),
            modifier = textMod,
        )
    }
}

@Composable
internal fun ContentSection() {
    @Suppress("UNUSED_VARIABLE")
    val sv = Repo.prefs.settingsVersion // se redibuja al momento al cambiar un ajuste
    val ctx = LocalContext.current
    val p = Repo.prefs
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow(
            "Categorías ocultas", "set_hidden",
            subtitle = "Las de adultos se ocultan solas. Puedes proteger esta opción con un PIN.",
            value = if (p.hasPin) "Con PIN" else null,
        ) { UiState.push(Screen.HiddenCats) }
        ToggleRow(
            "Logos de canales en alta calidad", "set_logos", p.hqLogos,
            subtitle = "Usa logos de bancos abiertos en lugar de los de tu lista. El nombre del canal no cambia.",
        ) { v ->
            p.hqLogos = v
            if (v) com.suso.miiptv.data.Logos.ensureLoaded()
        }
        SettingRow("Borrar «Continuar viendo»", "set_clear", subtitle = "Quita el historial de películas y series empezadas", chevron = false) {
            p.clearProgress()
            Actions.toast(ctx, "Historial borrado")
        }
    }
}

@Composable
internal fun AboutSection(onLog: () -> Unit, onBackup: () -> Unit, onRestore: () -> Unit, onShare: () -> Unit = {}) {
    val ctx = LocalContext.current
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(S.panel)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(64.dp)
        Spacer(Modifier.height(14.dp))
        Text(BrandName(), color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
        Text("Versión $version", color = C.gold, fontSize = Sz.body)
        Spacer(Modifier.height(10.dp))
        Text(
            "Reproductor para usar con tu propia lista IPTV.\nNo incluye canales ni contenidos.",
            color = C.text2, fontSize = Sz.body, textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
    }
    Spacer(Modifier.height(10.dp))
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    SettingRow(
        "Buscar actualizaciones", "set_update",
        subtitle = if (checking) "Comprobando…" else "Descarga e instala la última versión desde la propia app",
        chevron = false,
    ) {
        if (checking) return@SettingRow
        checking = true
        scope.launch {
            val has = try {
                com.suso.miiptv.data.Updater.check(ctx, force = true)
            } catch (e: Exception) {
                Actions.toast(ctx, "No se pudo comprobar: ${e.message ?: "sin conexión"}")
                checking = false
                return@launch
            }
            checking = false
            if (has) com.suso.miiptv.data.Updater.dismissed = false
            else Actions.toast(ctx, "Ya tienes la última versión")
        }
    }
    GroupLabel("Copia de seguridad")
    val last = remember { com.suso.miiptv.data.Backup.readFile()?.let { com.suso.miiptv.data.Backup.date(it) } }
    SettingRow(
        "Guardar copia de seguridad", "set_backup",
        subtitle = "Lista, ajustes, perfiles, favoritos y vistos, cifrados con un PIN, en Download/${com.suso.miiptv.data.Backup.FILE_NAME}",
        chevron = false,
    ) { onBackup() }
    // Pasar favoritos, perfiles y ajustes a otro aparato: la copia cifrada se envía por WhatsApp, Drive, correo,
    // «Send Files to TV»… y en el otro aparato se restaura con el mismo PIN.
    if (com.suso.miiptv.data.Device.touch) SettingRow(
        "Enviar copia a otro aparato", "set_share",
        subtitle = "La copia cifrada, para restaurarla en otro aparato con el mismo PIN",
        chevron = false,
    ) { onShare() }
    SettingRow(
        "Restaurar copia de seguridad", "set_restore",
        subtitle = last?.let { "Copia del " + java.text.SimpleDateFormat("d/M/yyyy HH:mm", java.util.Locale("es", "ES")).format(java.util.Date(it)) }
            ?: "Busca la copia en la carpeta Download",
        chevron = false,
    ) { onRestore() }
    GroupLabel("Problemas")
    SettingRow("Registro de errores", "set_log", subtitle = "Lo último que ha fallado, para poder enseñármelo") { onLog() }
    SettingRow("Revisión de logos", "set_logos", subtitle = "Canales sin logo de calidad y lista para mandarme a revisar") {
        UiState.push(Screen.MissingLogos)
    }
}

/** Ajustes → Perfiles: usar, añadir, renombrar y borrar perfiles. */
@Composable
internal fun ProfilesSection(
    ask: (String, List<Pair<String, String>>, String, (String) -> Unit) -> Unit,
    onName: (Int) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val sv = Repo.prefs.settingsVersion
    val p = Repo.prefs
    val ctx = LocalContext.current
    val list = p.profiles()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel("Perfiles")
        list.forEach { pr ->
            val active = pr.id == p.profileId
            SettingRow(
                pr.name, "set_prof_${pr.id}",
                subtitle = if (active) "En uso" else null,
                value = if (active) "✓" else null,
            ) {
                val opts = buildList {
                    if (!active) add("Usar este perfil" to "use")
                    add("Cambiar el nombre" to "rename")
                    if (pr.id != 0) add("Borrar el perfil" to "delete")
                }
                ask(pr.name, opts, "") { a ->
                    when (a) {
                        "use" -> {
                            p.switchProfile(pr.id)
                            Actions.toast(ctx, "Ahora estás en el perfil «${pr.name}»")
                        }
                        "rename" -> onName(pr.id)
                        "delete" -> {
                            p.deleteProfile(pr.id)
                            Actions.toast(ctx, "Perfil borrado")
                        }
                    }
                }
            }
        }
        if (list.size < 6) SettingRow("Añadir perfil", "set_prof_add", chevron = false) { onName(-1) }
        ToggleRow(
            "Preguntar quién ve al abrir la app", "set_prof_ask", p.askProfile,
            subtitle = "Solo si hay más de un perfil",
        ) { p.askProfile = it }
    }
}
