package com.shieldstream.tv.crash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shieldstream.tv.model.Lang
import com.shieldstream.tv.ui.theme.AppColors

/** Aviso al abrir la app si la vez anterior se cerró por un error, con el detalle para hacerle una foto. */
@Composable
fun CrashDialog(report: CrashLog.Report, onDismiss: () -> Unit) {
    val en = Lang.isEnglish
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .widthIn(max = 1100.dp)
                .padding(32.dp)
                .background(AppColors.Surface, RoundedCornerShape(20.dp))
                .padding(28.dp),
        ) {
            Text(
                if (en) "Pepino+ closed because of an error" else "Pepino+ se cerró por un error",
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (en) "Take a photo of this screen to find out what happened."
                else "Haz una foto de esta pantalla para poder averiguar qué pasó.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 17.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                report.summary,
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Field, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            )
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .align(Alignment.End)
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onDismiss)
                    .background(if (focused) Color.White else AppColors.SurfaceFocused, RoundedCornerShape(14.dp))
                    .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(
                    if (en) "Got it" else "Entendido",
                    color = if (focused) Color.Black else Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
