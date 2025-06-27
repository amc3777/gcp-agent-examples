package com.example.geminiassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geminiassistant.network.GeminiClient // Import your client
import com.example.geminiassistant.ui.ChatScreen
import com.example.geminiassistant.ui.ChatViewModel
import com.example.geminiassistant.ui.theme.GeminiAssistantTheme
import com.example.geminiassistant.service.AssistantAccessibilityService // Import service

// --- Securely load credentials ---
// Option 1: Use BuildConfig (requires setup in build.gradle)
// const val GEMINI_PROJECT_ID = BuildConfig.GEMINI_PROJECT_ID
// const val GEMINI_LOCATION = BuildConfig.GEMINI_LOCATION
// const val GEMINI_MODEL_NAME = BuildConfig.GEMINI_MODEL_NAME

// Option 2: Placeholder (replace with secure loading)
const val GEMINI_PROJECT_ID = "andrewcooley-genai-tests" // REPLACE
const val GEMINI_LOCATION = "us-central1"       // REPLACE
const val GEMINI_MODEL_NAME = "gemini-2.0-flash-001" // REPLACE

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Initialize Gemini Client Securely ---
        // Do this ONCE, ideally in your Application class or using DI
        // Ensure you handle potential initialization errors
        try {
            GeminiClient.initialize(GEMINI_MODEL_NAME)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize Gemini Client", e)
            // Handle critical initialization failure (e.g., show error message and exit)
        }


        setContent {
            GeminiAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AssistantApp()
                }
            }
        }
    }
}

@Composable
fun AssistantApp(viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current

    // --- Permission Handling ---
    var hasRecordAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasRecordAudioPermission = isGranted
            if (!isGranted) {
                // Optionally show a message explaining why permission is needed
                Log.w("AssistantApp", "Record Audio permission denied.")
            }
        }
    )
    // Request permission when needed
    LaunchedEffect(key1 = Unit) { // Request on first composition if not granted
        if (!hasRecordAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // --- Accessibility Service Check ---
    var isAccessibilityEnabled by remember { mutableStateOf(false) } // Initial check needed
    LaunchedEffect(Unit) { // Check periodically or on resume if needed
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
    }


    ChatScreen(
        viewModel = viewModel,
        hasRecordAudioPermission = hasRecordAudioPermission,
        isAccessibilityEnabled = isAccessibilityEnabled,
        onRequestRecordAudioPermission = {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onRequestAccessibilityPermission = {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        }
    )
}

// Helper function to check accessibility status (duplicate from executor, consider utils class)
fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val serviceId = "${context.packageName}/${AssistantAccessibilityService::class.java.canonicalName}"
    try {
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServicesSetting?.contains(serviceId) == true
    } catch (e: Exception) {
        Log.e("AssistantApp", "Error checking accessibility service status", e)
        return false
    }
}