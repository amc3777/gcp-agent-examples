package com.example.geminiassistant.data

import kotlinx.serialization.Serializable // Import Serializable

@Serializable // Add annotation if using kotlinx-serialization
enum class UiAction {
    CLICK,
    SET_TEXT,
    SCROLL_FORWARD // etc.
}