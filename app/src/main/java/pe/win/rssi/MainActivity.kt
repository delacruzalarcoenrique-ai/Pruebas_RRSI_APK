package pe.win.rssi

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView

class MainActivity : Activity() {

    // ┌───────────────────────────────────────────────────────────────┐
    // │  CONFIGURA AQUÍ la URL de tu página de speedtest en Vercel:    │
    // └───────────────────────────────────────────────────────────────┘
    private val webUrl = "https://pruebas-rrsi-apk.vercel.app/web/ejemplo.html"

    private lateinit var webView: WebView

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
        setContentView(webView)
        webView.loadUrl(webUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

/** Puente JS expuesto a la web como window.WinetWifi. */
class WinetWifi(private val ctx: Context) {

    @Suppress("DEPRECATION")
    private fun connectionInfo() =
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo

    private fun freq(): Int = try { connectionInfo().frequency } catch (e: Exception) { 0 }

    private fun bandFromFreq(mhz: Int): String = when {
        mhz >= 5925 -> "6 GHz"
        mhz >= 4900 -> "5 GHz"
        mhz > 0 -> "2.4 GHz"
        else -> ""
    }

    @JavascriptInterface
    fun getRssi(): Int = try { connectionInfo().rssi } catch (e: Exception) { 0 }

    @JavascriptInterface
    fun getBand(): String = bandFromFreq(freq())

    @JavascriptInterface
    fun getInfoJson(): String {
        val rssi = getRssi()
        val f = freq()
        val band = bandFromFreq(f)
        val available = f > 0            // hay WiFi conectado si la frecuencia es > 0
        return "{\"rssi\":$rssi,\"frequencyMhz\":$f,\"band\":\"$band\",\"available\":$available}"
    }
}
