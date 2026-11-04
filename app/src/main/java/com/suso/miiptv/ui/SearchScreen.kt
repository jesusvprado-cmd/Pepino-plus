package com.suso.miiptv.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.Repo
import com.suso.miiptv.data.SearchResult
import kotlinx.coroutines.delay

@Composable
fun SearchScreen() {
    val kb = LocalSoftwareKeyboardController.current
    val dv = Repo.dataVersion
    var q by remember { mutableStateOf(UiState.searchQuery) }
    var res by remember { mutableStateOf<SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(q, dv) {
        UiState.searchQuery = q
        if (q.trim().length < 2) {
            res = null
            loading = false
            return@LaunchedEffect
        }
        delay(450)
        loading = true
        res = try {
            Repo.search(q)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
        loading = false
    }

    // En el móvil: márgenes estrechos, cuadro en forma de cápsula y texto de ayuda corto (en una línea).
    val phone = isCompactPhone()
    val gap = sideGap()
    val touch = com.suso.miiptv.data.Device.touch
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = if (phone) 14.dp else 22.dp, bottom = 40.dp)) {
        item {
          androidx.compose.foundation.layout.Row(
              Modifier.padding(end = gap),
              verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
          ) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                placeholder = {
                    Text(
                        if (phone) "Buscar…" else "Buscar canales, películas o series",
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                // Borrar lo escrito de un toque.
                trailingIcon = if (touch && q.isNotEmpty()) ({
                    Text(
                        "✕", color = C.text2, fontSize = Sz.emph,
                        modifier = Modifier.clickable { q = "" }.padding(12.dp),
                    )
                }) else null,
                shape = if (touch) androidx.compose.foundation.shape.RoundedCornerShape(28.dp) else OutlinedTextFieldDefaults.shape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { kb?.hide() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.gold,
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = C.gold,
                ),
                modifier = Modifier
                    .padding(start = gap)
                    .weight(1f)
                    .memFocus("search_field"),
            )
          }
            Spacer(Modifier.height(20.dp))
        }
        val r = res
        when {
            loading -> item {
                Text(
                    "Buscando… (la primera búsqueda tarda más porque se carga todo el catálogo)",
                    color = C.text2, fontSize = Sz.body, modifier = Modifier.padding(horizontal = gap),
                )
            }
            r == null -> item {
                Text(
                    "Escribe al menos 2 letras.",
                    color = C.text2, fontSize = Sz.body, modifier = Modifier.padding(horizontal = gap),
                )
            }
            r.live.isEmpty() && r.movies.isEmpty() && r.series.isEmpty() -> item {
                Text("Sin resultados", color = C.text2, fontSize = Sz.body, modifier = Modifier.padding(horizontal = gap))
            }
            else -> {
                if (r.live.isNotEmpty()) item { HomeRowView(HomeRow("search_live", "Canales (${r.live.size})", Kind.LIVE, r.live)) }
                if (r.movies.isNotEmpty()) item { HomeRowView(HomeRow("search_movies", "Películas (${r.movies.size})", Kind.MOVIE, r.movies)) }
                if (r.series.isNotEmpty()) item { HomeRowView(HomeRow("search_series", "Series (${r.series.size})", Kind.SERIES, r.series)) }
            }
        }
    }
}
