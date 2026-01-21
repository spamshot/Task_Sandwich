package com.spam.tasksandwich


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// This sealed class represents the possible outcomes of our check
sealed class AuthState {
    object Loading : AuthState()
    object AuthenticatedAndProfileComplete : AuthState()
    object AuthenticatedButProfileIncomplete : AuthState()
    object Unauthenticated : AuthState()
}

class SplashViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState = _authState.asStateFlow()

    init {
        checkUserStatus()
    }

    fun checkUserStatus() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            // Case 1: No user is logged in
            _authState.value = AuthState.Unauthenticated
            return
        }

        // Case 2: User is logged in, now check for their profile document
        viewModelScope.launch {
            val db = Firebase.firestore
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists() && !document.getString("name").isNullOrBlank()) {
                        // Case 2a: Profile is complete
                        _authState.value = AuthState.AuthenticatedAndProfileComplete
                    } else {
                        // Case 2b: Document doesn't exist or name is blank
                        _authState.value = AuthState.AuthenticatedButProfileIncomplete
                    }
                }
                .addOnFailureListener {
                    // If we can't check Firestore, it's safest to assume they are unauthenticated
                    _authState.value = AuthState.Unauthenticated
                }
        }
    }
}