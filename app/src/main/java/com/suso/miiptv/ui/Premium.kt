package com.suso.miiptv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Kind
import kotlinx.coroutines.delay
import java.util.Locale

// ---------------- Carga elegante (brillo que pasa por encima) ----------------

/** Recuadro gris con un brillo que lo recorre, mientras se carga algo. */
fun Modifier.shimmer(shape: RoundedCornerShape = RoundedCornerShape(8.dp)): Modifier = composed {
    // Modo ligero: recuadro quieto, sin animación.
    if (com.suso.miiptv.data.Perf.light) return@composed this.clip(shape).background(Color(0xFF0F2213))
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "sx",
    )
    this
        .clip(shape)
        // Verde oscuro de fondo con un halo verde más claro que lo recorre.
        .background(Color(0xFF0F2213))
        .drawWithContent {
            drawContent()
            val w = size.width
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color(0x3398E486), Color.Transparent),
                    start = Offset(w * x - w * 0.5f, 0f),
                    end = Offset(w * x + w * 0.5f, size.height),
                )
            )
        }
}

/** Esqueleto de la pantalla de Inicio mientras se carga la lista. */
@Composable
fun SkeletonHome() {
    Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
        Box(
            Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(250.dp)
                .shimmer(RoundedCornerShape(14.dp))
        )
        Spacer(Modifier.height(26.dp))
        repeat(2) { r ->
            Box(Modifier.padding(start = 40.dp).width(200.dp).height(18.dp).shimmer(RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(14.dp))
            Row(Modifier.padding(start = 40.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(8) {
                    Box(
                        Modifier.width(if (r == 0) 170.dp else 130.dp)
                            .aspectRatio(if (r == 0) 16f / 10.5f else 2f / 3f)
                            .shimmer()
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

/** Esqueleto de la cuadrícula de canales o carátulas. */
@Composable
fun SkeletonGrid(kind: Kind) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (kind == Kind.LIVE) 112.dp else 128.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 30.dp, top = 14.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        userScrollEnabled = false,
    ) {
        items(24) {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(if (kind == Kind.LIVE) 16f / 10.5f else 2f / 3f).shimmer())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(12.dp).shimmer(RoundedCornerShape(3.dp)))
            }
        }
    }
}

// ---------------- Fondo ambiental ----------------

/** Imagen de la carátula con el foco: tiñe el fondo de la pantalla. */
object Ambient {
    var image by mutableStateOf<String?>(null)
}

/**
 * Fondo difuminado con la carátula elegida. Para que vaya fluido también en la Mi Box,
 * el difuminado se consigue cargando la imagen diminuta y ampliándola (sin efectos caros).
 */
@Composable
fun AmbientBackground(enabled: Boolean) {
    val target = if (enabled) Ambient.image else null
    var shown by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        // Espera a que dejes de moverte para cambiar el fondo (así el desplazamiento va fluido).
        delay(650)
        shown = target
    }
    val ctx = LocalContext.current
    Crossfade(targetState = shown, animationSpec = tween(700), label = "ambient", modifier = Modifier.fillMaxSize()) { url ->
        if (url != null) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = remember(url) { ImageRequest.Builder(ctx).data(url).size(18, 27).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    alpha = 0.42f,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x66000000), Color(0xCC000000), Color.Black))
                    )
                )
            }
        }
    }
}

// ---------------- Insignias de las carátulas ----------------

private val Q4K = Regex("(?i)\\b(4k|uhd|2160p?)\\b")
private val QHD = Regex("(?i)\\b(fhd|1080p?|hd)\\b")

/** Nota de la carátula («7,8»), o null si no tiene. */
fun ratingOf(item: Item?): String? {
    val r = item?.rating?.replace(',', '.')?.toFloatOrNull() ?: return null
    if (r <= 0f) return null
    val v = if (r > 10f) r / 10f else r
    return String.format(Locale("es", "ES"), "%.1f", v)
}

fun qualityOf(item: Item?): String? {
    val n = item?.name ?: return null
    return when {
        Q4K.containsMatchIn(n) -> "4K"
        QHD.containsMatchIn(n) -> "HD"
        else -> null
    }
}

/** Añadido en los últimos 10 días. */
fun isNew(item: Item?): Boolean {
    val a = item?.added ?: return false
    if (a <= 0) return false
    val secs = if (a > 100_000_000_000L) a / 1000 else a
    return System.currentTimeMillis() / 1000 - secs in 0..(10L * 24 * 3600)
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(if (compact) 4.dp else 5.dp)).background(bg)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = if (compact) 1.dp else 2.dp),
    ) {
        Text(
            text, color = fg, fontSize = Sz.label, fontWeight = FontWeight.ExtraBold,
            letterSpacing = if (compact) 0.sp else 0.5.sp,
            // Compacta: sin el margen interior de la fuente, para que el recuadro se ajuste al texto.
            style = if (compact) androidx.compose.ui.text.TextStyle(
                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                lineHeight = Sz.label,
            ) else androidx.compose.material3.LocalTextStyle.current,
        )
    }
}

/** Insignias sobre una carátula: NUEVO, calidad y nota. */
@Composable
fun BoxScope.PosterBadges(item: Item?, favBadge: Boolean = true) {
    if (item == null) return
    val q = remember(item.key) { qualityOf(item) }
    val r = remember(item.key, item.rating) { ratingOf(item) }
    val n = remember(item.key, item.added) { isNew(item) }
    // Ya vista: un ✓ verde discreto arriba a la izquierda.
    val pv = com.suso.miiptv.data.Repo.prefs.version
    val seen = remember(item.key, pv) { com.suso.miiptv.data.Repo.prefs.isWatched(item.key) }
    if (seen) {
        Box(
            Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp)
                .clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xCC000000)),
            contentAlignment = Alignment.Center,
        ) { Text("✓", color = C.gold, fontSize = Sz.small, fontWeight = FontWeight.ExtraBold) }
    }
    // Novedad: circulito amarillo con una «N», discreto, abajo a la derecha.
    if (n) {
        Box(
            Modifier.align(Alignment.BottomEnd).padding(6.dp).size(18.dp)
                .clip(androidx.compose.foundation.shape.CircleShape).background(C.gold),
            contentAlignment = Alignment.Center,
        ) {
            Text("N", color = Color.Black, fontSize = Sz.label, fontWeight = FontWeight.ExtraBold, lineHeight = 11.sp)
        }
    }
    // En favoritos: corazón verde arriba a la derecha (no en la propia pestaña de Favoritos).
    val fav = favBadge && remember(item.key, pv) { com.suso.miiptv.data.Repo.prefs.isFavorite(item.key) }
    if (fav) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                .clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xCC000000)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.Favorite, contentDescription = null,
                tint = C.fav, modifier = Modifier.size(13.dp),
            )
        }
    }
    // Calidad arriba a la derecha (debajo del corazón si lo hay).
    if (q != null) Pill(q, Color(0xCC000000), Color.White, Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = if (fav) 32.dp else 6.dp))
    if (r != null) Pill("★ $r", Color(0xCC000000), C.gold, Modifier.align(Alignment.BottomStart).padding(6.dp), compact = true)
}
