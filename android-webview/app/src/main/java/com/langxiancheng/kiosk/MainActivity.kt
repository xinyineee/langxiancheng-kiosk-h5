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
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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
 * WebView shell for LangXianCheng Kiosk v2.3
 * ASR: SUNMI Voice SDK (primary) + Android SpeechRecognizer (fallback)
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LXCKiosk"
        private const val MIC_REQUEST = 1001
    }

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())

    // ---- SUNMI Voice SDK state ----
    private var sunmiBaseReady = false
    private var sunmiSpeechReady = false
    private var sunmiAsrActive = false

    // ---- Android SpeechRecognizer fallback ----
    private var androidRecognizer: SpeechRecognizer? = null
    private var androidListening = false

    /** Which ASR engine is active: "sunmi" | "android" | "none" */
    private var activeEngine = "none"

    // ================================================================
    // Lifecycle
    // ================================================================

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean = false
        }

        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(AsrBridge(), "AndroidASR")
        webView.loadUrl("file:///android_asset/www/index.html")

        // Request mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
        }

        // Init SUNMI SDK
        initSunmiSDK()
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
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        stopAllAsr()
    }

    override fun onDestroy() {
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
            val config = InitialConfig("", "", "").apply {
                asrMode = SmAsrMode.WEB  // use WEB mode (LOCAL produces empty results on this device)
            }
            SmSpeechSDK.getInstance().initialize(this, config,
                object : ISpeechServiceStateListener {
                    override fun onInitSuccess() {
                        Log.i(TAG, "SUNMI speech SDK init success (reserved for future use)")
                        sunmiSpeechReady = true
                        // Still use Android SpeechRecognizer as primary engine
                        // SUNMI SDK reserved for when LOCAL mode works on this device
                        activeEngine = "android"
                        handler.post {
                            runJs("window._asrOnEngineReady('android', '')")
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
            // wakeUpCallback = null → skip wake-up, start ASR directly
            SmSpeechSDK.getInstance().startSemanticRecognizer(
                15000L,  // 15s timeout
                null as ISpeechSessionResultCallback.IWakeUpCallback?,
                object : ISpeechSessionResultCallback.IStreamingSpeechRecognitionCallback {
                    override fun onPartialResult(partialText: String, direction: Int, translate: Boolean) {
                        runJs("window._asrOnPartial(['${partialText.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")}'])")
                    }
                    override fun onAsrFinalResult(finalText: String, direction: Int, translate: Boolean) {
                        val json = "[\"${finalText.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")}\"]"
                        runJs("window._asrOnResult($json)")
                    }
                },
                object : ISpeechSessionResultCallback {
                    override fun onContinuationRequired(
                        currentResult: String?,
                        promptText: String?,
                        callback: IContinuationRequestCallback?
                    ) { /* not needed for kiosk */ }
                    override fun onSemanticResult(
                        nluResult: String?,
                        callback: IContinuationRequestCallback?
                    ) { /* ignore semantic results, only care about ASR text */ }
                    override fun onError(
                        errorCode: String?,
                        callback: IContinuationRequestCallback?
                    ) {
                        Log.w(TAG, "SUNMI ASR error: $errorCode")
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
                    override fun onSessionReady() { Log.d(TAG, "SUNMI session ready") }
                    override fun onWakeUpReady() {}
                    override fun onCaptureStarted() { runJs("window._asrOnStart()") }
                    override fun onCapturePaused() {}
                    override fun onCaptureResumed() {}
                    override fun onSemanticProcessingStarted() {}
                    override fun onSemanticProcessingIncomplete() {}
                    override fun onSemanticProcessingCompleted() {}
                    override fun onSessionEnded() {
                        sunmiAsrActive = false
                        runJs("window._asrOnStop()")
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
        sunmiStopListening()
        androidStopListening()
        // Don't reset activeEngine here — only reset on explicit destroy
    }

    // ================================================================
    // JSBridge
    // ================================================================

    inner class AsrBridge {

        @JavascriptInterface
        fun getEngine(): String = activeEngine

        @JavascriptInterface
        fun startListening(lang: String) {
            runOnUiThread {
                Log.i(TAG, "JSBridge startListening, engine=$activeEngine, lang=$lang")
                when (activeEngine) {
                    "sunmi" -> sunmiStartListening(lang)
                    "android" -> androidStartListening(lang)
                    else -> runJs("window._asrOnError('no-engine-ready')")
                }
            }
        }

        @JavascriptInterface
        fun stopListening() {
            runOnUiThread {
                stopAllAsr()
                runJs("window._asrOnStop()")
            }
        }

        @JavascriptInterface
        fun isSunmiReady(): Boolean = sunmiSpeechReady

        @JavascriptInterface
        fun isAndroidAvailable(): Boolean =
            SpeechRecognizer.isRecognitionAvailable(this@MainActivity)
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
