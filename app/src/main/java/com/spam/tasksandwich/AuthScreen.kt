package com.spam.tasksandwich

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
    onAuthSuccess: () -> Unit // Callback to navigate away on success
) {
    // Observe the state from the ViewModel
    val uiState by authViewModel.uiState.collectAsState()

    // Local UI state for the text fields and mode
    var authMode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    // Use a LaunchedEffect to react to authSuccess
    LaunchedEffect(uiState.authSuccess) {
        if (uiState.authSuccess) {
            onAuthSuccess()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (authMode == AuthMode.LOGIN){
                Image(painter = painterResource(id = R.drawable.icontasksandwich), contentDescription = "Locked", modifier = Modifier.size(142.dp).clip(CircleShape))
            }else{
                Text(
                    text = "Create an Account",
                    style = MaterialTheme.typography.headlineLarge
                )
            }

//            Text(
//                text = if (authMode == AuthMode.LOGIN) "Welcome Back" else "Create an Account",
//                style = MaterialTheme.typography.headlineLarge
//            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email Address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (authMode == AuthMode.SIGN_UP) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirm Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Error Message Display from ViewModel state
            if (uiState.error != null) {
                Text(
                    text = uiState.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Action Button
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
                Text(if (authMode == AuthMode.LOGIN) "Login" else "Sign Up")
            }

            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = {
                    authMode = if (authMode == AuthMode.LOGIN) AuthMode.SIGN_UP else AuthMode.LOGIN
                    // Consider adding an event to clear the error in the ViewModel
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
        AuthScreen(onAuthSuccess = {}) // Pass an empty lambda for the preview
    }
}