package com.example.geminiassistant.network

// --- Firebase and Kotlinx Imports ---
import android.util.Log
import com.example.geminiassistant.data.* // Your data classes
import com.google.firebase.Firebase
import com.google.firebase.vertexai.GenerativeModel // Firebase GenerativeModel
import com.google.firebase.vertexai.type.Content // Firebase Content
import com.google.firebase.vertexai.type.FunctionCallPart // Specific part type for function calls
import com.google.firebase.vertexai.type.FunctionDeclaration // Firebase FunctionDeclaration
import com.google.firebase.vertexai.type.Part // Firebase Part
import com.google.firebase.vertexai.type.Schema // Firebase Schema
import com.google.firebase.vertexai.type.Tool // Firebase Tool
import com.google.firebase.vertexai.vertexAI // Firebase entry point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json // Keep for potential fallback or complex args
import kotlinx.serialization.SerializationException // Keep for parsing errors

object GeminiClient {

    private var generativeModel: GenerativeModel? = null
    private var isInitialized = false
    private const val EXECUTE_ACTION_FUNCTION_NAME = "executeAndroidAction"

    // --- Define the Function Schema using Firebase Types ---
    // Note: Syntax uses builder functions directly, not newBuilder()
    private val executeAndroidActionSchema: FunctionDeclaration by lazy {
        // Define schema for FindCriteria (Object with optional string properties)
        val findCriteriaSchema = Schema.obj( // Use Schema.obj for objects
            properties = mapOf(
                "text" to Schema.string("Text content of the element"), // Use Schema.string
                "resourceId" to Schema.string("Resource ID of the element (e.g., com.package:id/name)"),
                "contentDesc" to Schema.string("Content description of the element")
            )
            // No 'required' list needed as all properties are optional here
        )

        // Define schema for UiStep (Object with required/optional properties)
        val uiStepSchema = Schema.obj(
            properties = mapOf(
                "findBy" to findCriteriaSchema, // Nested object schema
                "action" to Schema.string("Action to perform: CLICK, SET_TEXT, SCROLL_FORWARD"), // Enum as string
                "value" to Schema.string("Value to set for SET_TEXT action"), // Optional string
                "index" to Schema.integer("0-based index if multiple nodes match findBy (default 0)") // Use Schema.integer
            )
        )

        // Define schema for ActionStep (Object with required/optional properties)
        val actionStepSchema = Schema.obj(
            properties = mapOf(
                "type" to Schema.string("Type of action: LAUNCH_APP_WITH_URI, UI_INTERACTION, SPEAK_RESPONSE, UNSUPPORTED"), // Enum as string
                "targetAppPackage" to Schema.string("Target package name for UI_INTERACTION"), // Optional string
                "uri" to Schema.string("URI for LAUNCH_APP_WITH_URI"), // Optional string
                "extras" to Schema.obj( // Represent map as generic object
                    description = "Key-value pairs (string keys, string values assumed)",
                    // Defining arbitrary map keys in schema is difficult.
                    // Best to keep it as a generic object and handle in mapping.
                    properties = mapOf() // Empty properties map implies arbitrary keys
                ),
                "uiSteps" to Schema.array( // Use Schema.array for lists
                    items = uiStepSchema, // Specify the schema for items in the array
                    description = "Steps for UI interaction using AccessibilityService"
                )
            )
        )

        // Define the main Function Declaration
        FunctionDeclaration( // Use constructor
            name = EXECUTE_ACTION_FUNCTION_NAME,
            description = "Executes a specific action on the Android device...",

            // --- Pass the Map directly to 'parameters' ---
            parameters = mapOf(
                "taskId" to Schema.string("A unique identifier for this task."),
                "actions" to Schema.array(items = actionStepSchema, description = "List of actions"), // Array of ActionSteps
                "confirmationMessage" to Schema.string("Optional message to show user...") // Optional string
            )
        )
    }

