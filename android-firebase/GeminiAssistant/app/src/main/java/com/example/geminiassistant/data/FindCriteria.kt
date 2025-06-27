package com.example.geminiassistant.data

import kotlinx.serialization.Serializable // Import Serializable

@Serializable // Add annotation if using kotlinx-serialization
data class FindCriteria(
    val text: String? = null,
    val resourceId: String? = null,
    val contentDesc: String? = null
)