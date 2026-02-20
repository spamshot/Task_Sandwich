package com.spam.tasksandwich


import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// This sealed class represents the possible outcomes of our check
sealed class AuthState {
    object Loading : AuthState()
    object AuthenticatedAndProfileComplete : AuthState()
    object AuthenticatedButProfileIncomplete : AuthState()
    object Unauthenticated : AuthState()
    object Error : AuthState() // FIX 2: distinct error state vs unauthenticated
}

class SplashViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState = _authState.asStateFlow()

    init {
        checkUserStatus()
    }

    // FIX 3: Public so a retry button on the splash screen can call this.
    fun checkUserStatus() {
        _authState.value = AuthState.Loading // ✅ reset to loading on retry

        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            _authState.value = AuthState.Unauthenticated
            return
        }

        // FIX 1: Use .await() instead of mixing coroutine launch with callbacks.
        // The original wrapped addOnSuccessListener inside viewModelScope.launch,
        // which means the launch block completed immediately and the callbacks
        // ran on their own — the coroutine scope provided no value.
        viewModelScope.launch {
            try {
                val document = Firebase.firestore
                    .collection("users")
                    .document(currentUser.uid)
                    .get()
                    .await() // ✅ pure coroutine — no callback mixing

                _authState.value = if (
                    document.exists() && !document.getString("name").isNullOrBlank()
                ) {
                    AuthState.AuthenticatedAndProfileComplete
                } else {
                    AuthState.AuthenticatedButProfileIncomplete
                }

            } catch (e: Exception) {
                Log.e("SplashViewModel", "Failed to check user status: ${e.message}")
                // FIX 2: A network failure is NOT the same as being logged out.
                // Show an error state so the splash screen can offer a retry,
                // rather than silently redirecting to the login screen.
                _authState.value = AuthState.Error
            }
        }
    }
}
