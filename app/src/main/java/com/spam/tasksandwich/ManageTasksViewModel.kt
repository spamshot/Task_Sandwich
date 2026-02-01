package com.spam.tasksandwich

import android.util.Log
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

        // HomeViewModel Pattern: Tracking multiple listeners
        var membersLoaded = false
        var tasksLoaded = false
        var templatesLoaded = false

        fun checkCompletion() {
            if (membersLoaded && tasksLoaded && templatesLoaded) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // 1. Listen for Members
        db.collection("groups").document(roomId).collection("groupMembers")
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    viewModelScope.launch {
                        val memberJobs = snapshot.documents.map { memberDoc ->
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
                        membersLoaded = true
                        checkCompletion()
                    }
                } else {
                    membersLoaded = true; checkCompletion()
                }
            }

        // 2. Listen for Assigned Tasks (THE FIX: No date filtering)
        db.collection("tasks")
            .whereEqualTo("groupId", roomId)
            .whereEqualTo("status", "assigned") // We only want the active ones
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ManageTasksVM", "Task listener error: ${error.message}")
                    tasksLoaded = true; checkCompletion()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    allTasksCache = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }

                    // Immediately update UI lists based on the cache
                    _uiState.update { currentState ->
                        currentState.copy(
                            repeatingTasks = allTasksCache.filter { it.repeatOption != "Never" && it.repeatOption != null },
                            oneTimeTasks = allTasksCache.filter { it.repeatOption == "Never" || it.repeatOption == null }
                        )
                    }
                }
                tasksLoaded = true
                checkCompletion()
            }

        // 3. Listen for Auto-Assign Templates
        db.collection("groups").document(roomId).collection("autoAssignTemplates")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
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
                templatesLoaded = true
                checkCompletion()
            }
    }

    // --- OPTIMISTIC UPDATES (Mirroring your working logic) ---

    fun deleteTaskGroup(tasks: List<Task>) {
        val idsToDelete = tasks.map { it.id }.toSet()

        // 1. Optimistic Update: Remove from UI immediately
        _uiState.update { currentState ->
            currentState.copy(
                repeatingTasks = currentState.repeatingTasks.filter { it.id !in idsToDelete },
                oneTimeTasks = currentState.oneTimeTasks.filter { it.id !in idsToDelete }
            )
        }

        // 2. Background Deletion
        viewModelScope.launch {
            try {
                val batch = db.batch()
                tasks.forEach { batch.delete(db.collection("tasks").document(it.id)) }
                batch.commit().await()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Delete failed: ${e.message}") }
            }
        }
    }

    fun saveTask(
        title: String, pointsStr: String, repeatOption: String,
        assignedTo: RoomMember?, expiresInDays: Int, isAutoAssign: Boolean
    ) {
        val currentUser = auth.currentUser
        // Safety checks
        if (currentUser == null || assignedTo == null || title.isBlank()) return
        val points = pointsStr.toIntOrNull()
        if (points == null || points < 0) return

        val adminName = uiState.value.members.find { it.userId == currentUser.uid }?.name ?: "Admin"

        // 1. Determine who we are assigning to
        val membersToAssign = if (assignedTo.userId == "all") {
            uiState.value.members.filter { it.userId != "all" }
        } else {
            listOf(assignedTo)
        }

        // 2. Prepare common data
        val sharedTaskId = UUID.randomUUID().toString()
        val dueDate = if (expiresInDays > 0) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, expiresInDays)
            Timestamp(calendar.time)
        } else { null }

        val timestampNow = Timestamp.now()
        val newTasksForBatch = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Task>>()

        // 3. Generate IDs and Data Objects *Before* Network Call
        membersToAssign.forEach { member ->
            val newDocRef = db.collection("tasks").document() // Generate ID here

            // Create the Task object for Local UI + Network
            val taskObj = Task(
                id = newDocRef.id, // Use the real ID
                title = title,
                points = points,
                repeatOption = repeatOption,
                groupId = roomId,
                assignedToUserId = member.userId,
                assignedByUserId = currentUser.uid,
                assignedByName = adminName,
                status = "assigned",
                isPersonal = false,
                sharedTaskId = sharedTaskId,
                createdAt = timestampNow,
                dueDate = dueDate
            )

            newTasksForBatch.add(newDocRef to taskObj)
        }

        // 4. OPTIMISTIC UPDATE: Update UI *Immediately* (Don't wait for internet)
        // This ensures the task appears in the list instantly after clicking save.
        if (!isAutoAssign && newTasksForBatch.isNotEmpty()) {
            val createdTasks = newTasksForBatch.map { it.second }
            _uiState.update { currentState ->
                if (repeatOption == "Never") {
                    currentState.copy(oneTimeTasks = createdTasks + currentState.oneTimeTasks)
                } else {
                    currentState.copy(repeatingTasks = createdTasks + currentState.repeatingTasks)
                }
            }
        }

        // 5. Perform the actual Network Request
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val batch = db.batch()

                // Add tasks to batch
                newTasksForBatch.forEach { (docRef, task) ->
                    // Convert Task object back to HashMap for Firestore to ensure clean saving
                    // Or you can just batch.set(docRef, task) if Task is @Keep annotated and standard POJO
                    val taskData = hashMapOf(
                        "title" to task.title,
                        "points" to task.points,
                        "repeatOption" to task.repeatOption,
                        "groupId" to task.groupId,
                        "assignedToUserId" to task.assignedToUserId,
                        "assignedByUserId" to task.assignedByUserId,
                        "assignedByName" to task.assignedByName,
                        "status" to task.status,
                        "isPersonal" to task.isPersonal,
                        "sharedTaskId" to task.sharedTaskId,
                        "createdAt" to task.createdAt,
                        "dueDate" to task.dueDate
                    )
                    batch.set(docRef, taskData)
                }

                // Add Template to batch if needed
                if (isAutoAssign) {
                    val templateRef = db.collection("groups").document(roomId).collection("autoAssignTemplates").document()
                    val templateData = hashMapOf(
                        "title" to title, "points" to points, "repeatOption" to repeatOption,
                        "expiresInDays" to expiresInDays, "createdAt" to timestampNow, "createdBy" to currentUser.uid
                    )
                    batch.set(templateRef, templateData)
                }

                batch.commit().await()
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }

            } catch (e: Exception) {
                // If it fails, revert the change (optional) or just show error
                _uiState.update { it.copy(isSaving = false, error = e.message) }
                // Note: In a production app, you might remove the optimistic tasks here if save failed.
            }
        }
    }



    fun deleteAutoAssignTemplate(templateId: String) {
        // Optimistic UI Update
        _uiState.update { currentState ->
            currentState.copy(
                autoAssignTemplates = currentState.autoAssignTemplates.filter { it.id != templateId }
            )
        }

        // Network Request
        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId)
                    .collection("autoAssignTemplates").document(templateId)
                    .delete().await()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete template") }
            }
        }
    }

    fun onSaveHandled() {
        _uiState.update { it.copy(saveSuccess = false) }
    }
}