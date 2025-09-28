package com.spam.tasksandwich

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spam.tasksandwich.ui.theme.TaskSandwichTheme


@Composable
fun ProfileSetupScreen(
    // The viewModel is provided by the navigation library's lifecycle scope
    profileSetupViewModel: ProfileSetupViewModel = viewModel(),
    // This is the callback to navigate to the next screen (e.g., Home)
    onProfileSaved: () -> Unit
) {
    // 1. STATE MANAGEMENT
    // Get the UI state (isLoading, error, etc.) from the ViewModel
    val uiState by profileSetupViewModel.uiState.collectAsState()

    // Local state for the text fields. `rememberSaveable` survives screen rotation.
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }

    // State for the scrollable column
    val scrollState = rememberScrollState()

    // 2. SIDE EFFECTS
    // This block runs whenever `uiState.isProfileSaved` changes to true.
    // It calls the navigation callback exactly once.
    LaunchedEffect(uiState.isProfileSaved) {
        if (uiState.isProfileSaved) {
            onProfileSaved()
        }
    }

    // 3. UI LAYOUT
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                // THE FIX: This makes the entire column scrollable when content overflows
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Tell us about yourself",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = age,
                onValueChange = { age = it },
                label = { Text("Your Age (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 4. UI FEEDBACK (Error Messages)
            // Show an error message from the ViewModel if one exists
            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // 5. CONNECTING TO VIEWMODEL (The Button)
            Button(
                onClick = {
                    profileSetupViewModel.saveProfile(name, age)
                },
                modifier = Modifier.fillMaxWidth(),
                // Disable the button while loading to prevent multiple clicks
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary // Make it visible on the button
                    )
                } else {
                    Text("Save and Continue")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileSetupScreenPreview() {
    TaskSandwichTheme {
        ProfileSetupScreen(onProfileSaved = {})
    }
}