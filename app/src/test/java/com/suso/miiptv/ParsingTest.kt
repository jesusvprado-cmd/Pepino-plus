package com.suso.miiptv

import com.suso.miiptv.data.Config
import com.suso.miiptv.data.Kind
import com.suso.miiptv.data.M3uSource
import com.suso.miiptv.data.Repo
import com.suso.miiptv.data.XtreamSource
import com.suso.miiptv.data.normalizeServer
import com.suso.miiptv.player.maskUrl
import com.suso.miiptv.ui.catLabel
import com.suso.miiptv.ui.sentenceLabel
import com.suso.miiptv.player.swapLiveFormat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config as RConfig

/** Lectura de listas M3U y Xtream con un servidor de prueba, y utilidades de direcciones. */
@RunWith(RobolectricTestRunner::class)
@RConfig(sdk = [34], application = android.app.Application::class)
class ParsingTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        Repo.init(RuntimeEnvironment.getApplication())
        com.suso.miiptv.data.DiskCache.clear()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                val body = when {
                    path.startsWith("/lista.m3u") -> M3U
                    "action=get_live_categories" in path -> """[{"category_id":"1","category_name":"España"},{"category_id":"2","category_name":"Deportes"}]"""
                    "action=get_live_streams" in path -> """[{"num":1,"name":"La 1 HD","stream_id":101,"stream_icon":"http://logo/la1.png","epg_channel_id":"La1.es","category_id":"1","tv_archive":1,"tv_archive_duration":3},{"num":2,"name":"DAZN 1","stream_id":102,"stream_icon":"","epg_channel_id":null,"category_id":"2","tv_archive":0}]"""
                    "action=get_vod_streams" in path -> """[{"name":"Kraken (2026)","stream_id":201,"stream_icon":"http://p/k.jpg","category_id":"9","container_extension":"mkv","rating":"6.2"}]"""
                    path.startsWith("/player_api.php") -> """{"user_info":{"auth":1,"status":"Active"},"server_info":{"timestamp_now":0,"time_now":"2026-01-01 00:00:00"}}"""
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setBody(body)
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun listaM3u() = runBlocking {
        val src = M3uSource(Config("m3u", m3uUrl = server.url("/lista.m3u").toString()))
        val liveCats = src.categories(Kind.LIVE).map { it.name }
        assertEquals(listOf("España", "Deportes"), liveCats)
        val live = src.items(Kind.LIVE, null)
        assertEquals(2, live.size)
        assertEquals("La 1", live[0].name)
        assertEquals("La1.es", live[0].epgId)
        assertEquals("http://logo/la1.png", live[0].image)
        val movies = src.items(Kind.MOVIE, null)
        assertEquals(listOf("Kraken (2026)"), movies.map { it.name })
        val series = src.items(Kind.SERIES, null)
        assertEquals(1, series.size)
        val detail = src.seriesDetail(series[0])
        assertEquals(2, detail.seasons.sumOf { it.second.size })
    }

    @Test
    fun listaXtream() = runBlocking {
        val src = XtreamSource(Config("xtream", server = server.url("/").toString(), user = "yo", pass = "clave"))
        assertEquals(listOf("España", "Deportes"), src.categories(Kind.LIVE).map { it.name })
        val live = src.items(Kind.LIVE, null)
        assertEquals(2, live.size)
        assertEquals("101", live[0].id)
        assertEquals("La1.es", live[0].epgId)
        assertEquals(3, live[0].archive)
        assertEquals(0, live[1].archive)
        assertEquals(null, live[1].image)
        val vod = src.items(Kind.MOVIE, null)
        assertTrue(src.streamUrl(vod[0]).endsWith("/movie/yo/clave/201.mkv"))
        assertTrue(src.streamUrl(live[0]).endsWith("/live/yo/clave/101.ts"))
    }

    @Test
    fun direcciones() {
        assertEquals("http://servidor.tv:8080", normalizeServer("servidor.tv:8080/"))
        assertEquals("https://s.tv", normalizeServer("https://s.tv/player_api.php?username=a&password=b"))
        assertEquals("http://s/live/u/p/1.m3u8", swapLiveFormat("http://s/live/u/p/1.ts"))
        assertEquals("http://s/live/u/p/1.ts", swapLiveFormat("http://s/live/u/p/1.m3u8"))
        val masked = maskUrl("http://s.tv/timeshift/usuario/clave/60/2026-01-01:10-00/1.ts")
        assertFalse(masked.contains("usuario") || masked.contains("clave") || masked.contains("s.tv"))
        assertFalse(maskUrl("http://s.tv/player_api.php?username=yo&password=clave").contains("clave"))
    }

    @Test
    fun nombresDeCategorias() {
        assertEquals("Estrenos 2026", com.suso.miiptv.ui.catLabel("ESTRENOS 2026"))
        assertEquals("Cine Mundo 4K", com.suso.miiptv.ui.catLabel("CINE MUNDO 4K"))
        assertEquals("Movistar España", com.suso.miiptv.ui.catLabel("MOVISTAR ESPAÑA"))
        assertEquals("M. LaLiga", com.suso.miiptv.ui.catLabel("M. LALIGA"))
        assertEquals("Cine Aventura y Fantastico", com.suso.miiptv.ui.catLabel("CINE AVENTURA Y FANTASTICO"))
        assertEquals("DAZN", com.suso.miiptv.ui.catLabel("DAZN"))
        assertEquals("Bar TV", com.suso.miiptv.ui.catLabel("BAR TV"))
        assertEquals("NBA & NFL", com.suso.miiptv.ui.catLabel("NBA & NFL"))
        assertEquals("Latinos", com.suso.miiptv.ui.catLabel("Latinos"))
        assertEquals("Liga RFEF", com.suso.miiptv.ui.catLabel("LIGA RFEF"))
    }

    companion object {
        private val M3U = """
            #EXTM3U
            #EXTINF:-1 tvg-id="La1.es" tvg-logo="http://logo/la1.png" group-title="España",La 1
            http://s/live/u/p/1.ts
            #EXTINF:-1 group-title="Deportes",DAZN 1
            http://s/live/u/p/2.ts
            #EXTINF:-1 tvg-logo="http://p/k.jpg" group-title="Estrenos",Kraken (2026)
            http://s/movie/u/p/3.mkv
            #EXTINF:-1 group-title="Series",The Americans S01 E01
            http://s/series/u/p/4.mkv
            #EXTINF:-1 group-title="Series",The Americans S01 E02
            http://s/series/u/p/5.mkv
        """.trimIndent()
    }

    @Test fun prefijosDeCategoria() {
        assertEquals("Top", catLabel("SERIES TOP", Kind.SERIES))
        assertEquals("Netflix", catLabel("Series Netflix", Kind.SERIES))
        assertEquals("Series", catLabel("SERIES", Kind.SERIES))
        assertEquals("Harry Potter", catLabel("Saga Harry Potter", Kind.MOVIE))
        assertEquals("Estrenos", catLabel("Cine Estrenos", Kind.MOVIE))
        assertEquals("Cine Estrenos", catLabel("Cine Estrenos", Kind.LIVE))
    }

    @Test fun titulosDeEpisodio() {
        assertEquals("El regreso del rey", sentenceLabel("EL REGRESO DEL REY"))
        assertEquals("S01E01 - Piloto de HBO", sentenceLabel("S01E01 - PILOTO DE HBO"))
        assertEquals("Capítulo 3", sentenceLabel("CAPÍTULO 3"))
        assertEquals("Ya Bien Escrito", sentenceLabel("Ya Bien Escrito"))
    }
}
