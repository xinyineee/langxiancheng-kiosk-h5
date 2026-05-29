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
import android.app.PendingIntent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import java.io.File

// SUNMI AI Base SDK
import com.sm.ai.framework.base_sdk.ISmSDKStateListener
import com.sm.ai.framework.main.sdk.SmAIMainFrameworkSDK
import com.sm.ai.framework.main.sdk.callback.SdkVersionCallback
import com.sm.ai.framework.main.aidl.SdkStatusInfo

// SUNMI Voice SDK
import com.sm.ai.framework.asr.sdk.SmSpeechSDK
import com.sm.ai.framework.asr.sdk.InitialConfig
import com.sm.ai.framework.asr.sdk.SmAsrMode
import com.sm.ai.framework.asr.sdk.ISpeechServiceStateListener
import com.sm.ai.framework.asr.sdk.result.ISpeechSessionResultCallback
import com.sm.ai.framework.asr.aidl.result.IContinuationRequestCallback
import com.sm.ai.framework.asr.sdk.lifecycle.ISpeechSessionLifecycle

/**
 * WebView shell for LangXianCheng Kiosk v2.6
 * ASR: SUNMI Voice SDK (WEB mode) + Android SpeechRecognizer (fallback)
 * NFC: NDEF write for result sharing via short URL
 *
 * v2.6 changes:
 * - Added NFC module: write NDEF URL to tag for result sharing
 * - NfcBridge JSBridge exposed as "AndroidNFC"
 * - NFC is optional (required=false), gracefully degrades on non-NFC devices
 *
 * v2.5 changes:
 * - Switched to WEB mode for better Chinese recognition accuracy
 * - Auto-restart session in Kotlin (skip HTML round-trip delay)
 * - Precise timing logs for latency diagnosis
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LXCKiosk"
        private const val MIC_REQUEST = 1001
        /** How many consecutive empty LOCAL results before switching to WEB */
        private const val LOCAL_EMPTY_THRESHOLD = 2
    }

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())

    // ---- SUNMI Voice SDK state ----
    private var sunmiBaseReady = false
    private var sunmiSpeechReady = false
    private var sunmiAsrActive = false
    /** Current ASR mode: "local" or "web" */
    private var currentAsrMode = "local"
    /** Consecutive empty results from LOCAL mode */
    private var localEmptyCount = 0
    /** Whether LOCAL mode has been permanently disabled (fell back to WEB) */
    private var localDisabled = false
    /** Whether HTML wants ASR to auto-continue after session ends */
    private var autoContinue = false
    /** Last language requested via JSBridge */
    private var lastLang = "zh-CN"
    /** Timestamp for timing logs */
    private var sessionStartTime = 0L

    // ---- Android SpeechRecognizer fallback ----
    private var androidRecognizer: SpeechRecognizer? = null
    private var androidListening = false

    /** Which ASR engine is active: "sunmi" | "android" | "none" */
    private var activeEngine = "none"

    // ---- NFC state ----
    private var nfcAdapter: NfcAdapter? = null
    private var nfcWritePending = false
    private var nfcPendingUrl: String? = null
    private var nfcEnabled = false

    // ================================================================
    // Lifecycle
    // ================================================================

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        }

        webView.webChromeClient = WebChromeClient()
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

        // Init SUNMI SDK
        initSunmiSDK()
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
        enableNfcForegroundDispatch()
        hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        autoContinue = false
        stopAllAsr()
        disableNfcForegroundDispatch()
    }

    override fun onDestroy() {
        autoContinue = false
        destroySunmiSpeechSDK()
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
    // SUNMI Voice SDK
    // ================================================================

    private fun initSunmiSDK() {
        try {
            SmAIMainFrameworkSDK.getInstance().initialize(this,
                object : ISmSDKStateListener {
                    override fun onInitSuccess() {
                        Log.i(TAG, "SUNMI base SDK init success")
                        sunmiBaseReady = true
                        checkSunmiSpeechCapability()
                    }
                    override fun onInitFail(errorCode: String) {
                        Log.w(TAG, "SUNMI base SDK init fail: $errorCode, falling back to Android ASR")
                        activeEngine = "android"
                        runJs("window._asrOnEngineReady('android', '')")
                    }
                    override fun onDisconnected(code: Int, reason: String) {
                        Log.w(TAG, "SUNMI base disconnected: $code $reason")
                    }
                })
        } catch (e: Exception) {
            Log.w(TAG, "SUNMI base SDK not available, using Android ASR", e)
            activeEngine = "android"
            runJs("window._asrOnEngineReady('android', '')")
        }
    }

    private fun checkSunmiSpeechCapability() {
        try {
            SmAIMainFrameworkSDK.getInstance().checkSdkVersion(object : SdkVersionCallback {
                override fun onSdkVersionCheckResult(statuses: List<SdkStatusInfo>?) {
                    if (statuses == null) {
                        Log.w(TAG, "No SUNMI capabilities found")
                        runJs("window._asrOnEngineReady('android', 'no-capabilities')")
                        return
                    }
                    var speechAvailable = false
                    for (info in statuses) {
                        val name = try { info.sdkName } catch (e: Exception) { null }
                        val state = try { info.state } catch (e: Exception) { null }
                        Log.d(TAG, "SUNMI capability: name=$name state=$state")
                        if (name == "sunmi_speech_interaction" && state == "3") {
                            speechAvailable = true
                            break
                        }
                    }
                    if (speechAvailable) {
                        Log.i(TAG, "SUNMI speech SDK available, initializing...")
                        initSunmiSpeechSDK()
                    } else {
                        Log.w(TAG, "SUNMI speech SDK not available, using Android ASR")
                        activeEngine = "android"
                        runJs("window._asrOnEngineReady('android', '')")
                    }
                }
                override fun onError(errorCode: Int, errorMessage: String) {
                    Log.w(TAG, "SUNMI checkSdkVersion error: $errorCode $errorMessage")
                    runJs("window._asrOnEngineReady('android', 'check-error:$errorCode')")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "checkSdkVersion exception", e)
            runJs("window._asrOnEngineReady('android', 'check-exception')")
        }
    }

    private fun initSunmiSpeechSDK() {
        try {
            // Use WEB mode for best Chinese recognition accuracy
            val config = InitialConfig("", "", "").apply {
                asrMode = SmAsrMode.WEB
            }
            currentAsrMode = "web"
            SmSpeechSDK.getInstance().initialize(this, config,
                object : ISpeechServiceStateListener {
                    override fun onInitSuccess() {
                        Log.i(TAG, "SUNMI speech SDK init success (WEB mode), using SUNMI ASR engine")
                        sunmiSpeechReady = true
                        activeEngine = "sunmi"
                        handler.post {
                            runJs("window._asrOnEngineReady('sunmi', 'local')")
                        }
                    }
                    override fun onInitFail(errorCode: String) {
                        Log.w(TAG, "SUNMI speech SDK init fail: $errorCode, using Android ASR")
                        activeEngine = "android"
                        runJs("window._asrOnEngineReady('android', '')")
                    }
                    override fun onDisconnected(code: Int, reason: String) {
                        Log.w(TAG, "SUNMI speech disconnected: $code $reason")
                    }
                })
        } catch (e: Exception) {
            Log.w(TAG, "initSunmiSpeechSDK exception", e)
            runJs("window._asrOnEngineReady('android', 'speech-init-exception')")
        }
    }

    /** Re-initialize SDK with WEB mode after LOCAL mode fails */
    private fun switchToWebMode() {
        if (currentAsrMode == "web") return
        Log.i(TAG, "Switching ASR from LOCAL → WEB mode (LOCAL produced $localEmptyCount empty results)")
        currentAsrMode = "web"
        localDisabled = true
        try {
            SmSpeechSDK.getInstance().destroy()
            sunmiSpeechReady = false
            sunmiAsrActive = false

            val config = InitialConfig("", "", "").apply {
                asrMode = SmAsrMode.WEB
            }
            SmSpeechSDK.getInstance().initialize(this, config,
                object : ISpeechServiceStateListener {
                    override fun onInitSuccess() {
                        Log.i(TAG, "SUNMI speech SDK re-init success (WEB mode)")
                        sunmiSpeechReady = true
                        activeEngine = "sunmi"
                        runJs("window._asrOnEngineReady('sunmi', 'web')")
                    }
                    override fun onInitFail(errorCode: String) {
                        Log.w(TAG, "SUNMI speech SDK WEB init fail: $errorCode")
                        activeEngine = "android"
                        runJs("window._asrOnEngineReady('android', '')")
                    }
                    override fun onDisconnected(code: Int, reason: String) {
                        Log.w(TAG, "SUNMI speech disconnected: $code $reason")
                    }
                })
        } catch (e: Exception) {
            Log.w(TAG, "switchToWebMode exception", e)
            activeEngine = "android"
            runJs("window._asrOnEngineReady('android', '')")
        }
    }

    private fun destroySunmiSpeechSDK() {
        try {
            if (sunmiAsrActive) {
                SmSpeechSDK.getInstance().stopSemanticRecognizer()
                sunmiAsrActive = false
            }
            SmSpeechSDK.getInstance().destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroySunmiSpeechSDK error", e)
        }
    }

    private fun sunmiStartListening(lang: String) {
        if (!sunmiSpeechReady) return
        try {
            sunmiAsrActive = true
            sessionStartTime = System.currentTimeMillis()
            Log.i(TAG, "ASR start [mode=$currentAsrMode] t=0ms")

            SmSpeechSDK.getInstance().startSemanticRecognizer(
                15000L,  // 15s timeout
                null as ISpeechSessionResultCallback.IWakeUpCallback?,
                object : ISpeechSessionResultCallback.IStreamingSpeechRecognitionCallback {
                    override fun onPartialResult(partialText: String, direction: Int, translate: Boolean) {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.d(TAG, "ASR partial [${elapsed}ms]: $partialText")
                        runJs("window._asrOnPartial(['${partialText.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")}'])")
                    }
                    override fun onAsrFinalResult(finalText: String, direction: Int, translate: Boolean) {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.i(TAG, "ASR final [${elapsed}ms]: $finalText")

                        // LOCAL mode empty result detection
                        if (currentAsrMode == "local" && finalText.isBlank()) {
                            localEmptyCount++
                            Log.w(TAG, "LOCAL empty result #$localEmptyCount")
                            if (localEmptyCount >= LOCAL_EMPTY_THRESHOLD && !localDisabled) {
                                switchToWebMode()
                            }
                        } else if (finalText.isNotBlank()) {
                            localEmptyCount = 0  // reset on successful result
                        }

                        val json = "[\"${finalText.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")}\"]"
                        runJs("window._asrOnResult($json)")
                    }
                },
                object : ISpeechSessionResultCallback {
                    override fun onContinuationRequired(
                        currentResult: String?,
                        promptText: String?,
                        callback: IContinuationRequestCallback?
                    ) { /* not needed */ }
                    override fun onSemanticResult(
                        nluResult: String?,
                        callback: IContinuationRequestCallback?
                    ) { /* ignore semantic, only ASR text */ }
                    override fun onError(
                        errorCode: String?,
                        callback: IContinuationRequestCallback?
                    ) {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.w(TAG, "ASR error [${elapsed}ms]: $errorCode (mode=$currentAsrMode)")
                        // Suppress semantic/NLU errors — we only use ASR text, not NLU
                        if (errorCode != null && (errorCode.startsWith("A-FSN") ||
                            errorCode.contains("tool.json") || errorCode.contains("promptUUID"))) {
                            Log.d(TAG, "Suppressed semantic error (not an ASR error)")
                            return
                        }
                        val errCode = errorCode ?: "unknown"
                        runJs("window._asrOnError('sunmi-error:$errCode')")
                    }
                    override fun buildLlmToolJson(): String = ""
                    override fun buildLlmContent(rawText: String, direction: Int): String = rawText
                    override fun buildLlmPromptUUID(): String = ""
                    override fun buildLlmExtraContent(): String = ""
                    override fun buildLlmSourceLang(): String = ""
                },
                object : ISpeechSessionLifecycle {
                    override fun onSessionReady() {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.i(TAG, "ASR session ready [${elapsed}ms]")
                    }
                    override fun onWakeUpReady() {}
                    override fun onCaptureStarted() {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.i(TAG, "ASR capture started [${elapsed}ms]")
                        runJs("window._asrOnStart()")
                    }
                    override fun onCapturePaused() {}
                    override fun onCaptureResumed() {}
                    override fun onSemanticProcessingStarted() {}
                    override fun onSemanticProcessingIncomplete() {}
                    override fun onSemanticProcessingCompleted() {}
                    override fun onSessionEnded() {
                        val elapsed = System.currentTimeMillis() - sessionStartTime
                        Log.i(TAG, "ASR session ended [${elapsed}ms], autoContinue=$autoContinue")
                        sunmiAsrActive = false
                        runJs("window._asrOnStop()")

                        // Auto-restart: skip HTML round-trip, start new session immediately
                        if (autoContinue) {
                            handler.postDelayed({
                                if (autoContinue && !sunmiAsrActive && sunmiSpeechReady) {
                                    Log.i(TAG, "Auto-continuing ASR session (0ms HTML delay)")
                                    sunmiStartListening(lastLang)
                                }
                            }, 100)  // 100ms settle time instead of 200ms HTML round-trip
                        }
                    }
                }
            )
        } catch (e: Exception) {
            sunmiAsrActive = false
            Log.w(TAG, "sunmiStartListening error", e)
            runJs("window._asrOnError('sunmi-start-failed')")
        }
    }

    private fun sunmiStopListening() {
        autoContinue = false
        try {
            if (sunmiAsrActive) {
                SmSpeechSDK.getInstance().stopSemanticRecognizer()
                sunmiAsrActive = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "sunmiStopListening error", e)
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
        sunmiStopListening()
        androidStopListening()
    }

    // ================================================================
    // NFC Module
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

    /** Enable NFC foreground dispatch so we receive tag discovery intents */
    private fun enableNfcForegroundDispatch() {
        if (!nfcEnabled || nfcAdapter == null) return
        try {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try { addDataType("*/*") } catch (e: Exception) {}
            }
            val filters = arrayOf(ndefFilter)
            val techList = arrayOf(arrayOf(Ndef::class.java.name))
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, techList)
            Log.d(TAG, "NFC foreground dispatch enabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable NFC foreground dispatch", e)
        }
    }

    private fun disableNfcForegroundDispatch() {
        if (!nfcEnabled || nfcAdapter == null) return
        try {
            nfcAdapter?.disableForegroundDispatch(this)
            Log.d(TAG, "NFC foreground dispatch disabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable NFC foreground dispatch", e)
        }
    }

    /** Write NDEF URL to an NFC tag */
    private fun writeNdefToTag(url: String, tag: Tag): Boolean {
        val ndef = Ndef.get(tag) ?: run {
            Log.w(TAG, "Tag does not support NDEF")
            return false
        }
        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                Log.w(TAG, "Tag is not writable")
                return false
            }
            val uriRecord = NdefRecord.createUri(url)
            val ndefMessage = NdefMessage(uriRecord)
            if (ndef.maxSize < ndefMessage.byteArrayLength) {
                Log.w(TAG, "Tag capacity too small")
                return false
            }
            ndef.writeNdefMessage(ndefMessage)
            Log.i(TAG, "NFC write successful: $url")
            true
        } catch (e: Exception) {
            Log.e(TAG, "NFC write error", e)
            false
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle NFC tag discovery
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            if (tag != null && nfcWritePending && nfcPendingUrl != null) {
                val url = nfcPendingUrl!!
                val success = writeNdefToTag(url, tag)
                nfcWritePending = false
                nfcPendingUrl = null

                // Notify HTML of write result
                if (success) {
                    runJs("window._nfcOnWriteSuccess()")
                } else {
                    runJs("window._nfcOnWriteFailure('write-failed')")
                }
            }
        }
    }

    // ================================================================
    // JSBridge
    // ================================================================

    inner class AsrBridge {

        @JavascriptInterface
        fun getEngine(): String = activeEngine

        @JavascriptInterface
        fun getAsrMode(): String = currentAsrMode

        @JavascriptInterface
        fun startListening(lang: String) {
            runOnUiThread {
                lastLang = lang
                Log.i(TAG, "JSBridge startListening, engine=$activeEngine, mode=$currentAsrMode, lang=$lang")
                when (activeEngine) {
                    "sunmi" -> {
                        if (sunmiSpeechReady) sunmiStartListening(lang)
                        else {
                            Log.w(TAG, "SUNMI not ready, falling back to Android")
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
        fun isSunmiReady(): Boolean = sunmiSpeechReady

        @JavascriptInterface
        fun isAndroidAvailable(): Boolean =
            SpeechRecognizer.isRecognitionAvailable(this@MainActivity)

        /** HTML reports ASR no-match — counts towards LOCAL→WEB fallback */
        @JavascriptInterface
        fun reportNoMatch() {
            if (currentAsrMode == "local" && !localDisabled) {
                localEmptyCount++
                Log.w(TAG, "LOCAL no-match reported by HTML, count=$localEmptyCount")
                if (localEmptyCount >= LOCAL_EMPTY_THRESHOLD) {
                    switchToWebMode()
                }
            }
        }
    }

    inner class NfcBridge {
        @JavascriptInterface
        fun isNfcAvailable(): Boolean = nfcEnabled && nfcAdapter?.isEnabled == true

        @JavascriptInterface
        fun prepareWrite(drinkId: String) {
            runOnUiThread {
                val url = "https://xinyineee.github.io/langxiancheng-kiosk-h5/result?d=$drinkId"
                nfcPendingUrl = url
                nfcWritePending = true
                Log.i(TAG, "NFC write prepared: $url")
                runJs("window._nfcOnReady()")
            }
        }

        @JavascriptInterface
        fun cancelWrite() {
            nfcWritePending = false
            nfcPendingUrl = null
            Log.d(TAG, "NFC write cancelled")
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
