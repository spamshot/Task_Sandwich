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

    fun saveProfile(name: String, age: String, role: String) {
        if (name.isBlank() || name.length > 10) {
            _uiState.update { it.copy(error = "Name cannot be empty & must be less than 10 characters.") }
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(error = "User not logged in.") }
            return
        }

        val userProfile = hashMapOf(
            "name" to name,
            "age" to age.toIntOrNull(),
            "email" to currentUser.email,
            "selectedIconId" to "avatar_1",
            "selectedBackgroundId" to "bg_stars",
            "role" to role // ✅ now set by user choice instead of hardcoded
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
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}