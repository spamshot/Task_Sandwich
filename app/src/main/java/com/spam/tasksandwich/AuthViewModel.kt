package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
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

sealed interface AuthEvent {
    data class Login(val email: String, val password: String) : AuthEvent
    data class SignUp(val email: String, val password: String, val confirmPassword: String) : AuthEvent
}

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.Login -> loginUser(event.email, event.password)
            is AuthEvent.SignUp -> signUpUser(event.email, event.password, event.confirmPassword)
        }
    }

    // FIX 1: Called by AuthScreen when the user switches between Login/Sign Up modes
    // so the previous error message doesn't linger on the new form.
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // FIX 7: Called by AuthScreen's LaunchedEffect after navigation so that if
    // the user ever returns to this screen, the effect doesn't re-fire.
    fun onAuthHandled() {
        _uiState.update { it.copy(authSuccess = false) }
    }

    private fun loginUser(email: String, password: String) {
        // FIX 3: Trim email whitespace — trailing spaces from mobile autocomplete
        // cause Firebase to fail with a confusing "badly formatted" error.
        val trimmedEmail = email.trim()

        if (trimmedEmail.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password cannot be empty.") }
            return
        }

        // FIX 4: Basic local email format check before hitting the network.
        if (!isValidEmail(trimmedEmail)) {
            _uiState.update { it.copy(error = "Please enter a valid email address.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                auth.signInWithEmailAndPassword(trimmedEmail, password).await()
                _uiState.update { it.copy(isLoading = false, authSuccess = true) }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Login failed: ${e::class.simpleName}")

                FirebaseCrashlytics.getInstance().log("Error in AuthViewModel: User login")
                // FIX 6: Don't log raw email to Crashlytics — it's PII.
                // Log only the domain portion (after @) for debugging purposes.
                val emailDomain = trimmedEmail.substringAfter("@", missingDelimiterValue = "unknown")
                FirebaseCrashlytics.getInstance().setCustomKey("email_domain", emailDomain)
                FirebaseCrashlytics.getInstance().recordException(e)

                // FIX 2: Map Firebase exceptions to friendly messages.
                _uiState.update { it.copy(isLoading = false, error = mapAuthError(e)) }
            }
        }
    }

    private fun signUpUser(email: String, password: String, confirmPassword: String) {
        // FIX 3: Trim email whitespace.
        val trimmedEmail = email.trim()

        if (trimmedEmail.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Email and password cannot be empty.") }
            return
        }

        // FIX 4: Validate email format locally before the network call.
        if (!isValidEmail(trimmedEmail)) {
            _uiState.update { it.copy(error = "Please enter a valid email address.") }
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
                auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                _uiState.update { it.copy(isLoading = false, authSuccess = true) }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign up failed: ${e::class.simpleName}")
                FirebaseCrashlytics.getInstance().recordException(e)
                // FIX 2: Friendly error messages for sign-up failures.
                _uiState.update { it.copy(isLoading = false, error = mapAuthError(e)) }
            }
        }
    }

    // ============================================================
    // FIX 2: Maps Firebase auth exceptions to human-readable strings.
    // Firebase's default messages are technical SDK strings not
    // suitable for displaying directly to users.
    // ============================================================
    private fun mapAuthError(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidCredentialsException -> {
                // Covers wrong password AND badly formatted email
                when (e.errorCode) {
                    "ERROR_WRONG_PASSWORD",
                    "ERROR_INVALID_CREDENTIAL" -> "Incorrect email or password."
                    "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
                    else -> "Invalid credentials. Please try again."
                }
            }
            is FirebaseAuthInvalidUserException -> {
                // Account doesn't exist or has been disabled
                when (e.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> "No account found with this email."
                    "ERROR_USER_DISABLED" -> "This account has been disabled."
                    else -> "Account error. Please try again."
                }
            }
            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists."
            is FirebaseAuthWeakPasswordException ->
                "Password must be at least 6 characters."
            is FirebaseNetworkException ->
                "No internet connection. Please check your network."
            is FirebaseTooManyRequestsException ->
                "Too many attempts. Please wait a moment and try again."
            else -> "Something went wrong. Please try again."
        }
    }

    // ============================================================
    // FIX 4: Simple local email validation.
    // Catches obvious typos before hitting Firebase.
    // ============================================================
    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}






