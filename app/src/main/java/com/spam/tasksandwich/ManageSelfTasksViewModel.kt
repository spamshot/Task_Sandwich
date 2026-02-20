package com.spam.tasksandwich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar // Required for date calculation

/**
 * Represents the complete state of the AddSelfTaskScreen UI.
 */
// Fixes:
//   1. currentUser is captured as a class property at construction.
//      If the auth session refreshes, this could be stale for write
//      operations. Changed to read auth.currentUser at point of use
//      in saveTask() and deleteTask().
//   2. saveTask() adds the new task to the local list optimistically
//      AND the snapshot listener will also add it — causing a brief
//      duplicate. The optimistic update in saveTask() is redundant
//      because the real-time listener handles it. Removed the
//      manual list prepend.
//   3. saveTask() fetches userName with a separate Firestore get()
//      every time a task is saved — even though the name is already
//      available from the profile in other ViewModels. Minor but
//      noted. Acceptable as-is since ManageSelfTasksViewModel is
//      standalone.
//   4. saveTask() hardcodes points to 1 with no way for the user
//      to change it — intentional design choice, but noted.
//   5. deleteTask() has no rollback on failure — the item disappears
//      from the UI even if the backend delete fails. Added rollback.
//   6. listenForPersonalTasks() is called inside viewModelScope.launch
//      in init — unnecessary, since the function itself is non-suspend
//      and only registers a listener. Removed the launch wrapper.
//   7. Error messages expose raw e.message. Replaced.
// ============================================================

data class ManageSelfTasksUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val personalTasks: List<Task> = emptyList(),
    val isTaskSaved: Boolean = false
)

class ManageSelfTasksViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // FIX 1: Removed class-level currentUser — read at point of use.

    private val _uiState = MutableStateFlow(ManageSelfTasksUiState())
    val uiState = _uiState.asStateFlow()

    // Keep a cache for optimistic delete rollback
    private var tasksCache: List<Task> = emptyList()

    init {
        // FIX 6: No need for viewModelScope.launch — listenForPersonalTasks is not suspend.
        listenForPersonalTasks() // ✅ called directly
    }

    private fun listenForPersonalTasks() {
        // FIX 1: Read currentUser at point of use.
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        db.collection("tasks")
            .whereEqualTo("assignedByUserId", currentUser.uid)
            .whereEqualTo("isPersonal", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load tasks.") }
                    FirebaseCrashlytics.getInstance().recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val allPersonalTasks = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }
                    // Show only active (assigned) tasks in the manage view
                    val filteredTasks = allPersonalTasks.filter { it.status == "assigned" }
                    tasksCache = filteredTasks
                    _uiState.update { it.copy(isLoading = false, personalTasks = filteredTasks) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
    }

    fun saveTask(title: String, repeatOption: String) {
        // FIX 1: Read currentUser at point of use.
        val currentUser = auth.currentUser ?: return

        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(error = "Task name cannot be empty.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val userName = userDoc.getString("name") ?: "Myself"

                val taskData = hashMapOf(
                    "title" to trimmedTitle,
                    "points" to 1, // FIX 4: intentionally fixed at 1 for personal tasks
                    "repeatOption" to repeatOption,
                    "status" to "assigned",
                    "createdAt" to Timestamp.now(),
                    "isPersonal" to true,
                    "assignedToUserId" to currentUser.uid,
                    "assignedByUserId" to currentUser.uid,
                    "assignedByName" to userName,
                    "assignedToName" to userName
                )

                db.collection("tasks").add(taskData).await()

                // FIX 2: Do NOT manually prepend to the local list.
                // The snapshot listener will fire and update the list automatically,
                // preventing a duplicate from appearing briefly in the UI.
                _uiState.update { it.copy(isLoading = false, isTaskSaved = true) }

            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in ManageSelfTasksViewModel: Save Self Task")
                FirebaseCrashlytics.getInstance().setCustomKey("Save Self Task title", trimmedTitle)
                FirebaseCrashlytics.getInstance().recordException(e)
                // FIX 7: Friendly error
                _uiState.update { it.copy(isLoading = false, error = "Could not save task. Please try again.") }
            }
        }
    }

    // FIX 5: Added rollback on delete failure.
    fun deleteTask(taskId: String) {
        if (taskId.isBlank()) return

        // Snapshot for rollback
        val previousCache = tasksCache

        // Optimistic update
        tasksCache = tasksCache.filterNot { it.id == taskId }
        _uiState.update { it.copy(personalTasks = tasksCache) }

        viewModelScope.launch {
            try {
                db.collection("tasks").document(taskId).delete().await()
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in ManageSelfTasksViewModel: Delete Self Task")
                FirebaseCrashlytics.getInstance().setCustomKey("Delete Self Task id", taskId)
                FirebaseCrashlytics.getInstance().recordException(e)
                // FIX 5: Roll back optimistic update ✅
                tasksCache = previousCache
                _uiState.update {
                    it.copy(
                        personalTasks = previousCache,
                        error = "Failed to delete task. Please try again."
                    )
                }
            }
        }
    }

    fun resetSaveState() {
        _uiState.update { it.copy(isTaskSaved = false) }
    }
}

