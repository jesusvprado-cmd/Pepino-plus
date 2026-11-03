package com.suso.miiptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suso.miiptv.debrid.AllDebrid
import com.suso.miiptv.debrid.DebridStore

/** Nube: sección AllDebrid en Ajustes. */
internal val DEBRID_ICON by lazy {
    pathIcon("nube", "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z")
}

internal val AD_AUDIO = listOf("Castellano" to "es", "Latino" to "lat", "Inglés" to "en", "Versión original" to "vo")
internal val AD_RES = listOf("La mejor (hasta 4K)" to 2160, "Hasta 1080p" to 1080, "Hasta 720p" to 720)

/** Ajustes › AllDebrid. [dialog]: "pin", "key" o "tmdb" abre la pantalla correspondiente. */
@Composable
internal fun DebridSection(ask: Ask, dialog: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val av = AllDebrid.version
    @Suppress("UNUSED_VARIABLE") val dv = DebridStore.version
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (AllDebrid.linked) {
            val rows = mutableListOf<Pair<String, String>>()
            rows += "Usuario" to (AllDebrid.userName ?: "—")
            if (AllDebrid.premiumUntil > 0) rows += "Premium hasta" to dateStr(AllDebrid.premiumUntil)
            val st = DebridStore.stats
            val ts = DebridStore.titles
            val series = ts.filter { it.kind == com.suso.miiptv.debrid.DKind.SERIES }
            rows += "Torrents" to "${st.ready} listos de ${st.magnets}" + if (st.noVideo > 0) " (${st.noVideo} sin vídeo)" else ""
            rows += "Enlaces guardados" to "${st.saved}"
            rows += "Vídeos" to "${st.videos}" + if (DebridStore.discarded > 0) " (${DebridStore.discarded} muestras o tráileres no se muestran)" else ""
            if (st.others > 0) rows += "Otros archivos" to "${st.others} (rar, iso, subtítulos, imágenes…)"
            rows += "Biblioteca" to "${ts.size - series.size} películas · ${series.size} series (${series.sumOf { it.episodeCount }} capítulos)"
            InfoCard(rows, "Cuenta vinculada" to S.ok)
            val nr = DebridStore.notReady
            if (nr.isNotEmpty()) {
                val failed = nr.count { AllDebrid.failed(it.code) }
                SettingRow(
                    "Torrents no listos (${nr.size})", "ad_set_notready",
                    subtitle = if (failed > 0) "$failed fallidos (se pueden reintentar) · ${nr.size - failed} en marcha" else "Todos siguen descargándose",
                ) { dialog("notready") }
            }
            SettingRow("Comprobar cuenta", "ad_set_check", subtitle = "Lee de nuevo el usuario y la fecha de fin") {
                scope.launch {
                    val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AllDebrid.checkUser() } }
                    Actions.toast(ctx, r.fold({ "Cuenta correcta: $it" }, { it.message ?: "No se pudo comprobar" }))
                }
            }
            SettingRow("Actualizar biblioteca", "ad_set_sync", subtitle = "Vuelve a leer lo que hay en tu cuenta") {
                DebridStore.open(force = true)
                Actions.toast(ctx, "Actualizando la biblioteca…")
            }
            SettingRow("Desvincular cuenta", "ad_set_unlink", subtitle = "Borra la clave de este aparato") {
                AllDebrid.setKey(null)
                DebridStore.clear()
                Actions.toast(ctx, "Cuenta desvinculada")
            }
        } else {
            InfoCard(listOf("Estado" to "Sin vincular"), "Sin cuenta" to S.warn)
            SettingRow("Vincular con un código", "ad_set_pin", subtitle = "Escribes un código en alldebrid.com/pin desde el móvil") { dialog("pin") }
            SettingRow("Escribir la clave API", "ad_set_key", subtitle = "La sacas en alldebrid.com/apikeys") { dialog("key") }
        }

        GroupLabel("Versión que se elige")
        val p = DebridStore.prefs
        SettingRow("Audio preferido", "ad_set_audio", value = label(AD_AUDIO, p.audio)) {
            ask.of("Audio preferido", AD_AUDIO, p.audio) { DebridStore.prefs = DebridStore.prefs.copy(audio = it) }
        }
        SettingRow("Calidad en este aparato", "ad_set_res", value = label(AD_RES, p.maxRes), subtitle = "Si hay varias copias, se elige la mejor que no pase de aquí") {
            ask.of("Calidad en este aparato", AD_RES, p.maxRes) { DebridStore.prefs = DebridStore.prefs.copy(maxRes = it) }
        }
        ToggleRow(
            "Leer los idiomas de cada archivo", "ad_set_probe", DebridStore.probeOn,
            subtitle = "Mira dentro de los MKV qué audios y subtítulos llevan de verdad (descarga solo el principio)" +
                DebridStore.probePending().let { n -> if (DebridStore.probeOn && n > 0) ". Quedan $n archivos por leer." else "" },
        ) { DebridStore.probeOn = it }

        GroupLabel("Fichas")
        val tm = AllDebrid.tmdbKey
        SettingRow(
            "Clave de TMDB (opcional)", "ad_set_tmdb",
            value = if (tm.isBlank()) "Sin clave" else "Puesta",
            subtitle = "Con ella las sinopsis y títulos salen en español. Es gratis en themoviedb.org › Ajustes › API. Sin clave se usan fichas en inglés.",
        ) { dialog("tmdb") }
        SettingRow("Buscar de nuevo las fichas", "ad_set_meta", subtitle = "Si alguna carátula no corresponde") {
            DebridStore.refreshMeta()
            Actions.toast(ctx, "Buscando las fichas…")
        }
        Text("", fontSize = Sz.small)
    }
}

