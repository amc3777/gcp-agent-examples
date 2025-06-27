package com.example.ragengineliveapi.network

import com.example.ragengineliveapi.data.ChatRequest
import com.example.ragengineliveapi.data.ChatResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("chat")
    suspend fun sendChat(@Body chatRequest: ChatRequest): Response<ChatResponse>
}