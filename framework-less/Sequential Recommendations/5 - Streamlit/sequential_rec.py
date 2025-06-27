# Import libraries
# Streamlit
import streamlit as st

# Google Cloud
from google.cloud import storage
from google.cloud import firestore
from google.cloud.firestore import SERVER_TIMESTAMP
from google.cloud.firestore_v1.vector import Vector
from google.cloud.firestore_v1.base_vector_query import DistanceMeasure
import vertexai
from vertexai.generative_models import GenerativeModel, Tool
from vertexai.preview import reasoning_engines
import vertexai.preview.generative_models as generative_models
from vertexai.language_models import TextEmbeddingInput, TextEmbeddingModel


# Additional
from typing import List, Optional
import base64
import uuid
import re
from bs4 import BeautifulSoup

# Set configuration values
PROJECT_ID = "andrewcooley-test-project" 
LOCATION = "us-central1"

FIRESTORE_DB = "test-db"

MODEL_ID = "gemini-1.5-flash-001"
GENERATION_CONFIG = {
            "temperature": 0,
            "max_output_tokens": 8192,
            "top_p": 0.95
            }

SAFETY_SETTINGS = {
    generative_models.HarmCategory.HARM_CATEGORY_HATE_SPEECH: generative_models.HarmBlockThreshold.BLOCK_ONLY_HIGH,
    generative_models.HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT: generative_models.HarmBlockThreshold.BLOCK_ONLY_HIGH,
    generative_models.HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT: generative_models.HarmBlockThreshold.BLOCK_ONLY_HIGH,
    generative_models.HarmCategory.HARM_CATEGORY_HARASSMENT: generative_models.HarmBlockThreshold.BLOCK_ONLY_HIGH,
}

REMOTE_AGENT = "projects/619758184732/locations/us-central1/reasoningEngines/6556783660614287360"

# Instantiate Firestore and Vertex AI SDK clients
db = firestore.Client(project=PROJECT_ID, database=FIRESTORE_DB)

vertexai.init(project=PROJECT_ID, location=LOCATION)

# Get Reasoning Engine agent
remote_agent = reasoning_engines.ReasoningEngine(
    REMOTE_AGENT
)

# Define functions for orchestration and processes
def generate_uuid() -> str:
    """Generate a unique identifier"""

    return str(uuid.uuid4())

def display_image_from_gcs(bucket_name: str, blob_name: str) -> str:
    """Fetches and displays an image from GCS using a base64 encoded data URL"""

    storage_client = storage.Client()
    bucket = storage_client.bucket(bucket_name)
    blob = bucket.blob(blob_name)
    image_bytes = blob.download_as_bytes()
    encoded_image = base64.b64encode(image_bytes).decode('utf-8')

    # Return the image in Markdown format for seamless integration with the chat message
    return f'<img src="{f"data:image/png;base64,{encoded_image}"}" width="400">'

def get_nested_subcollections(doc_ref):
    """Recursively fetches all documents from a document and its nested subcollections."""

    doc = doc_ref.get()
    data = {}
    if doc.exists:
        data[doc_ref.id] = doc.to_dict()

        for subcollection in doc_ref.collections():
            data[subcollection.id] = {}
            for doc in subcollection.stream():
                nested_data = get_nested_subcollections(doc.reference)
                data[subcollection.id][doc.id] = nested_data

    return data

def get_field_values(collection_ref, field_name):
    """Return a list of field values for all documents in a collection"""

    docs = collection_ref.stream()
    list = [doc.to_dict()[field_name] for doc in docs]

    return list

def get_field_value(doc_ref, field_name):
    """Return a field value for a field name in a document"""

    doc = doc_ref.get()
    value = doc.to_dict()[field_name]

    return value

def generate_chat(tools=None, system_instruction="""""", text=""""""):
    """Generate a chat response given system instructions, user input, and tool output"""

    if tools:

        model = GenerativeModel(MODEL_ID,
        tools=tools,
        system_instruction=[system_instruction])

    else:

        model = GenerativeModel(MODEL_ID,
        system_instruction=[system_instruction])

    chat = model.start_chat()

    response = (chat.send_message(
    [f"""{text}"""],
    generation_config=GENERATION_CONFIG,
    safety_settings=SAFETY_SETTINGS))

    return response

