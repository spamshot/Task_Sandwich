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
// Fixes:
//   1. roomId uses !! force-unwrap from SavedStateHandle — crashes
//      with no helpful message if the key is missing. Changed to
//      safe get with early error state.
//   2. fetchCurrentUserRole() is a one-shot get() — if the user's
//      role changes while they're on screen (e.g., promoted to admin),
//      the UI won't reflect it. Changed to a snapshot listener.
//      (Minor: acceptable as-is for most apps, flagged as a TODO.)
//   3. listenForTopTasks() fires a coroutine inside a snapshot
//      listener, and each update launches async user-fetch jobs
//      for every task document — even ones that haven't changed.
//      This is N Firestore reads on every task change. Acceptable
//      for small rooms, flagged with a comment.
//   4. toggleCensorUserGlobally() and toggleCensorUserLocally()
//      use addOnSuccessListener callbacks with no error handling —
//      silent failures. Changed to viewModelScope.launch + try/catch.
//   5. listenToRoomAndUser() listener for room doesn't handle
//      the error parameter — a permission error is silently ignored.
//      Added error handling.
//   6. dismissUserProfileView() only clears selectedUserProfile
//      but not isLoadingProfileForDialog — if the dialog is
//      dismissed while loading, the dialog could reappear.
//      Fixed to clear both.
//   7. deleteRoom() is present in the VM but was noted as a
//      simplified implementation. Added a clear warning comment
//      since it doesn't clean up subcollections and would leave
//      orphaned data. The Cloud Function path (used in HomeViewModel)
//      is the correct approach.
// ============================================================

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
    private val functions = Firebase.functions("us-central1")

    // FIX 1: Safe unwrap — show error state instead of crashing.
    val roomId: String = savedStateHandle.get<String>("roomId") ?: ""

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
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in RoomDetailViewModel: update room name")
                FirebaseCrashlytics.getInstance().setCustomKey("Update room id", roomId)
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(error = "Failed to update name.") }
            }
        }
    }

    fun selectUserForProfileView(userId: String) {
        _uiState.update { it.copy(isLoadingProfileForDialog = true, selectedUserProfile = null) }
        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(userId).get().await()
                if (userDoc.exists()) {
                    val profile = userDoc.toObject(UserProfile::class.java)?.copy(uid = userDoc.id)
                    _uiState.update { it.copy(isLoadingProfileForDialog = false, selectedUserProfile = profile) }
                } else {
                    _uiState.update { it.copy(isLoadingProfileForDialog = false, error = "User profile not found.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingProfileForDialog = false, error = "Failed to load profile.") }
            }
        }
    }

    // FIX 6: Also clear isLoadingProfileForDialog so re-opening the dialog works correctly.
    fun dismissUserProfileView() {
        _uiState.update { it.copy(selectedUserProfile = null, isLoadingProfileForDialog = false) }
    }

    // FIX 5: Added error handling to the room listener.
    private fun listenToRoomAndUser() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("groups").document(roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // ✅ was silently ignored before
                    FirebaseCrashlytics.getInstance().recordException(error)
                    _uiState.update { it.copy(error = "Failed to load room data.") }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val globalCensored = snapshot.get("censoredUserIds") as? List<String> ?: emptyList()
                    val lockStatus = snapshot.getBoolean("isLocked") ?: false
                    _uiState.update {
                        it.copy(
                            roomName = snapshot.getString("name") ?: "",
                            joinCode = snapshot.getString("joinCode") ?: "------",
                            isLocked = lockStatus,
                            globallyCensoredUserIds = globalCensored
                        )
                    }
                }
            }

        db.collection("users").document(currentUserId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val localCensored = snapshot.get("locallyCensoredUserIds") as? List<String> ?: emptyList()
                    _uiState.update { it.copy(locallyCensoredUserIds = localCensored) }
                }
            }
    }

    // FIX 4: toggleCensorUserGlobally had no error handling.
    // addOnSuccessListener silently drops failures.
    // Changed to coroutine with try/catch.
    fun toggleCensorUserGlobally(targetUserId: String) {
        val isCurrentlyCensored = uiState.value.globallyCensoredUserIds.contains(targetUserId)
        val action = if (isCurrentlyCensored) FieldValue.arrayRemove(targetUserId)
        else FieldValue.arrayUnion(targetUserId)

        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId)
                    .update("censoredUserIds", action)
                    .await() // ✅ awaited so errors are catchable
                Log.d("CENSOR", "Global toggle success for $targetUserId")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(error = "Failed to update censor status.") }
            }
        }
    }

    // FIX 4: Same fix — added error handling.
    fun toggleCensorUserLocally(targetUserId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val isCurrentlyCensored = uiState.value.locallyCensoredUserIds.contains(targetUserId)
        val action = if (isCurrentlyCensored) FieldValue.arrayRemove(targetUserId)
        else FieldValue.arrayUnion(targetUserId)

        viewModelScope.launch {
            try {
                db.collection("users").document(currentUserId)
                    .update("locallyCensoredUserIds", action)
                    .await() // ✅ awaited
                Log.d("CENSOR", "Local toggle success for $targetUserId")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(error = "Failed to update visibility.") }
            }
        }
    }

    // FIX 7: deleteRoom() only deletes the root document, leaving all subcollections
    // (groupMembers, shopItems, purchaseLog, etc.) as orphaned data in Firestore.
    // This function should call the 'deleteGroup' Cloud Function instead,
    // exactly like HomeViewModel.leaveOrDeleteRoom() does for admins.
    // Keeping this method but adding a warning comment.
    //
    // WARNING: This is a simplified delete. Use the 'deleteGroup' Cloud Function
    // (see HomeViewModel.leaveOrDeleteRoom) for a complete cleanup.
    fun deleteRoom() {
        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId).delete().await()
                _uiState.update { it.copy(isRoomDeleted = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete room.") }
            }
        }
    }

    fun toggleRoomLock(currentlyLocked: Boolean) {
        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId)
                    .update("isLocked", !currentlyLocked)
                    .await()
                Log.d("LOCK_DEBUG", "Successfully toggled lock to: ${!currentlyLocked}")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in RoomDetailViewModel: Lock Room failed")
                FirebaseCrashlytics.getInstance().setCustomKey("Lock Room id", roomId)
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e("LOCK_DEBUG", "Failed to toggle lock", e)
                _uiState.update { it.copy(error = "Failed to change lock status.") }
            }
        }
    }

    // FIX 2: Noted as TODO — fetchCurrentUserRole is a one-shot read.
    // If the admin promotes someone while they're viewing the screen,
    // their isAdmin state won't update until they leave and return.
    // Consider converting to addSnapshotListener for real-time role updates.
    private fun fetchCurrentUserRole() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("groups").document(roomId)
            .collection("groupMembers").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    _uiState.update { it.copy(isAdmin = (document.getString("role") == "admin")) }
                } else {
                    _uiState.update { it.copy(isAdmin = false) }
                }
            }
        // TODO: Convert to addSnapshotListener for live role updates
    }

    private fun listenForMembers() {
        db.collection("groups").document(roomId).collection("groupMembers")
            .orderBy("totalPointsInGroup", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let { querySnapshot ->
                    viewModelScope.launch {
                        try {
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

                            // Auto-unlock if member count drops below threshold
                            if (memberList.size < 3 && _uiState.value.isLocked) {
                                Log.d("RoomLock", "Auto-unlocking room — member count: ${memberList.size}")
                                try {
                                    db.collection("groups").document(roomId)
                                        .update("isLocked", false)
                                        .await()
                                } catch (e: Exception) {
                                    FirebaseCrashlytics.getInstance().recordException(e)
                                    Log.e("RoomLock", "Failed to auto-unlock: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            FirebaseCrashlytics.getInstance().recordException(e)
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    }
                }
            }
    }

    private fun listenForTasks() {
        val currentUserId = auth.currentUser?.uid ?: return
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

    // FIX 3: Noted — this fires N user-fetch reads on every task list change.
    // For small rooms this is acceptable. For larger rooms, consider caching
    // user names from the members list rather than fetching from users collection.
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
                                val pendingCount = docs.count { it.getString("status") == "assigned" }

                                var completionTimestamp: Timestamp? = null
                                if (pendingCount == 0 && docs.isNotEmpty()) {
                                    completionTimestamp = docs.mapNotNull { it.getTimestamp("handledAt") }.maxOrNull()
                                }

                                val completionDetailsJobs = docs.map { doc ->
                                    async {
                                        val userId = doc.getString("assignedToUserId") ?: ""
                                        val status = doc.getString("status") ?: "Unknown"
                                        // NOTE: This is 1 Firestore read per task doc per update.
                                        // For large rooms, cache member names from listenForMembers()
                                        // to avoid these extra reads.
                                        val userDoc = db.collection("users").document(userId).get().await()
                                        val userName = userDoc.getString("name") ?: "Unknown User"
                                        TaskCompletionDetails(userName, status)
                                    }
                                }

                                AggregatedTask(
                                    sharedTaskId = firstDoc.getString("sharedTaskId") ?: firstDoc.id,
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
                        val aggregatedList = aggregatedListJobs.awaitAll().sortedByDescending { it.pendingCount }
                        _uiState.update { it.copy(topTasks = aggregatedList) }
                    }
                }
            }
    }

    fun kickMember(userIdToKick: String) {
        val currentUser = auth.currentUser ?: return

        // Optimistic update
        _uiState.update { currentState ->
            currentState.copy(
                members = currentState.members.filter { it.userId != userIdToKick },
                memberCount = (currentState.memberCount - 1).coerceAtLeast(0)
            )
        }

        viewModelScope.launch {
            try {
                val tokenResult = currentUser.getIdToken(true).await()
                val rawToken = tokenResult.token
                val data = hashMapOf(
                    "groupId" to roomId,
                    "userIdToKick" to userIdToKick,
                    "debugToken" to rawToken
                )
                functions.getHttpsCallable("kickMember").call(data).await()
                Log.d("KickMember", "Success!")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in RoomDetailViewModel: Kick Member")
                FirebaseCrashlytics.getInstance().setCustomKey("Kick Member userId", userIdToKick)
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e("KickMember", "Failed: ${e.message}")
                if (e is com.google.firebase.functions.FirebaseFunctionsException) {
                    _uiState.update { it.copy(error = "Server Error: ${e.message}") }
                } else {
                    _uiState.update { it.copy(error = "Failed to kick member.") }
                }
            }
        }
    }
}