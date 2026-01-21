package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll


// Note: All data classes (UserProfile, Task, UserRoom) should be in HomeData.kt
// and imported at the top of this file.
import com.spam.tasksandwich.Task
import com.spam.tasksandwich.UserProfile
import com.spam.tasksandwich.UserRoom


// Data class to represent a member in the UI
data class HomeUiState(
    val isLoading: Boolean = true,
    val userProfile: UserProfile? = null,
    val rooms: List<UserRoom> = emptyList(),
    val groupedTasks: Map<String, List<Task>> = emptyMap(),
    val createdRoomId: String? = null,
    val error: String? = null,
    val message: String? = null,
    val roomBeingDeletedId: String? = null
)

class HomeViewModel : ViewModel(), RefreshesViewModel {

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val functions = Firebase.functions("us-central1")

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private var userRooms: List<UserRoom> = emptyList()
    private var userTasks: List<Task> = emptyList()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        _uiState.update { it.copy(isLoading = true) }
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false, error = "User not logged in.") }
            return
        }

        var userListenerLoaded = false
        var tasksListenerLoaded = false
        fun checkCompletion() {
            if (userListenerLoaded && tasksListenerLoaded) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // Listener 1: Fetches user profile data, then asynchronously enriches the room data.
        val userDocRef = db.collection("users").document(currentUser.uid)
        userDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                _uiState.update { it.copy(error = "Failed to load user profile.") }
                userListenerLoaded = true
                checkCompletion()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                // 1. Immediately map the user profile data.
                val user = snapshot.toObject(UserProfile::class.java)

                // 2. Immediately update the UI state with the latest user profile.
                // This is the critical fix that ensures point changes are reflected instantly.
                _uiState.update { it.copy(userProfile = user) }

                @Suppress("UNCHECKED_CAST")
                val roomsData = snapshot.get("groupsJoined") as? List<HashMap<String, String>> ?: emptyList()

                // 3. Kick off the asynchronous work to fetch points for each room.
                viewModelScope.launch {
                    try {
                        val roomJobs = roomsData.map { roomMap ->
                            this.async {
                                val groupId = roomMap["groupId"] ?: ""
                                val groupName = roomMap["groupName"] ?: "Unnamed Room"
                                var points = 0
                                var isAdmin = false

                                if (groupId.isNotEmpty()) {
                                    val groupDoc = db.collection("groups").document(groupId).get().await()
                                    if (groupDoc.exists()) {
                                        isAdmin = groupDoc.getString("adminUserId") == currentUser.uid
                                    }

                                    val memberDoc = db.collection("groups").document(groupId)
                                        .collection("groupMembers").document(currentUser.uid)
                                        .get().await()
                                    if (memberDoc.exists()) {
                                        points = memberDoc.getLong("totalPointsInGroup")?.toInt() ?: 0
                                    }
                                }
                                UserRoom(groupId, groupName, points, isAdmin)
                            }
                        }
                        val roomsListWithPoints = roomJobs.awaitAll()

                        // 4. Update the UI state AGAIN, this time with the fully enriched room data.
                        _uiState.update { it.copy(rooms = roomsListWithPoints) }

                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = "Error loading room points.") }
                    } finally {
                        // This block is guaranteed to run, ensuring the listener is marked as "loaded".
                        userListenerLoaded = true
                        checkCompletion()
                    }
                }
            } else {
                // If the user document doesn't exist, we still count this listener as "loaded".
                userListenerLoaded = true
                checkCompletion()
            }
        }

        // Listener 2: Fetches tasks assigned to the current user.
        val tasksQuery = db.collection("tasks")
            .whereEqualTo("assignedToUserId", currentUser.uid)
            .whereEqualTo("status", "assigned")
        tasksQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                _uiState.update { it.copy(error = "Failed to load tasks.") }
                tasksListenerLoaded = true
                checkCompletion()
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val tasks = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Task::class.java)?.copy(id = doc.id)
                }
                _uiState.update { currentState ->
                    val rooms = currentState.rooms
                    val grouped = tasks.groupBy { task ->
                        val room = rooms.find { it.groupId == task.groupId }
                        val roomName = room?.groupName ?: "Personal Tasks"
                        "$roomName - from ${task.assignedByName}"
                    }
                    currentState.copy(groupedTasks = grouped)
                }
            }
            tasksListenerLoaded = true
            checkCompletion()
        }
    }

    private fun updateGroupedTasks() {
        val grouped = userTasks.groupBy { task ->
            val room = userRooms.find { it.groupId == task.groupId }
            val roomName = room?.groupName ?: "Personal Tasks"
            "$roomName - from ${task.assignedByName}"
        }
        _uiState.update { it.copy(groupedTasks = grouped) }
    }

    override fun onResume() {
        // Refresh logic if needed when coming back to screen
    }

    fun createRoom(roomName: String) {
        val currentUser = auth.currentUser ?: return

        // 1. Block UI immediately
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                // =================================================================
                // SPAM PROTECTION: The "Profile-First" Strategy
                // =================================================================
                // 1. Fetch the user's profile to see what rooms they have joined.
                // This is safer than querying the 'groups' collection directly,
                // which might be blocked by security rules.
                val userDoc = db.collection("users").document(currentUser.uid)
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()

                @Suppress("UNCHECKED_CAST")
                val groupsJoined = userDoc.get("groupsJoined") as? List<HashMap<String, String>> ?: emptyList()

                // Optimization: If you haven't joined 4 rooms, you definitely don't OWN 4 rooms.
                if (groupsJoined.size >= 4) {

                    // 2. We need to check if you are the ADMIN of these rooms.
                    var ownedCount = 0

                    // Check the rooms one by one (or in parallel)
                    val checkJobs = groupsJoined.map { map ->
                        async {
                            val groupId = map["groupId"] ?: ""
                            if (groupId.isNotEmpty()) {
                                val groupDoc = db.collection("groups").document(groupId).get().await()
                                if (groupDoc.exists() && groupDoc.getString("adminUserId") == currentUser.uid) {
                                    return@async 1
                                }
                            }
                            return@async 0
                        }
                    }

                    ownedCount = checkJobs.awaitAll().sum()

                    Log.d("RoomLimit", "User has joined ${groupsJoined.size} rooms and owns $ownedCount.")

                    if (ownedCount >= 4) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Limit reached: You can only create up to 4 rooms."
                            )
                        }
                        return@launch
                    }
                }
                // =================================================================

                // ... Proceed to Create Room ...
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
                val roomInfo = hashMapOf("groupId" to newRoomRef.id, "groupName" to roomName)

                db.runBatch { batch ->
                    batch.set(newRoomRef, newRoom)
                    batch.set(newRoomRef.collection("groupMembers").document(currentUser.uid), adminMember)
                    batch.update(userRef, "groupsJoined", FieldValue.arrayUnion(roomInfo))
                }.await()

                _uiState.update { it.copy(isLoading = false, createdRoomId = newRoomRef.id) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to create room: ${e.message}") }
            }
        }
    }


    fun onRoomCreationHandled() {
        _uiState.update { it.copy(createdRoomId = null) }
    }

    /**
     * Marks a given task as complete and atomically increments the correct point totals.
     * This function uses a batch write to ensure all database operations succeed or fail together.
     *
     * @param task The Task object to be marked as complete.
     */
    fun markTaskComplete(task: Task) {
        // Get the currently authenticated user. If no one is logged in, do nothing.
        val currentUser = auth.currentUser ?: return

        // Launch a coroutine in the ViewModel's scope for this background database operation.
        viewModelScope.launch {
            try {
                // A batch write allows us to perform multiple writes as a single atomic unit.
                val batch = db.batch()

                // 1. Get a reference to the specific task document.
                val taskRef = db.collection("tasks").document(task.id)
                // Update the task's status and add a timestamp for when it was handled.
                batch.update(
                    taskRef,
                    "status", "completed",
                    "handledAt", Timestamp.now()
                )

                // 2. Get a reference to the current user's document.
                val userRef = db.collection("users").document(currentUser.uid)

                // 3. THE CRITICAL FIX: Check if the task is personal or a group task.
                // We do this by checking if the groupId is null or empty. This is the most
                // robust method and works for both old and new personal tasks.
                if (task.groupId.isNullOrEmpty()) {
                    // If there is no groupId, it's a personal task. Increment 'totalSelfPoints'.
                    batch.update(userRef, "totalSelfPoints", FieldValue.increment(task.points.toLong()))
                } else {
                    // If there is a groupId, it's a group task.

                    // A) Increment the user's global 'totalPoints' for group-related activities.
                    batch.update(userRef, "totalPoints", FieldValue.increment(task.points.toLong()))

                    // B) Increment the room-specific 'totalPointsInGroup' for the leaderboard.
                    task.groupId?.let { roomId ->
                        if (roomId.isNotEmpty()) {
                            val memberRef = db.collection("groups").document(roomId)
                                .collection("groupMembers").document(currentUser.uid)
                            batch.update(memberRef, "totalPointsInGroup", FieldValue.increment(task.points.toLong()))
                        }
                    }
                }

                // 4. Commit all prepared operations to the database at once.
                batch.commit().await()

            } catch (e: Exception) {
                // If anything fails (e.g., network error, permissions issue),
                // update the UI state with an error message.
                _uiState.update { it.copy(error = "Could not complete task: ${e.message}") }
            }
        }
    }

    fun leaveOrDeleteRoom(room: UserRoom) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(error = "Not logged in.") }
            return
        }

        _uiState.update { it.copy(roomBeingDeletedId = room.groupId) }

        viewModelScope.launch {
            try {
                if (room.isAdmin) {
                    // =================================================================
                    // 1. ADMIN DELETE LOGIC (Cloud Function with Manual Auth Fix)
                    // =================================================================
//                    Log.d("DeleteRoom", "Preparing to delete room: ${room.groupId}")
                    // A. Force Token Refresh
                    val tokenResult = currentUser.getIdToken(true).await()
                    val rawToken = tokenResult.token

                    // B. Prepare Data with Manual Debug Token
                    // This bypasses the "You must be logged in" error if headers are stripped
                    val data = hashMapOf(
                        "groupId" to room.groupId,
                        "debugToken" to rawToken
                    )

                    // C. Call Function
                    functions.getHttpsCallable("deleteGroup")
                        .call(data)
                        .await()

                    Log.d("DeleteRoom", "Success!")
                    // The Firestore listeners will automatically remove the room from the UI

                } else {
                    // =================================================================
                    // 2. MEMBER LEAVE LOGIC (Firestore Transaction)
                    // =================================================================
                    Log.d("LeaveRoom", "Leaving room: ${room.groupId}")

                    val userRef = db.collection("users").document(currentUser.uid)
                    val memberRef = db.collection("groups").document(room.groupId)
                        .collection("groupMembers").document(currentUser.uid)

                    db.runTransaction { transaction ->
                        val userDoc = transaction.get(userRef)
                        @Suppress("UNCHECKED_CAST")
                        val groupsJoined = userDoc.get("groupsJoined") as? List<HashMap<String, Any>> ?: emptyList()
                        val roomToRemove = groupsJoined.find { it["groupId"] == room.groupId }

                        if (roomToRemove != null) {
                            transaction.update(userRef, "groupsJoined", FieldValue.arrayRemove(roomToRemove))
                        }
                        transaction.delete(memberRef)
                    }.await()
                }
            } catch (e: Exception) {
                Log.e("LeaveOrDelete", "Failed: ${e.message}")
                if (e is com.google.firebase.functions.FirebaseFunctionsException) {
                    _uiState.update { it.copy(error = "Server Error: ${e.message}") }
                } else {
                    _uiState.update { it.copy(error = "Failed: ${e.message}") }
                }
            }finally {
                // 2. ALWAYS clear the ghost state when done (or if failed)
                // This ensures the card becomes clickable again if the operation failed.
                _uiState.update { it.copy(roomBeingDeletedId = null) }
            }
        }
    }

}