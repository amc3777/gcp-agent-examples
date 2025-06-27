# Vertex AI - RAG Engine & Gemini Live API integration

**Demo video:**
[audio-to-audio in Android](./media/demo.mp4)

## Getting Started

Start by creating an isolated python virtual environment.
> for example, with venv and a virtual environment named 'venv', `python -m venv venv`

Install the required packages from requirements.txt.
> for example, with pip, `pip install -r requirements.txt`

You will need to create a RAG Engine corpus as a prerequisite. You may follow the [quickstart guide for Python](https://cloud.google.com/vertex-ai/generative-ai/docs/rag-engine/rag-quickstart). If you need a sample of text to import into your corpus for demonstration purposes, you may use the synthetic [hotel policies](../setup/The_Grand_Horizon_Hotel_&_Resort_-_Guest_Policy_Compendium.pdf) that I had Gemini generate. For step-by-step code to handle this preqrequisite, see [this notebook](../setup/rag_engine_vvs.ipynb) in the setup folder.

## Running the multi-turn Android application with isolated backend server

#### Backend steps - backend/main.py

Replace project and location variables to match your environment. The Live API model may have changed since the creation of this application.

`rag.get_corpus(name=list(rag.list_corpora())[-1].name)` assumes that the RAG Engine corpus will be returned last when listing existing corpora, as the most recently created corpus.

To start the backend server on localhost run this from a terminal: `uvicorn main:app --reload`

You should see that the application startup is complete.

Typically the server will run on http://127.0.0.1:8000.

Keep the server running as we set up the frontend...

#### Frontend steps - frontend/RAGEngineLiveAPIAudio

The client frontend is an Android mobile application. The basic steps are below:

> 1. Install the latest version [Android Studio](https://developer.android.com/studio) that matches your machine specifications.

> 2. Import (New > Import Project) 'RAGEngineLiveAPIAudip' folder as a project in Android Studio.

> 3. Sync project (File > Sync Project with Gradle Files).

> 4. Clean project (Build > Clean Project).

> 5. Build project (Build > Rebuild Project).

> 6. Run app (Run > Run 'app') on device - this can be an emulator.

The application should open and you should see a 'connected' status and button to 'start recording.' Press the button to record your audio input and then press 'stop recording' when you are finished. The request should be processed and you should hear Gemini's response. You may continue the conversation for many turns with the WebSockets connection open.

If the emulator microphone has trouble recognizing your speech, ensure that 'Extended Controls' for 'Microphone' has 'Virtual microphone uses host audio input' enabled.

Remember that the backend server must be running still. The frontend client communicates with the localhost FastAPI server.