package com.example.geminiassistant.data

import kotlinx.serialization.Serializable // Import Serializable

@Serializable // Add annotation if using kotlinx-serialization
data class UiStep(
    val findBy: FindCriteria, // Assumes FindCriteria is in the same package or imported
    val action: UiAction,     // Assumes UiAction is in the same package or imported
    val value: String? = null,
    val index: Int = 0
)