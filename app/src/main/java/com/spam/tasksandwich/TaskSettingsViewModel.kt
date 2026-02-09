package com.spam.tasksandwich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class TaskSettingsUiState(
    val isLoading: Boolean = true,
    val tasks: List<Task> = emptyList(),
    val error: String? = null
)

class TaskSettingsViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(TaskSettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchAssignedTasks()
    }

    private fun fetchAssignedTasks() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false, error = "User not logged in.") }
            return
        }

        // Query for all tasks created by the current user, order by most recent
        db.collection("tasks")
            .whereEqualTo("assignedToUserId", currentUser.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load tasks.") }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val taskList = snapshot.toObjects(Task::class.java).mapIndexed { index, task ->
                        task.copy(id = snapshot.documents[index].id) // Manually add the document ID
                    }
                    _uiState.update { it.copy(isLoading = false, tasks = taskList) }
                }
            }
    }

    fun deleteTask(taskId: String) {
        // 1. Optimistic UI Update: Immediately remove the task from the local state.
        _uiState.update { currentState ->
            currentState.copy(
                tasks = currentState.tasks.filterNot { it.id == taskId }
            )
        }

        // 2. Perform the backend operation.
        viewModelScope.launch {
            try {
                db.collection("tasks").document(taskId).delete().await()
                // If this succeeds, the real-time listener will eventually get the
                // same state we already set, so the UI won't change again.
            } catch (e: Exception) {

                FirebaseCrashlytics.getInstance().log("Error in TaskSettingsViewModel: Delete Task")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Delete Task", "Failed to delete task")

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)

                _uiState.update { it.copy(error = "Failed to delete task: ${e.message}") }
                // OPTIONAL: In a more complex app, you could add logic here
                // to add the task back to the list if the deletion fails,
                // and show a "Couldn't delete task" snackbar.
                // For now, just showing the error is sufficient.
            }
        }
    }
}