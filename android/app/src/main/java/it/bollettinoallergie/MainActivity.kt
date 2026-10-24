package it.bollettinoallergie

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/**
 * Mostra l'interfaccia (assets/www) in una WebView e le fornisce la posizione
 * del telefono tramite il servizio di localizzazione di Android.
 *
 * Ponte JS -> nativo: AndroidPosizione.richiediPosizione()
 * Risposte nativo -> JS: posizioneRicevuta(lat, lon, precisione) / posizioneErrore(messaggio)
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var annullaRichiesta: (() -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Le pagine sono servite da un'origine https locale, così fetch() verso le API funziona normalmente
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            setBackgroundColor(0)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

                // Se il processo di rendering termina (es. memoria esaurita) riparti invece di chiudere l'app
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    recreate()
                    return true
                }
            }
            addJavascriptInterface(Ponte(), "AndroidPosizione")
        }

        val contenitore = FrameLayout(this).apply { addView(webView) }
        // Da Android 15 l'app è disegnata sotto le barre di sistema (edge-to-edge): lascia lo spazio necessario
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            contenitore.setOnApplyWindowInsetsListener { v, insets ->
                val barre = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(barre.left, barre.top, barre.right, barre.bottom)
                WindowInsets.CONSUMED
            }
        }
        setContentView(contenitore)
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
    }

    override fun onDestroy() {
        annullaRichiesta?.invoke()
        webView.destroy()
        super.onDestroy()
    }

    private inner class Ponte {
        @JavascriptInterface
        fun richiediPosizione() {
            runOnUiThread { avviaLocalizzazione() }
        }
    }

    private fun haPermesso() =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode != RICHIESTA_PERMESSO) return
        if (haPermesso()) avviaLocalizzazione() else inviaErrore(getString(R.string.errore_permesso))
    }

    @SuppressLint("MissingPermission")
    private fun avviaLocalizzazione() {
        if (!haPermesso()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), RICHIESTA_PERMESSO)
            return
        }
        if (annullaRichiesta != null) return // una richiesta è già in corso

        val lm = getSystemService(LocationManager::class.java)
        val attivi = lm.getProviders(true)
        val provider = PROVIDER.firstOrNull { it in attivi }
        if (provider == null) {
            inviaErrore(getString(R.string.errore_disattivata))
            return
        }

        // Una posizione recente è sufficiente e immediata
        val ultima = attivi.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (ultima != null && System.currentTimeMillis() - ultima.time < VALIDITA_ULTIMA_MS) {
            inviaPosizione(ultima)
            return
        }

        val timeout = Runnable { concludi(null, ultima) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val segnale = CancellationSignal()
            annullaRichiesta = { segnale.cancel() }
            lm.getCurrentLocation(provider, segnale, mainExecutor) { concludi(it, ultima) }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) = concludi(location, ultima)
                // Nelle versioni di Android precedenti alla 11 questi metodi non hanno un'implementazione predefinita
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("LocationListener.onStatusChanged")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            annullaRichiesta = { lm.removeUpdates(listener) }
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
        handler.postDelayed(timeout, TIMEOUT_MS)
        val annullaProvider = annullaRichiesta
        annullaRichiesta = {
            handler.removeCallbacks(timeout)
            annullaProvider?.invoke()
        }
    }

    /** Chiude la richiesta in corso; se non arriva una posizione usa l'ultima nota, anche se vecchia. */
    private fun concludi(posizione: Location?, ripiego: Location?) {
        val annulla = annullaRichiesta ?: return // già conclusa
        annullaRichiesta = null
        annulla()
        val scelta = posizione ?: ripiego
        if (scelta != null) inviaPosizione(scelta) else inviaErrore(getString(R.string.errore_timeout))
    }

    private fun inviaPosizione(p: Location) =
        eseguiJs("posizioneRicevuta(${p.latitude}, ${p.longitude}, ${p.accuracy})")

    private fun inviaErrore(messaggio: String) =
        eseguiJs("posizioneErrore(${JSONObject.quote(messaggio)})")

    private fun eseguiJs(chiamata: String) =
        webView.evaluateJavascript("window.$chiamata", null)

    private companion object {
        const val RICHIESTA_PERMESSO = 1
        const val TIMEOUT_MS = 30_000L
        const val VALIDITA_ULTIMA_MS = 15 * 60_000L
        val PROVIDER = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
    }
}
