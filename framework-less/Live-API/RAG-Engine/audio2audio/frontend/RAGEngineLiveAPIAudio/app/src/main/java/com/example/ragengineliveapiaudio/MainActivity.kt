package com.example.ragengineliveapiaudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ragengineliveapiaudio.ui.theme.RAGEngineLiveAPIAudioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Handle the result of the permission request
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. You can now connect.
                viewModel.connect()
            } else {
                // Explain to the user that the feature is unavailable
                // You can show a dialog or a snackbar here
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RAGEngineLiveAPIAudioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }

        // Check for permission on launch
        checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted, proceed to connect
                viewModel.connect()
            }
            else -> {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val context = LocalContext.current

    val buttonText = when (uiState) {
        UiState.CONNECTED -> "Start Recording"
        UiState.RECORDING -> "Stop Recording"
        else -> "Record"
    }

    val isButtonEnabled = when (uiState) {
        UiState.CONNECTED, UiState.RECORDING -> true
        else -> false
    }

    val buttonColor = if (uiState == UiState.RECORDING) {
        ButtonDefaults.buttonColors(containerColor = Color.Red)
    } else {
        ButtonDefaults.buttonColors()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Gemini Live Audio Client",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Status: $statusText",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.onRecordButtonPressed(context) },
            enabled = isButtonEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = buttonColor
        ) {
            Text(text = buttonText, fontSize = 18.sp)
        }
    }
}