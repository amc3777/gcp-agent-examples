package com.example.geminiassistant.executor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.geminiassistant.R
import com.example.geminiassistant.data.ActionStep
import com.example.geminiassistant.data.ActionType
import com.example.geminiassistant.service.AccessibilityActionListener
import com.example.geminiassistant.service.AssistantAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ActionExecutor(private val context: Context) {

    private val TAG = "ActionExecutor"

    // --- Main execution function ---
    suspend fun executeActions(actions: List<ActionStep>): Result<String> {
        var finalConfirmation = "Action sequence initiated."
        for ((index, action) in actions.withIndex()) {
            Log.d(TAG, "Executing action ${index + 1}/${actions.size}: ${action.type}")
            val result = when (action.type) {
                ActionType.LAUNCH_APP_WITH_URI -> executeLaunchUri(action)
                ActionType.UI_INTERACTION -> executeUiInteraction(action)
                ActionType.SPEAK_RESPONSE -> Result.success("Showing confirmation.") // Handled by ViewModel
                ActionType.UNSUPPORTED -> Result.failure(Exception("Action type UNSUPPORTED received from Gemini."))
            }

            if (result.isFailure) {
                val errorMsg = "Failed at step ${index + 1} (${action.type}): ${result.exceptionOrNull()?.message}"
                Log.e(TAG, errorMsg)
                return Result.failure(Exception(errorMsg)) // Stop execution on failure
            } else {
                Log.i(TAG, "Step ${index + 1} successful: ${result.getOrNull()}")
                finalConfirmation = result.getOrNull() ?: finalConfirmation // Update confirmation
            }
        }
        return Result.success(finalConfirmation) // Return confirmation from last successful step
    }

    // --- Execute Standard Intents ---
    private fun executeLaunchUri(action: ActionStep): Result<String> {
        val uriString = action.uri
        val targetPackage = action.targetAppPackage

        Log.d(TAG, "executeLaunchUri: uriString='${uriString}', targetPackage='${targetPackage}'")
        val isUriMissing = uriString.isNullOrEmpty() // Simpler check for null or empty ""
        val isPackagePresent = !targetPackage.isNullOrBlank() // Keep this check
        Log.d(TAG, "executeLaunchUri: uriString='${uriString}', targetPackage='${targetPackage}'")
        Log.d(TAG, "executeLaunchUri: isUriMissing=$isUriMissing, isPackagePresent=$isPackagePresent")

        if (isUriMissing && isPackagePresent) {
            Log.i(TAG, "CONDITION MET: Attempting simple package launch for: $targetPackage")
            return try {
                // Use !! assertion - we know targetPackage is not null/blank here
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage!!)
                if (launchIntent != null) {
                    // It's generally safer to remove categories added for web URIs
                    // if we fall back to a simple package launch.
                    launchIntent.removeCategory(Intent.CATEGORY_BROWSABLE)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Result.success("Launched application: $targetPackage")
                } else {
                    val errorMsg = "Could not get launch intent for package: $targetPackage"
                    Log.e(TAG, errorMsg)
                    showToast(context.getString(R.string.error_app_not_found))
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error launching package: $targetPackage", e)
                Result.failure(e)
            }
        }

        Log.w(TAG, "CONDITION NOT MET: Proceeding to URI-based launch attempt.")

        if (uriString.isNullOrEmpty()) {
            Log.e(TAG,"Error: URI is null or empty.")
            // Return specific error or fallback? For now, failure.
            return Result.failure(IllegalArgumentException("Cannot launch URI-based intent with null or empty URI."))
        }

        return try {
            Log.d(TAG,"Attempting URI-based launch with URI: $uriString")
            val intent = Intent()
            val uri = Uri.parse(uriString)

            // Determine action based on scheme
            when (uri.scheme) {
                "smsto" -> intent.action = Intent.ACTION_SENDTO
                "geo", "google.navigation" -> intent.action = Intent.ACTION_VIEW
                "http", "https" -> {
                    intent.action = Intent.ACTION_VIEW
                    // --- ADD THIS CATEGORY for web links ---
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    // --- End of added category ---
                }
                else -> intent.action = Intent.ACTION_VIEW // Default assumption
            }
            intent.data = uri // Set the data URI

            // Add extras (e.g., for SMS)
            action.extras?.forEach { (key, value) ->
                intent.putExtra(key, value)
            }

            // Set package explicitly if known (e.g., for Maps)
            if ((uri.scheme == "geo" || uri.scheme == "google.navigation") && isPackagePresent) {
                intent.setPackage(targetPackage!!) // Use non-null assertion here too
            } else if (uri.scheme == "geo" || uri.scheme == "google.navigation") {
                intent.setPackage("com.google.android.apps.maps")
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Verify intent can be resolved
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                context.startActivity(intent)
                Result.success("Launched action for URI: $uriString")
            } else {
                val errorMsg = "No app found to handle action for URI: $uriString (Intent: $intent)" // Log intent details
                Log.e(TAG, errorMsg)
                showToast(context.getString(R.string.error_app_not_found))
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing LAUNCH_APP_WITH_URI: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- Execute UI Interactions via Accessibility Service ---
    private suspend fun executeUiInteraction(action: ActionStep): Result<String> {
        val targetPackage = action.targetAppPackage
        val uiSteps = action.uiSteps
        if (targetPackage.isNullOrBlank() || uiSteps.isNullOrEmpty()) {
            return Result.failure(IllegalArgumentException("targetAppPackage or uiSteps missing for UI_INTERACTION"))
        }

        // 1. Check if Accessibility Service is enabled
        if (!isAccessibilityServiceEnabled()) {
            // Show toast and potentially guide user
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, context.getString(R.string.error_permission_accessibility), Toast.LENGTH_LONG).show()
                // Optionally: context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return Result.failure(SecurityException("Accessibility Service not enabled."))
        }

        // 2. Communicate with the service (using static instance for simplicity - BINDING IS BETTER)
        val serviceInstance = AssistantAccessibilityService.instance
        if (serviceInstance == null) {
            Log.e(TAG, "Accessibility Service instance is null. Is it running?")
            return Result.failure(IllegalStateException("Accessibility Service not running or connected."))
        }

        // 3. Use suspendCancellableCoroutine to bridge callback to coroutine
        return suspendCancellableCoroutine { continuation ->
            val listener = object : AccessibilityActionListener {
                override fun onActionSuccess(stepIndex: Int) {
                    Log.d(TAG, "UI Step $stepIndex succeeded via Service.")
                    // Only resume continuation when ALL steps are done (logic inside service)
                    if (stepIndex == uiSteps.size - 1) {
                        if (continuation.isActive) {
                            continuation.resume(Result.success("UI Interaction completed successfully."))
                        }
                    }
                }

                override fun onActionFailure(stepIndex: Int, reason: String) {
                    Log.e(TAG, "UI Step $stepIndex failed via Service: $reason")
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("UI Interaction failed at step $stepIndex: $reason")))
                    }
                }
            }

            // Ensure listener is cleared if coroutine is cancelled
            continuation.invokeOnCancellation {
                Log.w(TAG, "UI Interaction coroutine cancelled.")
                // Maybe tell service to stop? Depends on service implementation.
                // AssistantAccessibilityService.instance?.resetTaskState() // Example
                AssistantAccessibilityService.staticActionListener = null // Clear static listener on cancel
            }

            // Start the process in the service
            Log.d(TAG, "Requesting Accessibility Service to perform UI steps.")
            serviceInstance.performUiSteps(targetPackage, uiSteps, listener)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        // Implementation from previous examples to check Settings.Secure
        val serviceId = "${context.packageName}/${AssistantAccessibilityService::class.java.canonicalName}"
        try {
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServicesSetting?.contains(serviceId) == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service status", e)
            return false
        }
    }

    private fun showToast(message: String) {
        // Ensure Toast is shown on the main thread
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}