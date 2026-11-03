package com.suso.miiptv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.suso.miiptv.data.Http
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Repo

/**
 * Un único reproductor para toda la app. Lo comparten la previsualización de TV en directo
 * y la pantalla completa, así al pulsar OK el canal sigue sin cortarse ni volver a conectar
 * (y nunca se abren dos conexiones a la vez con la cuenta IPTV).
 */
@OptIn(UnstableApi::class)
object PlayerHolder {
    /**
     * Vídeo con el decodificador por software de Android en vez del del aparato: se activa solo cuando el del
     * aparato no puede con un vídeo (p. ej. H.265 marcado como 4K en una tablet que solo admite 1080p).
     */
    @Volatile
    var softwareVideo = false

    private val codecSelector = androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mime, secure, tunneling ->
        val l = androidx.media3.exoplayer.mediacodec.MediaCodecUtil.getDecoderInfos(mime, secure, tunneling)
        if (softwareVideo && mime.startsWith("video/")) l.sortedBy { if (it.softwareOnly) 0 else 1 } else l
    }

    private var player: ExoPlayer? = null
    private var signature = ""

    /** true mientras la pantalla completa está abierta. */
    @Volatile
    var fullscreen = false

    /** La app se está cerrando: ya no se arranca ninguna previsualización. */
    @Volatile
    var closing = false

    /** Cambia cada vez que se vuelve a la pantalla principal (la previsualización se vuelve a enganchar). */
    var epoch by mutableIntStateOf(0)
        private set

    var previewError by mutableStateOf<String?>(null)
        private set

    private var triedHls = false

    private val previewListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (fullscreen) return
            val p = player ?: return
            val url = currentUrl() ?: return
            if (error.errorCode in 3000..3999 && !triedHls && !url.contains(".m3u8", true)) {
                triedHls = true
                load(p, url, true)
                return
            }
            previewError = "Sin señal"
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) previewError = null
        }
    }

    fun get(ctx: Context): ExoPlayer {
        val prefs = Repo.prefs
        val sig = "${prefs.bufferMode}|${prefs.audioDecode}|${Http.userAgent}"
        player?.let { existing ->
            if (sig == signature || fullscreen) return existing
            // Han cambiado los ajustes de reproducción: se crea uno nuevo.
            existing.release()
            player = null
        }
        val http = OkHttpDataSource.Factory(Http.client).setUserAgent(Http.userAgent)
        // El decodificador de audio de la app (FFmpeg) se usa como respaldo, o siempre si así se ha elegido.
        val renderers = NoVsyncRenderersFactory(ctx.applicationContext, noVsync = !com.suso.miiptv.data.Device.touch)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(codecSelector)
            .setExtensionRendererMode(
                if (prefs.audioDecode) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
        val load = when (prefs.bufferMode) {
            "low" -> DefaultLoadControl.Builder().setBufferDurationsMs(6_000, 20_000, 1_000, 2_000)
            "high" -> DefaultLoadControl.Builder().setBufferDurationsMs(30_000, 120_000, 3_000, 6_000)
            else -> DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 2_000, 4_000)
        }.build()
        val exo = ExoPlayer.Builder(ctx.applicationContext, renderers)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    http,
                    // Emisiones de satélite («SAT»): muchas no marcan bien dónde empieza cada imagen ni traen imágenes
                    // clave completas; sin estas opciones el reproductor descarta imágenes y el vídeo va a saltos.
                    androidx.media3.extractor.DefaultExtractorsFactory().setTsExtractorFlags(
                        androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                            androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                    ),
                )
            )
            .setLoadControl(load)
            .build()
        exo.playWhenReady = true
        exo.addListener(previewListener)
        applyTrackPrefs(exo)
        player = exo
        signature = sig
        return exo
    }

    /** Idiomas de audio y subtítulos de Ajustes (los mismos en la previsualización y en pantalla completa). */
    fun applyTrackPrefs(exo: ExoPlayer) {
        val prefs = Repo.prefs
        val tb = exo.trackSelectionParameters.buildUpon().clearOverrides()
        val al = prefs.audioLang ?: "es"
        if (al != "orig") tb.setPreferredAudioLanguage(al) else tb.setPreferredAudioLanguage(null)
        val sub = prefs.subLang
        tb.setPreferredTextLanguage(if (!sub.isNullOrEmpty()) sub else null)
        tb.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, sub == "")
        val built = tb.build()
        if (built != exo.trackSelectionParameters) exo.trackSelectionParameters = built
    }

    fun currentUrl(): String? = player?.currentMediaItem?.localConfiguration?.uri?.toString()

    fun load(exo: ExoPlayer, url: String, forceHls: Boolean) {
        val b = MediaItem.Builder().setUri(url)
        if (forceHls || url.contains(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        exo.setMediaItem(b.build())
        exo.prepare()
        exo.play()
    }

    /** Reproduce un canal en el recuadro de previsualización (con el volumen más bajo). */
    fun playPreview(ctx: Context, item: Item, force: Boolean = false) {
        if (fullscreen || closing) return
        val url = try {
            Repo.streamUrl(item).let { u -> if (Repo.prefs.usesAltFormat(item.key)) swapLiveFormat(u) else u }
        } catch (e: Exception) {
            return
        }
        val exo = get(ctx)
        exo.volume = 0.5f
        if (!force && currentUrl() == url && exo.playbackState != Player.STATE_IDLE) {
            if (!exo.isPlaying) exo.play()
            return
        }
        previewError = null
        triedHls = false
        applyTrackPrefs(exo)
        load(exo, url, false)
    }

    fun stopPreview() {
        if (fullscreen) return
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        previewError = null
    }

    /** Al salir de la app: se libera el reproductor del todo (nada sigue sonando). */
    fun releaseAll() {
        fullscreen = false
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        previewError = null
    }

    /** La pantalla principal vuelve a estar visible. */
    fun onMainVisible() {
        epoch++
    }
}


/**
 * El reproductor coloca cada imagen en el «golpe» de pantalla más cercano usando la frecuencia que dice Android.
 * Algunos aparatos (la Mi Box, con chip Amlogic) dicen una frecuencia equivocada (24 Hz cuando la tele va a 50 Hz)
 * hasta que la frecuencia cambia con el vídeo ya en marcha: las imágenes caen en golpes erróneos y el vídeo va a
 * microsaltos. En los aparatos de tele se le oculta la pantalla al decodificador de vídeo para que no haga ese ajuste
 * y entregue cada imagen a su hora (el aparato ya la sincroniza con la pantalla).
 */
private class NoVsyncRenderersFactory(ctx: Context, private val noVsync: Boolean) : DefaultRenderersFactory(ctx) {
    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: android.os.Handler,
        eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>,
    ) {
        val c = if (noVsync) NoDisplayContext(context.applicationContext) else context
        super.buildVideoRenderers(c, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out)
    }
}

/** Contexto que no da acceso a la lista de pantallas (el tamaño de la pantalla se sigue obteniendo por otra vía). */
private class NoDisplayContext(base: Context) : android.content.ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getSystemService(name: String): Any? = if (name == Context.DISPLAY_SERVICE) null else super.getSystemService(name)
}
