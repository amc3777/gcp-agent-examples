package com.example.geminiassistant.data

import kotlinx.serialization.Serializable // Import Serializable

@Serializable // Add annotation if using kotlinx-serialization
enum class ActionType {
    LAUNCH_APP_WITH_URI,
    UI_INTERACTION,
    SPEAK_RESPONSE,
    UNSUPPORTED
}