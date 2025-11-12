package com.spam.tasksandwich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface SaveResult {
    /** The default state before any save operation has been attempted. */
    object Idle : SaveResult

    /** Represents a successful save where the user remains on the screen to add another task. */
    object SuccessAndStay : SaveResult
}

/**
 * Represents the complete state of the AddSelfTaskScreen UI.
 * @param isLoading True if a save operation is in progress.
 * @param error A string containing an error message if an operation failed.
 * @param saveResult The result of the last save operation, used to trigger UI events.
 */
data class AddTaskUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveResult: SaveResult = SaveResult.Idle
)

/**
 * ViewModel for the AddSelfTaskScreen. Handles business logic for validating and
 * saving new personal tasks to Firestore.
 */
class AddSelfTaskViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(AddTaskUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * Validates user input and saves a new personal task to Firestore.
     * On success, it updates the UI state to allow the user to add another task.
     *
     * @param title The name of the task.
     * @param pointsStr The point value of the task as a String.
     * @param repeatOption The selected repetition frequency (e.g., "Never", "Every Day").
     */
    fun saveTask(title: String, pointsStr: String, repeatOption: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(error = "You must be logged in.") }
            return
        }

        // --- Input Validation ---
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "Task name cannot be empty.") }
            return
        }
        val points = pointsStr.ifBlank { "0" }.toIntOrNull()
        if (points == null || points < 0) {
            _uiState.update { it.copy(error = "Please enter a valid, non-negative number for points.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Fetch the user's name to store denormalized in the task document.
                // This saves us from having to do a separate lookup when displaying tasks.
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val userName = userDoc.getString("name") ?: "Myself"

                // --- Create the Task Data Object ---
                val taskData = hashMapOf(
                    "title" to title,
                    "points" to points,
                    "repeatOption" to repeatOption,
                    "status" to "assigned",
                    "createdAt" to Timestamp.now(),
                    "isPersonal" to true,
                    "assignedToUserId" to currentUser.uid, // Task is for the user
                    "assignedByUserId" to currentUser.uid, // Task is created by the user
                    "assignedByName" to userName
                )

                // --- Save to Firestore ---
                db.collection("tasks").add(taskData).await()
                _uiState.update { it.copy(isLoading = false, saveResult = SaveResult.SuccessAndStay) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Resets the saveResult state to Idle. This should be called from the UI
     * after the success or failure state has been handled (e.g., after a snackbar is shown)
     * to prevent the event from being triggered again on configuration change.
     */
    fun resetSaveState() {
        _uiState.update { it.copy(saveResult = SaveResult.Idle) }
    }
}