    // --- Initialize using Firebase SDK ---
    fun initialize(modelName: String) { // No context or explicit credentials needed now
        if (isInitialized) return
        try {
            Log.i("GeminiClient", "Initializing Firebase Vertex AI...")
            // Get the model instance, configuring it with the tool
            generativeModel = Firebase.vertexAI.generativeModel(
                modelName = modelName,
                tools = listOf(Tool.functionDeclarations(listOf(executeAndroidActionSchema)))
            )
            isInitialized = true
            Log.i("GeminiClient", "Firebase Vertex AI Initialized successfully.")
        } catch (e: Exception) {
            // Log initialization errors (e.g., Firebase not configured, network issues)
            Log.e("GeminiClient", "CRITICAL: Error initializing Firebase Vertex AI: ${e.message}", e)
        }
    }

    // --- Refactored getActionPlan ---
    suspend fun getActionPlan(userQuery: String): Result<GeminiActionResponse> {
        if (!isInitialized || generativeModel == null) {
            return Result.failure(IllegalStateException("GeminiClient not initialized."))
        }

        // --- Updated Prompt (Keep instructions to use the tool) ---
        val prompt = """
            You are an assistant agent running on an Android device. Your primary goal is to help the user perform tasks by using the available tool to interact with apps.

            TOOL DEFINITION:
            - You have access to ONE tool: `executeAndroidAction`.
            - Use this tool whenever the user request requires an action on the Android device that matches the tool's capabilities (opening apps, navigating, searching via URI, specific UI steps).
            - The tool takes parameters: `taskId` (string, generate unique ID), `actions` (list of action steps), `confirmationMessage` (string, optional message for user).
            - Each action step in the `actions` list requires a `type` (string enum: LAUNCH_APP_WITH_URI, UI_INTERACTION, etc.) and other parameters based on the type (like `targetAppPackage`, `uri`, `extras`, `uiSteps`).

            INSTRUCTIONS & EXAMPLES:

            1. SIMPLE APP LAUNCH:
               - If the user asks to "Open [App Name]", "Launch [App Name]", or similar without further details:
               - Call `executeAndroidAction`.
               - The `actions` list should contain ONE step.
               - Set `type` to "LAUNCH_APP_WITH_URI".
               - Set `targetAppPackage` to the correct package name (see below).
               - Set `uri` to an empty string ("") or null.
               - Example:
                 User Request: "Open YouTube"
                 Function Call Args: { "taskId": "launch-yt-1", "actions": [ { "type": "LAUNCH_APP_WITH_URI", "targetAppPackage": "com.google.android.youtube", "uri": "" } ], "confirmationMessage": "OK. Opening YouTube." }

            2. LAUNCH WITH URI (Search, Navigation, Web):
               - If the user asks to search, navigate, or open a web link:
               - Call `executeAndroidAction`.
               - The `actions` list should contain ONE step.
               - Set `type` to "LAUNCH_APP_WITH_URI".
               - Set `uri` to the appropriate intent URI (e.g., "https://www.youtube.com/results?search_query=...", "google.navigation:q=...", "https://..."). URL encode search terms.
               - Set `targetAppPackage` *only if* strictly necessary (like for Google Maps navigation: "com.google.android.apps.maps"). Usually leave it null for web links or YouTube search URIs to let the system choose the handler.
               - Example 1:
                 User Request: "Search YouTube for cute kittens"
                 Function Call Args: { "taskId": "yt-search-1", "actions": [ { "type": "LAUNCH_APP_WITH_URI", "uri": "https://www.youtube.com/results?search_query=cute+kittens" } ], "confirmationMessage": "Searching YouTube for cute kittens." }
               - Example 2:
                 User Request: "Navigate to the library"
                 Function Call Args: { "taskId": "nav-lib-1", "actions": [ { "type": "LAUNCH_APP_WITH_URI", "uri": "google.navigation:q=the+library", "targetAppPackage": "com.google.android.apps.maps" } ], "confirmationMessage": "Starting navigation to the library." }

            3. UI INTERACTION (Multi-step):
               - If the action requires multiple steps *within* an app (like opening YouTube AND THEN typing a search):
               - Call `executeAndroidAction`.
               - The `actions` list will contain MULTIPLE steps.
               - Often starts with a "LAUNCH_APP_WITH_URI" step (as in #1 or #2).
               - Subsequent steps will have `type` = "UI_INTERACTION", a `targetAppPackage`, and a list of `uiSteps` describing element finding (`findBy`) and actions (`action`, `value`).
               - Example:
                 User Request: "Open YouTube and search for synthwave mix"
                 Function Call Args: { "taskId": "yt-ui-search-1", "actions": [ { "type": "LAUNCH_APP_WITH_URI", "targetAppPackage": "com.google.android.youtube", "uri": "" }, { "type": "UI_INTERACTION", "targetAppPackage": "com.google.android.youtube", "uiSteps": [ { "findBy": { "contentDesc": "Search" }, "action": "CLICK" } ] }, { "type": "UI_INTERACTION", "targetAppPackage": "com.google.android.youtube", "uiSteps": [ { "findBy": { "resourceId": "com.google.android.youtube:id/search_edit_text" }, "action": "SET_TEXT", "value": "synthwave mix" } ] }, { "type": "UI_INTERACTION", "targetAppPackage": "com.google.android.youtube", "uiSteps": [ { "findBy": { "contentDesc": "Search" }, "action": "CLICK" } ] } ], "confirmationMessage": "OK. Opening YouTube and searching." }

            4. WHEN NOT TO CALL THE TOOL:
               - If the request is purely conversational (e.g., "How are you?", "Tell me a joke").
               - If the request is ambiguous (e.g., "Search for it"). Ask for clarification.
               - If the required action is not supported by the defined `actions` types or parameters. Explain you cannot do it.
               - In these cases, respond ONLY with natural language text. DO NOT call the function.

            PACKAGE NAMES:
            - YouTube: com.google.android.youtube
            - Google Maps: com.google.android.apps.maps

            Now, process the following user request and respond by calling the `executeAndroidAction` function with the appropriate arguments if an action is needed, or with only text otherwise.

            User Request: "$userQuery"
            """.trimIndent()

        return try {
            Log.d("GeminiClient", "Sending prompt to Gemini via Firebase SDK...")

            // --- Build Content using Firebase Type ---
            // The SDK provides convenient content builder DSL
            val userContent = com.google.firebase.vertexai.type.content(role = "user") {
                text(prompt)
            }

            // --- Make API Call using Firebase GenerativeModel ---
            val response = withContext(Dispatchers.IO) {
                // Call generateContent directly on the Firebase model instance
                generativeModel!!.generateContent(userContent) // Pass the Content object
            }

            // --- Process Response for Function Call using Firebase Types ---
            // Find the function call part in the response
            val functionCallPart = response.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull { it is FunctionCallPart } as? FunctionCallPart

            if (functionCallPart != null && functionCallPart.name == EXECUTE_ACTION_FUNCTION_NAME) {
                Log.i("GeminiClient", "Gemini returned function call: ${functionCallPart.name}")
                val args: Map<String, Any?> = functionCallPart.args // Args are directly a Map!

                Log.d("GeminiClient", "Function Call Args Map: $args")

                try {
                    // --- Manually Map Args Map to GeminiActionResponse ---
                    // This is safer than trying to serialize the generic Map directly
                    val actionResponse = mapArgsToActionResponse(args)
                    Result.success(actionResponse)
                } catch (e: Exception) {
                    // Catch potential casting or mapping errors
                    Log.e("GeminiClient", "Failed to map function call args: ${e.message}", e)
                    Result.failure(Exception("Failed to process arguments from Gemini function call: ${e.message}"))
                }

            } else {
                // No function call, handle text response
                val responseText = response.text?.trim() // Easier text access
                Log.i("GeminiClient", "Gemini responded with text (no function call): $responseText")
                val textResponse = GeminiActionResponse(
                    taskId = "text-${java.util.UUID.randomUUID()}",
                    actions = listOf(ActionStep(type = ActionType.SPEAK_RESPONSE)), // Indicate text response
                    confirmationMessage = responseText ?: "Sorry, I couldn't determine an action."
                )
                Result.success(textResponse)
            }

        } catch (e: Exception) {
            // Catch API call errors (network, authentication handled by Firebase, etc.)
            Log.e("GeminiClient", "Error calling Gemini API or processing response: ${e.message}", e)
            Result.failure(e)
        }
    }

