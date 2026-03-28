package com.example.planreminder.agent

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

// 负责把麦克风 PCM 数据实时发送到 DashScope 语音识别服务。
class DashScopeRealtimeSpeechClient {
    interface Listener {
        fun onRecordingStarted()
        fun onRecordingStopped()
        fun onPartialTranscript(text: String)
        fun onFinalTranscript(text: String)
        fun onError(message: String)
    }

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var listener: Listener? = null
    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var isStopping = false

    private var finalTranscript = ""

    fun start(
        settings: AgentSettings,
        listener: Listener,
    ) {
        if (webSocket != null || isRecording || isStopping) {
            listener.onError("语音识别正在进行中，请稍候。")
            return
        }

        this.listener = listener
        finalTranscript = ""
        isStopping = false

        val request = Request.Builder()
            .url(realtimeUrl(settings.baseUrl))
            .header("Authorization", "Bearer ${settings.apiKey}")
            .build()

        webSocket = httpClient.newWebSocket(request, createWebSocketListener())
    }

    fun stop() {
        if (webSocket == null && !isRecording) {
            return
        }

        isStopping = true
        stopRecordingInternal(notify = true)
        webSocket?.send(simpleEvent("input_audio_buffer.commit"))
        webSocket?.send(simpleEvent("session.finish"))
    }

    fun cancel() {
        isStopping = false
        stopRecordingInternal(notify = false)
        webSocket?.cancel()
        webSocket = null
        listener = null
        finalTranscript = ""
    }

    fun release() {
        cancel()
        scope.cancel()
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrentSocket(webSocket)) {
                    webSocket.cancel()
                    return
                }

                webSocket.send(sessionUpdateEvent())
                startRecordingInternal(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrentSocket(webSocket)) {
                    return
                }

                handleServerMessage(text)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                if (!isCurrentSocket(webSocket)) {
                    return
                }

                this@DashScopeRealtimeSpeechClient.webSocket = null
                reportError(t.message ?: "语音识别连接失败，请检查网络和接口配置。")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrentSocket(webSocket)) {
                    return
                }

                stopRecordingInternal(notify = false)
                this@DashScopeRealtimeSpeechClient.webSocket = null

                if (isStopping && finalTranscript.isBlank()) {
                    reportError("没有识别到清晰的语音内容，请再试一次。")
                } else {
                    listener = null
                }

                isStopping = false
                finalTranscript = ""
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecordingInternal(webSocket: WebSocket) {
        val recorder = createAudioRecord()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            reportError("初始化麦克风失败，请检查麦克风权限或设备状态。")
            return
        }

        audioRecord = recorder
        recordingJob = scope.launch {
            runCatching {
                recorder.startRecording()
                isRecording = true
                listener?.onRecordingStarted()

                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                while (isRecording) {
                    val bytesRead = recorder.read(buffer, 0, buffer.size)
                    if (!isRecording) break

                    when {
                        bytesRead > 0 -> {
                            val audioBase64 = Base64.encodeToString(
                                buffer,
                                0,
                                bytesRead,
                                Base64.NO_WRAP,
                            )
                            webSocket.send(
                                JSONObject()
                                    .put("event_id", UUID.randomUUID().toString())
                                    .put("type", "input_audio_buffer.append")
                                    .put("audio", audioBase64)
                                    .toString(),
                            )
                        }

                        bytesRead < 0 -> {
                            throw IOException("读取麦克风失败：$bytesRead")
                        }
                    }
                }
            }.onFailure { error ->
                reportError(error.message ?: "录音失败，请稍后重试。")
            }
        }
    }

    private fun stopRecordingInternal(notify: Boolean) {
        if (!isRecording && audioRecord == null) {
            return
        }

        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.run {
            runCatching {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
            }
            release()
        }
        audioRecord = null

        if (notify) {
            listener?.onRecordingStopped()
        }
    }

    private fun handleServerMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "conversation.item.input_audio_transcription.text" -> {
                json.optString("text")
                    .takeIf { it.isNotBlank() }
                    ?.let { listener?.onPartialTranscript(it) }
            }

            "conversation.item.input_audio_transcription.completed" -> {
                finalTranscript = json.optString("transcript").trim()
                if (finalTranscript.isNotBlank()) {
                    listener?.onFinalTranscript(finalTranscript)
                }
            }

            "conversation.item.input_audio_transcription.failed" -> {
                val message = json.optJSONObject("error")
                    ?.optString("message")
                    .orEmpty()
                    .ifBlank { "语音转写失败，请稍后重试。" }
                reportError(message)
            }

            "error" -> {
                val message = json.optJSONObject("error")
                    ?.optString("message")
                    .orEmpty()
                    .ifBlank { json.optString("message") }
                    .ifBlank { "语音识别请求失败，请检查接口配置。" }
                reportError(message)
            }
        }
    }

    private fun reportError(message: String) {
        val currentSocket = webSocket
        webSocket = null

        stopRecordingInternal(notify = false)
        isStopping = false
        finalTranscript = ""

        listener?.onError(message)
        listener = null

        currentSocket?.cancel()
    }

    private fun isCurrentSocket(candidate: WebSocket): Boolean {
        return webSocket === candidate
    }

    private fun createAudioRecord(): AudioRecord {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(CHUNK_SIZE_BYTES * 2)

        return AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBufferSize)
            .build()
    }

    private fun sessionUpdateEvent(): String {
        return JSONObject()
            .put("event_id", UUID.randomUUID().toString())
            .put("type", "session.update")
            .put(
                "session",
                JSONObject()
                    .put("input_audio_format", "pcm")
                    .put("sample_rate", SAMPLE_RATE_HZ)
                    .put(
                        "input_audio_transcription",
                        JSONObject().put("language", "zh"),
                    )
                    .put("turn_detection", JSONObject.NULL),
            )
            .toString()
    }

    private fun simpleEvent(type: String): String {
        return JSONObject()
            .put("event_id", UUID.randomUUID().toString())
            .put("type", type)
            .toString()
    }

    private fun realtimeUrl(baseUrl: String): String {
        val host = if (baseUrl.contains("dashscope-intl.aliyuncs.com", ignoreCase = true)) {
            "dashscope-intl.aliyuncs.com"
        } else {
            "dashscope.aliyuncs.com"
        }
        return "wss://$host/api-ws/v1/realtime?model=$DEFAULT_REALTIME_MODEL"
    }

    companion object {
        private const val SAMPLE_RATE_HZ = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val CHUNK_DURATION_MILLIS = 40
        private const val CHUNK_SIZE_BYTES =
            SAMPLE_RATE_HZ * BYTES_PER_SAMPLE * CHUNK_DURATION_MILLIS / 1_000
        private const val DEFAULT_REALTIME_MODEL = "qwen3-asr-flash-realtime"
    }
}
