package com.spam.tasksandwich

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.update
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query


// Data class to represent a member in the UI
data class RoomMember(
    val userId: String = "",
    val name: String = "",
    val totalPointsInGroup: Int = 0
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
    val error: String? = null
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