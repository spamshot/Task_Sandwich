package com.spam.tasksandwich

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AppShellUiState(
    val newlyCreatedRoomId: String? = null,
    val newlyJoinedRoomId: String? = null,
    val error: String? = null,
    // --- NEW: Controls visibility of the Create button ---
    val canCreateRoom: Boolean = true
)

class AppShellViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(AppShellUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // Start listening to the room count as soon as the app starts
        listenForOwnedRoomCount()
    }

    // --- NEW LOGIC: Count how many rooms the user owns ---
    private fun listenForOwnedRoomCount() {
        val currentUser = auth.currentUser ?: return

        db.collection("groups")
            .whereEqualTo("adminUserId", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                if (snapshot != null) {
                    val count = snapshot.size()
                    // If count is 4 or more, disable creation
                    _uiState.update { it.copy(canCreateRoom = count < 4) }
                }
            }
    }

    fun joinRoom(joinCode: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(error = "You must be logged in to join a room.") }
            return
        }
        if (joinCode.length != 6) {
            _uiState.update { it.copy(error = "Please enter a valid 6-digit code.") }
            return
        }

        viewModelScope.launch {
            try {
                val groupQuery = db.collection("groups")
                    .whereEqualTo("joinCode", joinCode)
                    .limit(1)
                    .get()
                    .await()

                if (groupQuery.isEmpty) {
                    _uiState.update { it.copy(error = "Invalid room code. Please try again.") }
                    return@launch
                }

                val groupDoc = groupQuery.documents.first()
                val groupId = groupDoc.id
                val groupRef = db.collection("groups").document(groupId)

                val templatesSnapshot = groupRef.collection("autoAssignTemplates").get().await()

                val adminId = groupDoc.getString("adminUserId")
                var adminName = "Admin"
                if (adminId != null) {
                    val adminDoc = db.collection("users").document(adminId).get().await()
                    if (adminDoc.exists()) {
                        adminName = adminDoc.getString("name") ?: adminName
                    }
                }

                val batch = db.batch()

                val memberRef = groupRef.collection("groupMembers").document(currentUser.uid)
                val newMemberData = hashMapOf(
                    "userId" to currentUser.uid,
                    "role" to "member",
                    "status" to "approved",
                    "totalPointsInGroup" to 0,
                    "joinedAt" to Timestamp.now()
                )
                batch.set(memberRef, newMemberData)

                val userRef = db.collection("users").document(currentUser.uid)
                val roomInfo = hashMapOf(
                    "groupId" to groupId,
                    "groupName" to groupDoc.getString("name")
                )
                batch.update(userRef, "groupsJoined", FieldValue.arrayUnion(roomInfo))

                templatesSnapshot.documents.forEach { templateDoc ->
                    val newTaskRef = db.collection("tasks").document()
                    val taskData = templateDoc.data!!

                    taskData["groupId"] = groupId
                    taskData["assignedToUserId"] = currentUser.uid
                    taskData["assignedByUserId"] = adminId
                    taskData["assignedByName"] = adminName
                    taskData["status"] = "assigned"
                    taskData["isPersonal"] = false
                    taskData["createdAt"] = Timestamp.now()

                    val expiresInDays = templateDoc.getLong("expiresInDays")?.toInt() ?: 0
                    if (expiresInDays > 0) {
                        val calendar = java.util.Calendar.getInstance()
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, expiresInDays)
                        taskData["dueDate"] = Timestamp(calendar.time)
                    }

                    batch.set(newTaskRef, taskData)
                }

                batch.commit().await()

                _uiState.update { it.copy(newlyJoinedRoomId = groupId, error = null) }

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "An error occurred: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onRoomNavigationHandled() {
        _uiState.update { it.copy(newlyCreatedRoomId = null, newlyJoinedRoomId = null) }
    }

    fun createRoom(roomName: String) {
        val currentUser = auth.currentUser
        if (currentUser == null || roomName.isBlank()) return

        // --- Safety Check ---
        if (!_uiState.value.canCreateRoom) {
            _uiState.update { it.copy(error = "Limit reached: Max 4 rooms.") }
            return
        }

        viewModelScope.launch {
            try {
                val newRoomRef = db.collection("groups").document()
                val joinCode = (100000..999999).random().toString()

                val newRoom = hashMapOf(
                    "name" to roomName,
                    "adminUserId" to currentUser.uid,
                    "joinCode" to joinCode,
                    "autoAcceptMembers" to true,
                    "createdAt" to Timestamp.now()
                )

                val adminMember = hashMapOf(
                    "userId" to currentUser.uid,
                    "role" to "admin",
                    "status" to "approved",
                    "totalPointsInGroup" to 0,
                    "joinedAt" to Timestamp.now()
                )

                val userRef = db.collection("users").document(currentUser.uid)
                val roomInfo = hashMapOf(
                    "groupId" to newRoomRef.id,
                    "groupName" to roomName
                )

                db.runBatch { batch ->
                    batch.set(newRoomRef, newRoom)
                    batch.set(newRoomRef.collection("groupMembers").document(currentUser.uid), adminMember)
                    batch.update(userRef, "groupsJoined", FieldValue.arrayUnion(roomInfo))
                }.await()

                _uiState.update { it.copy(newlyCreatedRoomId = newRoomRef.id) }

            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}