/** Torrents de la cuenta que no están listos: motivo y reintentar. */
@Composable
internal fun NotReadyPanel(onClose: () -> Unit) {
    androidx.activity.compose.BackHandler { onClose() }
    @Suppress("UNUSED_VARIABLE") val dv = DebridStore.version
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = DebridStore.notReady.sortedWith(compareBy<AllDebrid.Magnet>({ !AllDebrid.failed(it.code) }, { it.name.lowercase() }))
    val failed = list.filter { AllDebrid.failed(it.code) }
    val fr = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        runCatching { fr.requestFocus() }
    }
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().background(C.bg),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter,
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            androidx.compose.ui.Modifier.widthIn(max = 820.dp).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Torrents no listos", color = androidx.compose.ui.graphics.Color.White, fontSize = Sz.title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(
                        "No se pueden ver hasta que AllDebrid los tenga completos. Los fallidos se pueden reintentar: AllDebrid vuelve a buscarlos " +
                            "(si nadie los comparte ya, volverán a fallar). Pulsa uno para reintentarlo.",
                        color = C.text2, fontSize = Sz.body,
                    )
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (failed.isNotEmpty()) TvButton("Reintentar los ${failed.size} fallidos", "nr_all", filled = true, focusRequester = fr) {
                            scope.launch { Actions.toast(ctx, DebridStore.restart(failed.map { it.id })) }
                        }
                        TvButton("Volver", "nr_back", focusRequester = if (failed.isEmpty()) fr else null) { onClose() }
                    }
                }
            }
            items(list.size) { i ->
                val m = list[i]
                val f = AllDebrid.failed(m.code)
                val pct = if (m.code == 1 && m.size > 0) " · ${(m.downloaded * 100 / m.size).coerceIn(0, 100)} %" else ""
                val size = if (m.size >= 1L shl 30) String.format(java.util.Locale.US, "%.1f GB", m.size / 1073741824.0) else if (m.size > 0) "${m.size / 1048576} MB" else ""
                SettingRow(
                    m.name.ifBlank { "Torrent ${m.id}" }, "nr_${m.id}",
                    subtitle = AllDebrid.statusText(m.code) + pct + (if (m.date > 0) " · añadido el ${dateStr(m.date)}" else ""),
                    value = size.ifBlank { null },
                    chevron = f,
                    enabled = true,
                ) {
                    if (f) scope.launch { Actions.toast(ctx, DebridStore.restart(listOf(m.id))) }
                    else Actions.toast(ctx, "Este sigue en marcha en AllDebrid")
                }
            }
        }
    }
}
