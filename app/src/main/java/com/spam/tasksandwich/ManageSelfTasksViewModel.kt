package com.spam.tasksandwich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await



/**
 * Represents the complete state of the AddSelfTaskScreen UI.
 * @param isLoading True if a save operation is in progress.
 * @param error A string containing an error message if an operation failed.
 * @param saveResult The result of the last save operation, used to trigger UI events.
 */
data class ManageSelfTasksUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val personalTasks: List<Task> = emptyList(),
    val isTaskSaved: Boolean = false // Use a simple boolean
)

/**
 * ViewModel for the AddSelfTaskScreen. Handles business logic for validating and
 * saving new personal tasks to Firestore.
 */
class ManageSelfTasksViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val currentUser = auth.currentUser

    private val _uiState = MutableStateFlow(ManageSelfTasksUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenForPersonalTasks()
    }

    private fun listenForPersonalTasks() {
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        db.collection("tasks")
            .whereEqualTo("assignedToUserId", currentUser.uid)
            .whereEqualTo("isPersonal", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val tasks = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }
                    _uiState.update { it.copy(isLoading = false, personalTasks = tasks) }
                }
            }
    }

    /**
     * Validates user input and saves a new personal task to Firestore.
     * On success, it updates the UI state to allow the user to add another task.
     *
     * @param title The name of the task.
     * @param pointsStr The point value of the task as a String.
     * @param repeatOption The selected repetition frequency (e.g., "Never", "Every Day").
     */
    fun saveTask(title: String, repeatOption: String) { // <-- REMOVED pointsStr parameter
        val currentUser = auth.currentUser ?: return

        // Validation is now simpler
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "Task name cannot be empty.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val userName = userDoc.getString("name") ?: "Myself"

                val taskData = hashMapOf(
                    "title" to title,
                    "points" to 1, // <-- HARDCODED point value to 1
                    "repeatOption" to repeatOption,
                    "status" to "assigned",
                    "createdAt" to Timestamp.now(),
                    "isPersonal" to true,
                    "assignedToUserId" to currentUser.uid,
                    "assignedByUserId" to currentUser.uid,
                    "assignedByName" to userName
                )

                val newDocRef = db.collection("tasks").add(taskData).await()

                val newTask = Task(
                    id = newDocRef.id,
                    title = title,
                    points = 1, // Use the hardcoded value here too
                    repeatOption = repeatOption,
                    isPersonal = true,
                    assignedByName = userName
                )

                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        isTaskSaved = true,
                        personalTasks = listOf(newTask) + currentState.personalTasks
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    fun deleteTask(taskId: String) {
        if (taskId.isBlank()) return

        // 1. Optimistic UI Update: Immediately remove the task from the local state.
        _uiState.update { currentState ->
            currentState.copy(
                personalTasks = currentState.personalTasks.filterNot { it.id == taskId }
            )
        }

        // 2. Perform the backend operation in the background.
        viewModelScope.launch {
            try {
                db.collection("tasks").document(taskId).delete().await()
                // If this succeeds, the real-time listener will eventually get the
                // same state we already set, so the UI won't change again.
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete task: ${e.message}") }
                // Optional: In a production app, you could add logic here
                // to re-fetch the list if the deletion fails.
            }
        }
    }

    /**
     * Resets the saveResult state to Idle. This should be called from the UI
     * after the success or failure state has been handled (e.g., after a snackbar is shown)
     * to prevent the event from being triggered again on configuration change.
     */
    fun resetSaveState() {
        _uiState.update { it.copy(isTaskSaved = false) }
    }
}