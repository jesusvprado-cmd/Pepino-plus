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

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(ctl: PlayerCtl, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val item = ctl.current
    var overlay by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var listOpen by remember { mutableStateOf(false) }
    var tracksOpen by remember { mutableStateOf(false) }
    // Teclado numérico en pantalla (OK mantenido en un directo) y número tecleado con el mando.
    var padOpen by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var okLong by remember { mutableStateOf(false) }
    var reminderOpen by remember { mutableStateOf(false) }
    var playersOpen by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(Repo.prefs.resizeMode) }
    // Permiso de Android para la VPN (solo la primera vez).
    val vpnPerm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) VpnManager.connect()
        else com.suso.miiptv.ui.AppToast.show("Sin permiso no se puede activar la VPN")
    }
    fun toggleVpn() {
        val p = Repo.prefs
        if (VpnManager.state == VpnManager.State.ON || VpnManager.state == VpnManager.State.CONNECTING) {
            VpnManager.disconnect()
            return
        }
        if (p.vpnMode == "off") p.vpnMode = if (p.wgConf != null) "wg" else "warp"
        val intent = VpnManager.permissionIntent(ctx)
        if (intent == null) {
            VpnManager.connect()
            return
        }
        try {
            vpnPerm.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            com.suso.miiptv.ui.AppToast.show("Este dispositivo no permite que las apps usen VPN")
        }
    }
    // Al conectar o desconectar la VPN se vuelve a abrir el canal por la nueva ruta.
    val vpnChanges = VpnManager.changes
    var lastVpnChanges by remember { mutableIntStateOf(vpnChanges) }
    LaunchedEffect(vpnChanges) {
        if (vpnChanges != lastVpnChanges) {
            lastVpnChanges = vpnChanges
            ctl.retry()
        }
    }
    val rootFr = remember { FocusRequester() }
    val pv = Repo.prefs.version
    val fav = remember(pv, ctl.index) { ctl.isFavorite() }

    // Siguiente episodio: aviso en los últimos 25 segundos (OK lo empieza ya; si no, empieza solo al terminar).
    val hasNext = !item.live && ctl.index < ctl.items.lastIndex
    var nextDismissed by remember(ctl.index) { mutableStateOf(false) }
    // La posición cambia cada medio segundo: se lee dentro de derivedStateOf para que la pantalla entera
    // no se vuelva a dibujar dos veces por segundo (solo cuando el aviso aparece o desaparece).
    val showNext by remember(ctl.index, hasNext) {
        androidx.compose.runtime.derivedStateOf {
            val remaining = ctl.duration - ctl.position
            hasNext && !nextDismissed && ctl.duration > 120_000 && remaining in 1..25_000 && ctl.error == null && ctl.scrubPos == null
        }
    }

    fun show() {
        overlay = true
        overlayTick++
    }

    fun toggleFav() {
        val added = ctl.toggleFavorite() ?: return
        com.suso.miiptv.ui.AppToast.show(if (added) "Añadido a favoritos" else "Quitado de favoritos")
        show()
    }

    LaunchedEffect(ctl.index) { show() }
    LaunchedEffect(overlayTick, overlay, ctl.isPlaying, ctl.error) {
        if (overlay && ctl.error == null && (item.live || ctl.isPlaying)) {
            delay(5000)
            overlay = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            ctl.tick()
            // Temporizador de apagado.
            val end = SleepTimer.endAt
            if (end > 0 && System.currentTimeMillis() >= end) {
                SleepTimer.endAt = 0L
                ctl.player.pause()
                com.suso.miiptv.ui.AppToast.show("Temporizador: reproducción detenida", true)
                onExit()
            }
            delay(500)
        }
    }
    // Número tecleado: al dejar de pulsar un momento, se salta a ese canal.
    LaunchedEffect(typed) {
        if (typed.isEmpty() || padOpen) return@LaunchedEffect
        delay(1600)
        val n = typed.toIntOrNull()
        typed = ""
        if (n != null && !ctl.zapToNumber(n)) com.suso.miiptv.ui.AppToast.show("No hay canal $n")
    }
    // Avisos breves (reconexión, VPN...).
    val notice = ctl.notice
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3500)
            ctl.clearNotice()
        }
    }
    LaunchedEffect(listOpen, tracksOpen, padOpen, reminderOpen, playersOpen) {
        if (!listOpen && !tracksOpen && !padOpen && !reminderOpen && !playersOpen) {
            delay(40)
            runCatching { rootFr.requestFocus() }
        }
    }

    BackHandler {
        when {
            playersOpen -> playersOpen = false
            showNext -> nextDismissed = true
            padOpen -> {
                padOpen = false
                typed = ""
            }
            ctl.scrubPos != null -> ctl.cancelScrub()
            tracksOpen -> tracksOpen = false
            listOpen -> listOpen = false
            ctl.backToLive() -> {}
            else -> onExit()
        }
    }

    fun step(repeat: Int): Long = ctl.seekStepMs(repeat)

    fun shortOk() {
        when {
            ctl.commitScrub() -> show()
            ctl.error != null -> ctl.retry()
            // Sin imagen en la app: OK abre la lista de reproductores para verlo con otro.
            ctl.videoProblem != null -> playersOpen = true
            showNext -> ctl.playNext()
            // En directo, OK solo enseña la barra (antes cambiaba el favorito y se tocaba sin querer).
            item.live -> show()
            else -> {
                ctl.togglePlay()
                show()
            }
        }
    }

    val onKey: (KeyEvent) -> Boolean = handler@{ e ->
        if (listOpen || tracksOpen || padOpen || reminderOpen || playersOpen) return@handler false
        val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        // En directo, OK se decide al soltar: pulsación corta = lo de siempre; mantenido = teclado de números.
        if (isOk && item.live) {
            if (e.type == KeyEventType.KeyUp) {
                if (!okLong) shortOk()
                okLong = false
            } else if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 1 && ctl.error == null) {
                okLong = true
                typed = ""
                padOpen = true
            }
            return@handler true
        }
        if (e.type != KeyEventType.KeyDown) return@handler false
        val rep = e.nativeKeyEvent.repeatCount
        // Números del mando (teles con teclado numérico).
        val digit = when (e.nativeKeyEvent.keyCode) {
            in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> e.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_0
            in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> e.nativeKeyEvent.keyCode - android.view.KeyEvent.KEYCODE_NUMPAD_0
            else -> -1
        }
        if (digit >= 0 && item.live) {
            if (rep == 0 && typed.length < 4) typed += digit
            return@handler true
        }
        if (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_LAST_CHANNEL) {
            if (!ctl.zapBack()) show()
            return@handler true
        }
        when (e.key) {
            Key.DirectionUp -> {
                if (item.live) ctl.zap(-1) else show()
                true
            }
            Key.DirectionDown -> {
                if (item.live) ctl.zap(1) else tracksOpen = true
                true
            }
            Key.Menu -> {
                tracksOpen = true
                true
            }
            Key.ChannelUp -> {
                ctl.zap(1)
                true
            }
            Key.ChannelDown -> {
                ctl.zap(-1)
                true
            }
            Key.DirectionLeft -> {
                if (item.live) {
                    if (ctl.items.size > 1) listOpen = true else show()
                } else {
                    ctl.scrubBy(-step(rep))
                    show()
                }
                true
            }
            Key.DirectionRight -> {
                if (item.live) {
                    tracksOpen = true
                } else {
                    ctl.scrubBy(step(rep))
                    show()
                }
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (rep == 0) shortOk()
                true
            }
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.Spacebar -> {
                ctl.togglePlay()
                show()
                true
            }
            Key.MediaFastForward -> {
                ctl.scrubBy(30_000)
                show()
                true
            }
            Key.MediaRewind -> {
                ctl.scrubBy(-30_000)
                show()
                true
            }
            else -> false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent(onKey)
            .focusRequester(rootFr)
            .focusable()
    ) {
        val ratio = when (aspect) {
            "16:9" -> 16f / 9f
            "4:3" -> 4f / 3f
            else -> null
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false
                        player = ctl.player
                        isFocusable = false
                        isFocusableInTouchMode = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        // Círculo de carga en el verde de la app (el del sistema sale azul).
                        findViewById<android.widget.ProgressBar>(androidx.media3.ui.R.id.exo_buffering)?.indeterminateTintList =
                            android.content.res.ColorStateList.valueOf(C.gold.toArgb())
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = true
                        styleSubtitles(subtitleView)
                        // Se escucha después que el propio PlayerView, así que nuestros subtítulos (abajo) son los que quedan.
                        val sv = subtitleView
                        val l = object : androidx.media3.common.Player.Listener {
                            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                                sv?.setCues(bottomCues(cueGroup.cues))
                            }
                        }
                        ctl.player.addListener(l)
                        tag = l
                    }
                },
                onRelease = { v ->
                    (v.tag as? androidx.media3.common.Player.Listener)?.let { ctl.player.removeListener(it) }
                    v.player = null
                },
                update = { v ->
                    v.resizeMode = when (aspect) {
                        "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        "fill", "16:9", "4:3" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize(),
            )
        }
        // Imagen en imagen (ventanita): solo el vídeo, sin controles ni avisos.
        if (ctl.pip) return@Box

        // Capa táctil (móvil y tablet): toque = mostrar/ocultar; doble toque a los lados = ±10 s;
        // arrastrar en horizontal = avanzar/retroceder (o cambiar de canal en directo);
        // arrastrar en vertical = brillo (mitad izquierda) y volumen (mitad derecha).
        TouchLayer(
            ctl = ctl,
            live = item.live,
            onTap = { if (overlay) overlay = false else show() },
            onShow = { show() },
        )

        val err = ctl.error
        if (err != null) {
            Column(
                Modifier.align(Alignment.Center).padding(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(item.title, color = C.gold, fontSize = Sz.sub, fontWeight = FontWeight.Bold)
                Text(err, color = Color.White, fontSize = Sz.emph, textAlign = TextAlign.Center)
                Text(
                    "Pulsa OK para reintentar" + if (item.live) " · ▲▼ para cambiar de canal" else "",
                    color = C.text2, fontSize = Sz.body,
                    modifier = Modifier.focusProperties { canFocus = false }.clickable { ctl.retry() },
                )
            }
        }

        val vp = ctl.videoProblem
        if (vp != null && err == null && !playersOpen && !listOpen && !tracksOpen) {
            Column(
                Modifier.align(Alignment.Center).padding(40.dp).widthIn(max = 560.dp).clip(RoundedCornerShape(12.dp))
                    .background(Color(0xDD000000)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(item.title, color = C.gold, fontSize = Sz.sub, fontWeight = FontWeight.Bold)
                Text(
                    "No se puede mostrar la imagen en la app: $vp. Otros reproductores del aparato sí pueden.",
                    color = Color.White, fontSize = Sz.body, textAlign = TextAlign.Center,
                )
                ctl.videoInfo?.let { Text("Vídeo: $it", color = C.text2, fontSize = Sz.small, textAlign = TextAlign.Center) }
                Text(
                    "Pulsa OK para abrirlo con otro reproductor" + if (item.live) " · ▲▼ para cambiar de canal" else "",
                    color = C.text2, fontSize = Sz.small,
                    modifier = Modifier.focusProperties { canFocus = false }.clickable { playersOpen = true },
                )
            }
        }

        if (overlay && err == null) {
            if (item.live) LiveOverlay(ctl, item, fav, onFav = { toggleFav() })
            else VodOverlay(ctl, item)
        }
        // Botones para el dedo (móvil y tablet).
        if (com.suso.miiptv.data.Device.touch && (overlay || err != null) && !listOpen && !tracksOpen && !playersOpen) {
            TouchTopBar(
                live = item.live,
                fav = fav,
                modifier = Modifier.align(Alignment.TopCenter),
                onBack = onExit,
                onList = { listOpen = true },
                onPrev = { ctl.zap(-1); show() },
                onNext = { ctl.zap(1); show() },
                onFav = { toggleFav() },
                onOptions = { tracksOpen = true },
            )
        }

        // Tocar fuera de un panel lo cierra.
        if (listOpen || tracksOpen || playersOpen) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { listOpen = false; tracksOpen = false; playersOpen = false }
                }
            )
        }

        if (listOpen) {
            ChannelListPanel(ctl, onPick = { i ->
                listOpen = false
                if (i != ctl.index) ctl.play(i, 0)
            }, onClose = { listOpen = false })
        }

        if (showNext) {
            NextEpisodeCard(
                ctl,
                Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = if (overlay) 150.dp else 40.dp),
            )
        }

        // Número que se está tecleando con el mando.
        if (typed.isNotEmpty() && !padOpen) {
            Text(
                typed, color = C.gold, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 50.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(horizontal = 22.dp, vertical = 6.dp),
            )
        }

        // Aviso breve arriba (reconexión automática, VPN...).
        if (notice != null) {
            Text(
                notice, color = Color.White, fontSize = Sz.body, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xE6102414))
                    .border(1.dp, C.gold.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        if (padOpen) {
            NumberPad(
                typed = typed,
                hasPrevious = true,
                onDigit = { d -> if (typed.length < 4) typed += d },
                onDelete = { typed = typed.dropLast(1) },
                onPrevious = {
                    padOpen = false
                    typed = ""
                    if (!ctl.zapBack()) com.suso.miiptv.ui.AppToast.show("Aún no has cambiado de canal")
                },
                onGo = {
                    val n = typed.toIntOrNull()
                    padOpen = false
                    typed = ""
                    if (n != null && !ctl.zapToNumber(n)) com.suso.miiptv.ui.AppToast.show("No hay canal $n")
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Aviso de un programa con recordatorio que empieza ahora.
        ReminderPopup(onVisible = { reminderOpen = it }) { r ->
            val i = ctl.items.indexOfFirst { it.channel?.key == r.channel.key }
            if (i >= 0) ctl.play(i, 0) else ctl.playCatchup(Repo.playItem(r.channel))
        }

        if (playersOpen) {
            PlayersPanel(
                players = remember { PlayQueue.installedPlayers(ctx) },
                modifier = Modifier.align(Alignment.TopEnd),
                onPick = { pkg ->
                    playersOpen = false
                    // En directo se corta nuestra conexión del todo: muchas cuentas solo admiten una a la vez
                    // y, si no, el otro reproductor puede quedarse sin imagen a los pocos segundos.
                    if (PlayQueue.openExternal(ctx, ctl.current, pkg = pkg)) {
                        if (ctl.current.live) ctl.player.stop() else ctl.player.pause()
                    }
                },
            )
        }

        if (tracksOpen) {
            OptionsPanel(
                ctl,
                Modifier.align(Alignment.TopEnd),
                aspect = aspect,
                onAspect = { aspect = nextAspect(aspect) },
                onVpn = { toggleVpn() },
                onStartOver = { ch, e ->
                    tracksOpen = false
                    val url = Repo.catchupUrl(ch, e)
                    if (url == null) {
                        com.suso.miiptv.ui.AppToast.show("Este canal no permite verlo desde el principio")
                    } else {
                        ctl.playCatchup(
                            com.suso.miiptv.data.PlayItem("CU|${ch.key}|${e.start}", e.title, ch.name + " · desde el principio", ch.image, url, false)
                        )
                    }
                },
                onExternal = {
                    tracksOpen = false
                    // Lista propia de reproductores (la ventana de Android quedaba cortada en la tele).
                    playersOpen = true
                },
                fav = fav,
                onFav = if (item.live && item.channel != null) ({ toggleFav() }) else null,
            ) { tracksOpen = false }
        }
        AppToastHost()
    }
}

internal val ASPECTS = listOf("fit", "zoom", "fill", "16:9", "4:3")

fun nextAspect(a: String): String = ASPECTS[(ASPECTS.indexOf(a) + 1) % ASPECTS.size]

fun aspectLabel(a: String): String = when (a) {
    "zoom" -> "Rellenar (zoom)"
    "fill" -> "Estirar"
    "16:9" -> "16:9"
    "4:3" -> "4:3"
    else -> "Ajustar"
}

/** Aplica el tamaño, color y fondo de subtítulos elegidos en Ajustes. */
@OptIn(UnstableApi::class)
fun styleSubtitles(v: SubtitleView?) {
    v ?: return
    val p = Repo.prefs
    val fg = if (p.subColor == "yellow") android.graphics.Color.rgb(255, 230, 80) else android.graphics.Color.WHITE
    // Letra limpia y sombra suave, como en Netflix.
    val font = runCatching { androidx.core.content.res.ResourcesCompat.getFont(v.context, com.suso.miiptv.R.font.manrope_semibold) }.getOrNull()
    val style = if (p.subBg == "band") {
        CaptionStyleCompat(fg, android.graphics.Color.argb(170, 0, 0, 0), android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.BLACK, font)
    } else {
        CaptionStyleCompat(fg, android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, android.graphics.Color.argb(230, 0, 0, 0), font)
    }
    val scale = when (p.subSize) {
        "s" -> 0.85f
        "l" -> 1.25f
        "xl" -> 1.5f
        else -> 1f
    }
    v.setApplyEmbeddedStyles(false)
    v.setApplyEmbeddedFontSizes(false)
    v.setStyle(style)
    v.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
    // Lo más abajo posible (Netflix deja un margen pequeño).
    v.setBottomPaddingFraction(0.02f)
}

/**
 * Quita la posición que traen algunos subtítulos (que los subía a media pantalla) para que salgan siempre abajo.
 * Los subtítulos de imagen (PGS) se dejan como vienen.
 */
fun bottomCues(cues: List<androidx.media3.common.text.Cue>): List<androidx.media3.common.text.Cue> = cues.map { c ->
    if (c.bitmap != null || c.text == null) c
    else c.buildUpon()
        .setLine(androidx.media3.common.text.Cue.DIMEN_UNSET, androidx.media3.common.text.Cue.TYPE_UNSET)
        .setLineAnchor(androidx.media3.common.text.Cue.TYPE_UNSET)
        .setPosition(androidx.media3.common.text.Cue.DIMEN_UNSET)
        .setPositionAnchor(androidx.media3.common.text.Cue.TYPE_UNSET)
        .setSize(androidx.media3.common.text.Cue.DIMEN_UNSET)
        .setTextAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
        .setVerticalType(androidx.media3.common.text.Cue.TYPE_UNSET)
        .build()
}
