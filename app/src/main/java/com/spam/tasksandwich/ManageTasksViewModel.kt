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
                    Log.e("ManageTasksVM", "Error loading members", error)
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
                            val memberList = memberJobs.awaitAll()
                            _uiState.update { it.copy(members = memberList) }
                        } catch (e: Exception) {
                            Log.e("ManageTasksVM", "Error resolving member names", e)
                        }
                    }
                }
            }
    }

    private fun listenForAssignedTasks() {
        // NOTE: This query requires a Firestore Index because of the 'where' + 'orderBy'.
        // If the index is missing, the 'error' block below will trigger.
        // Check Logcat for a URL to create the index automatically.
        db.collection("tasks")
            .whereEqualTo("groupId", roomId)
            .whereEqualTo("status", "assigned")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // FIX: Handle error properly so the loading wheel stops
                    Log.e("ManageTasksVM", "Listen failed (Check for missing Index in Logcat): ${error.message}")
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load tasks. Check logs.") }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val allTasks = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }
                    // FIX: Updates UI immediately and stops loading
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            repeatingTasks = allTasks.filter { task -> task.repeatOption != "Never" },
                            oneTimeTasks = allTasks.filter { task -> task.repeatOption == "Never" }
                        )
                    }
                }
            }
    }

    private fun listenForAutoAssignTemplates() {
        db.collection("groups").document(roomId).collection("autoAssignTemplates")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ManageTasksVM", "Error loading templates", error)
                    // We don't necessarily stop main loading here, but good to know
                    return@addSnapshotListener
                }
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

    // FIX: Using the secure batch delete we discussed earlier
    fun deleteTaskGroup(tasks: List<Task>) {
        // 1. Get the list of IDs we are about to delete
        val idsToDelete = tasks.map { it.id }.toSet()

        // 2. OPTIMISTIC UPDATE:
        // Immediately remove these tasks from the UI state (don't wait for Firebase)
        _uiState.update { currentState ->
            currentState.copy(
                // Keep only tasks whose ID is NOT in our delete list
                repeatingTasks = currentState.repeatingTasks.filter { it.id !in idsToDelete },
                oneTimeTasks = currentState.oneTimeTasks.filter { it.id !in idsToDelete },

                // Optional: Show saving spinner if you want,
                // but usually immediate removal feels snappier without it.
                isSaving = true
            )
        }

        // 3. Perform the actual Network Request in the background
        viewModelScope.launch {
            try {
                val batch = db.batch()
                tasks.forEach { task ->
                    val docRef = db.collection("tasks").document(task.id)
                    batch.delete(docRef)
                }

                // Commit to Firestore
                batch.commit().await()

                // Success: Just turn off the saving flag
                // (The UI is already correct because we updated it in step 2)
                _uiState.update { it.copy(isSaving = false) }

            } catch (e: Exception) {
                // If the delete FAILED, we should probably show an error.
                // In a perfect world, we would also add the tasks back to the list here.
                _uiState.update {
                    it.copy(isSaving = false, error = "Failed to delete: ${e.message}")
                }
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