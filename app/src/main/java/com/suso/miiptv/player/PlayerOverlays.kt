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

// Capas sobre el vídeo: información del directo, barra de tiempo, aviso de siguiente episodio y etiquetas.

/** Aviso de siguiente episodio con la cuenta atrás (lee la posición aquí, no en toda la pantalla). */
@Composable
internal fun NextEpisodeCard(ctl: PlayerCtl, modifier: Modifier) {
    val nextTitle = ctl.items.getOrNull(ctl.index + 1)?.title ?: return
    val remaining = ctl.duration - ctl.position
    Column(
        modifier
            .width(360.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xEE0B140D))
            .border(1.dp, C.gold.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(18.dp),
    ) {
        Text("Siguiente episodio en ${(remaining / 1000).coerceAtLeast(0)} s", color = C.gold, fontSize = Sz.body, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(nextTitle, color = Color.White, fontSize = Sz.emph, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text("OK: verlo ya  ·  Atrás: quedarse", color = C.text2, fontSize = Sz.small)
    }
}

/** Barra inferior del directo: canal, programa actual con su progreso y el siguiente. */
@Composable
internal fun LiveOverlay(ctl: PlayerCtl, item: PlayItem, fav: Boolean, onFav: () -> Unit) {
    val now = System.currentTimeMillis()
    val cur = ctl.epg.firstOrNull { it.start <= now && it.end > now }
    val next = ctl.epg.firstOrNull { it.start >= (cur?.end ?: now) }
    val number = item.channel?.num?.takeIf { it > 0 } ?: (ctl.index + 1)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(280.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0x99000000), 1f to Color(0xF2000000)))
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(120.dp).height(76.dp).clip(RoundedCornerShape(8.dp))
                    .background(
                        Logos.url(item.title, item.image).let { u ->
                            if (u != null) Color.White else Color(0x33FFFFFF)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val logo = remember(item.key, Logos.version) { Logos.url(item.title, item.image) }
                if (logo != null) {
                    ChannelLogoImage(logo, Modifier.fillMaxSize().padding(8.dp), fallback = item.image)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$number", color = C.gold, fontSize = Sz.sub, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text(item.title, color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    StreamBadges(ctl.streamBadges)
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (fav) C.fav else C.text2,
                        modifier = Modifier.size(24.dp).focusProperties { canFocus = false }.clickable { onFav() },
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (cur != null) {
                    Text("${hm(cur.start)} - ${hm(cur.end)}   ${cur.title}", color = Color.White, fontSize = Sz.emph, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    val frac = ((now - cur.start).toFloat() / (cur.end - cur.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    val left = ((cur.end - now) / 60_000).coerceAtLeast(0)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.fillMaxWidth(0.55f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x40FFFFFF))) {
                            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(C.gold))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(if (left > 0) "Quedan $left min" else "Terminando", color = C.text2, fontSize = Sz.small)
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text("Sin guía de programación", color = C.text2, fontSize = Sz.emph)
                }
                if (next != null) {
                    Text("Después  ${hm(next.start)}   ${next.title}", color = C.text2, fontSize = Sz.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(20.dp))
            if (!com.suso.miiptv.data.Device.touch) Column(horizontalAlignment = Alignment.End) {
                Text("▲ ▼  Cambiar canal", color = C.text2, fontSize = Sz.small)
                Text("◀  Lista de canales", color = C.text2, fontSize = Sz.small)
                Text("▶  Opciones y favorito", color = C.text2, fontSize = Sz.small)
                Text("OK mantenido  Número de canal", color = C.text2, fontSize = Sz.small)
            }
        }
    }
}

/** Barra inferior de películas y series, como en la captura: carátula, título dorado y barra dorada. */
@Composable
internal fun VodOverlay(ctl: PlayerCtl, item: PlayItem) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(320.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0x99000000), 1f to Color(0xF2000000)))
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 40.dp, vertical = 30.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (item.image != null) {
                AsyncImage(
                    model = item.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(96.dp).height(140.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, color = C.gold, fontSize = Sz.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    StreamBadges(ctl.streamBadges)
                }
                item.subtitle?.let {
                    Text(it, color = Color(0xFFDDDDDD), fontSize = Sz.emph, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (ctl.duration > 0) {
                    val rest = ((ctl.duration - ctl.position).coerceAtLeast(0) / ctl.speed.coerceAtLeast(0.25f)).toLong()
                    Text("Termina a las ${hm(System.currentTimeMillis() + rest)}", color = C.text2, fontSize = Sz.body)
                }
                if (!com.suso.miiptv.data.Device.touch) {
                    Text("▼  Opciones     ◀ ▶  Retroceder / avanzar     OK  Pausa", color = C.text2, fontSize = Sz.small, maxLines = 1)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(C.gold)
                            .focusProperties { canFocus = false }
                            .clickable { ctl.togglePlay() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ctl.isPlaying) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.width(6.dp).height(20.dp).background(Color.Black))
                                Box(Modifier.width(6.dp).height(20.dp).background(Color.Black))
                            }
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", tint = Color.Black, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    val shownPos = ctl.scrubPos ?: ctl.position
                    Text(fmtTime(shownPos), color = if (ctl.scrubPos != null) C.gold else Color.White, fontSize = Sz.body)
                    Spacer(Modifier.width(14.dp))
                    val frac = if (ctl.duration > 0) (shownPos.toFloat() / ctl.duration).coerceIn(0f, 1f) else 0f
                    SeekBar(
                        frac, Modifier.weight(1f), preview = ctl.scrubPos,
                        onScrub = { f -> ctl.scrubToFraction(f) }, onScrubEnd = { ctl.commitScrub() },
                    ) { f -> ctl.seekToFraction(f) }
                    Spacer(Modifier.width(14.dp))
                    Text(fmtTime(ctl.duration), color = Color.White, fontSize = Sz.body)
                }
            }
        }
    }
}

