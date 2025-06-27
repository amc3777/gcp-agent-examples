import os
import uvicorn
import vertexai
from vertexai import rag
from google import genai
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import List, Dict, Any

# --- Configuration ---
PROJECT_ID = os.environ.get("GCP_PROJECT_ID", "andrewcooley-genai-tests")
LOCATION = os.environ.get("GCP_LOCATION", "us-central1")
MODEL_NAME = "gemini-2.0-flash-live-preview-04-09"

# --- Initialization (runs once on server startup) ---
print("Initializing Vertex AI and Gemini Client...")
try:
    vertexai.init(project=PROJECT_ID, location=LOCATION)
    
    # Get RAG corpus for use with Live API
    corpora_list = list(rag.list_corpora())
    if not corpora_list:
        print("No RAG corpora found. Continuing without RAG.")
        TOOLS = []
    else:
        rag_corpus = rag.get_corpus(name=corpora_list[-1].name)
        print(f"Using RAG Corpus: {rag_corpus.display_name} ({rag_corpus.name})")

    # Define the tools for the agent
    TOOLS = [
        {
            "retrieval": {
                "vertex_rag_store": {
                    "rag_resources": [
                        {"rag_corpus": f"{rag_corpus.name}"}
                    ]
                }
            }
        }
    ]
    
    # Initialize the Gemini Client
    client = genai.Client(
      vertexai=True,
      project=PROJECT_ID,
      location=LOCATION
    )
    
    # Define the generation config
    config = {"response_modalities": ["TEXT"], "tools": TOOLS}

except Exception as e:
    print(f"FATAL: Could not initialize Vertex AI or RAG Corpus. Error: {e}")
    # Exit if we can't initialize properly
    exit(1)


# --- FastAPI Application ---
app = FastAPI(
    title="Vertex AI RAG Engine Live API",
    description="An interface for multi-turn conversations with the Gemini Live API using a RAG Engine corpus.",
    version="1.0.0",
)

# --- Pydantic Models for Request and Response Validation ---

class Part(BaseModel):
    text: str

class Turn(BaseModel):
    role: str = Field(..., pattern="^(user|model)$") # Role must be 'user' or 'model'
    parts: List[Part]

class ChatRequest(BaseModel):
    history: List[Turn] = Field(
        ...,
        description="The entire conversation history, including the latest user message.",
        example=[{"role": "user", "parts": [{"text": "What is the pet policy?"}]}]
    )

class ChatResponse(BaseModel):
    response: str
    history: List[Turn]

# --- API Endpoint ---

@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """
    Handles a single turn in a conversation.

    Receives the entire conversation history and returns the model's response.
    The client is responsible for maintaining and sending the history.
    """
    # The history from the request is already validated by Pydantic.
    # We need to convert it from Pydantic models to simple dicts for the Gemini API.
    history_dicts = [turn.model_dump() for turn in request.history]
    
    # Get the last user prompt for logging
    last_user_prompt = history_dicts[-1]['parts'][0]['text'] if history_dicts else "No prompt"
    print(f"\nReceived request. Last prompt: '{last_user_prompt}'")
    print(f"History contains {len(history_dicts)} turns.")

    full_response = ""
    
    try:
        async with client.aio.live.connect(model=MODEL_NAME, config=config) as session:
            # Send the ENTIRE history to the model
            await session.send_client_content(turns=history_dicts)

            # Stream the model's response for this turn
            async for chunk in session.receive():
                if chunk.server_content:
                    if chunk.text:
                        print(chunk.text, end="", flush=True)
                        full_response += chunk.text

                    # Optional: Log tool usage if needed
                    model_turn = chunk.server_content.model_turn
                    if model_turn:
                        for part in model_turn.parts:
                            if part.executable_code is not None:
                                print(f"\n[Tool Code]:\n{part.executable_code.code}")
                            if part.code_execution_result is not None:
                                print(f"\n[Tool Output]:\n{part.code_execution_result.output}")
        
        print("\nStream complete.")

        # Append the model's full response to the history to be returned
        updated_history = history_dicts + [{"role": "model", "parts": [{"text": full_response}]}]
        
        # Convert the updated history back to Pydantic models for the response
        response_history_models = [Turn(**turn) for turn in updated_history]

        return ChatResponse(response=full_response, history=response_history_models)

    except Exception as e:
        print(f"\nAn error occurred during the API call: {e}")
        raise HTTPException(status_code=500, detail=f"An error occurred: {str(e)}")


# To run the server from the command line
if __name__ == "__main__":
    # Note: Use `uvicorn main:app --reload` for development
    uvicorn.run(app, host="0.0.0.0", port=8000)