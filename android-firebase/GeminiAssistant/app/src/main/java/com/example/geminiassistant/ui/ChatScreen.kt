package com.example.geminiassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geminiassistant.R
import com.example.geminiassistant.data.ChatMessage
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    hasRecordAudioPermission: Boolean,
    isAccessibilityEnabled: Boolean, // Add this parameter
    onRequestRecordAudioPermission: () -> Unit,
    onRequestAccessibilityPermission: () -> Unit // Add this parameter
) {
    val chatMessages by remember { derivedStateOf { viewModel.chatMessages } }
    val uiState by viewModel.uiState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll to bottom when new message arrives
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(chatMessages.size - 1)
            }
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                // Show Accessibility warning if needed for UI actions
                if (!isAccessibilityEnabled) { // Show warning persistently if needed
                    AccessibilityWarningBanner(onRequestAccessibilityPermission)
                }
                // Show error messages
                errorMessage?.let { message ->
                    ErrorBanner(message) { viewModel.clearError() }
                }
                ChatBottomBar(
                    uiState = uiState,
                    hasPermission = hasRecordAudioPermission,
                    onMicClick = {
                        if (hasRecordAudioPermission) {
                            viewModel.startListening()
                        } else {
                            onRequestRecordAudioPermission()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatMessages, key = { it.id }) { message ->
                ChatMessageItem(message)
            }
            // Show typing/processing indicator
            if (uiState == UiState.PROCESSING || uiState == UiState.EXECUTING) {
                item {
                    ProcessingIndicator()
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f) // Max width for message bubble
                .clip(
                    RoundedCornerShape(
                        topStart = if (message.isUser) 16.dp else 0.dp,
                        topEnd = if (message.isUser) 0.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(
                    if (message.isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                )
                .padding(12.dp)
        ) {
            Text(text = message.text, color = if (message.isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun ChatBottomBar(
    uiState: UiState,
    hasPermission: Boolean,
    onMicClick: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onMicClick,
                enabled = (uiState == UiState.IDLE || uiState == UiState.ERROR) && hasPermission,
                modifier = Modifier.size(56.dp), // Keep button size circular
                // --- ADD THIS TO REMOVE PADDING ---
                contentPadding = PaddingValues(0.dp)
                // --- END OF ADDED PADDING ---
            ) {
                when (uiState) {
                    UiState.LISTENING -> Text(stringResource(R.string.listening), fontSize = 10.sp)
                    else -> Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.chat_input_hint),
                        // Now this size should have more effect
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProcessingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Assistant is working...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}


@Composable
fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(8.dp),
        action = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Text(message)
    }
}

@Composable
fun AccessibilityWarningBanner(onRequestAccessibilityPermission: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        action = {
            Button(onClick = onRequestAccessibilityPermission) {
                Text(stringResource(R.string.go_to_accessibility_settings))
            }
        },
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(stringResource(R.string.error_permission_accessibility))
    }
}