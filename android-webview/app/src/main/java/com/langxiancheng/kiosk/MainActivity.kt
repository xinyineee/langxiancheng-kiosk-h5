package com.langxiancheng.kiosk

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowInsetsController
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.nfc.NfcAdapter
import java.io.File

/**
 * WebView shell for LangXianCheng Kiosk v3.0
 * ASR: iFlytek IAT (WebSocket real-time ASR) + Android SpeechRecognizer (fallback)
 * NFC: NDEF write for result sharing via short URL
 *
 * v3.0 changes:
 * - Replaced SUNMI Voice SDK with iFlytek IAT (语音听写) for better Chinese recognition
 * - SUNMI SDK had WEB mode degrading to LOCAL (Offline:true), producing garbage results
 * - iFlytek IAT uses WebSocket to cloud, 6ms latency from device, superior accuracy
 * - JSBridge interface unchanged — H5 needs zero changes
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LXCKiosk"
        private const val MIC_REQUEST = 1001

        // iFlytek credentials — get from https://console.xfyun.cn/app/myapp
        // Free tier: 500 calls/day for voice dictation
        private const val IFLYTEK_APP_ID = "181d21eb"
        private const val IFLYTEK_API_KEY = "d8517eafd932725a938ddd083729f509"
        private const val IFLYTEK_API_SECRET = "OWM3ZjA1NGQ0MTA2N2M4NjNlNWQyM2Ey"
    }

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())

    // ---- iFlytek ASR state ----
    private var iflytekEngine: IflytekAsrEngine? = null
    private var iflytekReady = false
    private var iflytekActive = false

    /** Whether HTML wants ASR to auto-continue after session ends */
    private var autoContinue = false
    /** Last language requested via JSBridge */
    private var lastLang = "zh-CN"
    /** Timestamp for timing logs */
    private var sessionStartTime = 0L

    // ---- Android SpeechRecognizer fallback ----
    private var androidRecognizer: SpeechRecognizer? = null
    private var androidListening = false

    /** Which ASR engine is active: "iflytek" | "android" | "none" */
    private var activeEngine = "none"

    // ---- NFC state ----
    private var nfcAdapter: NfcAdapter? = null
    private var nfcEnabled = false

    // ================================================================
    // Lifecycle
    // ================================================================

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        // Fullscreen flags must be set BEFORE setContentView
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        hideSystemBars()

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
        }

        WebView.setWebContentsDebuggingEnabled(true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Notify H5 that ASR engine is ready (after JS functions are loaded)
                when (activeEngine) {
                    "iflytek" -> runJs("window._asrOnEngineReady('iflytek', 'cloud')")
                    "android" -> runJs("window._asrOnEngineReady('android', 'fallback')")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                val text = msg?.message() ?: return false
                if (text.startsWith("[ASR]")) {
                    handler.post {
                        android.widget.Toast.makeText(this@MainActivity, text, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                return super.onConsoleMessage(msg)
            }
        }
        webView.addJavascriptInterface(AsrBridge(), "AndroidASR")
        webView.addJavascriptInterface(NfcBridge(), "AndroidNFC")

        // Dev mode: load from app's external files dir if index.html exists there
        val devDir = File(getExternalFilesDir(null), "kiosk")
        val devHtml = File(devDir, "index.html")
        if (devDir.exists() && devHtml.exists()) {
            try {
                val html = devHtml.readText()
                val baseUrl = "file://${devDir.absolutePath}/"
                Log.i(TAG, "DEV MODE: loading from external files, base=$baseUrl (${html.length} chars)")
                webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                Log.e(TAG, "DEV MODE: failed to read HTML, falling back to assets", e)
                webView.loadUrl("file:///android_asset/www/index.html")
            }
        } else {
            Log.i(TAG, "PROD MODE: loading bundled assets (dev dir: ${devDir.absolutePath})")
            webView.loadUrl("file:///android_asset/www/index.html")
        }

        // Request mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
        }

        // Init iFlytek ASR
        initIflytekAsr()
        initNfc()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_REQUEST) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            webView.evaluateJavascript(
                "if(typeof window._asrPermissionResult==='function') window._asrPermissionResult($granted);",
                null
            )
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        autoContinue = false
        stopAllAsr()
    }

    override fun onDestroy() {
        autoContinue = false
        iflytekEngine?.destroy()
        androidRecognizer?.destroy()
        webView.destroy()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): use WindowInsetsController
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            controller?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            // Android 10 and below: legacy flags
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ================================================================
    // iFlytek IAT ASR Engine
    // ================================================================

    private fun initIflytekAsr() {
        // Check if credentials are configured
        if (IFLYTEK_APP_ID == "YOUR_APP_ID" || IFLYTEK_API_KEY == "YOUR_API_KEY") {
            Log.w(TAG, "iFlytek credentials not configured, falling back to Android ASR")
            Log.w(TAG, "Set IFLYTEK_APP_ID, IFLYTEK_API_KEY, IFLYTEK_API_SECRET in MainActivity.kt")
            activeEngine = "android"
            // onPageFinished will notify H5
            return
        }

        try {
            iflytekEngine = IflytekAsrEngine(
                appId = IFLYTEK_APP_ID,
                apiKey = IFLYTEK_API_KEY,
                apiSecret = IFLYTEK_API_SECRET,
                callback = object : IflytekAsrEngine.Callback {
                    override fun onStart() {
                        iflytekActive = true
                        sessionStartTime = System.currentTimeMillis()
                        Log.i(TAG, "iFlytek ASR capture started [${System.currentTimeMillis() - sessionStartTime}ms]")
                        runJs("window._asrOnStart()")
                    }

                    override fun onPartial(text: String) {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.d(TAG, "iFlytek ASR partial [${elapsed}ms]: $text")
                        val escaped = text.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
                        runJs("window._asrOnPartial(['$escaped'])")
                    }

                    override fun onResult(text: String) {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.i(TAG, "iFlytek ASR final [${elapsed}ms]: $text")
                        val escaped = text.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
                        runJs("window._asrOnResult(['$escaped'])")
                    }

                    override fun onError(error: String) {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.w(TAG, "iFlytek ASR error [${elapsed}ms]: $error")
                        runJs("window._asrOnError('$error')")
                    }

                    override fun onStop() {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.i(TAG, "iFlytek ASR session ended [${elapsed}ms], autoContinue=$autoContinue")
                        iflytekActive = false
                        runJs("window._asrOnStop()")

                        // Auto-restart: skip HTML round-trip, start new session immediately
                        if (autoContinue) {
                            handler.postDelayed({
                                if (autoContinue && !iflytekActive && iflytekReady) {
                                    Log.i(TAG, "Auto-continuing iFlytek ASR session (0ms HTML delay)")
                                    iflytekEngine?.startListening()
                                }
                            }, 200)  // 200ms settle for new WebSocket connection
                        }
                    }
                }
            )
            iflytekReady = true
            activeEngine = "iflytek"
            Log.i(TAG, "iFlytek ASR engine initialized successfully")
            // onPageFinished will notify H5 with _asrOnEngineReady
        } catch (e: Exception) {
            Log.w(TAG, "iFlytek init failed, falling back to Android ASR", e)
            activeEngine = "android"
            // onPageFinished will notify H5
        }
    }

    // ================================================================
    // Android SpeechRecognizer (fallback)
    // ================================================================

    private fun androidStartListening(lang: String) {
        if (androidRecognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                runJs("window._asrOnError('android-not-available')")
                return
            }
            androidRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(androidRecognitionListener)
            }
        }
        activeEngine = "android"
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
            }
            androidRecognizer?.startListening(intent)
        } catch (e: Exception) {
            runJs("window._asrOnError('android-start-failed')")
        }
    }

    private fun androidStopListening() {
        try { androidRecognizer?.stopListening() } catch (e: Exception) {}
        androidListening = false
    }

    private val androidRecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            androidListening = true
            Log.i(TAG, "Android ASR ready")
            runJs("window._asrOnStart()")
        }
        override fun onBeginningOfSpeech() { Log.d(TAG, "Android ASR: speech began") }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            androidListening = false
            Log.d(TAG, "Android ASR: speech ended")
        }
        override fun onError(error: Int) {
            androidListening = false
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "no-match"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                SpeechRecognizer.ERROR_AUDIO -> "audio-error"
                SpeechRecognizer.ERROR_CLIENT -> "client-error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "denied"
                SpeechRecognizer.ERROR_NETWORK -> "network-error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network-timeout"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
                SpeechRecognizer.ERROR_SERVER -> "server-error"
                else -> "error-$error"
            }
            Log.w(TAG, "Android ASR error: $msg ($error)")
            runJs("window._asrOnError('$msg')")
        }
        override fun onResults(results: Bundle?) {
            androidListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: arrayListOf()
            Log.i(TAG, "Android ASR results: $matches")
            val json = matches.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
            runJs("window._asrOnResult([$json])")
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                Log.d(TAG, "Android ASR partial: ${matches.first()}")
                val json = matches.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
                runJs("window._asrOnPartial([$json])")
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ================================================================
    // Unified stop
    // ================================================================

    private fun stopAllAsr() {
        autoContinue = false
        iflytekEngine?.stopListening()
        androidStopListening()
    }

    // ================================================================
    // NFC Module (HCE mode — phone taps screen to receive URL)
    // ================================================================

    private fun initNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Log.w(TAG, "NFC not available on this device")
            nfcEnabled = false
            return
        }
        nfcEnabled = true
        Log.i(TAG, "NFC available: ${nfcAdapter?.isEnabled}")
    }

    // ================================================================
    // JSBridge
    // ================================================================

    inner class AsrBridge {

        @JavascriptInterface
        fun getEngine(): String = activeEngine

        @JavascriptInterface
        fun getAsrMode(): String = if (activeEngine == "iflytek") "cloud" else "local"

        @JavascriptInterface
        fun startListening(lang: String) {
            runOnUiThread {
                lastLang = lang
                Log.i(TAG, "JSBridge startListening, engine=$activeEngine, lang=$lang")
                when (activeEngine) {
                    "iflytek" -> {
                        if (iflytekReady && !iflytekActive) {
                            iflytekEngine?.startListening()
                        } else {
                            Log.w(TAG, "iFlytek not ready or busy, falling back to Android")
                            androidStartListening(lang)
                        }
                    }
                    "android" -> androidStartListening(lang)
                    else -> runJs("window._asrOnError('no-engine-ready')")
                }
            }
        }

        @JavascriptInterface
        fun stopListening() {
            runOnUiThread {
                autoContinue = false
                stopAllAsr()
                runJs("window._asrOnStop()")
            }
        }

        /** HTML tells Kotlin to auto-continue ASR after session ends */
        @JavascriptInterface
        fun setAutoContinue(continueListening: Boolean) {
            autoContinue = continueListening
            Log.d(TAG, "setAutoContinue: $continueListening")
        }

        @JavascriptInterface
        fun isSunmiReady(): Boolean = false  // No longer using SUNMI

        @JavascriptInterface
        fun isAndroidAvailable(): Boolean =
            SpeechRecognizer.isRecognitionAvailable(this@MainActivity)

        /** HTML reports ASR no-match — no longer needed for iFlytek (cloud ASR doesn't need fallback) */
        @JavascriptInterface
        fun reportNoMatch() {
            Log.d(TAG, "reportNoMatch: no-op (using iFlytek cloud ASR)")
        }
    }

    inner class NfcBridge {
        @JavascriptInterface
        fun isNfcAvailable(): Boolean = nfcEnabled && nfcAdapter?.isEnabled == true

        /**
         * Set the URL to be served via HCE when a phone taps the NFC area.
         * The NdefHceService will respond with this URL as an NDEF URI record.
         */
        @JavascriptInterface
        fun prepareWrite(drinkId: String) {
            runOnUiThread {
                val url = "https://kiosk-h5.pages.dev/result/$drinkId"
                NdefHceService.pendingUrl = url
                Log.i(TAG, "NFC HCE URL set: $url")
                runJs("window._nfcOnReady()")
            }
        }

        @JavascriptInterface
        fun cancelWrite() {
            NdefHceService.pendingUrl = null
            Log.d(TAG, "NFC HCE URL cleared")
        }
    }

    // ================================================================
    // JS helper
    // ================================================================

    private fun runJs(script: String) {
        handler.post {
            try { webView.evaluateJavascript(script, null) }
            catch (e: Exception) { Log.w(TAG, "runJs error", e) }
        }
    }
}
