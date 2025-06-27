# Vertex AI - RAG Engine & Gemini Live API integration

## Recommended steps to follow

Clone the respository and navigate to 'framework-less/Live-API/RAG-Engine.' Each subfolder at this level will have a README to follow.

Start with the 'setup' subfolder to generate an example RAG Engine corpus.

Then proceed to 'text2text' and/or 'audio2audio' to build out an end-to-end Android application that utilizes the Live API with a RAG Engine retrieval tool.

**Important!:** you will need to update GCP project and location values for configuration variables in several places throughout this project.

**Also important!:** you will need [Vertex AI APIs enabled](https://console.cloud.google.com/flows/enableapi?apiid=aiplatform.googleapis.com) and a Google Cloud indentity with at least permissions that mirror the [Vertex AI User IAM role](https://cloud.google.com/vertex-ai/docs/general/access-control#aiplatform.user).