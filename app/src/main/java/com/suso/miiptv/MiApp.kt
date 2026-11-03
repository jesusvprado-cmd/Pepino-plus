package com.suso.miiptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.suso.miiptv.data.Http
import com.suso.miiptv.data.Logos
import com.suso.miiptv.data.Repo
import com.suso.miiptv.vpn.VpnManager

class MiApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        com.suso.miiptv.data.ErrorLog.init(this)
        com.suso.miiptv.data.Perf.init(this)
        com.suso.miiptv.data.Device.init(this)
        Repo.init(this)
        VpnManager.init(this)
        VpnManager.autoStart()
        Logos.init(this)
    }

    /** Carátulas y logos con caché en disco para que la Mi Box no los descargue cada vez. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                // Wikimedia exige que cada app se identifique con su propio nombre para servir los logos.
                Http.client.newBuilder().addInterceptor { chain ->
                    val r = chain.request()
                    if (r.url.host.endsWith("wikimedia.org") || r.url.host.endsWith("wikipedia.org")) {
                        chain.proceed(r.newBuilder().header("User-Agent", Logos.WIKI_UA).build())
                    } else {
                        chain.proceed(r)
                    }
                }.build()
            }
            // En modo ligero, sin fundido al cargar las imágenes.
            .crossfade(!com.suso.miiptv.data.Perf.light)
            .respectCacheHeaders(false)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("imagenes"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .build()
}
