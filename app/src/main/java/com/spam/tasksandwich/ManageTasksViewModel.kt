package com.spam.tasksandwich

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.Calendar

data class ManageTasksUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val members: List<RoomMember> = emptyList(),
    val assignedTasks: List<Task> = emptyList(),
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class ManageTasksViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val roomId: String = savedStateHandle["roomId"]!!

    private val _uiState = MutableStateFlow(ManageTasksUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenForMembers()
        listenForAssignedTasks()
    }

    private fun listenForMembers() {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Room ID is missing.") }
            return
        }

        db.collection("groups").document(roomId).collection("groupMembers")
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load members.") }
                    return@addSnapshotListener
                }
                snapshot?.let { querySnapshot ->
                    viewModelScope.launch {
                        val memberJobs = querySnapshot.documents.map { memberDoc ->
                            async {
                                val userDoc = db.collection("users").document(memberDoc.id).get().await()
                                RoomMember(
                                    userId = memberDoc.id,
                                    name = userDoc.getString("name") ?: "Unknown User",
                                    totalPointsInGroup = memberDoc.getLong("totalPointsInGroup")?.toInt() ?: 0
                                )
                            }
                        }
                        val memberList = memberJobs.awaitAll()
                        _uiState.update { it.copy(isLoading = false, members = memberList) }
                    }
                }
            }
    }


    private fun listenForAssignedTasks() {
        db.collection("tasks")
            .whereEqualTo("groupId", roomId)
            .whereEqualTo("status", "assigned")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val tasks = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }
                    // We can group them here to make the UI display easier
                    _uiState.update { it.copy(isLoading = false, assignedTasks = tasks) }
                }
            }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            db.collection("tasks").document(taskId).delete().await()
        }
    }

    fun saveTask(title: String, pointsStr: String, repeatOption: String, assignedTo: RoomMember?, expiresInDays: Int) {
        val currentUser = auth.currentUser
        // Find the admin's name from the member list to use as the 'assignedByName'
        val adminName = uiState.value.members.find { it.userId == currentUser?.uid }?.name ?: "Admin"

        // --- Validation ---
        if (currentUser == null || assignedTo == null || title.isBlank()) {
            _uiState.update { it.copy(error = "Please fill out all fields.") }
            return
        }
        val points = pointsStr.toIntOrNull()
        if (points == null || points < 0) {
            _uiState.update { it.copy(error = "Please enter a valid number for points.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                // Determine if we are assigning to a single person or all members.
                val membersToAssign = if (assignedTo.userId == "all") {
                    // Filter out the "All Members" placeholder from the list.
                    uiState.value.members.filter { it.userId != "all" }
                } else {
                    // Create a list containing just the single selected member.
                    listOf(assignedTo)
                }

                // If for some reason the list of members to assign to is empty, abort.
                if (membersToAssign.isEmpty()) {
                    _uiState.update { it.copy(isSaving = false, error = "No members to assign task to.") }
                    return@launch
                }

                val sharedTaskId = UUID.randomUUID().toString()

                // Create a single, representative Task object for the immediate UI update.
                val representativeTaskForUi = Task(
                    id = "temp_${UUID.randomUUID()}", // A temporary, unique ID for the UI key
                    title = title,
                    points = points,
                    repeatOption = repeatOption,
                    groupId = roomId,
                    sharedTaskId = sharedTaskId,
                    assignedByName = adminName,
                    // Calculate the due date here to show it in the UI immediately
                    dueDate = if (expiresInDays > 0) {
                        val calendar = java.util.Calendar.getInstance()
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, expiresInDays)
                        Timestamp(calendar.time)
                    } else {
                        null
                    }
                )

                // --- OPTIMISTIC UI UPDATE ---
                // This is the key fix: We immediately update the local state.
                _uiState.update { currentState ->
                    currentState.copy(
                        // Prepend the new task to the front of the assigned tasks list.
                        assignedTasks = listOf(representativeTaskForUi) + currentState.assignedTasks,
                        saveSuccess = true, // Signal to the UI to clear the form fields.
                        isSaving = false
                    )
                }
                // ---------------------------

                // --- Backend Operation ---
                // Now, perform the actual database writes in the background.
                val batch = db.batch()
                membersToAssign.forEach { member ->
                    val newTaskRef = db.collection("tasks").document()
                    val taskData = hashMapOf(
                        "title" to title,
                        "points" to points,
                        "repeatOption" to repeatOption,
                        "groupId" to roomId,
                        "assignedToUserId" to member.userId,
                        "assignedByUserId" to currentUser.uid,
                        "assignedByName" to adminName,
                        "status" to "assigned",
                        "isPersonal" to false,
                        "sharedTaskId" to sharedTaskId,
                        "createdAt" to Timestamp.now(),
                        "dueDate" to representativeTaskForUi.dueDate // Reuse the calculated dueDate
                    )
                    batch.set(newTaskRef, taskData)
                }
                batch.commit().await()
                // The real-time listener will eventually get the "real" data from the server,
                // which will seamlessly replace our temporary optimistic update.

            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
    fun onSaveHandled() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}