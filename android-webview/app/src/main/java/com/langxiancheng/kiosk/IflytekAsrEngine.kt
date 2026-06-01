package com.langxiancheng.kiosk

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * iFlytek IAT (语音听写) ASR Engine — WebSocket-based real-time speech recognition.
 *
 * Protocol: wss://iat-api.xfyun.cn/v2/iat
 * Audio: 16kHz, 16bit, mono PCM (L16;rate=16000)
 * Auth: HMAC-SHA256 signature in URL query string
 *
 * Replaces SUNMI Voice SDK with iFlytek cloud ASR for better Chinese recognition.
 */
class IflytekAsrEngine(
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val callback: Callback
) {
    companion object {
        private const val TAG = "LXCKiosk-IFLYTEK"
        private const val HOST = "iat-api.xfyun.cn"
        private const val PATH = "/v2/iat"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 1280  // 40ms of 16kHz 16bit mono PCM
        private const val SEND_INTERVAL_MS = 40L
        private const val CONNECT_TIMEOUT_SEC = 10L
        private const val MAX_AUDIO_SECONDS = 60
    }

    interface Callback {
        fun onStart()
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(error: String)
        fun onStop()
    }

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private val audioQueue = LinkedBlockingQueue<ByteArray>(256)
    private val executor: ExecutorService = Executors.newFixedThreadPool(3)  // ws send, audio read, main

    // Dynamic correction (dwa=wpgs) state
    private val resultHistory = mutableMapOf<Int, String>()

    /**
     * Build the authenticated WebSocket URL with HMAC-SHA256 signature.
     */
    private fun buildAuthUrl(): String {
        val date = generateRfc1123Date()
        val signatureOrigin = "host: $HOST\ndate: $date\nGET $PATH HTTP/1.1"
        val signature = hmacSha256(apiSecret, signatureOrigin)
        val authorizationOrigin = "api_key=\"$apiKey\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signature\""
        val authorization = Base64.encodeToString(authorizationOrigin.toByteArray(), Base64.NO_WRAP)

        return "wss://$HOST$PATH?authorization=$authorization&date=${java.net.URLEncoder.encode(date, "UTF-8")}&host=$HOST"
    }

    private fun generateRfc1123Date(): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        return sdf.format(Date())
    }

    private fun hmacSha256(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Start ASR session: connect WebSocket + start audio recording.
     */
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening, ignoring startListening")
            return
        }
        isListening = true
        resultHistory.clear()

        val url = buildAuthUrl()
        Log.i(TAG, "Connecting to iFlytek IAT: $url")
        Log.i(TAG, "Auth debug: appId=$appId apiKey=${apiKey.take(8)}... apiSecret=${apiSecret.take(8)}... secretLen=${apiSecret.length}")

        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES)  // no read timeout for streaming
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)  // keepalive
            .build()

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected, starting audio capture")
                callback.onStart()
                startAudioRecording()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // iFlytek always sends text frames, ignore binary
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                stopListening()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = response?.let { "HTTP ${it.code}: ${it.message}" } ?: t.message ?: "unknown"
                Log.e(TAG, "WebSocket failure: $msg", t)
                if (isListening) {
                    callback.onError("iflytek-connection: $msg")
                    stopListening()
                }
            }
        })
    }

    /**
     * Stop ASR session: stop audio, send final frame, close WebSocket.
     */
    fun stopListening() {
        if (!isListening) return
        isListening = false

        // Stop audio recording first
        stopAudioRecording()

        // Send final frame (status=2)
        try {
            val finalFrame = JSONObject().apply {
                put("data", JSONObject().apply {
                    put("status", 2)
                    put("format", "audio/L16;rate=16000")
                    put("encoding", "raw")
                    put("audio", "")
                })
            }
            val sent = webSocket?.send(finalFrame.toString()) ?: false
            Log.d(TAG, "Sent final frame (status=2), result=$sent")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send final frame", e)
        }

        // Close WebSocket
        try {
            webSocket?.close(1000, "session ended")
        } catch (_: Exception) {}
        webSocket = null

        callback.onStop()
    }

    /**
     * Parse and handle JSON messages from iFlytek server.
     */
    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)
            val code = json.optInt("code", -1)
            if (code != 0) {
                val message = json.optString("message", "unknown error")
                Log.w(TAG, "iFlytek error: code=$code message=$message")
                callback.onError("iflytek-$code: $message")
                return
            }

            val data = json.optJSONObject("data") ?: return
            val result = data.optJSONObject("result") ?: return
            val status = data.optInt("status", 0)  // 0=first, 1=middle, 2=last
            val sn = result.optInt("sn", 0)
            val ls = result.optBoolean("ls", false)

            // Extract text from ws[].cw[].w
            val sb = StringBuilder()
            val ws = result.optJSONArray("ws") ?: return
            for (i in 0 until ws.length()) {
                val word = ws.getJSONObject(i)
                val cw = word.optJSONArray("cw") ?: continue
                if (cw.length() > 0) {
                    sb.append(cw.getJSONObject(0).optString("w", ""))
                }
            }
            val recognizedText = sb.toString()

            // Dynamic correction support (dwa=wpgs)
            val pgs = result.optString("pgs", "")
            val rg = result.optJSONArray("rg")

            if (pgs == "rpl" && rg != null && rg.length() >= 2) {
                // Replace range [rg[0], rg[1]] in history
                val from = rg.getInt(0)
                val to = rg.getInt(1)
                for (i in from..to) {
                    resultHistory.remove(i)
                }
                resultHistory[sn] = recognizedText
            } else {
                resultHistory[sn] = recognizedText
            }

            // Build current full text from history (ordered by sn)
            val fullText = resultHistory.toSortedMap().values.joinToString("")

            if (status == 2 || ls) {
                // Final result
                Log.i(TAG, "iFlytek FINAL: $fullText")
                callback.onResult(fullText)
            } else {
                // Partial result
                Log.d(TAG, "iFlytek partial: $fullText")
                callback.onPartial(fullText)
            }

            // If this is the last result, schedule stop
            if (status == 2) {
                Log.i(TAG, "Server sent status=2, session complete")
                Thread { Thread.sleep(200); stopListening() }.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse iFlytek message: $text", e)
        }
    }

    /**
     * Start recording audio from microphone and feeding it to WebSocket.
     */
    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            callback.onError("audio-record: cannot get buffer size")
            stopListening()
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            audioRecord?.startRecording()

            // Audio read thread — reads PCM chunks and puts them in the queue
            executor.submit {
                val buffer = ByteArray(FRAME_SIZE)
                var totalSent = 0
                val maxBytes = MAX_AUDIO_SECONDS * SAMPLE_RATE * 2  // 16bit = 2 bytes

                while (isListening && totalSent < maxBytes) {
                    val read = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
                    if (read <= 0) continue

                    if (!audioQueue.offer(buffer.copyOf())) {
                        Log.w(TAG, "Audio queue full, dropping frame")
                    }
                    totalSent += read
                }

                if (totalSent >= maxBytes) {
                    Log.w(TAG, "Reached ${MAX_AUDIO_SECONDS}s audio limit, stopping")
                    stopListening()
                }
            }

            // WebSocket send thread — takes frames from queue and sends them
            executor.submit {
                // Send first frame with common+business+data
                val firstAudio = audioQueue.poll(2, TimeUnit.SECONDS)
                if (firstAudio == null) {
                    Log.w(TAG, "No audio data received, aborting")
                    stopListening()
                    return@submit
                }

                val firstFrame = JSONObject().apply {
                    put("common", JSONObject().apply { put("app_id", appId) })
                    put("business", JSONObject().apply {
                        put("language", "zh_cn")
                        put("domain", "iat")
                        put("accent", "mandarin")
                        put("ptt", 1)       // add punctuation
                        // Note: dwa and nbest removed — they may cause 10165 on some accounts
                    })
                    put("data", JSONObject().apply {
                        put("status", 0)
                        put("format", "audio/L16;rate=16000")
                        put("encoding", "raw")
                        put("audio", Base64.encodeToString(firstAudio, Base64.NO_WRAP))
                    })
                }
                val sent = webSocket?.send(firstFrame.toString()) ?: false
                if (!sent) {
                    Log.e(TAG, "Failed to send first audio frame")
                    stopListening()
                    return@submit
                }
                Log.d(TAG, "Sent first audio frame (${firstAudio.size} bytes)")

                // Send middle frames (status=1)
                while (isListening) {
                    val audioData = audioQueue.poll(SEND_INTERVAL_MS + 10, TimeUnit.MILLISECONDS)
                        ?: continue  // queue empty, wait more

                    if (!isListening) break

                    val midFrame = JSONObject().apply {
                        put("data", JSONObject().apply {
                            put("status", 1)
                            put("format", "audio/L16;rate=16000")
                            put("encoding", "raw")
                            put("audio", Base64.encodeToString(audioData, Base64.NO_WRAP))
                        })
                    }
                    webSocket?.send(midFrame.toString())
                }
            }

        } catch (e: SecurityException) {
            callback.onError("audio-permission: microphone access denied")
            stopListening()
        } catch (e: Exception) {
            callback.onError("audio-record: ${e.message}")
            stopListening()
        }
    }

    /**
     * Stop audio recording and release resources.
     */
    private fun stopAudioRecording() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        audioQueue.clear()
    }

    /**
     * Release all resources. Call when activity is destroyed.
     */
    fun destroy() {
        stopListening()
        executor.shutdown()
        try { executor.awaitTermination(3, TimeUnit.SECONDS) } catch (_: Exception) {}
    }
}
