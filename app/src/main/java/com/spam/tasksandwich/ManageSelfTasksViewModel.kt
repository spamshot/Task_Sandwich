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
data class ManageSelfTasksUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val personalTasks: List<Task> = emptyList(),
    val isTaskSaved: Boolean = false
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
        viewModelScope.launch {
            listenForPersonalTasks()
        }
    }

    private fun listenForPersonalTasks() {
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        // 1. Query: Get all personal tasks created by the user.
        // We order by createdAt so the newest ones are at the top.
        db.collection("tasks")
            .whereEqualTo("assignedByUserId", currentUser.uid)
            .whereEqualTo("isPersonal", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = "Loading DB") }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // 2. Map documents to Task objects
                    val allPersonalTasks = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }

                    // 3. UPDATED FILTERING:
                    // We have removed the Calendar/Midnight Tomorrow logic.
                    // In the 'Manage' view, the user should see everything that is active.
                    val filteredTasks = allPersonalTasks.filter { task ->
                        // Only show tasks that are currently "assigned" (active).
                        // This prevents old "completed" versions of repeating tasks from cluttering the list.
                        task.status == "assigned"
                    }

                    // 4. Update UI state
                    _uiState.update { it.copy(isLoading = false, personalTasks = filteredTasks) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
    }

    /**
     * Validates user input and saves a new personal task to Firestore.
     */
    fun saveTask(title: String, repeatOption: String) {
        val currentUser = auth.currentUser ?: return

        if (title.isBlank()) {
            _uiState.update { it.copy(error = "Task name cannot be empty.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val userName = userDoc.getString("name") ?: "Myself"

                // If the user selects a "Repeat" option (e.g., Every Day),
                // we interpret "Expires In" as effectively 0 (Start Now).
                // If "Never" (One-time), we let them pick expiration in the UI?
                // (Based on your UI code, you might be handling Expiration calculation here
                // or just defaulting to null. The Cloud Function handles the rest).

                val taskData = hashMapOf(
                    "title" to title,
                    "points" to 1,
                    "repeatOption" to repeatOption,
                    "status" to "assigned",
                    "createdAt" to Timestamp.now(),
                    "isPersonal" to true,
                    "assignedToUserId" to currentUser.uid,
                    "assignedByUserId" to currentUser.uid,
                    "assignedByName" to userName,
                    "assignedToName" to userName
                    // Note: dueDate is null by default here, which means "Start Now"
                )

                val newDocRef = db.collection("tasks").add(taskData).await()

                val newTask = Task(
                    id = newDocRef.id,
                    title = title,
                    points = 1,
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

                FirebaseCrashlytics.getInstance().log("Error in ManageSelfTasksViewModel: Save Self Task")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Save Self Task", "Save Self Task")

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)

                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deleteTask(taskId: String) {
        if (taskId.isBlank()) return

        // 1. Optimistic UI Update
        _uiState.update { currentState ->
            currentState.copy(
                personalTasks = currentState.personalTasks.filterNot { it.id == taskId }
            )
        }

        // 2. Perform Backend Operation
        viewModelScope.launch {
            try {
                db.collection("tasks").document(taskId).delete().await()
            } catch (e: Exception) {

                FirebaseCrashlytics.getInstance().log("Error in ManageSelfTasksViewModel: Delete Self Task")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Delete Self Task", "Delete Self Task")

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)

                _uiState.update { it.copy(error = "Failed to delete task: ${e.message}") }
            }
        }
    }

    fun resetSaveState() {
        _uiState.update { it.copy(isTaskSaved = false) }
    }
}