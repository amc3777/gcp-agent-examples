package com.example.ragengineliveapiaudio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.atomic.AtomicBoolean

enum class UiState {
    DISCONNECTED,
    CONNECTED,
    RECORDING,
    PROCESSING
}

class MainViewModel : ViewModel() {

    private val serverUrl = "ws://10.0.2.2:8000/ws"

    // Input audio configuration
    private val inputSampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val outputSampleRate = 24000

    private val _uiState = MutableStateFlow(UiState.DISCONNECTED)
    val uiState = _uiState.asStateFlow()

    private val _statusText = MutableStateFlow("Disconnected")
    val statusText = _statusText.asStateFlow()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)

    private val client = OkHttpClient()

    fun connect() {
        if (_uiState.value != UiState.DISCONNECTED) return
        _statusText.value = "Connecting..."
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _uiState.value = UiState.CONNECTED
                _statusText.value = "Connected. Press record to speak."
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "TURN_COMPLETE") {
                    _uiState.value = UiState.CONNECTED
                    _statusText.value = "Response finished. Ready for next turn."
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                playAudioChunk(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _uiState.value = UiState.DISCONNECTED
                _statusText.value = "Connection failed: ${t.message}"
            }
        })
    }

    fun onRecordButtonPressed(context: Context) {
        if (isRecording.get()) {
            stopRecording()
        } else {
            startRecording(context)
        }
    }

    private fun startRecording(context: Context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _statusText.value = "Microphone permission not granted."
            return
        }

        isRecording.set(true)
        _uiState.value = UiState.RECORDING
        _statusText.value = "Recording..."

        val bufferSize = AudioRecord.getMinBufferSize(inputSampleRate, channelConfig, audioFormat)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            _statusText.value = "Recording parameters not supported."
            isRecording.set(false)
            _uiState.value = UiState.CONNECTED
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            inputSampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (isActive && isRecording.get()) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readSize > 0) {
                    webSocket?.send(buffer.toByteString(0, readSize))
                }
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording.getAndSet(false)) return

        _uiState.value = UiState.PROCESSING
        _statusText.value = "Processing response..."

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error stopping AudioRecord", e)
        }

        webSocket?.send("STOP_RECORDING")
    }

    private fun initializeAudioTrack() {

        val minBufferSize = AudioTrack.getMinBufferSize(
            outputSampleRate, // Correct 24kHz sample rate
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = minBufferSize * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(outputSampleRate) // Correct 24kHz sample rate
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize) // Use our larger buffer
            .build()
        audioTrack?.play()
    }

    private fun playAudioChunk(chunk: ByteArray) {
        if (audioTrack == null || audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
            initializeAudioTrack()
        }
        audioTrack?.write(chunk, 0, chunk.size)
    }

    override fun onCleared() {
        super.onCleared()
        webSocket?.close(1000, "ViewModel cleared")
        if(isRecording.get()){
            stopRecording()
        }
        audioTrack?.release()
        client.dispatcher.executorService.shutdown()
    }
}
