package com.example.geminiassistant.data

import kotlinx.serialization.Serializable // Import Serializable

@Serializable // Add annotation if using kotlinx-serialization
data class ActionStep(
    val type: ActionType, // Assumes ActionType is in the same package or imported
    val targetAppPackage: String? = null,
    val uri: String? = null,
    val extras: Map<String, String>? = null,
    val uiSteps: List<UiStep>? = null // Assumes UiStep is in the same package or imported
)