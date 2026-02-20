package com.spam.tasksandwich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
        // FIX 3: Trim whitespace from name before validation.
        val trimmedName = name.trim()

        if (trimmedName.isBlank() || trimmedName.length > 10) {
            _uiState.update { it.copy(error = "Name must be between 1 and 10 characters.") }
            return
        }

        // FIX 2: Clamp age to a valid range instead of accepting any number.
        val ageInt = age.toIntOrNull()
        val validatedAge = if (ageInt != null && ageInt in 1..120) ageInt else null

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(error = "User not logged in.") }
            return
        }

        val userProfile = hashMapOf(
            "name" to trimmedName, // ✅ trimmed
            "age" to validatedAge, // ✅ validated range or null
            "email" to currentUser.email,
            "selectedIconId" to "avatar_1",
            "selectedBackgroundId" to "bg_stars",
            "role" to "parent" // TODO: make this a user choice in a future step
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                db.collection("users").document(currentUser.uid)
                    .set(userProfile)
                    .await()
                _uiState.update { it.copy(isLoading = false, isProfileSaved = true) }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in ProfileSetupViewModel: Save Profile")
                FirebaseCrashlytics.getInstance().setCustomKey("Save Profile", "Set up profile failed to save")
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(isLoading = false, error = "Could not save profile. Please try again.") }
            }
        }
    }

    // FIX 4: Reset flag after navigation so back-navigation doesn't re-trigger it.
    fun onProfileSaveHandled() {
        _uiState.update { it.copy(isProfileSaved = false) }
    }
}