def embed_query(
    texts: List[str],
    task: str = "RETRIEVAL_QUERY",
    model_name: str = "text-embedding-004",
    dimensionality: Optional[int] = 384,
) -> List[List[float]]:
    """Embeds texts into vectors with defined parameters"""

    model = TextEmbeddingModel.from_pretrained(model_name)
    inputs = [TextEmbeddingInput(text, task) for text in texts]
    kwargs = dict(output_dimensionality=dimensionality) if dimensionality else {}
    embeddings = model.get_embeddings(inputs, **kwargs)
    return [embedding.values for embedding in embeddings]

def query_index(query_vector):
    """Queries a vector index deployed over a Firestore collection with documents that contain an embeddings field"""

    collection = db.collection("restaurants")

    query = collection.find_nearest(
    vector_field="docVector",
    query_vector=Vector(query_vector),
    distance_measure=DistanceMeasure.DOT_PRODUCT,
    limit=1)

    for d in query.get():
        result = d.to_dict()

    return result

def query_agent(prompt, session):
    """Query a Reasoning Engine agent with user input prompt and session ID"""

    response = remote_agent.query(
        input=f"{prompt}",
        config={"configurable": {"session_id": session}},
    )
    return response

#Streamlit UI objects
st.set_page_config(layout="wide")
st.header("Sequential Recommendations", divider="rainbow")

with st.sidebar:
    
    # Populate selection box with customer use names from the Firestore customers collection
    collection_ref = db.collection('customers')
    user_names = get_field_values(collection_ref, 'userName')
    options = user_names
    customer = st.selectbox("Customer username:", options, key="customer_selectbox")

    # Manage state changes when selected customer changes
    if st.session_state.get("prev_customer") != customer:  
        st.session_state.clear()
    st.session_state["prev_customer"] = customer

    # Set user_profile variable to supply downstream context
    doc_ref = db.collection('customers').document(customer)
    user_profile = get_nested_subcollections(doc_ref)

    # Additional UI elements
    st.caption("Connected to a Firestore database")
    st.markdown("---")
    st.markdown("""<p><img src="https://uxwing.com/wp-content/themes/uxwing/download/brands-and-social-media/google-gemini-icon.png" alt="Google" width="18" height="18">
        <small> Intelligence by Gemini Flash</small></p>""", unsafe_allow_html=True)


st.text_area("**Context:**", user_profile, key="initial-existing-user-profile-ta")

# Save chat history to Firestore, clear active chat dialog, and clear session state
if st.button("Clear chat"):

    if "session_id" in st.session_state:
        
        # Get current session ID
        session_ref = db.collection('customers').document(customer).collection('sessions').document(st.session_state.session_id)

        messages_to_save = st.session_state.messages.copy()

        # Remove encoded image data from HTML before saving to Firestore
        for message in messages_to_save:
            if 'content' in message:
                soup = BeautifulSoup(message['content'], "html.parser")
                message['content'] = soup.get_text()
                message['content'] = re.sub(r'\s+', ' ', message['content']).strip()

        session_ref.set({"messages": messages_to_save}, merge=True)

    st.session_state.clear()

