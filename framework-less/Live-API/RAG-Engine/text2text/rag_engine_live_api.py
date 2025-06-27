# How to run file:
# python rag_engine_live_api.py "What is the pet policy for the Grand Horizon Resort & Hotel?"

import vertexai
from vertexai import rag
import asyncio
from google import genai
from google.genai import types

vertexai.init(project="andrewcooley-genai-tests", location="us-central1")

# Get RAG corpus for use with Live API
rag_corpus = rag.get_corpus(name=list(rag.list_corpora())[-1].name)

print("Fetching RAG Corpus...")
print(f"Using RAG Corpus: {rag_corpus.name}")

# Define the tools for the agent - types matter (i.e. pydantic)!
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

MODEL="gemini-2.0-flash-live-preview-04-09"

client = genai.Client(
  vertexai=True,
  project="andrewcooley-genai-tests",
  location="us-central1"
)

config = {"response_modalities": ["TEXT"], "tools": TOOLS}

async def main():
    """
    Starts an interactive, multi-turn chat session with the Gemini model.
    Manages history and creates a new session for each turn.
    """
    # This history list is the key to multi-turn conversation.
    # It will store the entire conversation and be sent with each request.
    history = []

    print("\n--- Start Chat Session ---")
    print("Ask a question about the Grand Horizon Resort & Hotel.")
    print("Type 'exit' or 'quit' to end the session.\n")

    while True:
        # 1. Get user input in a non-blocking way
        try:
            prompt = await asyncio.to_thread(input, "You: ")
            if not prompt or prompt.lower() in ["exit", "quit"]:
                print("\n--- End Chat Session ---")
                break
        except (EOFError, KeyboardInterrupt):
            print("\n--- End Chat Session ---")
            break

        # 2. Append the new user message to our history
        history.append({"role": "user", "parts": [{"text": prompt}]})
        print(f"\nGemini: ", end="", flush=True)

        full_response = ""
        
        # 3. Start a NEW session for this turn
        # The `async with` block is now INSIDE the loop.
        try:
            async with client.aio.live.connect(model=MODEL, config=config) as session:
                
                # 4. Send the ENTIRE history to the model
                await session.send_client_content(turns=history)

                # 5. Stream the model's response for this turn
                async for chunk in session.receive():
                    if chunk.server_content:
                        if chunk.text is not None:
                            print(chunk.text, end="", flush=True)
                            full_response += chunk.text

                        model_turn = chunk.server_content.model_turn
                        if model_turn:
                            for part in model_turn.parts:
                                if part.executable_code is not None:
                                    print(f"\n[Tool Code]:\n{part.executable_code.code}")
                                if part.code_execution_result is not None:
                                    print(f"\n[Tool Output]:\n{part.code_execution_result.output}")
            
            # 6. Once the turn is complete, add the model's full response to the history
            history.append({"role": "model", "parts": [{"text": full_response}]})
            print("\n")

        except Exception as e:
            print(f"\nAn error occurred during the API call: {e}")
            # Remove the last user message from history since the turn failed
            history.pop() 
            # Optionally, you could break the loop here if errors are frequent
            # break

if __name__ == "__main__":

    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n--- Chat Interrupted. Exiting. ---")