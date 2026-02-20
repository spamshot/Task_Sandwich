package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Fixes:
//   1. auth.addAuthStateListener is called in init but the
//      listener is never removed — it leaks beyond the ViewModel's
//      lifecycle. Stored and removed in onCleared().
//   2. joinRoom() does not check member count or locked status
//      before proceeding — it does, actually. This is already
//      well-implemented. No change needed here.
//   3. joinRoom()'s autoAssign task loop reads templateDoc.data!!
//      with a force unwrap — crashes if data is null. Changed to
//      safe access with ?: emptyMap().
//   4. createRoom() roomName has no blank validation — if called
//      with a blank name it would write an empty string to Firestore.
//      Added trim + blank check.
//   5. joinRoom() and createRoom() error messages expose raw
//      e.message. Replaced with friendly messages.
//   6. joinRoom() auto-assign task due date snaps to start of
//      day (calendar.time without setting hour/min/sec) rather
//      than end of day (23:59:59) as ManageTasksViewModel does.
//      This means tasks expire at midnight on the due day rather
//      than at the end of it. Aligned to 23:59:59 like the rest
//      of the app.
// ============================================================

data class AppShellUiState(
    val newlyCreatedRoomId: String? = null,
    val newlyJoinedRoomId: String? = null,
    val error: String? = null,
    val canCreateRoom: Boolean = true,
    val isLoading: Boolean = false
)

class AppShellViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(AppShellUiState())
    val uiState = _uiState.asStateFlow()

    // FIX 1: Store the listener so we can remove it in onCleared().
    private var authStateListener: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null

    init {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                listenForOwnedRoomCount(user.uid)
            }
        }
        authStateListener = listener
        auth.addAuthStateListener(listener) // ✅ stored for cleanup
    }

    // FIX 1: Remove the auth listener when the ViewModel is cleared to prevent a leak.
    override fun onCleared() {
        super.onCleared()
        authStateListener?.let { auth.removeAuthStateListener(it) }
    }

    private fun listenForOwnedRoomCount(uid: String) {
        db.collection("groups")
            .whereEqualTo("adminUserId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AppShellViewModel", "Room count listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val count = snapshot.size()
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

                val isLocked = groupDoc.getBoolean("isLocked") ?: false
                if (isLocked) {
                    _uiState.update { it.copy(error = "This room is locked. No new members can join.") }
                    return@launch
                }

                val memberCountQuery = groupRef.collection("groupMembers").count()
                    .get(com.google.firebase.firestore.AggregateSource.SERVER).await()
                if (memberCountQuery.count >= 25) {
                    _uiState.update { it.copy(error = "This room is full (max 25 members).") }
                    return@launch
                }

                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val joiningUserName = userDoc.getString("name") ?: "New Member"

                val templatesSnapshot = groupRef.collection("autoAssignTemplates").get().await()

                val adminId = groupDoc.getString("adminUserId")
                var adminName = "Admin"
                if (adminId != null) {
                    val adminDoc = db.collection("users").document(adminId).get().await()
                    if (adminDoc.exists()) adminName = adminDoc.getString("name") ?: adminName
                }

                val batch = db.batch()

                batch.set(
                    groupRef.collection("groupMembers").document(currentUser.uid),
                    hashMapOf(
                        "userId" to currentUser.uid,
                        "role" to "member",
                        "status" to "approved",
                        "totalPointsInGroup" to 0,
                        "joinedAt" to Timestamp.now()
                    )
                )

                batch.update(
                    db.collection("users").document(currentUser.uid),
                    "groupsJoined", FieldValue.arrayUnion(hashMapOf(
                        "groupId" to groupId,
                        "groupName" to groupDoc.getString("name")
                    ))
                )

                templatesSnapshot.documents.forEach { templateDoc ->
                    val newTaskRef = db.collection("tasks").document()
                    // FIX 3: Safe access instead of !! force-unwrap on data
                    val taskData = (templateDoc.data ?: emptyMap<String, Any>()).toMutableMap()

                    taskData["groupId"] = groupId
                    taskData["assignedToUserId"] = currentUser.uid
                    taskData["assignedToName"] = joiningUserName
                    taskData["assignedByUserId"] = adminId
                    taskData["assignedByName"] = adminName
                    taskData["status"] = "assigned"
                    taskData["isPersonal"] = false
                    taskData["createdAt"] = Timestamp.now()
                    taskData["sharedTaskId"] = newTaskRef.id

                    val expiresInDays = templateDoc.getLong("expiresInDays")?.toInt() ?: 0
                    if (expiresInDays > 0) {
                        val calendar = java.util.Calendar.getInstance()
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, expiresInDays)
                        // FIX 6: Snap to END of day (23:59:59) to match ManageTasksViewModel behavior
                        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                        calendar.set(java.util.Calendar.MINUTE, 59)
                        calendar.set(java.util.Calendar.SECOND, 59)
                        taskData["dueDate"] = Timestamp(calendar.time)
                    }

                    batch.set(newTaskRef, taskData)
                }

                batch.commit().await()
                _uiState.update { it.copy(newlyJoinedRoomId = groupId, error = null) }

            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in AppShellViewModel: joinRoom")
                FirebaseCrashlytics.getInstance().setCustomKey("Joining room code", joinCode)
                FirebaseCrashlytics.getInstance().recordException(e)
                // FIX 5: Friendly error message
                _uiState.update { it.copy(error = "Could not join room. Please try again.") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onRoomNavigationHandled() {
        _uiState.update { it.copy(newlyCreatedRoomId = null, newlyJoinedRoomId = null) }
    }

    // FIX 4: Added roomName blank validation.
    fun createRoom(roomName: String) {
        val currentUser = auth.currentUser ?: return

        val trimmedName = roomName.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(error = "Room name cannot be empty.") }
            return
        }

        if (!_uiState.value.canCreateRoom) {
            _uiState.update { it.copy(error = "Limit reached: Max 4 rooms.") }
            return
        }

        viewModelScope.launch {
            try {
                val newRoomRef = db.collection("groups").document()
                val joinCode = (100000..999999).random().toString()

                val newRoom = hashMapOf(
                    "name" to trimmedName, // ✅ trimmed
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
                val roomInfo = hashMapOf("groupId" to newRoomRef.id, "groupName" to trimmedName)

                db.runBatch { batch ->
                    batch.set(newRoomRef, newRoom)
                    batch.set(newRoomRef.collection("groupMembers").document(currentUser.uid), adminMember)
                    batch.update(userRef, "groupsJoined", FieldValue.arrayUnion(roomInfo))
                }.await()

                _uiState.update { it.copy(newlyCreatedRoomId = newRoomRef.id) }

            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in AppShellViewModel: createRoom")
                FirebaseCrashlytics.getInstance().setCustomKey("Create room name", trimmedName)
                FirebaseCrashlytics.getInstance().recordException(e)
                // FIX 5: Friendly error message
                _uiState.update { it.copy(error = "Could not create room. Please try again.") }
            }
        }
    }
}