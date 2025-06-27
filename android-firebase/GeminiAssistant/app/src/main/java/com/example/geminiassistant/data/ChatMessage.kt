package com.example.geminiassistant.data

import java.util.UUID // Import needed for UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean
)