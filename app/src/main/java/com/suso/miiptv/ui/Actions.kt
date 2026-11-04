package com.suso.miiptv.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.Prefs
import com.suso.miiptv.data.Repo
import com.suso.miiptv.player.PlayQueue
import kotlinx.coroutines.launch

object Actions {
    @Suppress("UNUSED_PARAMETER")
    fun toast(ctx: Context, msg: String) = AppToast.show(msg)

    fun open(item: Item) {
        when (item.kind) {
            Kind.MOVIE -> UiState.push(Screen.Movie(item))
            Kind.SERIES -> UiState.push(Screen.Series(item))
            Kind.LIVE -> Unit
        }
    }

    fun playLive(ctx: Context, list: List<Item>, index: Int, fromPreview: Boolean = false) {
        try {
            PlayQueue.start(ctx, list.map { Repo.playItem(it) }, index, 0, fromPreview)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            toast(ctx, e.message ?: "No se puede reproducir")
        }
    }

    /** Todos los episodios de la serie en orden (todas las temporadas), para pasar de uno al siguiente. */
    fun episodeQueue(seriesId: String, m: com.suso.miiptv.data.SeriesDetail, fallbackImage: String?): List<com.suso.miiptv.data.PlayItem> =
        m.seasons.flatMap { it.second }.map { ep ->
            com.suso.miiptv.data.PlayItem(
                key = "EP|$seriesId|${ep.id}",
                title = m.title,
                subtitle = "S${ep.season}E${ep.number} - ${sentenceLabel(ep.title)}",
                image = m.poster ?: fallbackImage,
                url = ep.url,
                live = false,
            )
        }

    private val scope = kotlinx.coroutines.MainScope()

    fun resume(ctx: Context, p: Prefs.Progress) {
        val k = p.play.key
        // Episodio a medias («Continuar viendo»): se carga la serie entera para que al terminar siga el siguiente.
        if (k.startsWith("EP|")) {
            // «EP|serie|episodio»: el id de la serie puede llevar «|» (listas M3U).
            val seriesId = k.removePrefix("EP|").substringBeforeLast('|').takeIf { it.isNotEmpty() }
            if (seriesId != null) {
                scope.launch {
                    // Si la serie tarda en cargar, se avisa (antes no se veía nada durante unos segundos).
                    val hint = scope.launch {
                        kotlinx.coroutines.delay(800)
                        toast(ctx, "Cargando la serie…")
                    }
                    val m = kotlinx.coroutines.withTimeoutOrNull(5000) {
                        runCatching { Repo.seriesDetail(Item(Kind.SERIES, seriesId, p.play.title, image = p.play.image)) }.getOrNull()
                    }
                    hint.cancel()
                    val q = m?.let { episodeQueue(seriesId, it, p.play.image) }.orEmpty()
                    val i = q.indexOfFirst { it.key == k }
                    if (i >= 0) PlayQueue.start(ctx, q, i, p.pos) else PlayQueue.start(ctx, listOf(p.play), 0, p.pos)
                }
                return
            }
        }
        PlayQueue.start(ctx, listOf(p.play), 0, p.pos)
    }

    fun toggleFav(ctx: Context, item: Item) {
        val added = Repo.prefs.toggleFavorite(item)
        toast(ctx, if (added) "Añadido a favoritos" else "Quitado de favoritos")
    }

    fun openTrailer(ctx: Context, t: String) {
        val url = if (t.startsWith("http")) t else "https://www.youtube.com/watch?v=$t"
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast(ctx, "No hay ninguna app para ver el tráiler (instala YouTube)")
        }
    }
}
