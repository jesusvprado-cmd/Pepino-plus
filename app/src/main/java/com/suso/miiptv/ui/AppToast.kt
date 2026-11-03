package com.suso.miiptv.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Avisos breves con el aspecto de la app (en lugar de los avisos grises de Android). */
object AppToast {
    var message by mutableStateOf<String?>(null)
        private set
    var serial by mutableIntStateOf(0)
        private set
    var long = false
        private set
    private val main = Handler(Looper.getMainLooper())

    fun show(msg: String, longer: Boolean = false) {
        main.post {
            long = longer || msg.length > 60
            message = msg
            serial++
        }
    }
}

/** Dónde se dibujan los avisos: abajo en el centro, sobre todo lo demás. */
@Composable
fun BoxScope.AppToastHost() {
    val s = AppToast.serial
    // Un aviso que ya se mostró antes de abrir esta pantalla (p. ej. en Ajustes) no se vuelve a enseñar aquí.
    val shownBefore = remember { AppToast.serial }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(s) {
        if (s == 0 || s == shownBefore) return@LaunchedEffect
        visible = true
        delay(if (AppToast.long) 3800 else 2400)
        visible = false
    }
    AnimatedVisibility(
        visible && AppToast.message != null,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp, start = 24.dp, end = 24.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
    ) {
        Text(
            AppToast.message ?: "",
            color = Color.White, fontSize = Sz.body, textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(C.card)
                .border(1.dp, C.gold.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}
