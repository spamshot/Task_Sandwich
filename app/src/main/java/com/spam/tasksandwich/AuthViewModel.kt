package com.spam.tasksandwich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// 1. Define the states the UI can be in
data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val authSuccess: Boolean = false
)

// 2. Define the events the UI can send
sealed interface AuthEvent {
    data class Login(val email: String, val password: String) : AuthEvent
    data class SignUp(val email: String, val password: String, val confirmPassword: String) : AuthEvent
}


class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow() // The UI will observe this state

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.Login -> {
                loginUser(event.email, event.password)
            }
            is AuthEvent.SignUp -> {
                signUpUser(event.email, event.password, event.confirmPassword)
            }
        }
    }

    private fun loginUser(email: String, password: String) {
        // Basic validation
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password cannot be empty.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _uiState.update { it.copy(isLoading = false, authSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun signUpUser(email: String, password: String, confirmPassword: String) {
        // Basic validation
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password cannot be empty.") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(error = "Passwords do not match.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                // Here, we would also create the user document in Firestore.
                // We'll add that in a later step.
                _uiState.update { it.copy(isLoading = false, authSuccess = true) }
            } catch (e: Exception) {
                val errorMessage = if (e is FirebaseAuthUserCollisionException) {
                    "An account with this email already exists."
                } else {
                    e.message
                }
                _uiState.update { it.copy(isLoading = false, error = errorMessage) }
            }
        }
    }
}