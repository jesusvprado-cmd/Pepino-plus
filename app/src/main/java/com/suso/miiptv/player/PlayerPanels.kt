package com.suso.miiptv.player

import android.app.Activity
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.toArgb
import com.suso.miiptv.ui.Sz
import com.suso.miiptv.ui.AppToastHost
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C as MC
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil.compose.AsyncImage
import com.suso.miiptv.data.Logos
import com.suso.miiptv.ui.ChannelLogoImage
import com.suso.miiptv.ui.Clock
import com.suso.miiptv.ui.LogoTone
import com.suso.miiptv.data.PlayItem
import com.suso.miiptv.data.Repo
import com.suso.miiptv.ui.ReminderPopup
import com.suso.miiptv.ui.C
import com.suso.miiptv.ui.fmtTime
import com.suso.miiptv.ui.hm
import com.suso.miiptv.ui.tvClick
import com.suso.miiptv.vpn.VpnManager
import kotlinx.coroutines.delay

// Paneles del reproductor: lista de canales, opciones (audio, subtítulos, imagen), teclado numérico y otros reproductores.

/** Lista lateral de canales (flecha izquierda en directo). */
@Composable
internal fun ChannelListPanel(ctl: PlayerCtl, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (ctl.index - 4).coerceAtLeast(0))
    Box(
        Modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(Color(0xF00A0A0A))
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                    onClose()
                    true
                } else {
                    false
                }
            }
    ) {
        LazyColumn(state = state, contentPadding = PaddingValues(vertical = 20.dp)) {
            itemsIndexed(ctl.items, key = { i, _ -> i }) { i, p ->
                var focused by remember { mutableStateOf(false) }
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    if (i == ctl.index) {
                        delay(60)
                        runCatching { fr.requestFocus() }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .focusRequester(fr)
                        .onFocusChanged { focused = it.isFocused }
                        .tvClick { onPick(i) }
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when {
                                focused -> Color.White
                                i == ctl.index -> C.sel
                                else -> Color.Transparent
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val n = p.channel?.num?.takeIf { it > 0 } ?: (i + 1)
                    Text("$n", color = if (focused) Color.Black else C.gold, fontSize = Sz.body, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                    val logo = remember(p.key, Logos.version) { Logos.url(p.title, p.image) }
                    Box(
                        Modifier.width(44.dp).height(28.dp).clip(RoundedCornerShape(4.dp))
                            .background(
                                if (logo != null) Color.White else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (logo != null) {
                            ChannelLogoImage(logo, Modifier.fillMaxSize().padding(2.dp), fallback = p.image)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        p.title,
                        color = if (focused) Color.Black else Color.White,
                        fontSize = Sz.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Panel lateral derecho: audio, subtítulos, imagen, velocidad, VPN y reproductor externo. */
@Composable
internal fun OptionsPanel(
    ctl: PlayerCtl,
    modifier: Modifier,
    aspect: String,
    onAspect: () -> Unit,
    onVpn: () -> Unit,
    onStartOver: (com.suso.miiptv.data.Item, com.suso.miiptv.data.EpgEntry) -> Unit,
    onExternal: () -> Unit,
    fav: Boolean = false,
    onFav: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    val v = ctl.tracksVersion
    @Suppress("UNUSED_VARIABLE")
    val sv = Repo.prefs.settingsVersion // se redibuja al cambiar un ajuste (p. ej. la frecuencia de la pantalla)
    val audio = remember(v, ctl.index) { ctl.trackOptions(MC.TRACK_TYPE_AUDIO) }
    val subs = remember(v, ctl.index) { ctl.trackOptions(MC.TRACK_TYPE_TEXT) }
    val subsOff = remember(v, ctl.index) { ctl.subtitlesOff() }
    val focusIdx = audio.indexOfFirst { it.selected }.coerceAtLeast(0)
    // El cursor solo se coloca al abrir el panel: si luego se recarga el canal (p. ej. al cambiar el formato de
    // emisión) y vuelven a aparecer las pistas, el cursor se queda en la opción que estabas tocando.
    var opening by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(400)
        opening = false
    }
    Column(
        modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(Color(0xF00A0A0A))
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 22.dp),
    ) {
        // Directo: el favorito se pone o se quita desde aquí (OK ya no lo cambia).
        if (onFav != null) {
            TrackRow(if (fav) "♥  Quitar de favoritos" else "♡  Añadir a favoritos", false, requestFocus = false) { onFav() }
            Spacer(Modifier.height(18.dp))
        }
        Text("Audio", color = C.gold, fontSize = Sz.emph, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        if (audio.isEmpty()) {
            Text("No hay pistas de audio", color = C.text2, fontSize = Sz.body, modifier = Modifier.padding(start = 10.dp))
        }
        audio.forEachIndexed { i, o ->
            TrackRow(o.label, o.selected, requestFocus = opening && i == focusIdx) {
                ctl.selectTrack(MC.TRACK_TYPE_AUDIO, o)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Subtítulos", color = C.gold, fontSize = Sz.emph, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        TrackRow("Desactivados", subsOff, requestFocus = opening && audio.isEmpty()) {
            ctl.selectTrack(MC.TRACK_TYPE_TEXT, null)
        }
        if (subs.isEmpty()) {
            Text("Este contenido no trae subtítulos", color = C.text2, fontSize = Sz.body, modifier = Modifier.padding(start = 10.dp, top = 6.dp))
        }
        subs.forEach { o ->
            TrackRow(o.label, o.selected && !subsOff, requestFocus = false) {
                ctl.selectTrack(MC.TRACK_TYPE_TEXT, o)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("Imagen y reproducción", color = C.gold, fontSize = Sz.emph, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        TrackRow("Formato de imagen: ${aspectLabel(aspect)}", false, requestFocus = false) { onAspect() }
        if (!ctl.current.live) {
            TrackRow("Velocidad: ${speedLabel(ctl.speed)}", false, requestFocus = false) { ctl.nextSpeed() }
        }
        // Si un canal va a saltos: probar el otro formato de emisión o ajustar la frecuencia de la pantalla al vídeo.
        if (ctl.current.live) {
            TrackRow("Formato de emisión: ${ctl.liveFormatLabel()} (cambiar)", false, requestFocus = false) { ctl.toggleLiveFormat() }
        }
        // Datos técnicos del vídeo (para saber por qué un canal no se ve o va a saltos).
        val hz = androidx.compose.ui.platform.LocalView.current.display?.refreshRate?.let { " · pantalla a ${"%.0f".format(it)} Hz" } ?: ""
        ctl.videoInfo?.let {
            Text("Vídeo: $it$hz", color = C.text2, fontSize = Sz.small, modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp))
        }
        TrackRow(
            "Ajustar frecuencia de la pantalla: " + (if (Repo.prefs.afr) "sí" else "no"),
            false, requestFocus = false,
        ) {
            Repo.prefs.afr = !Repo.prefs.afr
            ctl.reapplyFrameRate()
        }
        // Película o episodio: quitar o poner la ✓ de «ya visto».
        val wk = ctl.current.key
        if (!ctl.current.live && !wk.startsWith("CU|")) {
            val seen = remember(Repo.prefs.version, wk) { Repo.prefs.isWatched(wk) }
            TrackRow(if (seen) "Vista ✓ (quitar la marca)" else "Marcar como vista", false, requestFocus = false) {
                Repo.prefs.setWatched(wk, !seen)
            }
        }
        val left = SleepTimer.minutesLeft()
        TrackRow(
            "Temporizador de apagado: " + if (left > 0) "quedan $left min" else "desactivado",
            false, requestFocus = false,
        ) { SleepTimer.cycle() }
        // Canal que guarda lo emitido: empezar el programa actual desde el principio (p. ej. un partido ya empezado).
        val ch = ctl.current.channel
        val nowMs = System.currentTimeMillis()
        val cur = ctl.epg.firstOrNull { it.start <= nowMs && it.end > nowMs }
        // Si la guía dice que el servidor no tiene grabado nada de este canal, no se ofrece (daría error 404).
        val archiveWorks by androidx.compose.runtime.produceState<Boolean?>(null, ch?.key) {
            value = ch?.let { Repo.archiveWorks(it) }
        }
        if (ctl.current.live && ch != null && ch.archive > 0 && cur != null && archiveWorks != false) {
            TrackRow("Ver desde el principio: ${cur.title}", false, requestFocus = false) { onStartOver(ch, cur) }
        }
        val vs = VpnManager.state
        val vpnText = when (vs) {
            VpnManager.State.ON -> "VPN: activada" + (VpnManager.info?.let { " · $it" } ?: "")
            VpnManager.State.CONNECTING -> "VPN: conectando…"
            VpnManager.State.ERROR -> "VPN: error · " + (VpnManager.message ?: "")
            VpnManager.State.OFF -> "VPN: desactivada"
        }
        TrackRow(vpnText, vs == VpnManager.State.ON, requestFocus = false) { onVpn() }
        TrackRow("Abrir en otro reproductor", false, requestFocus = false) { onExternal() }
        Spacer(Modifier.height(18.dp))
        Text("◀ para cerrar", color = C.text2, fontSize = Sz.small, modifier = Modifier.padding(start = 10.dp))
    }
}

internal fun speedLabel(f: Float): String = when (f) {
    0.5f -> "0,5x"
    0.75f -> "0,75x"
    1.25f -> "1,25x"
    1.5f -> "1,5x"
    2f -> "2x"
    else -> "normal"
}

@Composable
internal fun TrackRow(label: String, selected: Boolean, requestFocus: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (requestFocus) {
            delay(60)
            runCatching { fr.requestFocus() }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused }
            .tvClick(onClick = onClick)
            .padding(end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.suso.miiptv.ui.FocusBar(focused, height = 22.dp)
        Text(
            if (selected) "✓" else "",
            color = C.gold,
            fontSize = Sz.emph, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(26.dp),
        )
        Text(label, color = if (focused) C.gold else Color.White, fontSize = Sz.body, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Teclado numérico en pantalla para ir a un canal por su número (mandos sin números, como el de la Mi Box). */
@Composable
internal fun NumberPad(
    typed: String,
    hasPrevious: Boolean,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onPrevious: () -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF00B140D))
            .border(1.dp, C.gold.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Ir al canal", color = C.text2, fontSize = Sz.body)
        Text(
            typed.ifEmpty { "—" }, color = C.gold, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("↩", "0", "⌫"))
        rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                row.forEachIndexed { c, k ->
                    PadKey(
                        k, "pad_$k",
                        enabled = k != "↩" || hasPrevious,
                        modifier = if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier,
                    ) {
                        when (k) {
                            "↩" -> onPrevious()
                            "⌫" -> onDelete()
                            else -> onDigit(k.toInt())
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PadKey("Ir", "pad_go", wide = true, enabled = typed.isNotEmpty()) { onGo() }
        Spacer(Modifier.height(8.dp))
        Text("↩ canal anterior · ⌫ borrar · Atrás cerrar", color = Color(0xFF7A7A7A), fontSize = Sz.small)
    }
}

@Composable
internal fun PadKey(label: String, key: String, modifier: Modifier = Modifier, wide: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    // Solo cuenta una pulsación que empieza en esta tecla (así no se «pulsa» al soltar el OK mantenido que abrió el teclado).
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .width(if (wide) 222.dp else 64.dp)
            .height(54.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) pressed = false
            }
            .focusProperties { canFocus = enabled }
            .focusable()
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                when {
                    !ok -> false
                    e.type == KeyEventType.KeyDown -> {
                        if (e.nativeKeyEvent.repeatCount == 0) pressed = true
                        true
                    }
                    e.type == KeyEventType.KeyUp -> {
                        if (pressed) onClick()
                        pressed = false
                        true
                    }
                    else -> true
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) C.gold else Color(0xFF1C2A1F)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = Sz.sub, fontWeight = FontWeight.Bold,
            color = when {
                focused -> Color.Black
                enabled -> Color.White
                else -> Color(0xFF555555)
            },
        )
    }
}


/** Lista de reproductores instalados para abrir el vídeo (se desplaza; no queda cortada). */
@Composable
internal fun PlayersPanel(players: List<Pair<String, String>>, modifier: Modifier, onPick: (String) -> Unit) {
    Column(
        modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(Color(0xF00A0A0A))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 22.dp),
    ) {
        Text("Abrir con otro reproductor", color = C.gold, fontSize = Sz.emph, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        if (players.isEmpty()) {
            Text("No hay otros reproductores instalados", color = C.text2, fontSize = Sz.body, modifier = Modifier.padding(start = 10.dp))
        }
        players.forEachIndexed { i, (name, pkg) ->
            TrackRow(name, pkg == Repo.prefs.externalPkg, requestFocus = i == 0) { onPick(pkg) }
        }
        Spacer(Modifier.height(14.dp))
        Text("Atrás para cerrar", color = C.text2, fontSize = Sz.small, modifier = Modifier.padding(start = 10.dp))
    }
}
