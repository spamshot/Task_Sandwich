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

data class AssignTaskUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val members: List<RoomMember> = emptyList(),
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class AssignTaskViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val roomId: String = savedStateHandle.get("roomId")!!

    private val _uiState = MutableStateFlow(AssignTaskUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenForMembers()
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

    fun saveTask(
        title: String,
        pointsStr: String,
        repeatOption: String,
        assignedTo: RoomMember?,
        expiresInDays: Int
    ) {
        val currentUser = auth.currentUser
        val adminName = uiState.value.members.find { it.userId == currentUser?.uid }?.name ?: "Admin"

        // Basic validation
        if (currentUser == null || assignedTo == null || title.isBlank()) {
            _uiState.update { it.copy(error = "Please fill out all fields.") }
            return
        }
        val points = pointsStr.toIntOrNull()
        if(points == null || points < 0) {
            _uiState.update { it.copy(error = "Please enter a valid number for points.") }
            return
        }


        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                var dueDate: Timestamp? = null
                if (expiresInDays > 0) {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, expiresInDays)
                    dueDate = Timestamp(calendar.time)
                }

                // Determine if we're assigning to one person or many
                val membersToAssign = if (assignedTo.userId == "all") {
                    // Filter out the "All Members" placeholder
                    uiState.value.members.filter { it.userId != "all" }
                } else {
                    // Create a list with just the single selected member
                    listOf(assignedTo)
                }


                // --- BUG FIX ---
                // Create a single, unique ID for this batch of assignments.
                // This allows us to de-duplicate when calculating "Earnable Pts".
                val sharedTaskId = UUID.randomUUID().toString()
                // ---------------

                val batch = db.batch()
                membersToAssign.forEach { member ->
                    val newTaskRef = db.collection("tasks").document() // Create a new unique task doc
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
                        "sharedTaskId" to sharedTaskId, // Add the shared ID to each task
                        "createdAt" to Timestamp.now(),
                        "dueDate" to dueDate
                    )
                    batch.set(newTaskRef, taskData)
                }

                batch.commit().await()
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun onSaveHandled() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}