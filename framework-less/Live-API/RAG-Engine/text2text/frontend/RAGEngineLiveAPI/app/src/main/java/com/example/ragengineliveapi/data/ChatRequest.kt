package com.example.ragengineliveapi.data

data class ChatRequest(
    val history: List<Turn>
)

data class Turn(
    val role: String,
    val parts: List<Part>
)

data class Part(
    val text: String
)