# Begin new chat with proactive recommendation
if "messages" not in st.session_state:

    # Create new session and create session document in Firestore
    session_id = generate_uuid()
    st.session_state.session_id = session_id
    session_ref = db.collection('customers').document(customer).collection('sessions').document(session_id)
    session_ref.set({"timestamp": SERVER_TIMESTAMP})

    # Check if existing customer - use system instructions to reason over order and chat history to make a proactive recommendation
    if get_field_value(db.collection('customers').document(customer), 'orderHistory') is True and get_field_value(db.collection('customers').document(customer), 'chatHistory') is True:

        system_instruction = get_field_value(db.collection('system_instructions').document('greeting_existing'), 'prompt')

        text = user_profile

        output = generate_chat(None, system_instruction, text)

        # Add new assistant message to session state
        st.session_state.messages = [
            {"role": "assistant", "content": output.text}
        ]

    else:

        # Use the Grounding with Google search tool to augment responses with information outside of training data
        tools = [Tool.from_google_search_retrieval(google_search_retrieval=generative_models.grounding.GoogleSearchRetrieval()),]
            
        system_instruction = get_field_value(db.collection('system_instructions').document('greeting_new'), 'prompt')

        firstName = get_field_value(doc_ref, 'firstName')

        location = get_field_value(doc_ref, 'location')

        # Structure the context for the chat model
        text = f"""
        Speaking to {firstName}
        Search: best food {location}
        """

        output = generate_chat(tools, system_instruction, text)

        # Instantiate content placeholder and append
        assistant_content = ""
        assistant_content += f"{output.text}"
        assistant_content += f"""<p><img src="https://cdn.logojoy.com/wp-content/uploads/20230801145608/Current-Google-logo-2015-2023-600x203.png" alt="Google" width="60" height="20">
        <small> Grounded with Google Search</small><hr style="border: 0; border-top: 1px dotted #ccc;"></p>"""

        # Add new assistant message to session state
        st.session_state.messages = [
            {"role": "assistant", "content": assistant_content}
        ]

# Display current session chat messages on reload
for message in st.session_state.messages:
    with st.chat_message(message["role"]):
        st.markdown(message["content"], unsafe_allow_html=True)

# Chat input box
if prompt := st.chat_input("I really want..."):

    # Add new user message to session state
    st.chat_message("user").markdown(prompt)
    st.session_state.messages.append({"role": "user", "content": prompt})

    # Start new assistant message that is dependent on a routing decision by the chat model
    with st.chat_message("assistant"):
        
        system_instruction = get_field_value(db.collection('system_instructions').document('router'), 'prompt')
        text = st.session_state.messages
        response = generate_chat(None, system_instruction, text)

        st.session_state.route = response.text.strip()

        # When the chat model determines that the user is ready for the top pick recommendation
        if st.session_state.route == "recommendation":

            # This is the query-writing step - contained in the response
            system_instruction = get_field_value(db.collection('system_instructions').document('top_picks_query'), 'prompt')
            text = ''
            text += str(st.session_state.messages)
            text += str(user_profile)
            response = generate_chat(None, system_instruction, text)

            # This vectorizes the query, does an (a)NN search, and extracts restaurant's name and address for the reviews-summarization agent to use downstream
            query_vector = embed_query([response.text])[0]
            doc_vector = query_index(query_vector)
            top_result = doc_vector['restaurantName'].replace("'", "") + ' ' + doc_vector['restaurantAddress'].replace("'", "")

            # Query Reasoning Engine agent to summarize Google Maps reviews about the matched restaurant
            reviews = query_agent(top_result, st.session_state.session_id)

            # Retrieve Imagen-generated art for the matched restaurant
            image_uri = doc_vector['imageUri']
            image_markdown = display_image_from_gcs(image_uri.split("/", 1)[0], image_uri.split("/", 1)[1])

            # Instantiate content placeholder and append
            assistant_content = ""
            assistant_content += f"<h5>My top pick for you...</h5>"
            assistant_content += image_markdown
            assistant_content += f"""<p><img src="https://pbs.twimg.com/profile_images/1695024885070737408/-M-HSH5P_400x400.jpg" alt="Google Deepmind" width="20" height="20">
            <small> AI-generated by Google Imagen</small></p>"""
            assistant_content += f"<h3>{doc_vector['restaurantName']}</h3>"
            assistant_content += f"<p><b>Why we think you'll love it:</b> {reviews['output']}</p>"
            assistant_content += f"""<p><img src="https://static.vecteezy.com/system/resources/previews/012/871/377/non_2x/google-maps-icon-google-product-illustration-free-png.png" alt="Google Maps" width="14" height="20">
            <small> Based on Google Maps reviews</small><hr style="border: 0; border-top: 1px dotted #ccc;"></p>"""

        # When the chat model determines that the user needs more assistance towards a recommendation
        else:

            assistant_content = ""
            assistant_content += response.text
        
        st.markdown(assistant_content, unsafe_allow_html=True)

    # Add new assistant message to session state
    st.session_state.messages.append({"role": "assistant", "content": assistant_content})