    // --- Helper function to map the args Map to our data class ---
    private fun mapArgsToActionResponse(args: Map<String, Any?>): GeminiActionResponse {
        // --- More robust retrieval for taskId ---
        val taskIdValue = args["taskId"] // Get the value first
        val taskId = if (taskIdValue is String) { // Check if it IS a String
            taskIdValue // Use it directly
        } else {
            // If not a String, try converting toString() or throw error
            taskIdValue?.toString() ?: throw IllegalArgumentException("Missing or invalid taskId (value: $taskIdValue)")
        }
        // --- End of change ---

        val confirmationMessage = args["confirmationMessage"] as? String // Nullable, keep as is

        // Map the 'actions' list
        val actionsListMap = args["actions"] as? List<Map<String, Any?>>
            ?: throw IllegalArgumentException("Missing or invalid actions list")

        val actions = actionsListMap.map { actionMap ->
            val typeValue = actionMap["type"]
            // --- Clean the string and perform case-insensitive lookup ---
            val rawTypeString = if (typeValue is String) typeValue else typeValue?.toString()

            if (rawTypeString.isNullOrBlank()) {
                throw IllegalArgumentException("Missing or invalid type string in action")
            }

            // Trim potential quotes and whitespace
            val cleanedTypeString = rawTypeString.trim().removeSurrounding("\"")

            // Find enum value ignoring case
            val type = ActionType.entries.find { it.name.equals(cleanedTypeString, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown ActionType value received: '$cleanedTypeString' (raw: '$rawTypeString')")

            val targetPackageValue = actionMap["targetAppPackage"]
            val rawTargetPackageString = if (targetPackageValue is String) targetPackageValue else targetPackageValue?.toString()
            val targetAppPackage = rawTargetPackageString?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }

            val uriValue = actionMap["uri"]
            val rawUriString = if (uriValue is String) uriValue else uriValue?.toString()
            // Remove quotes here too, just in case, then check if blank
            val uri = rawUriString?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }

            // --- More robust retrieval for extras ---
            val extrasMapRaw = actionMap["extras"] as? Map<*, *> // Cast to Map<*, *> first
            val extras = extrasMapRaw?.mapNotNull { (k, v) ->
                val keyString = k?.toString() // Convert key to String
                val valueString = v?.toString() // Convert value to String
                if (keyString != null && valueString != null) keyString to valueString else null
            }?.toMap()
            // --- End of change ---


            // Map uiSteps list
            val uiStepsListMap = actionMap["uiSteps"] as? List<Map<String, Any?>>
            val uiSteps = uiStepsListMap?.map { uiStepMap ->
                val findByMap = uiStepMap["findBy"] as? Map<String, Any?> ?: throw IllegalArgumentException("Missing findBy in uiStep")
                val findBy = FindCriteria(
                    text = findByMap["text"] as? String,
                    resourceId = findByMap["resourceId"] as? String,
                    contentDesc = findByMap["contentDesc"] as? String
                )

                // --- More robust retrieval for actionString ---
                val actionValue = uiStepMap["action"]
                // --- Apply same cleaning/lookup logic for UiAction ---
                val rawActionString = if (actionValue is String) actionValue else actionValue?.toString()
                if (rawActionString.isNullOrBlank()) {
                    throw IllegalArgumentException("Missing or invalid action string in uiStep")
                }
                val cleanedActionString = rawActionString.trim().removeSurrounding("\"")
                val action = UiAction.entries.find { it.name.equals(cleanedActionString, ignoreCase = true) }
                    ?: throw IllegalArgumentException("Unknown UiAction value received: '$cleanedActionString' (raw: '$rawActionString')")

                val value = uiStepMap["value"] as? String
                val index = (uiStepMap["index"] as? Number)?.toInt() ?: 0

                UiStep(findBy = findBy, action = action, value = value, index = index)
            }

            ActionStep(
                type = type,
                targetAppPackage = targetAppPackage,
                uri = uri,
                extras = extras,
                uiSteps = uiSteps
            )
        }

        return GeminiActionResponse(
            taskId = taskId,
            actions = actions,
            confirmationMessage = confirmationMessage
        )
    }

    // No cleanup needed for PredictionServiceClient
}