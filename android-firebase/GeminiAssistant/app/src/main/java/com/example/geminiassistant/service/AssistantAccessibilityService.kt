package com.example.geminiassistant.service

// --- Imports needed for the Service class ---
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.geminiassistant.data.FindCriteria // Assuming FindCriteria is in data package
import com.example.geminiassistant.data.UiAction     // Assuming UiAction is in data package
import com.example.geminiassistant.data.UiStep       // Assuming UiStep is in data package
import kotlinx.coroutines.*
// Note: AccessibilityActionListener is in the same package, so direct import might not be needed,
// but it's good practice if you prefer explicit imports:
// import com.example.geminiassistant.service.AccessibilityActionListener
// --- End Imports ---


// --- The Service Implementation Class ---
class AssistantAccessibilityService : AccessibilityService() {

    private val TAG = "AssistantAccessService"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob) // Use Main for UI thread access if needed

    // --- State for current task ---
    private var currentSteps: List<UiStep>? = null
    private var currentStepIndex: Int = -1
    private var targetPackage: String? = null
    private var actionListener: AccessibilityActionListener? = null // How the service reports back

    companion object {
        // Static reference for binding or communication (use with caution, consider proper binding)
        var instance: AssistantAccessibilityService? = null
        var staticActionListener: AccessibilityActionListener? = null // Simpler static listener
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility Service Connected")
        // Configuration is mostly done via XML, but you can refine here if needed
        serviceInfo = serviceInfo?.apply { // Apply modifications to existing info from XML
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED // Be more specific
            // packageNames = arrayOf("com.google.android.youtube") // Already set in XML
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS // Request more info
        }
        Log.d(TAG,"Service Info Flags: ${serviceInfo?.flags}")
        Log.d(TAG,"Service Info Event Types: ${serviceInfo?.eventTypes}")
        Log.d(TAG,"Service Info Package Names: ${serviceInfo?.packageNames?.joinToString()}")
    }

    // --- Method called by ActionExecutor to start UI interaction ---
    fun performUiSteps(targetPkg: String, steps: List<UiStep>, listener: AccessibilityActionListener) {
        Log.i(TAG, "Received UI steps for package: $targetPkg")
        if (steps.isEmpty()) {
            listener.onActionFailure(-1, "No UI steps provided.")
            return
        }
        targetPackage = targetPkg
        currentSteps = steps
        currentStepIndex = 0
        actionListener = listener // Store the listener
        staticActionListener = listener // Also store statically for simplicity now

        // Trigger processing the first step
        processCurrentStep()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.v(TAG, "Event: ${event?.eventType} from ${event?.packageName}")
        // Only process if we are actively working on UI steps and the event is from the target app
        if (currentSteps == null || currentStepIndex < 0 || event?.packageName != targetPackage) {
            return
        }

        // Often useful to react to window changes to ensure UI is ready
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            Log.d(TAG, "Window content changed for $targetPackage, re-evaluating step $currentStepIndex")
            // Debounce or delay slightly to allow UI to settle
            serviceScope.launch {
                delay(300) // Small delay
                processCurrentStep()
            }
        }
    }

    private fun processCurrentStep() {
        if (currentSteps == null || currentStepIndex < 0 || currentStepIndex >= currentSteps!!.size) {
            Log.d(TAG, "No current step or index out of bounds.")
            return
        }

        val step = currentSteps!![currentStepIndex]
        Log.d(TAG, "Processing Step $currentStepIndex: Action=${step.action}, FindBy=${step.findBy}")

        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "Root node is null, cannot process step $currentStepIndex.")
            // Optionally report failure after a timeout?
            return
        }

        findAndPerformAction(rootNode, step) { success, reason ->
            rootNode.recycle() // Recycle the root node when done with it for this attempt
            if (success) {
                Log.i(TAG, "Step $currentStepIndex successful.")
                actionListener?.onActionSuccess(currentStepIndex)
                currentStepIndex++
                if (currentStepIndex < currentSteps!!.size) {
                    // Move to next step, maybe with a small delay
                    serviceScope.launch {
                        delay(500) // Delay before next action
                        processCurrentStep()
                    }
                } else {
                    Log.i(TAG, "All UI steps completed.")
                    resetTaskState() // Task finished
                }
            } else {
                Log.e(TAG, "Step $currentStepIndex failed: $reason")
                actionListener?.onActionFailure(currentStepIndex, reason ?: "Unknown error")
                resetTaskState() // Stop processing on failure
            }
        }
    }

    // --- Core Logic: Find Node and Perform Action ---
    private fun findAndPerformAction(
        rootNode: AccessibilityNodeInfo,
        step: UiStep,
        callback: (Boolean, String?) -> Unit
    ) {
        serviceScope.launch(Dispatchers.Default) { // Perform find operations off the main thread
            val targetNodes = findNodes(rootNode, step.findBy)

            if (targetNodes.isNullOrEmpty()) {
                // Important: Recycle rootNode if nodes not found before calling callback
                rootNode.recycle()
                withContext(Dispatchers.Main) { callback(false, "Node not found for criteria: ${step.findBy}") }
                return@launch
            }

            if (step.index >= targetNodes.size) {
                targetNodes.forEach { it.recycle() } // Recycle found nodes
                // Important: Recycle rootNode if index out of bounds before calling callback
                rootNode.recycle()
                withContext(Dispatchers.Main) { callback(false, "Index ${step.index} out of bounds for found nodes (${targetNodes.size})") }
                return@launch
            }

            val targetNode = targetNodes[step.index]
            var success = false
            var errorReason: String? = null

            try {
                Log.d(TAG, "Attempting action ${step.action} on node: ${targetNode.viewIdResourceName} / ${targetNode.text} / ${targetNode.contentDescription}")
                success = when (step.action) {
                    UiAction.CLICK -> targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    UiAction.SET_TEXT -> {
                        val arguments = Bundle()
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.value ?: "")
                        targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    }
                    UiAction.SCROLL_FORWARD -> targetNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    // Add other actions as needed
                }
                if (!success) {
                    errorReason = "performAction returned false for ${step.action}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during performAction: ${e.message}", e)
                success = false
                errorReason = "Exception: ${e.message}"
            } finally {
                // Recycle all nodes found in the list, including the target one
                targetNodes.forEach { it.recycle() }
                // Important: Recycle rootNode after action attempt
                rootNode.recycle()
            }


            withContext(Dispatchers.Main) { // Report result on the main thread
                callback(success, errorReason)
            }
        }
    }

    // --- Helper to find nodes based on criteria ---
    private fun findNodes(rootNode: AccessibilityNodeInfo, criteria: FindCriteria): List<AccessibilityNodeInfo>? {
        // Note: It's generally safer to return an empty list than null
        return when {
            !criteria.resourceId.isNullOrBlank() -> rootNode.findAccessibilityNodeInfosByViewId(criteria.resourceId)
            !criteria.text.isNullOrBlank() -> rootNode.findAccessibilityNodeInfosByText(criteria.text)
            !criteria.contentDesc.isNullOrBlank() -> rootNode.findAccessibilityNodeInfosByText(criteria.contentDesc) // Often contentDesc is treated like text
            else -> emptyList() // Return empty list instead of null
        } ?: emptyList() // Handle potential null from findAccessibilityNodeInfosBy... methods
    }


    private fun resetTaskState() {
        Log.d(TAG, "Resetting task state.")
        currentSteps = null
        currentStepIndex = -1
        targetPackage = null
        actionListener = null
        staticActionListener = null // Clear static listener too
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
        actionListener?.onActionFailure(currentStepIndex, "Service interrupted")
        resetTaskState()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceJob.cancel() // Cancel coroutines
        Log.i(TAG, "Accessibility Service Destroyed")
    }
}