@Composable
internal fun SeekBar(
    fraction: Float,
    modifier: Modifier,
    preview: Long? = null,
    onScrub: (Float) -> Unit = {},
    onScrubEnd: () -> Unit = {},
    onSeek: (Float) -> Unit,
) {
    BoxWithConstraints(
        modifier
            .height(if (com.suso.miiptv.data.Device.touch) 32.dp else 20.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> if (size.width > 0) onSeek(o.x / size.width) }
            }
            // Arrastrar el dedo por la barra: se ve el minuto al que irá y salta al soltar.
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> if (size.width > 0) onScrub(o.x / size.width) },
                    onDragEnd = { onScrubEnd() },
                    onDragCancel = { onScrubEnd() },
                ) { change, _ ->
                    change.consume()
                    if (size.width > 0) onScrub(change.position.x / size.width)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        // Minuto al que vas, sobre la barra, mientras avanzas o retrocedes.
        if (preview != null) {
            val tw = 76.dp
            val x = ((w * fraction) - tw / 2).coerceIn(0.dp, (w - tw).coerceAtLeast(0.dp))
            Box(
                Modifier
                    .offset(x = x)
                    .layout { measurable, constraints ->
                        val pl = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                        layout(pl.width, 0) { pl.place(0, -pl.height - 10.dp.roundToPx()) }
                    }
                    .width(tw),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    fmtTime(preview), color = Color.White, fontSize = Sz.small, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, softWrap = false,
                    modifier = Modifier.wrapContentWidth(unbounded = true).clip(RoundedCornerShape(5.dp))
                        .background(Color(0xCC111111)).padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x66FFFFFF)))
        Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(C.gold))
        Box(
            Modifier
                .offset(x = (w * fraction) - 7.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(C.gold)
        )
    }
}

/** Etiquetas pequeñas junto al título (FHD, 5.1, DOLBY...): borde fino y texto blanco suave, legibles sobre cualquier imagen. */
@Composable
internal fun StreamBadges(badges: List<String>) {
    if (badges.isEmpty()) return
    Row(Modifier.padding(start = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        badges.forEach { b ->
            val sound = b == "DOLBY" || b == "DD+" || b == "ATMOS" || b == "DTS"
            Box(
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(
                    b, color = Color.White.copy(alpha = 0.85f), fontSize = Sz.label, lineHeight = 11.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                    ),
                    fontWeight = if (sound) FontWeight.ExtraBold else FontWeight.Bold,
                    letterSpacing = if (sound) 1.sp else 0.5.sp,
                )
            }
        }
    }
}
