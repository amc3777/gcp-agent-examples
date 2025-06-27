# Vertex AI - RAG Engine & Gemini Live API integration

**Demo video:**
[Text-to-text in Android](./media/demo.mp4)

## Getting Started

Start by creating an isolated python virtual environment.
> for example, with venv and a virtual environment named 'venv', `python -m venv venv`

Install the required packages from requirements.txt.
> for example, with pip, `pip install -r requirements.txt`

You will need to create a RAG Engine corpus as a prerequisite. You may follow the [quickstart guide for Python](https://cloud.google.com/vertex-ai/generative-ai/docs/rag-engine/rag-quickstart). If you need a sample of text to import into your corpus for demonstration purposes, you may use the synthetic [hotel policies](../setup/The_Grand_Horizon_Hotel_&_Resort_-_Guest_Policy_Compendium.pdf) that I had Gemini generate. For step-by-step code to handle this preqrequisite, see [this notebook](../setup/rag_engine_vvs.ipynb) in the setup folder.

## Running the single-turn notebook example - rag_engine_live_api.ipynb

Replace project and location variables to match your environment. The Live API model may have changed since the creation of this notebook.

`rag.get_corpus(name=list(rag.list_corpora())[-1].name)` assumes that the RAG Engine corpus will be returned last when listing existing corpora, as the most recently created corpus.

Replace the prompt variable with something that makes sense for the corpus you created.

Run the two cells in the notebook.

The model should respond using retrieved context found in your RAG Engine corpus.

## Running the multi-turn standalone python program - rag_engine_live_api.py

Replace project and location variables to match your environment. The Live API model may have changed since the creation of this file.

`rag.get_corpus(name=list(rag.list_corpora())[-1].name)` assumes that the RAG Engine corpus will be returned last when listing existing corpora, as the most recently created corpus.

To run the program, from a terminal, execute: `python rag_engine_live_api.py "What is the pet policy for the Grand Horizon Resort & Hotel?"` (replace what is in quotes with whatever you'd like to start the conversation with).

Type `exit` or `quit` to end the conversation.

## Running the multi-turn Android application with isolated backend server

#### Backend steps - backend/main.py

Replace project and location variables to match your environment. The Live API model may have changed since the creation of this application.

`rag.get_corpus(name=list(rag.list_corpora())[-1].name)` assumes that the RAG Engine corpus will be returned last when listing existing corpora, as the most recently created corpus.

To start the backend server on localhost run this from a terminal: `uvicorn main:app --reload`

You should see that the application startup is complete.

Typically the server will run on http://127.0.0.1:8000. FastAPI will provide a docs site out of the box, at http://127.0.0.1:8000/docs. You can even test the API through the docs site.

Keep the server running as we set up the frontend...

#### Frontend steps - frontend/RAGEngineLiveAPI

The client frontend is an Android mobile application. The basic steps are below:

> 1. Install the latest version [Android Studio](https://developer.android.com/studio) that matches your machine specifications.

> 2. Import (New > Import Project) 'RAGEngineLiveAPI' folder as a project in Android Studio.

> 3. Sync project (File > Sync Project with Gradle Files).

> 4. Clean project (Build > Clean Project).

> 5. Build project (Build > Rebuild Project).

> 6. Run app (Run > Run 'app') on device - this can be an emulator.

The application should open and you should see a text field to type a prompt. Press the button to send and start a conversation.

Remember that the backend server must be running still. The frontend client communicates with the localhost FastAPI server.