package pe.win.rssi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    // ┌───────────────────────────────────────────────────────────────┐
    // │  CONFIGURA AQUÍ la URL de tu página de speedtest en Vercel:    │
    // └───────────────────────────────────────────────────────────────┘
    private val webUrl = "https://pruebas-rrsi-apk.vercel.app/web/ejemplo.html"

    /**
     * Copia local de la página, empaquetada en el APK (ver tarea `copiarWeb`).
     * Se usa si la página remota no carga: el técnico suele estar en sitios
     * sin internet, y la medición de señal no depende de la red.
     */
    private val urlRespaldo = "file:///android_asset/ejemplo.html"

    private lateinit var webView: WebView
    private var usandoRespaldo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android exige permiso de ubicación para leer datos del WiFi conectado.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(WinetWifi(this), "WinetWifi")

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // Solo nos importa el fallo de la página principal, no de subrecursos.
                if (request.isForMainFrame && !usandoRespaldo) {
                    usandoRespaldo = true
                    view.loadUrl(urlRespaldo)
                }
            }
        }

        setContentView(webView)
        webView.loadUrl(webUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

/**
 * Puente JS expuesto a la web como window.WinetWifi.
 *
 * Lee el RSSI por TRES vías distintas y prefiere la del escaneo, que es la que
 * muestran los analizadores WiFi (WiFi Analyzer y similares):
 *
 *   - `scan`: ScanResult.level del BSSID conectado — RSSI medido de los beacons
 *     del AP. Es el valor que reporta WiFi Analyzer como RX.
 *   - `link`: WifiInfo.rssi — estimación del driver sobre tramas de datos;
 *     suele leer varios dB por debajo del valor de beacon.
 *   - `caps`: NetworkCapabilities.getSignalStrength() (API 29+) — API moderna.
 *
 * Se usa `scan` cuando hay un resultado del BSSID conectado; si no lo hay
 * (escaneo aún sin refrescar), cae a `link` para no dejar la pantalla vacía.
 */
class WinetWifi(private val ctx: Context) {

    /**
     * Intervalo mínimo entre solicitudes de escaneo. Android limita a 4 por cada
     * 2 minutos en primer plano; 35 s queda por debajo del límite (30 s lo roza
     * y algunas solicitudes se descartan).
     */
    private val scanRequestIntervalMs = 35_000L
    private var lastScanRequestMs = 0L

    private fun wifiManager() =
        ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Suppress("DEPRECATION")
    private fun connectionInfo() = wifiManager().connectionInfo

    private fun bandFromFreq(mhz: Int): String = when {
        mhz >= 5925 -> "6 GHz"
        mhz >= 4900 -> "5 GHz"
        mhz > 0 -> "2.4 GHz"
        else -> ""
    }

    /** Pide un escaneo nuevo de vez en cuando para que ScanResult no envejezca. */
    @Suppress("DEPRECATION")
    private fun maybeRequestScan() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScanRequestMs < scanRequestIntervalMs) return
        lastScanRequestMs = now
        try { wifiManager().startScan() } catch (e: Exception) { /* limitado por el sistema */ }
    }

    /** RSSI del escaneo para el BSSID dado, con su antigüedad en ms. */
    private fun scanRssiFor(bssid: String): Pair<Int?, Long> {
        if (bssid.isEmpty()) return Pair(null, -1L)
        return try {
            @Suppress("DEPRECATION")
            val results = wifiManager().scanResults ?: return Pair(null, -1L)
            val match = results.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }
                ?: return Pair(null, -1L)
            val ageMs = (SystemClock.elapsedRealtime() * 1000L - match.timestamp) / 1000L
            Pair(match.level, ageMs)
        } catch (e: Exception) {
            Pair(null, -1L)
        }
    }

    /**
     * RSSI según la API moderna de NetworkCapabilities (API 29+).
     * Se invoca por reflexión: así el proyecto compila y corre en cualquier SDK,
     * aunque el método no esté expuesto. Es un valor solo de diagnóstico.
     */
    private fun capsRssi(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val cm = ctx.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return null
            val strength = caps.javaClass.getMethod("getSignalStrength").invoke(caps) as? Int
            if (strength == null || strength == Int.MIN_VALUE) null else strength
        } catch (e: Exception) {
            null
        }
    }

    @JavascriptInterface
    fun getRssi(): Int {
        val info = try { connectionInfo() } catch (e: Exception) { null } ?: return 0
        val linkRssi = try { info.rssi } catch (e: Exception) { 0 }
        val bssid = try { info.bssid ?: "" } catch (e: Exception) { "" }
        return scanRssiFor(bssid).first ?: linkRssi
    }

    @JavascriptInterface
    fun getBand(): String =
        bandFromFreq(try { connectionInfo().frequency } catch (e: Exception) { 0 })

    @JavascriptInterface
    fun getInfoJson(): String {
        maybeRequestScan()

        var linkRssi = 0
        var freq = 0
        var bssid = ""
        var linkSpeed = -1
        try {
            val info = connectionInfo()
            linkRssi = info.rssi
            freq = info.frequency
            bssid = info.bssid ?: ""
            linkSpeed = info.linkSpeed
        } catch (e: Exception) { /* sin WiFi o sin permiso */ }

        val (scanRssi, scanAgeMs) = scanRssiFor(bssid)
        val capsRssi = capsRssi()

        // Preferimos el valor del escaneo: es el que muestran los analizadores WiFi.
        val rssi = scanRssi ?: linkRssi
        val source = if (scanRssi != null) "scan" else "link"
        val band = bandFromFreq(freq)
        val available = freq > 0

        return "{" +
            "\"rssi\":$rssi," +
            "\"source\":\"$source\"," +
            "\"rssiScan\":${scanRssi ?: "null"}," +
            "\"rssiLink\":$linkRssi," +
            "\"rssiCaps\":${capsRssi ?: "null"}," +
            "\"scanAgeMs\":$scanAgeMs," +
            "\"frequencyMhz\":$freq," +
            "\"band\":\"$band\"," +
            "\"bssid\":\"$bssid\"," +
            "\"linkSpeedMbps\":$linkSpeed," +
            "\"available\":$available" +
            "}"
    }
}
