import asyncio
import logging
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
     
import google.genai as genai
from google.genai.types import Blob
import vertexai
from vertexai import rag

# --- Configuration & Initialization  ---
PROJECT_ID = "andrewcooley-genai-tests"
LOCATION = "us-central1"
MODEL_ID = "gemini-2.0-flash-live-preview-04-09"
INPUT_SAMPLE_RATE = 16000

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)
app = FastAPI()

try:
    vertexai.init(project=PROJECT_ID, location=LOCATION)
    rag_corpora = list(vertexai.rag.list_corpora())
    if not rag_corpora:
        raise RuntimeError("No RAG corpora found. Please create one.")
    rag_corpus = rag_corpora[-1]
    logger.info(f"Using RAG Corpus: {rag_corpus.name}")
    TOOLS = [{"retrieval": {"vertex_rag_store": {"rag_resources": [{"rag_corpus": rag_corpus.name}]}}}]
    GEMINI_CONFIG = {
        "response_modalities": ["AUDIO"], "tools": TOOLS,
        "system_instruction": "You are a helpful assistant..."
    }
    gemini_client = genai.Client(vertexai=True, project=PROJECT_ID, location=LOCATION)
except Exception as e:
    logger.error(f"Failed to initialize Vertex AI or RAG: {e}")
    exit(1)

# --- WebSocket Endpoint ---
@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    logger.info("WebSocket connection accepted.")

    try:
        async with gemini_client.aio.live.connect(model=MODEL_ID, config=GEMINI_CONFIG) as session:
            logger.info("Gemini Live session started.")

            async def gemini_receiver():
                try:
                    async for message in session.receive():
                        if (message.server_content.model_turn and 
                            message.server_content.model_turn.parts):
                            for part in message.server_content.model_turn.parts:
                                if part.inline_data and part.inline_data.data:
                                    await websocket.send_bytes(part.inline_data.data)
                        if message.server_content.turn_complete:
                            await websocket.send_text("TURN_COMPLETE")
                            break
                except Exception as e:
                    logger.error(f"Error in Gemini receiver task: {e}")

            # Main loop to forward messages from the client to Gemini
            try:
                while True:
                    message = await websocket.receive()
                    
                    if "text" in message:
                        # This is the signal to end the audio stream
                        if message["text"] == "STOP_RECORDING":
                            logger.info("Client stopped recording. Sending end-of-turn signal.")
                            # Send the final empty chunk to tell Gemini the turn is over
                            await session.send_realtime_input(media=Blob(data=b'', mime_type=f"audio/pcm;rate={INPUT_SAMPLE_RATE}"))
                            
                            # Spawn a listener for the response
                            asyncio.create_task(gemini_receiver())
                            logger.info("Spawned new listener task for Gemini response.")
                    
                    elif "bytes" in message:
                        # This is a raw PCM audio chunk, forward it directly
                        await session.send_realtime_input(media=Blob(data=message["bytes"], mime_type=f"audio/pcm;rate={INPUT_SAMPLE_RATE}"))

            except WebSocketDisconnect:
                logger.info("Client disconnected gracefully.")

    except Exception as e:
        logger.error(f"An error occurred in the WebSocket endpoint: {e}")