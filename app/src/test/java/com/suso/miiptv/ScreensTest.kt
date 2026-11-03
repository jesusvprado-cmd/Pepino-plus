package com.suso.miiptv

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.suso.miiptv.data.AccountInfo
import com.suso.miiptv.data.Category
import com.suso.miiptv.data.Config
import com.suso.miiptv.data.Device
import com.suso.miiptv.data.EpgEntry
import com.suso.miiptv.data.Item
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.Logos
import com.suso.miiptv.data.MovieDetail
import com.suso.miiptv.data.Repo
import com.suso.miiptv.data.SeriesDetail
import com.suso.miiptv.data.Source
import com.suso.miiptv.ui.AppTheme
import com.suso.miiptv.ui.MainScreen
import com.suso.miiptv.ui.MovieDetailScreen
import com.suso.miiptv.ui.SeriesDetailScreen
import com.suso.miiptv.ui.SettingsScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.suso.miiptv.ui.Tab
import com.suso.miiptv.ui.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RConfig
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de las pantallas principales con datos de ejemplo, en tamaño de móvil, tablet y tele.
 * No forman parte de la compilación normal: se lanzan aparte (flujo «Capturas») para revisar el diseño.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setup(touch: Boolean) {
        val ctx = RuntimeEnvironment.getApplication()
        Device.touch = touch
        Repo.init(ctx)
        Logos.loadForTest(ctx)
        Repo.save(Config("xtream", server = "http://demo.tv", user = "demo", pass = "demo"), FakeSource)
        UiState.resetAll()
    }

    private fun shoot(prefix: String, touch: Boolean) {
        setup(touch)
        var screen by mutableStateOf("main")
        rule.setContent {
            AppTheme {
                when (screen) {
                    "movie" -> MovieDetailScreen(FakeSource.sample(Kind.MOVIE))
                    "series" -> SeriesDetailScreen(FakeSource.sample(Kind.SERIES))
                    "settings" -> SettingsScreen(onChangeList = {})
                    else -> MainScreen()
                }
            }
        }
        for (t in listOf(Tab.HOME, Tab.LIVE, Tab.MOVIES, Tab.SERIES, Tab.GUIDE, Tab.SEARCH)) {
            rule.runOnIdle { UiState.tab = t }
            settle()
            save("${prefix}_${t.name.lowercase()}")
        }
        for (sc in listOf("movie", "series", "settings")) {
            rule.runOnIdle { screen = sc }
            settle()
            save("${prefix}_z_$sc")
        }
    }

    private fun settle() {
        repeat(6) {
            rule.waitForIdle()
            Thread.sleep(300)
            rule.mainClock.advanceTimeBy(500)
        }
        rule.waitForIdle()
    }

    private fun save(name: String) {
        // Se dibuja la ventana entera en un mapa de bits (la captura de Compose no funciona en Robolectric).
        var bmp: Bitmap? = null
        rule.runOnUiThread {
            val v = rule.activity.window.decorView
            val b = Bitmap.createBitmap(v.width.coerceAtLeast(1), v.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            bmp = b
        }
        val out = bmp!!
        val f = File("build/screens/$name.png")
        f.parentFile?.mkdirs()
        f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @RConfig(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h860dp-port-xxhdpi")
    fun movilVertical() = shoot("1_movil_vertical", true)

    @Test @RConfig(sdk = [34], application = android.app.Application::class, qualifiers = "w860dp-h411dp-land-xxhdpi")
    fun movilHorizontal() = shoot("2_movil_horizontal", true)

    @Test @RConfig(sdk = [34], application = android.app.Application::class, qualifiers = "w1280dp-h752dp-land-hdpi")
    fun tabletHorizontal() = shoot("3_tablet_horizontal", true)

    @Test @RConfig(sdk = [34], application = android.app.Application::class, qualifiers = "w800dp-h1232dp-port-hdpi")
    fun tabletVertical() = shoot("4_tablet_vertical", true)

    @Test @RConfig(sdk = [34], application = android.app.Application::class, qualifiers = "w960dp-h540dp-land-xhdpi")
    fun tele() = shoot("5_tele", false)
}

private object FakeSource : Source {
    private val channels = listOf(
        "LA 1", "LA 2", "ANTENA 3", "CUATRO", "TELECINCO", "LA SEXTA", "M+ LALIGA", "M+ LIGA DE CAMPEONES",
        "DAZN 1", "DAZN 2", "EUROSPORT 1", "TELEDEPORTE", "TCM", "AXN", "COMEDY CENTRAL", "NATIONAL GEOGRAPHIC",
        "DISNEY CHANNEL", "CLAN", "BOING", "24 HORAS", "CANAL SUR", "TV3", "ETB 1", "TELEMADRID",
    )
    private val movies = listOf(
        "La bola negra (2026)", "Kraken (2026)", "Magallanes (2025)", "La luz (2026)", "Viaje al país de los blancos (2026)",
        "Contra el huracán (2026)", "Todos los colores (2026)", "Kangaroo (2025)", "El último verano (2026)", "Noche cerrada (2025)",
        "Suits: la clave del éxito", "The Americans (2013)", "The Blacklist (2013)", "Anatomía de Grey (2005)",
    )

    override suspend fun check() = AccountInfo("Active", null, "5", "1")
    override suspend fun categories(kind: Kind) = when (kind) {
        Kind.LIVE -> listOf("MOVISTAR ESPAÑA", "AUTONOMICOS", "M. LALIGA", "DAZN", "BAR TV", "LIGA RFEF")
        Kind.MOVIE -> listOf("ESTRENOS 2026", "ESTRENOS 2025", "CINE MUNDO 4K", "DOCUMENTALES", "CINE ACCION")
        Kind.SERIES -> listOf("SERIES TOP", "NETFLIX", "HBO MAX", "DISNEY+", "APPLE TV+")
    }.mapIndexed { i, n -> Category("${kind.name}$i", n) }

    override suspend fun items(kind: Kind, categoryId: String?): List<Item> = when (kind) {
        Kind.LIVE -> channels.mapIndexed { i, n -> Item(Kind.LIVE, "c$i", n, categoryId = categoryId ?: "LIVE0", num = i + 1) }
        else -> (movies + movies).mapIndexed { i, n ->
            Item(kind, "${kind.name}$i", n, categoryId = categoryId ?: "", rating = listOf("8,2", "6,2", "6,3", "8,8")[i % 4], added = System.currentTimeMillis() / 1000)
        }
    }

    fun sample(kind: Kind) = Item(kind, "${kind.name}1", "Kraken (2026)", rating = "6,2", added = System.currentTimeMillis() / 1000)

    private const val PLOT = "Una expedición científica en los fiordos noruegos despierta a una criatura legendaria que lleva siglos " +
        "dormida en lo más profundo del mar. Mientras el pueblo costero se prepara para lo peor, una bióloga marina intenta " +
        "descubrir qué quiere realmente el monstruo antes de que sea demasiado tarde."

    override suspend fun movieDetail(item: Item) = MovieDetail(
        item, item.name, PLOT, "2026", "1h 52min", "Terror, Aventura", null, null, null,
        "Ana Torrent, Luis Tosar, Marta Etura", "Pål Øie", "6.2",
    )

    override suspend fun seriesDetail(item: Item) = SeriesDetail(
        item, "The Americans", PLOT, "2013", "Drama, Suspense", null, null, "Keri Russell, Matthew Rhys", "8.0",
        (1..3).map { s ->
            s to (1..8).map { e ->
                com.suso.miiptv.data.Episode("$s-$e", s, e, "Episodio $e", "Resumen del episodio $e.", null, "45 min", "http://demo.tv/$s$e.mkv")
            }
        },
    )
    override suspend fun epg(item: Item): List<EpgEntry> {
        val now = System.currentTimeMillis()
        val h = 3_600_000L
        return listOf(
            EpgEntry("Informativo", "Noticias del día", now - h / 2, now + h / 2),
            EpgEntry("Película de la tarde", null, now + h / 2, now + 2 * h),
        )
    }
    override fun streamUrl(item: Item) = "http://demo.tv/${item.id}.ts"
}
