package com.suso.miiptv.vpn

import com.suso.miiptv.data.Http
import com.wireguard.crypto.KeyPair
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.TlsVersion
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Registro gratuito en Cloudflare WARP (mismo método que la herramienta wgcf).
 * Devuelve una configuración WireGuard lista para usar.
 */
object Warp {
    private const val API = "https://api.cloudflareclient.com/v0a1922/reg"
    private const val PEER_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
    private const val DEFAULT_ENDPOINT = "engage.cloudflareclient.com:2408"

    fun register(): String {
        val kp = KeyPair()
        val tos = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        val body = JSONObject().apply {
            put("key", kp.publicKey.toBase64())
            put("install_id", "")
            put("fcm_token", "")
            put("tos", tos)
            put("model", "PC")
            put("serial_number", "")
            put("type", "Android")
            put("locale", "es_ES")
        }
        // Cloudflare solo acepta TLS 1.2 y HTTP/1.1 en este punto.
        val client = Http.client.newBuilder()
            .connectionSpecs(listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).tlsVersions(TlsVersion.TLS_1_2).build()))
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
        val req = Request.Builder()
            .url(API)
            .header("User-Agent", "okhttp/3.12.1")
            .header("CF-Client-Version", "a-6.3-1922")
            .post(body.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
        val text = try {
            client.newCall(req).execute().use { r ->
                val t = r.body?.string() ?: ""
                if (!r.isSuccessful) throw IOException("Cloudflare rechazó el registro (HTTP ${r.code})")
                t
            }
        } catch (e: java.net.UnknownHostException) {
            throw IOException("No se puede contactar con Cloudflare. Revisa tu conexión.")
        }
        val root = JSONObject(text)
        val res = root.optJSONObject("result") ?: root
        val cfg = res.optJSONObject("config") ?: throw IOException("Respuesta de Cloudflare no válida")
        val peer = cfg.optJSONArray("peers")?.optJSONObject(0)
        val peerKey = peer?.optString("public_key")?.takeIf { it.isNotBlank() } ?: PEER_KEY
        val ep = peer?.optJSONObject("endpoint")
        var endpoint = ep?.optString("host")?.takeIf { it.isNotBlank() } ?: DEFAULT_ENDPOINT
        if (!endpoint.contains(":")) endpoint += ":2408"
        if (endpoint.endsWith(":0")) endpoint = endpoint.removeSuffix(":0") + ":2408"
        val addr = cfg.optJSONObject("interface")?.optJSONObject("addresses")
        val v4 = addr?.optString("v4")?.takeIf { it.isNotBlank() } ?: "172.16.0.2"
        val v6 = addr?.optString("v6")?.takeIf { it.isNotBlank() }
        val address = if (v6 != null) "$v4/32, $v6/128" else "$v4/32"
        return """
            [Interface]
            PrivateKey = ${kp.privateKey.toBase64()}
            Address = $address
            DNS = 1.1.1.1, 1.0.0.1
            MTU = 1280

            [Peer]
            PublicKey = $peerKey
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = $endpoint
            PersistentKeepalive = 25
        """.trimIndent()
    }
}
