package com.suso.miiptv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suso.miiptv.data.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Aviso de versión nueva con botón para actualizar desde la propia app. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UpdateDialog() {
    val rel = Updater.available ?: return
    if (Updater.dismissed) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    val downloading = Updater.progress
    BackHandler { if (downloading == null) Updater.dismissed = true }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { fr.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.8f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141414))
                .border(1.dp, C.gold.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(28.dp)
                // El foco no se escapa a la pantalla de detrás mientras está abierto el aviso.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(44.dp)
                Spacer(Modifier.padding(start = 14.dp))
                Column {
                    Text("Nueva versión disponible", color = Color.White, fontSize = Sz.sub, fontWeight = FontWeight.Bold)
                    Text(
                        "Tienes la ${Updater.currentName(ctx)} · nueva: 1.${rel.build}",
                        color = C.gold, fontSize = Sz.body,
                    )
                }
            }
            if (rel.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("Novedades", color = C.text2, fontSize = Sz.small, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                    Text(rel.notes, color = Color(0xFFDDDDDD), fontSize = Sz.body, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            if (downloading != null) {
                Text("Descargando… ${(downloading * 100).toInt()} %", color = Color.White, fontSize = Sz.body)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(downloading.coerceIn(0.02f, 1f)).fillMaxHeight().background(GoldBrush))
                }
            } else {
                Updater.error?.let {
                    Text(it, color = C.error, fontSize = Sz.body)
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton("Actualizar ahora", "upd_go", filled = true, focusRequester = fr) {
                        scope.launch { Updater.downloadAndInstall(ctx) }
                    }
                    TvButton("Más tarde", "upd_later") { Updater.dismissed = true }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Se instala encima de la actual: no pierdes tu lista, favoritos ni ajustes. " +
                        "Si Android pide permiso para instalar apps de Pepino+, actívalo y vuelve atrás.",
                    color = C.text2, fontSize = Sz.small, lineHeight = 17.sp,
                )
            }
        }
    }
}
