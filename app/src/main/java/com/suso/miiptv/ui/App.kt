package com.suso.miiptv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suso.miiptv.data.Repo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRoot(activity: Activity) {
    AppTheme {
      CompositionLocalProvider(LocalBringIntoViewSpec provides TvBringIntoView) {
        var configured by remember { mutableStateOf(Repo.source != null) }
        // Salir con Atrás desde Inicio: hay que pulsarlo dos veces seguidas (un toque de más no cierra la app).
        var lastBack by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
        var exitHint by remember { mutableStateOf(0L) }
        var editing by remember { mutableStateOf(false) }
        // Varios perfiles: se pregunta quién está viendo al abrir la app.
        var picking by remember {
            mutableStateOf(!ProfileSession.chosen && Repo.prefs.askProfile && Repo.prefs.profiles().size > 1)
        }
        // Cada vez que se abre la app mira si hay versión nueva y, si la hay, sale el aviso.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            runCatching { com.suso.miiptv.data.Updater.check(ctx, force = true) }
                .onFailure { com.suso.miiptv.data.ErrorLog.add("Actualizaciones: no se pudo comprobar (${it.message ?: it.javaClass.simpleName})") }
        }
        Box(Modifier.fillMaxSize().background(C.bg).systemBarsPadding()) {
            when {
                !configured -> SetupScreen(canCancel = false, onDone = { configured = true }, onCancel = {})
                editing -> SetupScreen(canCancel = true, onDone = { editing = false }, onCancel = { editing = false })
                picking -> {
                    BackHandler { activity.finish() }
                    ProfilePicker { picking = false }
                }
                else -> {
                    val top = UiState.stack.lastOrNull()
                    BackHandler {
                        when {
                            top != null -> UiState.pop()
                            // Dentro de una sección (Guía, TV...): Atrás lleva primero a su título en el menú de arriba.
                            // También en Inicio: primero vuelve a «Inicio» arriba, y desde ahí ya avisa para salir.
                            !UiState.topBarFocused && !com.suso.miiptv.data.Device.touch &&
                                runCatching { UiState.tabRequester(UiState.tab).requestFocus() }.isSuccess -> {}
                            UiState.tab != Tab.HOME -> {
                                UiState.tab = Tab.HOME
                                runCatching { UiState.tabRequester(Tab.HOME).requestFocus() }
                            }
                            else -> {
                                val now = System.currentTimeMillis()
                                if (now - lastBack < 2500) {
                                    activity.finish()
                                } else {
                                    lastBack = now
                                    exitHint = now
                                }
                            }
                        }
                    }
                    when (top) {
                        null -> MainScreen()
                        is Screen.Movie -> key(top) { MovieDetailScreen(top.item) }
                        is Screen.Series -> key(top) { SeriesDetailScreen(top.item) }
                        Screen.Settings -> SettingsScreen(onChangeList = { editing = true })
                        Screen.HiddenCats -> HiddenCatsScreen()
                        Screen.MissingLogos -> MissingLogosScreen()
                    }
                }
            }
            if (configured && !editing && !picking) ItemMenuHost()
            if (configured && !editing && !picking) UpdateDialog()
            ExitHint(exitHint)
            AppToastHost()
            SplashOverlay()
        }
      }
    }
}


/** Aviso «Pulsa Atrás otra vez para salir» con el aspecto de la app (en lugar del aviso del sistema). */
@Composable
private fun BoxScope.ExitHint(shownAt: Long) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(shownAt) {
        if (shownAt == 0L) return@LaunchedEffect
        visible = true
        kotlinx.coroutines.delay(2300)
        visible = false
    }
    androidx.compose.animation.AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
        exit = androidx.compose.animation.fadeOut(),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(C.card)
                .border(1.dp, C.gold.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("↩", color = C.gold, fontSize = Sz.emph, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text("Pulsa ", color = Color.White, fontSize = Sz.body)
            Text("Atrás", color = C.gold, fontSize = Sz.body, fontWeight = FontWeight.Bold)
            Text(" otra vez para salir", color = Color.White, fontSize = Sz.body)
        }
    }
}
