package com.example.geminiassistant.ui

import android.app.Application // Use AndroidViewModel for context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geminiassistant.R
import com.example.geminiassistant.data.ChatMessage
import com.example.geminiassistant.data.ActionType
import com.example.geminiassistant.executor.ActionExecutor
import com.example.geminiassistant.network.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

// --- UI State ---
enum class UiState {
    IDLE, LISTENING, PROCESSING, EXECUTING, ERROR
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState.IDLE)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _chatMessages = mutableStateListOf<ChatMessage>()
    val chatMessages: List<ChatMessage> = _chatMessages

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val actionExecutor = ActionExecutor(application.applicationContext)

    // --- Speech Recognition Setup ---
    init {
        // Initialize Gemini Client (pass credentials securely)
        // Consider using Hilt/DI for this
        // GeminiClient.initialize(PROJECT_ID, LOCATION, MODEL_NAME) // Call from Application or DI

        if (SpeechRecognizer.isRecognitionAvailable(application)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _uiState.value = UiState.LISTENING
                    Log.d("ChatViewModel", "Speech: onReadyForSpeech")
                }
                override fun onBeginningOfSpeech() { Log.d("ChatViewModel", "Speech: onBeginningOfSpeech") }
                override fun onRmsChanged(rmsdB: Float) {} // Ignore
                override fun onBufferReceived(buffer: ByteArray?) {} // Ignore
                override fun onEndOfSpeech() {
                    _uiState.value = UiState.PROCESSING // Start processing after speech ends
                    Log.d("ChatViewModel", "Speech: onEndOfSpeech")
                }
                override fun onError(error: Int) {
                    val errorMsg = getSpeechErrorMessage(error)
                    Log.e("ChatViewModel", "Speech Error: $error - $errorMsg")
                    _errorMessage.value = errorMsg
                    _uiState.value = UiState.ERROR
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val recognizedText = matches[0]
                        Log.i("ChatViewModel", "Speech Result: $recognizedText")
                        addMessage(recognizedText, true) // Add user message
                        processUserQuery(recognizedText) // Send to Gemini
                    } else {
                        Log.w("ChatViewModel", "Speech: No results found.")
                        _errorMessage.value = getApplication<Application>().getString(R.string.error_speech_generic)
                        _uiState.value = UiState.ERROR
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {} // Ignore
                override fun onEvent(eventType: Int, params: Bundle?) {} // Ignore
            })
        } else {
            Log.e("ChatViewModel", "Speech recognition not available")
            _errorMessage.value = getApplication<Application>().getString(R.string.error_speech_unavailable)
            _uiState.value = UiState.ERROR // Set error state if SR not available
        }
    }

    // --- Public Actions ---
    fun startListening() {
        // Check permission first (handled in Activity/Composable)
        if (_uiState.value == UiState.IDLE || _uiState.value == UiState.ERROR) {
            _errorMessage.value = null // Clear previous error
            if (speechRecognizer != null) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, getApplication<Application>().getString(R.string.speak_prompt))
                }
                speechRecognizer?.startListening(intent)
            } else {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_speech_unavailable)
                _uiState.value = UiState.ERROR
            }
        }
    }

    fun stopListening() {
        if (_uiState.value == UiState.LISTENING) {
            speechRecognizer?.stopListening()
            _uiState.value = UiState.PROCESSING // Assume processing after manual stop
        }
    }

    fun clearError() {
        _errorMessage.value = null
        if (_uiState.value == UiState.ERROR) {
            _uiState.value = UiState.IDLE
        }
    }

    // --- Internal Logic ---
    private fun processUserQuery(query: String) {
        viewModelScope.launch {
            _uiState.value = UiState.PROCESSING
            val result = GeminiClient.getActionPlan(query) // Call Gemini

            result.fold(
                onSuccess = { response ->
                    Log.i("ChatViewModel", "Gemini Response: ${response.confirmationMessage}")
                    // Add confirmation message from Gemini before executing
                    if (!response.confirmationMessage.isNullOrBlank()) {
                        addMessage(response.confirmationMessage, false)
                    }

                    if (response.actions.isNotEmpty() && response.actions.first().type != ActionType.UNSUPPORTED) {
                        _uiState.value = UiState.EXECUTING
                        addMessage(getApplication<Application>().getString(R.string.status_executing_action), false) // Generic executing message
                        val executionResult = actionExecutor.executeActions(response.actions) // Execute actions

                        executionResult.fold(
                            onSuccess = { finalMsg ->
                                Log.i("ChatViewModel", "Action Execution Success: $finalMsg")
                                // Update message or add new one based on execution result
                                // Maybe replace "Executing..." message
                                _chatMessages.removeLastOrNull() // Remove "Executing..."
                                addMessage(finalMsg, false) // Add final status
                                _uiState.value = UiState.IDLE
                            },
                            onFailure = { error ->
                                Log.e("ChatViewModel", "Action Execution Failed: ${error.message}")
                                _chatMessages.removeLastOrNull() // Remove "Executing..."
                                _errorMessage.value = error.message ?: getApplication<Application>().getString(R.string.error_action_failed)
                                _uiState.value = UiState.ERROR
                            }
                        )
                    } else {
                        // Handle unsupported action or empty actions
                        if (!response.confirmationMessage.isNullOrBlank()) {
                            // Already added confirmation, just go back to idle
                        } else {
                            addMessage("Sorry, I can't do that.", false) // Default unsupported message
                        }
                        _uiState.value = UiState.IDLE
                    }
                },
                onFailure = { error ->
                    Log.e("ChatViewModel", "Gemini Request Failed: ${error.message}")
                    _errorMessage.value = error.message ?: getApplication<Application>().getString(R.string.error_gemini_request)
                    _uiState.value = UiState.ERROR
                }
            )
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        _chatMessages.add(ChatMessage(text = text, isUser = isUser))
    }

    private fun getSpeechErrorMessage(error: Int): String {
        val context = getApplication<Application>()
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
            SpeechRecognizer.ERROR_CLIENT -> "Client side error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.error_permission_record_audio)
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.error_speech_network)
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy."
            SpeechRecognizer.ERROR_SERVER -> "Server error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input."
            else -> context.getString(R.string.error_speech_generic)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d("ChatViewModel", "ViewModel Cleared, SpeechRecognizer destroyed.")
    }
}