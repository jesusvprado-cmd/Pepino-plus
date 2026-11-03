package com.suso.miiptv.ui

import androidx.compose.foundation.layout.Arrangement
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
            rows += "Biblioteca" to "${DebridStore.titles.size} títulos"
            InfoCard(rows, "Cuenta vinculada" to S.ok)
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
            subtitle = "Mira dentro de los MKV qué audios y subtítulos llevan de verdad (descarga solo el principio)",
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
