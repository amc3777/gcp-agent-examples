#### Background on this project
[go/mobile-oem-ai-agents](https://docs.google.com/document/d/1_VpwPFrkY-6sE6nJcl3N0qnss8fZ551pLdxMfibk0ig/edit?usp=sharing&resourcekey=0-X4SI7Exj5CpSuDUikPAa1g)

## Gemini Assistant

### Running the application

1. Install the latest version [Android Studio](https://developer.android.com/studio) that matches your machine specifications.

2. Import (New > Import Project) 'GeminiAssistant' folder as a project in Android Studio.

**Firebase setup:**
Follow step 1 of the [Vertex AI in Firebase (Gemini API) guide for the Android platform](https://firebase.google.com/docs/vertex-ai/get-started?platform=android) to set up your Firebase project and connect the app to Firebase. You will be ready to proceed to the follow steps after... (1) your Firebase project is created, (2) your Android app is registered, (3) your google-services.json file is downloaded and copied to the correct location in your Android project, (4) and the Vertex AI APIs are enabled.

3. Sync project (File > Sync Project with Gradle Files).

4. Clean project (Build > Clean Project).

5. Build project (Build > Rebuild Project).

6. Run app (Run > Run 'app') on device - this could be an emulator.

The application should open once installed on the device or emulator. Follow the prompt to 'Go to Settings.' Allow Gemini Assistant Action Service - this will give the app the ability to operate your device through UI simulation. Return to the app. Press the microphone button and speak a command.

If the emulator microphone has trouble recognizing your speech, ensure that 'Extended Controls' for 'Microphone' has 'Virtual microphone uses host audio input' enabled.

**Video demonstration:**
[Basic actions](./media/gemini_assistant_basic_actions.mp4)

**Example commands to try:**
<br>"Search YouTube for dog videos."
<br>"Navigate to London England in Maps."
<p>Note: For now, actions in 1st party apps work best.