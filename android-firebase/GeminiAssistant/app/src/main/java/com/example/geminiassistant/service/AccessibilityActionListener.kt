package com.example.geminiassistant.service

// Define an interface for communication back to the ViewModel/Executor
interface AccessibilityActionListener {
    fun onActionSuccess(stepIndex: Int)
    fun onActionFailure(stepIndex: Int, reason: String)
}