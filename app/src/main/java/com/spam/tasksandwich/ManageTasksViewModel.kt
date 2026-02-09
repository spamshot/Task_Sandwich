package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
    val roomName: String = "",
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

    private var allTasksCache: List<Task> = emptyList()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Room ID is missing.") }
            return
        }
        _uiState.update { it.copy(isLoading = true) }

        var roomLoaded = false
        var membersLoaded = false
        var tasksLoaded = false
        var templatesLoaded = false

        fun checkCompletion() {
            if (roomLoaded && membersLoaded && tasksLoaded && templatesLoaded) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // 1. Room Name Listener
        db.collection("groups").document(roomId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    _uiState.update { it.copy(roomName = snapshot.getString("name") ?: "Room") }
                }
                roomLoaded = true; checkCompletion()
            }

        // 2. Members Listener
        db.collection("groups").document(roomId).collection("groupMembers")
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirebaseCrashlytics.getInstance().recordException(error)
                    membersLoaded = true; checkCompletion()
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
                                        totalPointsInGroup = 0
                                    )
                                }
                            }
                            _uiState.update { it.copy(members = memberJobs.awaitAll()) }
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().recordException(e)
                        } finally {
                            membersLoaded = true
                            checkCompletion()
                        }
                    }
                }
            }

        // 3. Assigned Tasks Listener (With Grouping)
        db.collection("tasks")
            .whereEqualTo("groupId", roomId)
            .whereEqualTo("status", "assigned")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirebaseCrashlytics.getInstance().recordException(error)
                    tasksLoaded = true; checkCompletion()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    allTasksCache = snapshot.documents.mapNotNull { doc ->
                        val task = doc.toObject(Task::class.java)
                        task?.copy(id = doc.id, assignedToName = doc.getString("assignedToName") ?: "")
                    }

                    val grouped = allTasksCache.groupBy { it.sharedTaskId ?: it.id }
                    val displayTasks = grouped.map { (_, group) ->
                        val representative = group.first()
                        val names = group.mapNotNull { it.assignedToName }.filter { it.isNotBlank() }.distinct()
                        representative.copy(assigneeNames = names)
                    }

                    _uiState.update { currentState ->
                        currentState.copy(
                            repeatingTasks = displayTasks.filter { it.repeatOption != "Never" },
                            oneTimeTasks = displayTasks.filter { it.repeatOption == "Never" }
                        )
                    }
                }
                tasksLoaded = true; checkCompletion()
            }

        // 4. Templates Listener
        db.collection("groups").document(roomId).collection("autoAssignTemplates")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val templates = snapshot.documents.mapNotNull { doc ->
                        AutoAssignTaskTemplate(doc.id, doc.getString("title") ?: "", doc.getLong("points")?.toInt() ?: 0)
                    }
                    _uiState.update { it.copy(autoAssignTemplates = templates) }
                }
                templatesLoaded = true; checkCompletion()
            }
    }

    fun saveTask(
        title: String, pointsStr: String, repeatOption: String,
        assignedTo: RoomMember?, expiresInDays: Int, isAutoAssign: Boolean
    ) {
        val currentUser = auth.currentUser
        val points = pointsStr.toIntOrNull()
        if (currentUser == null || assignedTo == null || title.isBlank() || points == null) return

        val adminName = uiState.value.members.find { it.userId == currentUser.uid }?.name ?: "Admin"
        val membersToAssign = if (assignedTo.userId == "all") {
            uiState.value.members.filter { it.userId != "all" }
        } else listOf(assignedTo)

        // --- UPDATED DUE DATE LOGIC: SNAP TO 23:59:59 ---
        val dueDate = if (expiresInDays > 0) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, expiresInDays)
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            Timestamp(calendar.time)
        } else null

        val sharedTaskId = UUID.randomUUID().toString()
        val timestampNow = Timestamp.now()
        val batch = db.batch()

        membersToAssign.forEach { member ->
            val newDocRef = db.collection("tasks").document()
            val taskData = hashMapOf(
                "title" to title, "points" to points, "repeatOption" to repeatOption,
                "groupId" to roomId, "assignedToUserId" to member.userId, "assignedToName" to member.name,
                "assignedByUserId" to currentUser.uid, "assignedByName" to adminName,
                "status" to "assigned", "isPersonal" to false, "sharedTaskId" to sharedTaskId,
                "createdAt" to timestampNow, "dueDate" to dueDate
            )
            batch.set(newDocRef, taskData)
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                if (isAutoAssign) {
                    val templateRef = db.collection("groups").document(roomId).collection("autoAssignTemplates").document()
                    batch.set(templateRef, hashMapOf(
                        "title" to title, "points" to points, "repeatOption" to repeatOption,
                        "expiresInDays" to expiresInDays, "createdAt" to timestampNow
                    ))
                }
                batch.commit().await()
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("SaveTask Failed in Room: $roomId")
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(isSaving = false, error = "Failed to save: ${e.message}") }
            }
        }
    }

    fun deleteTaskGroup(representativeTask: Task) {
        val targetSharedId = representativeTask.sharedTaskId ?: representativeTask.id
        val tasksToDelete = allTasksCache.filter { (it.sharedTaskId ?: it.id) == targetSharedId }
        val idsToDelete = tasksToDelete.map { it.id }.toSet()

        _uiState.update { currentState ->
            currentState.copy(
                repeatingTasks = currentState.repeatingTasks.filter { it.id !in idsToDelete },
                oneTimeTasks = currentState.oneTimeTasks.filter { it.id !in idsToDelete }
            )
        }

        viewModelScope.launch {
            try {
                val batch = db.batch()
                tasksToDelete.forEach { batch.delete(db.collection("tasks").document(it.id)) }
                batch.commit().await()
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(error = "Delete failed: ${e.message}") }
            }
        }
    }

    fun deleteAutoAssignTemplate(templateId: String) {
        _uiState.update { it.copy(autoAssignTemplates = it.autoAssignTemplates.filter { it.id != templateId }) }
        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId).collection("autoAssignTemplates").document(templateId).delete().await()
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun onSaveHandled() { _uiState.update { it.copy(saveSuccess = false) } }
}