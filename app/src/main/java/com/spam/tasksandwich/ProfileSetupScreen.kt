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
    profileSetupViewModel: ProfileSetupViewModel = viewModel(),
    onProfileSaved: () -> Unit
) {
    val uiState by profileSetupViewModel.uiState.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.isProfileSaved) {
        if (uiState.isProfileSaved) onProfileSaved()
    }

    // FIX 1: Wrap in Scaffold so status bar inset is respected.
    Scaffold { scaffoldPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding) // ✅ status bar respected
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    // FIX 2: imePadding ensures the keyboard doesn't cover the button
                    .imePadding() // ✅ keyboard pushes content up
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
                    onValueChange = { if (it.length <= 10) name = it },
                    label = { Text("Your Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = age,
                    onValueChange = { if (it.length <= 2) age = it },
                    label = { Text("Your Age (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(32.dp))

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Button(
                    onClick = { profileSetupViewModel.saveProfile(name, age) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save and Continue")
                    }
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