package com.spam.tasksandwich

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spam.tasksandwich.ui.theme.TaskSandwichTheme

// An enum to hold the state of the screen
enum class AuthMode {
    LOGIN,
    SIGN_UP
}

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel = viewModel(),
    onAuthSuccess: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()
    var authMode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.authSuccess) {
        if (uiState.authSuccess) onAuthSuccess()
    }

    // FIX 1: Scaffold so status bar insets are respected.
    Scaffold { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)           // ✅ status bar respected
                .padding(horizontal = 24.dp)
                .imePadding()                       // FIX 2a: keyboard pushes content up
                .verticalScroll(rememberScrollState()), // FIX 2b: scrollable on small screens
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // FIX 4: Show logo in both modes for visual consistency.
            // Show different heading text below it instead.
            Image(
                painter = painterResource(id = R.drawable.icontasksandwich),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(if (authMode == AuthMode.LOGIN) 142.dp else 80.dp) // Smaller in sign-up
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (authMode == AuthMode.SIGN_UP) {
                Text(
                    text = "Create an Account",
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email Address") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next  // ✅ moves focus to next field
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (authMode == AuthMode.LOGIN) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (authMode == AuthMode.LOGIN && !uiState.isLoading) {
                            authViewModel.onEvent(AuthEvent.Login(email, password))
                        }
                    }
                ),
                singleLine = true
            )

            if (authMode == AuthMode.SIGN_UP) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!uiState.isLoading) {
                                authViewModel.onEvent(AuthEvent.SignUp(email, password, confirmPassword))
                            }
                        }
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // FIX 5: Inline loading indicator inside the button, consistent with other screens.
            // Original showed the button + a separate spinner below it simultaneously.
            Button(
                onClick = {
                    if (authMode == AuthMode.LOGIN) {
                        authViewModel.onEvent(AuthEvent.Login(email, password))
                    } else {
                        authViewModel.onEvent(AuthEvent.SignUp(email, password, confirmPassword))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (authMode == AuthMode.LOGIN) "Login" else "Sign Up")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = {
                    authMode = if (authMode == AuthMode.LOGIN) AuthMode.SIGN_UP else AuthMode.LOGIN
                    // FIX 3: Clear the error when switching modes so the previous
                    // error message doesn't linger on the new form.
                    // Add this to your AuthViewModel:
                    //   fun clearError() { _uiState.update { it.copy(error = null) } }
                    // Then call it here:
                    authViewModel.clearError() // ✅ requires clearError() in AuthViewModel
                }
            ) {
                Text(
                    if (authMode == AuthMode.LOGIN) "Don't have an account? Sign Up"
                    else "Already have an account? Login"
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AuthScreenLoginPreview() {
    TaskSandwichTheme {
        AuthScreen(onAuthSuccess = {})
    }
}