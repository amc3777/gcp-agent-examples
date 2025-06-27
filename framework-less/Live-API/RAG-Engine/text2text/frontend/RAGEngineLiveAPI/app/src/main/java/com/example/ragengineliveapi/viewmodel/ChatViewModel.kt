package com.example.ragengineliveapi.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.ragengineliveapi.data.ChatRequest
import com.example.ragengineliveapi.data.Part
import com.example.ragengineliveapi.data.Turn
import com.example.ragengineliveapi.network.RetrofitClient

class ChatViewModel : ViewModel() {

    private val _chatHistory = MutableLiveData<List<Turn>>()
    val chatHistory: LiveData<List<Turn>> = _chatHistory

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    init {
        // Initialize with an empty history or a welcome message
        _chatHistory.value = emptyList()
    }

    fun sendMessage(message: String) {
        val currentHistory = _chatHistory.value.orEmpty().toMutableList()
        currentHistory.add(Turn("user", listOf(Part(message))))

        viewModelScope.launch {
            try {
                val request = ChatRequest(history = currentHistory)
                val response = RetrofitClient.instance.sendChat(request)

                if (response.isSuccessful) {
                    _chatHistory.postValue(response.body()?.history)
                } else {
                    _errorMessage.postValue("Error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                _errorMessage.postValue("Failed to connect to the server: ${e.message}")
            }
        }
    }
}