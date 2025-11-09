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
    val error: String? = null
)

/**
 * ViewModel for the AppShell. It's responsible for handling logic for global UI
 * components like the navigation drawer.
 */
class AppShellViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(AppShellUiState())
    val uiState = _uiState.asStateFlow()

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
                // 1. Find the group document with the matching join code.
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

                // 2. Fetch all auto-assign task templates for this room.
                val templatesSnapshot = groupRef.collection("autoAssignTemplates").get().await()

                // 3. Fetch the admin's name to use as the 'assignedByName' for the new tasks.
                val adminId = groupDoc.getString("adminUserId")
                var adminName = "Admin" // Default name
                if (adminId != null) {
                    val adminDoc = db.collection("users").document(adminId).get().await()
                    if (adminDoc.exists()) {
                        adminName = adminDoc.getString("name") ?: adminName
                    }
                }

                // 4. Prepare all database operations in a single atomic batch write.
                val batch = db.batch()

                // Operation A: Add the new user to the room's 'groupMembers' subcollection.
                val memberRef = groupRef.collection("groupMembers").document(currentUser.uid)
                val newMemberData = hashMapOf(
                    "userId" to currentUser.uid,
                    "role" to "member",
                    "status" to "approved",
                    "totalPointsInGroup" to 0,
                    "joinedAt" to Timestamp.now()
                )
                batch.set(memberRef, newMemberData)

                // Operation B: Add the room's info to the user's personal 'groupsJoined' list.
                val userRef = db.collection("users").document(currentUser.uid)
                val roomInfo = hashMapOf(
                    "groupId" to groupId,
                    "groupName" to groupDoc.getString("name")
                )
                batch.update(userRef, "groupsJoined", FieldValue.arrayUnion(roomInfo))

                // Operation C: For each template, create a new task document assigned to the new user.
                templatesSnapshot.documents.forEach { templateDoc ->
                    val newTaskRef = db.collection("tasks").document()
                    val taskData = templateDoc.data!! // Get all data from the template

                    // Add/overwrite fields to make it a specific assignment
                    taskData["groupId"] = groupId
                    taskData["assignedToUserId"] = currentUser.uid
                    taskData["assignedByUserId"] = adminId
                    taskData["assignedByName"] = adminName
                    taskData["status"] = "assigned"
                    taskData["isPersonal"] = false
                    taskData["createdAt"] = Timestamp.now() // Set a fresh creation date

                    // Recalculate the due date based on when the user joined.
                    val expiresInDays = templateDoc.getLong("expiresInDays")?.toInt() ?: 0
                    if (expiresInDays > 0) {
                        val calendar = java.util.Calendar.getInstance()
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, expiresInDays)
                        taskData["dueDate"] = Timestamp(calendar.time)
                    }

                    batch.set(newTaskRef, taskData)
                }

                // 5. Commit all operations at once.
                batch.commit().await()

                // 6. Signal success to the UI with the groupId to trigger navigation.
                _uiState.update { it.copy(newlyJoinedRoomId = groupId, error = null) }

            } catch (e: Exception) {
                _uiState.update { it.copy(error = "An error occurred: ${e.message}") }
            }
        }
    }

    // --- NEW FUNCTION TO CLEAR ERROR ---
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onRoomNavigationHandled() { // Rename for clarity
        _uiState.update { it.copy(newlyCreatedRoomId = null, newlyJoinedRoomId = null) }
    }

    /**
     * Creates a new room in Firestore. This is a comprehensive operation that:
     * 1. Creates a new document in the 'groups' collection.
     * 2. Creates a document for the admin in the 'groupMembers' subcollection.
     * 3. Updates the admin's own user document to include the new room in their 'groupsJoined' list.
     * All operations are performed in a single atomic batch write.
     *
     * @param roomName The name for the new room, provided by the user from the dialog.
     */
    fun createRoom(roomName: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // In a real app, you might want to expose an error state here.
            return
        }
        if (roomName.isBlank()) {
            // Also a good place for an error state for the UI to show.
            return
        }

        viewModelScope.launch {
            try {
                val newRoomRef = db.collection("groups").document()
                val joinCode = (100000..999999).random().toString()

                // 1. Data for the main group document
                val newRoom = hashMapOf(
                    "name" to roomName,
                    "adminUserId" to currentUser.uid,
                    "joinCode" to joinCode,
                    "autoAcceptMembers" to true,
                    "createdAt" to Timestamp.now()
                )

                // 2. Data for the admin's entry in the groupMembers subcollection
                val adminMember = hashMapOf(
                    "userId" to currentUser.uid,
                    "role" to "admin",
                    "status" to "approved",
                    "totalPointsInGroup" to 0,
                    "joinedAt" to Timestamp.now()
                )

                // 3. Data for the user's personal list of joined rooms
                val userRef = db.collection("users").document(currentUser.uid)
                val roomInfo = hashMapOf(
                    "groupId" to newRoomRef.id,
                    "groupName" to roomName
                )

                // Perform all writes in a single, atomic batch
                db.runBatch { batch ->
                    batch.set(newRoomRef, newRoom)
                    batch.set(newRoomRef.collection("groupMembers").document(currentUser.uid), adminMember)
                    batch.update(userRef, "groupsJoined", FieldValue.arrayUnion(roomInfo))
                }.await()

                // On success, update the state to signal the UI to navigate
                _uiState.update { it.copy(newlyCreatedRoomId = newRoomRef.id) }

            } catch (e: Exception) {
                // In a production app, you would update the UI state with this error.
                // For example: _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Resets the navigation event state after it has been handled by the UI.
     * This prevents the app from trying to re-navigate on configuration changes.
     */
    fun onRoomCreationHandled() {
        _uiState.update { it.copy(newlyCreatedRoomId = null) }
    }
}