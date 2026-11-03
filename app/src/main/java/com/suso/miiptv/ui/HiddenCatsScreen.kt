package com.suso.miiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suso.miiptv.data.Category
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.Repo
import kotlinx.coroutines.delay

/** Ajustes → Categorías ocultas (con PIN opcional). */
@Composable
fun HiddenCatsScreen() {
    val ctx = LocalContext.current
    val prefs = Repo.prefs
    var unlocked by remember { mutableStateOf(!prefs.hasPin) }
    var pinMode by remember { mutableStateOf<String?>(null) } // "new" o "confirm:xxxx"

    if (!unlocked) {
        PinEntry("Introduce el PIN para ver las categorías ocultas", onCancel = { UiState.pop() }) { p ->
            if (prefs.pinMatches(p)) unlocked = true else Actions.toast(ctx, "PIN incorrecto")
        }
        return
    }

    val pm = pinMode
    if (pm != null) {
        if (pm == "new") {
            PinEntry("Elige un PIN (de 4 a 8 números)", onCancel = { pinMode = null }) { p ->
                if (p.length < 4) Actions.toast(ctx, "El PIN debe tener al menos 4 números") else pinMode = "confirm:$p"
            }
        } else {
            val first = pm.removePrefix("confirm:")
            PinEntry("Repite el PIN", onCancel = { pinMode = null }) { p ->
                if (p == first) {
                    prefs.setPin(p)
                    Actions.toast(ctx, "PIN guardado")
                    pinMode = null
                } else {
                    Actions.toast(ctx, "Los PIN no coinciden")
                    pinMode = "new"
                }
            }
        }
        return
    }

    var kind by remember { mutableStateOf(Kind.LIVE) }
    var cats by remember(kind) { mutableStateOf<List<Category>?>(null) }
    LaunchedEffect(kind, Repo.dataVersion) {
        cats = try {
            Repo.allCategories(kind)
        } catch (e: Exception) {
            emptyList()
        }
    }
    val hv = prefs.hiddenVersion
    val firstFr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstFr.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(C.bg).padding(horizontal = 48.dp, vertical = 28.dp)) {
        Text("Categorías ocultas", color = Color.White, fontSize = Sz.title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Las categorías ocultas no aparecen en el menú, en Inicio ni en el buscador. " +
                "Las de adultos se ocultan solas al aparecer por primera vez.",
            color = C.text2, fontSize = Sz.body,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TabChip("TV en directo", kind == Kind.LIVE, "hc_live", focusRequester = firstFr) { kind = Kind.LIVE }
            TabChip("Películas", kind == Kind.MOVIE, "hc_mov") { kind = Kind.MOVIE }
            TabChip("Series", kind == Kind.SERIES, "hc_ser") { kind = Kind.SERIES }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Ocultar adultos", "hc_adult") {
                val l = cats ?: emptyList()
                val keys = l.filter { Repo.isAdult(it.name) }.map { prefs.catKey(kind, it.id) }
                prefs.setHidden(keys, true)
                Actions.toast(ctx, if (keys.isEmpty()) "No hay categorías de adultos aquí" else "${keys.size} categorías ocultas")
            }
            TvButton("Mostrar todas", "hc_all") {
                prefs.setHidden((cats ?: emptyList()).map { prefs.catKey(kind, it.id) }, false)
            }
            if (!prefs.hasPin) {
                TvButton("Poner PIN", "hc_pin") { pinMode = "new" }
            } else {
                TvButton("Cambiar PIN", "hc_pin") { pinMode = "new" }
                TvButton("Quitar PIN", "hc_nopin") {
                    prefs.setPin("")
                    Actions.toast(ctx, "PIN quitado")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val l = cats
        when {
            l == null -> Loading()
            l.isEmpty() -> EmptyText("No hay categorías")
            else -> {
                val hiddenCount = l.count { prefs.isHidden(kind, it.id) }
                Text("$hiddenCount de ${l.size} ocultas", color = C.text2, fontSize = Sz.small)
                Spacer(Modifier.height(6.dp))
                LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
                    itemsIndexed(l, key = { i, c -> "$i:${c.id}:$hv" }) { _, c ->
                        val hidden = prefs.isHidden(kind, c.id)
                        CatToggleRow(catLabel(c.name, kind), hidden, "hcr_${kind.name}_${c.id}") {
                            prefs.setHidden(listOf(prefs.catKey(kind, c.id)), !hidden)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatToggleRow(name: String, hidden: Boolean, focusKey: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .memFocus(focusKey) { focused = it }
            .tvClick(onClick = onClick)
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            color = when {
                focused -> Color.Black
                hidden -> C.text2
                else -> Color.White
            },
            fontSize = Sz.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            if (hidden) "Oculta" else "Visible",
            color = if (focused) Color.Black else if (hidden) C.error else C.gold,
            fontSize = Sz.body,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Pantalla para escribir un PIN numérico. */
@Composable
fun PinEntry(title: String, onCancel: () -> Unit, onDone: (String) -> Unit) {
    var value by remember(title) { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(title) {
        delay(120)
        runCatching { fr.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 60.dp, start = 32.dp, end = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, color = Color.White, fontSize = Sz.sub, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = value,
                onValueChange = { v -> value = v.filter { it.isDigit() }.take(8) },
                singleLine = true,
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone(value) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.gold,
                    unfocusedBorderColor = Color(0xFF555555),
                    focusedLabelColor = C.gold,
                    cursorColor = C.gold,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton("Aceptar", "pin_ok", filled = true) { onDone(value) }
                TvButton("Cancelar", "pin_cancel") { onCancel() }
            }
        }
    }
}
