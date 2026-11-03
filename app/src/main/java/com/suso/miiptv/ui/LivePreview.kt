package com.suso.miiptv.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.suso.miiptv.data.EpgEntry
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Repo
import com.suso.miiptv.player.PlayerHolder
import com.suso.miiptv.vpn.VpnManager
import kotlinx.coroutines.delay

/** Cabecera de TV en directo: vídeo del canal seleccionado y su guía. */
@Composable
fun LiveHeader(item: Item?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 14.dp, end = 30.dp, top = 12.dp, bottom = 4.dp)) {
        LivePreviewBox(item, Modifier.width(288.dp).aspectRatio(16f / 9f))
        Spacer(Modifier.width(20.dp))
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(Motion.FADE_MS)) togetherWith fadeOut(tween(Motion.FADE_MS / 2)) },
            contentKey = { it?.key },
            label = "liveinfo",
            modifier = Modifier.weight(1f).height(162.dp),
        ) { target ->
            LiveInfo(target)
        }
    }
}

@Composable
private fun LiveInfo(item: Item?) {
    var epg by remember(item?.key) { mutableStateOf<List<EpgEntry>>(emptyList()) }
    LaunchedEffect(item?.key) {
        if (item == null) return@LaunchedEffect
        delay(300)
        epg = Repo.epg(item)
    }
    Column(Modifier.fillMaxSize()) {
        if (item == null) {
            Text("Elige un canal", color = C.text2, fontSize = Sz.emph)
            return@Column
        }
        Text(item.name, color = Color.White, fontSize = Sz.sub, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        val now = System.currentTimeMillis()
        val cur = epg.firstOrNull { it.start <= now && it.end > now }
        val next = epg.firstOrNull { it.start >= (cur?.end ?: now) }
        if (cur == null) {
            Text("Sin guía de programación", color = C.text2, fontSize = Sz.body, maxLines = 1)
        } else {
            Text("${hm(cur.start)} - ${hm(cur.end)}   ${cur.title}", color = Color.White, fontSize = Sz.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            val frac = ((now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(0.7f).height(3.dp).background(Color(0x44FFFFFF))) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(C.gold))
            }
        }
        if (next != null) {
            Spacer(Modifier.height(5.dp))
            Text("Después  ${hm(next.start)}   ${next.title}", color = C.text2, fontSize = Sz.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // La descripción usa solo el hueco que sobra; «OK pantalla completa» siempre cabe abajo.
        val desc = cur?.desc?.takeIf { it.isNotBlank() }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val lh = with(LocalDensity.current) { 17.sp.toDp() }
            val lines = ((maxHeight - 5.dp) / lh).toInt().coerceIn(0, 2)
            if (desc != null && lines > 0) {
                Text(
                    desc, color = C.text2, fontSize = Sz.small, lineHeight = 17.sp, maxLines = lines,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
        Text("OK  pantalla completa", color = C.gold, fontSize = Sz.small, maxLines = 1)
    }
}

/** Recuadro de vídeo que reproduce el canal enfocado (tras una breve pausa al moverse). */
@OptIn(UnstableApi::class)
@Composable
fun LivePreviewBox(item: Item?, modifier: Modifier) {
    val ctx = LocalContext.current
    val epoch = PlayerHolder.epoch
    val player = remember(epoch) { PlayerHolder.get(ctx) }
    LaunchedEffect(item?.key, epoch) {
        if (item == null) return@LaunchedEffect
        delay(600)
        PlayerHolder.playPreview(ctx, item)
    }
    // Al conectar o desconectar la VPN, el canal se vuelve a abrir por la nueva ruta.
    val vpn = VpnManager.changes
    var lastVpn by remember { mutableIntStateOf(vpn) }
    LaunchedEffect(vpn) {
        if (vpn != lastVpn) {
            lastVpn = vpn
            if (item != null) PlayerHolder.playPreview(ctx, item, force = true)
        }
    }
    DisposableEffect(Unit) {
        onDispose { PlayerHolder.stopPreview() }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            onRelease = { v -> v.player = null },
            update = { v ->
                if (v.tag != epoch) {
                    v.player = null
                    v.player = player
                    v.tag = epoch
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        val err = PlayerHolder.previewError
        when {
            item == null -> Text("Elige un canal", color = C.text2, fontSize = Sz.body)
            err != null -> Text(err, color = C.text2, fontSize = Sz.body)
        }
    }
}
