package com.suso.miiptv.player

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.suso.miiptv.data.Repo
import com.suso.miiptv.ui.AppTheme

class PlayerActivity : ComponentActivity() {
    private var ctl: PlayerCtl? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        if (PlayQueue.items.isEmpty()) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        PlayerHolder.fullscreen = true
        val c = PlayerCtl(this)
        c.onFinished = { finish() }
        // La frecuencia de la pantalla se ajusta solo con el ajuste activado (también desde las opciones del reproductor).
        c.onVideoFormat = { f -> if (Repo.prefs.afr) matchFrameRate(f.frameRate) else resetFrameRate() }
        ctl = c
        c.start()
        setContent { AppTheme { PlayerScreen(c) { finish() } } }
    }

    private var afrApplied = -1f

    /** Pide a la pantalla el modo [modeId] (0 = el normal del aparato). */
    private fun applyMode(modeId: Int) {
        val lp = window.attributes
        if (lp.preferredDisplayModeId == modeId) return
        lp.preferredDisplayModeId = modeId
        window.attributes = lp
    }

    /** Vuelve a la frecuencia normal de la pantalla (al desactivar el ajuste). */
    private fun resetFrameRate() {
        if (afrApplied <= 0f) return
        afrApplied = -1f
        try {
            applyMode(0)
        } catch (e: Exception) {
            // Sin importancia.
        }
    }

    /**
     * Cambia la frecuencia de la pantalla para que coincida con la del vídeo (24, 25, 50, 60 Hz...),
     * en las teles y reproductores que lo permiten. Se deshace sola al salir del reproductor.
     */
    private fun matchFrameRate(fps: Float) {
        if (Build.VERSION.SDK_INT < 23 || fps <= 0f) return
        try {
            val display = window.decorView.display ?: return
            val cur = display.mode
            val same = display.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            fun fits(m: android.view.Display.Mode): Boolean {
                val ratio = m.refreshRate / fps
                val n = Math.round(ratio)
                return n >= 1 && kotlin.math.abs(ratio - n) <= 0.01f * n
            }
            // La frecuencia más baja que sea múltiplo de los fps del vídeo.
            val b = same.filter { fits(it) }.minByOrNull { it.refreshRate }
                ?: return
            afrApplied = fps
            applyMode(b.modeId)
        } catch (e: Exception) {
            // Este dispositivo no permite cambiar la frecuencia.
        }
    }

    /**
     * Imagen en imagen (móvil y tablet): al salir con el botón de inicio mientras se ve algo, sigue en una ventanita.
     */
    private fun pipParams(): android.app.PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < 26) return null
        val f = ctl?.player?.videoFormat
        val r = if (f != null && f.width > 0 && f.height > 0) {
            val w = f.width.toFloat() * f.pixelWidthHeightRatio
            // Android solo admite proporciones entre 1:2,39 y 2,39:1.
            android.util.Rational((w * 100).toInt(), f.height * 100).let { x ->
                if (x.toFloat() > 2.39f) android.util.Rational(239, 100) else if (x.toFloat() < 0.42f) android.util.Rational(100, 239) else x
            }
        } else android.util.Rational(16, 9)
        return android.app.PictureInPictureParams.Builder().setAspectRatio(r).build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val c = ctl ?: return
        if (!com.suso.miiptv.data.Device.touch || Build.VERSION.SDK_INT < 26) return
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (!c.player.isPlaying) return
        runCatching { pipParams()?.let { enterPictureInPictureMode(it) } }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ctl?.pip = isInPictureInPictureMode
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onStart() {
        super.onStart()
        ctl?.onStart()
    }

    override fun onStop() {
        // Se ha cerrado la ventanita de imagen en imagen: se sale del reproductor.
        val closingPip = ctl?.pip == true
        ctl?.onStop(isFinishing || closingPip)
        super.onStop()
        if (closingPip && !isFinishing) finish()
    }

    override fun onDestroy() {
        ctl?.release()
        ctl = null
        PlayerHolder.fullscreen = false
        super.onDestroy()
    }
}
