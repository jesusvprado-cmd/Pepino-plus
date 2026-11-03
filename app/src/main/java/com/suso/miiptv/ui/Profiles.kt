package com.suso.miiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suso.miiptv.data.Prefs
import com.suso.miiptv.data.Repo
import kotlinx.coroutines.delay

/** Se pregunta quién está viendo solo una vez por cada vez que se abre la app. */
object ProfileSession {
    var chosen = false
}

/** Círculo de color con la inicial del perfil. */
@Composable
fun ProfileAvatar(p: Prefs.Profile, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Color(p.color)), contentAlignment = Alignment.Center) {
        Text(
            p.name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.Black, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** «¿Quién está viendo?» al abrir la app cuando hay varios perfiles. */
@Composable
fun ProfilePicker(onDone: () -> Unit) {
    val list = remember { Repo.prefs.profiles() }
    val current = Repo.prefs.profileId
    val frs = remember { list.associate { it.id to FocusRequester() } }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { (frs[current] ?: frs.values.first()).requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(56.dp)
            Spacer(Modifier.height(22.dp))
            Text("¿Quién está viendo?", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                list.forEach { p ->
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        Modifier
                            .focusRequester(frs.getValue(p.id))
                            .memFocus("profile_${p.id}") { focused = it }
                            .tvClick {
                                Repo.prefs.switchProfile(p.id)
                                ProfileSession.chosen = true
                                onDone()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProfileAvatar(
                            p, 110.dp,
                            Modifier.scale(if (focused) 1.1f else 1f)
                                .then(if (focused) Modifier.border(4.dp, Color.White, CircleShape) else Modifier),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            p.name, fontSize = Sz.emph,
                            color = if (focused) Color.White else C.text2,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            Text("Los perfiles se gestionan en Ajustes → Perfiles", color = Color(0xFF6E6E6E), fontSize = Sz.small)
        }
    }
}
