package com.example.geminiassistant.data

import kotlinx.serialization.Serializable // Import Serializable

@Serializable // Add annotation if using kotlinx-serialization
data class GeminiActionResponse(
    val taskId: String,
    val actions: List<ActionStep>, // Assumes ActionStep is in the same package or imported
    val confirmationMessage: String?
)