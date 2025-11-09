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

// Data class for the auto-assign templates shown in the UI
data class AutoAssignTaskTemplate(
    val id: String,
    val title: String,
    val points: Int
)

// The complete UI state for the entire ManageTasksScreen
data class ManageTasksUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val members: List<RoomMember> = emptyList(),
    val autoAssignTemplates: List<AutoAssignTaskTemplate> = emptyList(),
    val repeatingTasks: List<Task> = emptyList(),
    val oneTimeTasks: List<Task> = emptyList(),
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
        listenForAutoAssignTemplates()
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
                        try {
                            val memberJobs = querySnapshot.documents.map { memberDoc ->
                                async {
                                    val userDoc = db.collection("users").document(memberDoc.id).get().await()
                                    RoomMember(
                                        userId = memberDoc.id,
                                        name = userDoc.getString("name") ?: "Unknown User",
                                        totalPointsInGroup = 0 // Not needed for this screen
                                    )
                                }
                            }
                            val memberList = memberJobs.awaitAll()
                            _uiState.update { it.copy(members = memberList) }
                        } catch (e: Exception) {
                            _uiState.update { it.copy(isLoading = false, error = "Error resolving member names.") }
                        }
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
                    val allTasks = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }
                    // Filter the tasks into two separate lists for the UI
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            repeatingTasks = allTasks.filter { it.repeatOption != "Never" },
                            oneTimeTasks = allTasks.filter { it.repeatOption == "Never" }
                        )
                    }
                }
            }
    }

    private fun listenForAutoAssignTemplates() {
        db.collection("groups").document(roomId).collection("autoAssignTemplates")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val templates = snapshot.documents.mapNotNull { doc ->
                        AutoAssignTaskTemplate(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            points = doc.getLong("points")?.toInt() ?: 0
                        )
                    }
                    _uiState.update { it.copy(autoAssignTemplates = templates) }
                }
            }
    }

    fun saveTask(
        title: String, pointsStr: String, repeatOption: String,
        assignedTo: RoomMember?, expiresInDays: Int, isAutoAssign: Boolean
    ) {
        val currentUser = auth.currentUser
        val adminName = uiState.value.members.find { it.userId == currentUser?.uid }?.name ?: "Admin"
        if (currentUser == null || assignedTo == null || title.isBlank()) { return }
        val points = pointsStr.toIntOrNull()
        if (points == null || points < 0) { return }

        // Perform optimistic UI update only for immediate assignments
        if (!isAutoAssign) {
            val representativeTaskForUi = Task(
                id = "temp_${UUID.randomUUID()}", title = title, points = points,
                repeatOption = repeatOption, groupId = roomId, sharedTaskId = UUID.randomUUID().toString(),
                assignedByName = adminName,
                dueDate = if (expiresInDays > 0) {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, expiresInDays)
                    Timestamp(calendar.time)
                } else { null }
            )
            _uiState.update { currentState ->
                if (representativeTaskForUi.repeatOption == "Never") {
                    currentState.copy(oneTimeTasks = listOf(representativeTaskForUi) + currentState.oneTimeTasks)
                } else {
                    currentState.copy(repeatingTasks = listOf(representativeTaskForUi) + currentState.repeatingTasks)
                }
            }
        }

        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val batch = db.batch()
                val membersToAssign = if (assignedTo.userId == "all") {
                    uiState.value.members.filter { it.userId != "all" }
                } else { listOf(assignedTo) }

                if (membersToAssign.isNotEmpty()) {
                    val sharedTaskId = UUID.randomUUID().toString()
                    val dueDate = if (expiresInDays > 0) {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.DAY_OF_YEAR, expiresInDays)
                        Timestamp(calendar.time)
                    } else { null }
                    membersToAssign.forEach { member ->
                        val newTaskRef = db.collection("tasks").document()
                        val taskData = hashMapOf(
                            "title" to title, "points" to points, "repeatOption" to repeatOption,
                            "groupId" to roomId, "assignedToUserId" to member.userId,
                            "assignedByUserId" to currentUser.uid, "assignedByName" to adminName,
                            "status" to "assigned", "isPersonal" to false,
                            "sharedTaskId" to sharedTaskId, "createdAt" to Timestamp.now(), "dueDate" to dueDate
                        )
                        batch.set(newTaskRef, taskData)
                    }
                }
                if (isAutoAssign) {
                    val templateRef = db.collection("groups").document(roomId).collection("autoAssignTemplates").document()
                    val templateData = hashMapOf(
                        "title" to title, "points" to points, "repeatOption" to repeatOption,
                        "expiresInDays" to expiresInDays, "createdAt" to Timestamp.now(), "createdBy" to currentUser.uid
                    )
                    batch.set(templateRef, templateData)
                }
                batch.commit().await()
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            db.collection("tasks").document(taskId).delete().await()
        }
    }

    fun deleteAutoAssignTemplate(templateId: String) {
        viewModelScope.launch {
            db.collection("groups").document(roomId)
                .collection("autoAssignTemplates").document(templateId)
                .delete().await()
        }
    }

    fun onSaveHandled() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}