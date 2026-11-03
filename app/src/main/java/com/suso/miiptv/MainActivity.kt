package com.suso.miiptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.suso.miiptv.data.Repo
import com.suso.miiptv.player.PlayerHolder
import com.suso.miiptv.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        // La app gestiona ella misma el hueco del teclado (imePadding) para que los campos no queden tapados.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Móvil en horizontal: la app llega hasta el borde también por el lado de la cámara (sin franja negra).
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        setContent { AppRoot(this) }
        handleResume(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleResume(intent)
    }

    /** Abierto desde «Continuar viendo» de Android TV: reanuda esa película o episodio. */
    private fun handleResume(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "pepinoplus" || data.host != "resume") return
        val key = data.getQueryParameter("key") ?: return
        if (!Repo.isReady()) return
        val p = Repo.prefs.progress(key) ?: return
        intent.data = null
        com.suso.miiptv.ui.Actions.resume(this, p)
    }

    private var startedOnce = false

    override fun onStart() {
        super.onStart()
        // Al volver de la pantalla completa, la previsualización se vuelve a enganchar.
        if (startedOnce) PlayerHolder.onMainVisible()
        startedOnce = true
    }

    override fun onStop() {
        // Si la app pasa a segundo plano, se corta la previsualización (libera la conexión).
        PlayerHolder.stopPreview()
        if (isFinishing) closeApp()
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        // Saliendo con Atrás: el sonido se corta en el acto, sin esperar a nada.
        if (isFinishing) closeApp()
    }

    override fun onDestroy() {
        val exiting = isFinishing
        super.onDestroy()
        // Al salir de la app se cierra del todo: no queda nada sonando ni funcionando en segundo plano.
        if (exiting) android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun closeApp() {
        PlayerHolder.closing = true
        PlayerHolder.releaseAll()
    }

    override fun onResume() {
        super.onResume()
        // Al volver del reproductor, refresca "Continuar viendo".
        if (Repo.isReady()) {
            Repo.prefs.bump()
            com.suso.miiptv.data.WatchNext.sync(this)
        }
    }
}
