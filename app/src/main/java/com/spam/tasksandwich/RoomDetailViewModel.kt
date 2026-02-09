package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.update
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.Query
import com.google.firebase.functions.functions


// Data class to represent a member in the UI
data class RoomMember(
    val userId: String = "",
    val name: String = "",
    val totalPointsInGroup: Int = 0
)

data class TaskCompletionDetails(
    val userName: String,
    val status: String
)

data class AggregatedTask(
    val sharedTaskId: String,
    val title: String,
    val points: Int,
    val completedCount: Int,
    val pendingCount: Int,
    val createdAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val completions: List<TaskCompletionDetails> = emptyList()
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
    val currentUserId: String = "",
    val selectedUserProfile: UserProfile? = null,
    val isLoadingProfileForDialog: Boolean = false,
    val globallyCensoredUserIds: List<String> = emptyList(),
    val locallyCensoredUserIds: List<String> = emptyList(),
    val isLocked: Boolean = false,
)

class RoomDetailViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    val roomId: String = savedStateHandle.get("roomId")!!


    private val functions = Firebase.functions("us-central1")

    private val _uiState = MutableStateFlow(RoomDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Room ID is missing.") }
        } else {
            _uiState.update { it.copy(currentUserId = auth.currentUser?.uid ?: "") }
            fetchCurrentUserRole()
            listenToRoomAndUser()
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

                FirebaseCrashlytics.getInstance().log("Error in RoomDetailViewModel: update room name")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Update room", "Failed to update room name")

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)

                _uiState.update { it.copy(error = "Failed to update name: ${e.message}") }
            }
        }
    }

    //Fetches the full UserProfile for a given userId.
    fun selectUserForProfileView(userId: String) {
        _uiState.update { it.copy(isLoadingProfileForDialog = true, selectedUserProfile = null) }

        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(userId).get().await()
                if (userDoc.exists()) {
                    // --- THE FIX ---
                    val profile = userDoc.toObject(UserProfile::class.java)?.copy(uid = userDoc.id)
                    // ---------------

                    _uiState.update { it.copy(isLoadingProfileForDialog = false, selectedUserProfile = profile) }
                } else {
                    _uiState.update { it.copy(isLoadingProfileForDialog = false, error = "User profile not found.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingProfileForDialog = false, error = e.message) }
            }
        }
    }
//Clears the selected user profile, which will dismiss the dialog.
    fun dismissUserProfileView() {
        _uiState.update { it.copy(selectedUserProfile = null) }
    }

    private fun listenToRoomAndUser() {
        val currentUserId = auth.currentUser?.uid ?: return

        // 1. Listen to the ROOM (For Name, Code, and LOCK status)
        db.collection("groups").document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null && snapshot.exists()) {
                    val globalCensored = snapshot.get("censoredUserIds") as? List<String> ?: emptyList()

                    // --- THE FIX IS THESE TWO LINES ---
                    val lockStatus = snapshot.getBoolean("isLocked") ?: false
                    // ----------------------------------

                    _uiState.update { it.copy(
                        roomName = snapshot.getString("name") ?: "",
                        joinCode = snapshot.getString("joinCode") ?: "------",
                        isLocked = lockStatus, // --- AND UPDATING THIS HERE ---
                        globallyCensoredUserIds = globalCensored
                    ) }
                    Log.d("LOCK_DEBUG", "Listener received isLocked: $lockStatus")
                }
            }

        // 2. Listen to the CURRENT USER (For Local Censorship)
        db.collection("users").document(currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null && snapshot.exists()) {
                    val localCensored = snapshot.get("locallyCensoredUserIds") as? List<String> ?: emptyList()
                    _uiState.update { it.copy(locallyCensoredUserIds = localCensored) }
                }
            }
    }

    fun toggleCensorUserGlobally(targetUserId: String) {
        // 1. Check current state from the UI State
        val isCurrentlyCensored = uiState.value.globallyCensoredUserIds.contains(targetUserId)

        // 2. Decide if we are adding or removing
        val action = if (isCurrentlyCensored) {
            FieldValue.arrayRemove(targetUserId)
        } else {
            FieldValue.arrayUnion(targetUserId)
        }

        // 3. Update the Room document
        db.collection("groups").document(roomId)
            .update("censoredUserIds", action)
            .addOnSuccessListener {
                Log.d("CENSOR", "Global toggle success for $targetUserId")
            }
    }

    fun toggleCensorUserLocally(targetUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        // 1. Check current state
        val isCurrentlyCensored = uiState.value.locallyCensoredUserIds.contains(targetUserId)

        // 2. Decide action
        val action = if (isCurrentlyCensored) {
            FieldValue.arrayRemove(targetUserId)
        } else {
            FieldValue.arrayUnion(targetUserId)
        }

        // 3. Update the User's personal document
        db.collection("users").document(currentUserId)
            .update("locallyCensoredUserIds", action)
            .addOnSuccessListener {
                Log.d("CENSOR", "Local toggle success for $targetUserId")
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

    fun toggleRoomLock(currentlyLocked: Boolean) {
        viewModelScope.launch {
            try {
                // This updates Firestore
                db.collection("groups").document(roomId)
                    .update("isLocked", !currentlyLocked)
                    .await()

                // We don't need to manually update _uiState here because
                // listenToRoomAndUser() will catch the change and update it for us.
                Log.d("LOCK_DEBUG", "Successfully toggled lock to: ${!currentlyLocked}")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in RoomDetailViewModel: Lock Room failed")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Lock Room", "Room lock failed to lock")

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)

                Log.e("LOCK_DEBUG", "Failed to toggle lock", e)
                _uiState.update { it.copy(error = "Failed to change lock status") }
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

    private fun listenForMembers() {
        db.collection("groups").document(roomId).collection("groupMembers")
            .orderBy("totalPointsInGroup", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let { querySnapshot ->
                    viewModelScope.launch {
                        val memberJobs = querySnapshot.documents.map { memberDoc ->
                            async {
                                val userDoc =
                                    db.collection("users").document(memberDoc.id).get().await()
                                RoomMember(
                                    userId = memberDoc.id,
                                    name = userDoc.getString("name") ?: "Unknown User",
                                    totalPointsInGroup = memberDoc.getLong("totalPointsInGroup")
                                        ?.toInt() ?: 0
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

                        // --- AUTO-UNLOCK LOGIC --- Tested it works, not making the bug
                        if (memberList.size < 3 && _uiState.value.isLocked) {
                            Log.d("RoomLock", "Auto-unlocking room due to member count.${memberList.size}")
                            try {
                                db.collection("groups").document(roomId)
                                    .update("isLocked", false)
                                    .await()
                            } catch (e: Exception) {

                                FirebaseCrashlytics.getInstance().log("Error in RoomDetailViewModel: Unlock room auto failed")

                                // 2. Add custom context (e.g., which Room ID)
                                FirebaseCrashlytics.getInstance().setCustomKey("Unlock room", "Auto-unlock failed")

                                // 3. Record the actual error (This sends the report to Firebase)
                                FirebaseCrashlytics.getInstance().recordException(e)
                                Log.e("RoomLock", "Failed to auto-unlock: ${e.message}")
                            }
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
                    viewModelScope.launch {
                        val tasksBySharedId = snapshot.documents.groupBy {
                            it.getString("sharedTaskId") ?: it.id
                        }

                        val aggregatedListJobs = tasksBySharedId.map { (_, docs) ->
                            async {
                                val firstDoc = docs.first()
                                val completed = docs.count { it.getString("status") != "assigned" }
                                val pendingCount =
                                    docs.count { it.getString("status") == "assigned" }

                                var completionTimestamp: Timestamp? = null
                                if (pendingCount == 0 && docs.isNotEmpty()) {
                                    completionTimestamp =
                                        docs.mapNotNull { it.getTimestamp("handledAt") }.maxOrNull()
                                }

                                // --- Admin View: Fetch completion details ---
                                val completionDetailsJobs = docs.map { doc ->
                                    async {
                                        val userId = doc.getString("assignedToUserId") ?: ""
                                        val status = doc.getString("status") ?: "Unknown"
                                        val userDoc =
                                            db.collection("users").document(userId).get().await()
                                        val userName = userDoc.getString("name") ?: "Unknown User"
                                        TaskCompletionDetails(userName, status)
                                    }
                                }

                                AggregatedTask(
                                    sharedTaskId = firstDoc.getString("sharedTaskId")
                                        ?: firstDoc.id,
                                    title = firstDoc.getString("title") ?: "Unknown Task",
                                    points = firstDoc.getLong("points")?.toInt() ?: 0,
                                    completedCount = completed,
                                    pendingCount = pendingCount,
                                    createdAt = firstDoc.getTimestamp("createdAt"),
                                    completedAt = completionTimestamp,
                                    completions = completionDetailsJobs.awaitAll()
                                )
                            }
                        }
                        val aggregatedList =
                            aggregatedListJobs.awaitAll().sortedByDescending { it.pendingCount }
                        _uiState.update { it.copy(topTasks = aggregatedList) }
                    }
                }
            }
    }


    fun kickMember(userIdToKick: String) {
        val currentUser = auth.currentUser ?: return

        _uiState.update { currentState ->
            currentState.copy(
                members = currentState.members.filter { it.userId != userIdToKick },
                memberCount = (currentState.memberCount - 1).coerceAtLeast(0)
            )
        }

        viewModelScope.launch {
            try {
                // 1. GET TOKEN (Copying the logic from leaveOrDeleteRoom)
                // We must force-fetch the token to bypass the "Zombie Auth" issue
                val tokenResult = currentUser.getIdToken(true).await()
                val rawToken = tokenResult.token

                // 2. PREPARE DATA
                // We include 'debugToken' manually so the Cloud Function can verify us
                val data = hashMapOf(
                    "groupId" to roomId,
                    "userIdToKick" to userIdToKick,
                    "debugToken" to rawToken
                )

//                Log.d("KickMember", "Attempting to kick user: $userIdToKick")

                // 3. CALL FUNCTION
                functions.getHttpsCallable("kickMember")
                    .call(data)
                    .await()

                Log.d("KickMember", "Success!")
                // The real-time listeners will automatically update the UI

            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in RoomDetailViewModel: Kick Member")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Kick Member", "Failed to kick member: $userIdToKick")

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e("KickMember", "Failed: ${e.message}")

                // Detailed Error Logging (Just like in HomeViewModel)
                if (e is com.google.firebase.functions.FirebaseFunctionsException) {
                    Log.e("KickMember", "Code: ${e.code}")
                    Log.e("KickMember", "Details: ${e.details}")
                    _uiState.update { it.copy(error = "Server Error: ${e.message}") }
                } else {
                    _uiState.update { it.copy(error = "Failed to kick member: ${e.message}") }
                }
            }
        }
    }
}
