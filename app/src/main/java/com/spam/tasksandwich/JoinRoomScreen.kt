package com.spam.tasksandwich

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun JoinRoomScreen(
    joinRoomViewModel: JoinRoomViewModel = viewModel(),
    // Callback that takes the room ID to navigate to
    onJoinSuccess: (String) -> Unit
) {
    val uiState by joinRoomViewModel.uiState.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }

    // This effect will trigger navigation when the ViewModel signals success
    LaunchedEffect(uiState.joinSuccessRoomId) {
        uiState.joinSuccessRoomId?.let { roomId ->
            onJoinSuccess(roomId)
            joinRoomViewModel.onNavigationHandled() // Reset the state
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Join a Room", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Enter the 6-digit code from your parent or teacher.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it },
                label = { Text("6-Digit Code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = { joinRoomViewModel.joinRoom(code) },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Join Room")
                }
            }
        }
    }
}