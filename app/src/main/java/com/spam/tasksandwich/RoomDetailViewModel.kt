package com.spam.tasksandwich

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.update
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Query


// Data class to represent a member in the UI
data class RoomMember(
    val userId: String = "",
    val name: String = "",
    val totalPointsInGroup: Int = 0
)

data class AggregatedTask(
    val sharedTaskId: String,
    val title: String,
    val points: Int,
    val completedCount: Int,
    val pendingCount: Int,
    val createdAt: Timestamp? = null,
    val completedAt: Timestamp? = null
)

// Data class representing the entire screen's state
data class RoomDetailUiState(
    val roomName: String = "Loading...",
    val joinCode: String = "...",
    val members: List<RoomMember> = emptyList(),
    val memberCount: Int = 0,
    val totalEarnablePoints: Int = 0,
    val isAdmin: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val isRoomDeleted: Boolean = false,
    val topTasks: List<AggregatedTask> = emptyList(),
)

class RoomDetailViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    val roomId: String = savedStateHandle.get("roomId")!!

    private val _uiState = MutableStateFlow(RoomDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Room ID is missing.") }
        } else {
            fetchCurrentUserRole()
            fetchRoomDetails()
            listenForMembers()
            listenForTasks()
            listenForTopTasks()
        }
    }

    fun updateRoomName(newName: String) {
        if (newName.isBlank()) {
            _uiState.update { it.copy(error = "Room name cannot be empty.") }
            return
        }
        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId).update("name", newName).await()
                // The listener will update the UI. We also need to update all user docs.
                // This is complex, so for now we accept the home screen might show an old name
                // until the app is restarted or the user rejoins.
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to update name: ${e.message}") }
            }
        }
    }

    fun deleteRoom() {
        viewModelScope.launch {
            try {
                // Simplified delete: only removes the main group document.
                // A full implementation requires a Cloud Function to clean up subcollections.
                db.collection("groups").document(roomId).delete().await()

                // Signal to the UI that the room is gone and it should navigate away.
                _uiState.update { it.copy(isRoomDeleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete room: ${e.message}") }
            }
        }
    }

    private fun fetchCurrentUserRole() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("groups").document(roomId)
            .collection("groupMembers").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    _uiState.update { it.copy(isAdmin = (role == "admin")) }
                } else {
                    _uiState.update { it.copy(isAdmin = false) }
                }
            }
    }

    private fun fetchRoomDetails() {
        db.collection("groups").document(roomId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let { doc ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            roomName = doc.getString("name") ?: "Room",
                            joinCode = doc.getString("joinCode") ?: "------"
                        )
                    }
                }
            }
    }

    private fun listenForMembers() {
        db.collection("groups").document(roomId).collection("groupMembers")
            .orderBy("totalPointsInGroup", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
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
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                members = memberList,
                                memberCount = memberList.size
                            )
                        }
                    }
                }
            }
    }

    private fun listenForTasks() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _uiState.update { it.copy(totalEarnablePoints = 0) }
            return
        }
        db.collection("tasks")
            .whereEqualTo("groupId", roomId)
            .whereEqualTo("status", "assigned")
            .whereEqualTo("assignedToUserId", currentUserId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let { querySnapshot ->
                    val totalPoints = querySnapshot.documents.sumOf { doc ->
                        doc.getLong("points")?.toInt() ?: 0
                    }
                    _uiState.update { it.copy(totalEarnablePoints = totalPoints) }
                }
            }
    }

    private fun listenForTopTasks() {
        db.collection("tasks")
            .whereEqualTo("groupId", roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(error = "Failed to load top tasks.") }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    // Group all task documents by their shared ID
                    val tasksBySharedId = snapshot.documents.groupBy {
                        it.getString("sharedTaskId") ?: it.id
                    }

                    // Process each group into an AggregatedTask object
                    val aggregatedList = tasksBySharedId.map { (_, docs) ->
                        val firstDoc = docs.first()
                        val completed = docs.count { it.getString("status") != "assigned" }
                        val pendingCount = docs.count { it.getString("status") == "assigned" }

                        var completionTimestamp: Timestamp? = null
                        // If there are no pending tasks and at least one task exists...
                        if (pendingCount == 0 && docs.isNotEmpty()) {
                            // Find the document with the latest 'handledAt' timestamp.
                            // The 'handledAt' field should be set when a task is marked "completed".
                            // We need to add this to our 'markTaskComplete' function.
                            completionTimestamp = docs
                                .mapNotNull { it.getTimestamp("handledAt") }
                                .maxOrNull()
                        }

                        AggregatedTask(
                            sharedTaskId = firstDoc.getString("sharedTaskId") ?: firstDoc.id,
                            title = firstDoc.getString("title") ?: "Unknown Task",
                            points = firstDoc.getLong("points")?.toInt() ?: 0,
                            completedCount = completed,
                            pendingCount = pendingCount,
                            createdAt = firstDoc.getTimestamp("createdAt"),
                            completedAt = completionTimestamp
                        )
                    }.sortedByDescending { it.pendingCount } // Show tasks with the most pending first

                    _uiState.update { it.copy(topTasks = aggregatedList) }
                }
            }
    }

    fun kickMember(userIdToKick: String) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                // Client-side guard to check for admin status
                val groupDocSnapshot = db.collection("groups").document(roomId).get().await()
                val adminId = groupDocSnapshot.getString("adminUserId")
                if (currentUser.uid != adminId) {
                    _uiState.update { it.copy(error = "You do not have permission to kick members.") }
                    return@launch
                }
                if (userIdToKick == currentUser.uid) {
                    _uiState.update { it.copy(error = "You cannot kick yourself from the room.") }
                    return@launch
                }

                // Use a Transaction to ensure all operations succeed or fail together
                db.runTransaction { transaction ->
                    val memberRef = db.collection("groups").document(roomId)
                        .collection("groupMembers").document(userIdToKick)
                    val userRef = db.collection("users").document(userIdToKick)
//                    val groupRef = db.collection("groups").document(roomId)

                    // Read the user's document to get their current list of rooms
                    val userDoc = transaction.get(userRef)
                    @Suppress("UNCHECKED_CAST")
                    val groupsJoined = userDoc.get("groupsJoined") as? List<HashMap<String, Any>> ?: emptyList()

                    // Modify the list by filtering out the room they are being kicked from
                    val newGroupsJoined = groupsJoined.filter { it["groupId"] != roomId }

                    // Write the new, filtered list back to the user's document
                    transaction.update(userRef, "groupsJoined", newGroupsJoined)
                    // Remove the user's ID from the group's 'memberIds' array for security rules
//                    transaction.update(groupRef, "memberIds", FieldValue.arrayRemove(userIdToKick))
                    // Delete the user from the room's member list
                    transaction.delete(memberRef)
                }.await()

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to kick member: ${e.message}") }
            }
        }
    }
}