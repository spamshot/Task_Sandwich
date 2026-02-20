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

// Fixes:
//   1. fetchAssignedTasks() uses addSnapshotListener but the
//      function is named "fetch" implying a one-time read.
//      The real-time listener is correct behavior, but the
//      name is misleading. Renamed to listenForAssignedTasks().
//   2. snapshot.toObjects(Task::class.java).mapIndexed is used
//      to manually pair tasks with their document IDs via index.
//      This is fragile — toObjects() and documents[] must stay
//      in the same order, which is not guaranteed after filtering.
//      Changed to the safer mapNotNull { doc -> doc.toObject()?.copy(id = doc.id) }
//      pattern used consistently in other ViewModels.
//   3. deleteTask() performs optimistic UI update but doesn't
//      restore the item on failure. At minimum the error message
//      should tell the user the task wasn't deleted. Added.
//   4. No input validation on deleteTask() — blank taskId would
//      silently call delete() on an empty document path.
//      Added blank check (already fixed in ProfileSettingsViewModel
//      but missing here).
// ============================================================

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

    // Keep a cache for optimistic update rollback
    private var tasksCache: List<Task> = emptyList()

    init {
        listenForAssignedTasks() // FIX 1: renamed from fetchAssignedTasks
    }

    // FIX 1: Renamed to reflect it's a real-time listener, not a one-shot fetch.
    private fun listenForAssignedTasks() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false, error = "User not logged in.") }
            return
        }

        db.collection("tasks")
            .whereEqualTo("assignedToUserId", currentUser.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load tasks.") }
                    FirebaseCrashlytics.getInstance().recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    // FIX 2: Safe ID mapping via mapNotNull instead of index-based pairing.
                    val taskList = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id) // ✅ safe, index-independent
                    }
                    tasksCache = taskList
                    _uiState.update { it.copy(isLoading = false, tasks = taskList) }
                }
            }
    }

    fun deleteTask(taskId: String) {
        // FIX 4: Guard against blank taskId silently deleting a bad path.
        if (taskId.isBlank()) return

        // Snapshot of current list for rollback on failure.
        val previousTasks = tasksCache

        // Optimistic update
        tasksCache = tasksCache.filterNot { it.id == taskId }
        _uiState.update { it.copy(tasks = tasksCache) }

        viewModelScope.launch {
            try {
                db.collection("tasks").document(taskId).delete().await()
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in TaskSettingsViewModel: Delete Task")
                FirebaseCrashlytics.getInstance().setCustomKey("Delete Task id", taskId)
                FirebaseCrashlytics.getInstance().recordException(e)

                // FIX 3: Roll back the optimistic update on failure so the
                // user knows the task was NOT deleted and can try again.
                tasksCache = previousTasks
                _uiState.update {
                    it.copy(
                        tasks = previousTasks,
                        error = "Failed to delete task. Please try again."
                    )
                }
            }
        }
    }
}