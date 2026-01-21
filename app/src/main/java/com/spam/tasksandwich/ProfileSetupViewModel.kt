package com.spam.tasksandwich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProfileSetupUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isProfileSaved: Boolean = false
)

class ProfileSetupViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState = _uiState.asStateFlow()

    fun saveProfile(name: String, age: String) {
        // Basic validation
        if (name.isBlank() ) { // || age.isBlank()
            _uiState.update { it.copy(error = "Name and age cannot be empty.") }
            return
        }


        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(error = "User not logged in.") }
            return
        }

        // Create a user object to save. This is where you'll add more fields later.
        val userProfile = hashMapOf(
            "name" to name,
            "age" to age.toIntOrNull(), // Convert age to number
            "email" to currentUser.email,
            // Add default values for new fields from our plan
            "selectedIconId" to "avatar_1", // Default icon
            "selectedBackgroundId" to "bg_stars", // Default background
            // The global role for creating/joining groups
            "role" to "parent" // We can add UI for this choice later
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // We use the user's UID as the document ID.
                // This creates a permanent link between their auth account and their data.
                db.collection("users").document(currentUser.uid)
                    .set(userProfile)
                    .await() // from the coroutines-play-services library

                _uiState.update { it.copy(isLoading = false, isProfileSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}