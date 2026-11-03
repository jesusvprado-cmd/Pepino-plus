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

// Controles táctiles del reproductor (móvil y tablet): toques, gestos y barra superior.

/** Gestos con el dedo sobre el vídeo. */
@Composable
internal fun TouchLayer(ctl: PlayerCtl, live: Boolean, onTap: () -> Unit, onShow: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? android.app.Activity
    val audio = remember { ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) {
        if (hint != null) {
            delay(900)
            hint = null
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(live) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { o ->
                        if (!live) {
                            val left = o.x < size.width / 2
                            ctl.skipBy(if (left) -10_000 else 10_000)
                            hint = if (left) "−10 s" else "+10 s"
                            onShow()
                        }
                    },
                )
            }
            .pointerInput(live) {
                var startX = 0f
                var startY = 0f
                var mode = 0 // 0 sin decidir, 1 horizontal, 2 brillo, 3 volumen
                var acc = 0f
                var baseVol = 0
                var baseBright = 0.5f
                detectDragGestures(
                    onDragStart = { o ->
                        startX = o.x; startY = o.y; mode = 0; acc = 0f
                        baseVol = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                        baseBright = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
                    },
                    onDragEnd = {
                        if (mode == 1) {
                            if (live) {
                                // Deslizar en directo: canal anterior / siguiente.
                                if (kotlin.math.abs(acc) > size.width * 0.18f) ctl.zap(if (acc < 0) 1 else -1)
                            } else {
                                ctl.commitScrub()
                            }
                        }
                        mode = 0
                    },
                    onDragCancel = { if (!live) ctl.cancelScrub(); mode = 0 },
                ) { change, drag ->
                    change.consume()
                    if (mode == 0) {
                        val dx = change.position.x - startX
                        val dy = change.position.y - startY
                        if (kotlin.math.abs(dx) > 24f || kotlin.math.abs(dy) > 24f) {
                            mode = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1 else if (startX < size.width / 2) 2 else 3
                        }
                    }
                    when (mode) {
                        1 -> {
                            acc += drag.x
                            if (!live) {
                                // Todo el ancho de la pantalla = 3 minutos.
                                val ms = (drag.x / size.width * 180_000).toLong()
                                ctl.scrubBy(ms)
                                onShow()
                            }
                        }
                        2 -> {
                            acc -= drag.y
                            val b = (baseBright + acc / size.height).coerceIn(0.02f, 1f)
                            activity?.window?.let { w ->
                                val lp = w.attributes
                                lp.screenBrightness = b
                                w.attributes = lp
                            }
                            hint = "Brillo ${(b * 100).toInt()} %"
                        }
                        3 -> {
                            acc -= drag.y
                            val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                            val v = (baseVol + acc / size.height * max * 1.5f).toInt().coerceIn(0, max)
                            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, v, 0)
                            hint = "Volumen ${v * 100 / max.coerceAtLeast(1)} %"
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        hint?.let {
            Text(
                it, color = Color.White, fontSize = Sz.sub, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xAA000000)).padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/** Barra de botones arriba para manejar el reproductor con el dedo. */
@Composable
internal fun TouchTopBar(
    live: Boolean,
    fav: Boolean,
    modifier: Modifier,
    onBack: () -> Unit,
    onList: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onFav: () -> Unit,
    onOptions: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TouchBtn("‹  Atrás", onBack)
        Spacer(Modifier.weight(1f))
        if (live) {
            TouchBtn("▲", onPrev)
            TouchBtn("▼", onNext)
            TouchBtn("Canales", onList)
            TouchBtn(if (fav) "♥" else "♡", onFav)
        }
        TouchBtn("Opciones", onOptions)
    }
}

@Composable
internal fun TouchBtn(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x55000000))
            .focusProperties { canFocus = false }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = Sz.emph, fontWeight = FontWeight.SemiBold)
    }
}
