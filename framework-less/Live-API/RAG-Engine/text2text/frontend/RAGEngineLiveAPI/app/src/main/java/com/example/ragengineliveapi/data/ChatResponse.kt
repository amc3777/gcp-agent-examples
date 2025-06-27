package com.example.ragengineliveapi.data

data class ChatResponse(
    val response: String,
    val history: List<Turn>
)