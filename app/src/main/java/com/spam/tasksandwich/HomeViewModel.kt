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
import com.google.firebase.functions.functions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.spam.tasksandwich.IconRepository
import java.util.Calendar // Added for date calculation

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
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val update = hashMapOf("lastActive" to com.google.firebase.Timestamp.now())
            db.collection("users").document(uid)
                .set(update, com.google.firebase.firestore.SetOptions.merge())
        }
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
                userListenerLoaded = true; checkCompletion()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                // 1. Immediately map the user profile data.
                var user = snapshot.toObject(UserProfile::class.java)
                if (user?.role == "super_admin") {
                    user = user.copy(totalSelfPoints = 999)
                }

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
                        userRooms = roomsListWithPoints
                        _uiState.update { it.copy(rooms = roomsListWithPoints) }
                        updateGroupedTasks()

                    } catch (e: Exception) {
                        _uiState.update { it.copy(error = "Error loading room points.") }
                    } finally {
                        userListenerLoaded = true
                        checkCompletion()
                    }
                }
            } else {
                userListenerLoaded = true
                checkCompletion()
            }
        }

        // ====================================================================================
        // Listener 2: Fetches tasks assigned to the current user (WITH FILTERING)
        // ====================================================================================
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
                val allTasks = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Task::class.java)?.copy(id = doc.id)
                }


                // Calculate Midnight the DAY AFTER Tomorrow (48-hour window)
                // This ensures tasks due tomorrow evening are visible today.
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 1) // Change from 1 to 2. Change 1 from 2 so you can test next day
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val cutoffTime = cal.time // This is now Midnight the day after tomorrow

                userTasks = allTasks.filter { task ->
                    val due = task.dueDate?.toDate()

                    // Rule 1: Always show one-time tasks (Function will delete them from DB when done)
                    if (task.repeatOption == "Never" || task.repeatOption == null) {
                        return@filter true
                    }

                    // Rule 2: Repeating tasks
                    // Show if: No date OR due before the 48-hour cutoff.
                    // This allows "Tomorrow's" daily tasks to show up today.
                    due == null || due.before(cutoffTime)
                }

                updateGroupedTasks()
            }
            tasksListenerLoaded = true
            checkCompletion()
        }
    }

    private fun updateGroupedTasks() {
        val grouped = userTasks.groupBy { task ->
            val room = userRooms.find { it.groupId == task.groupId }
            val roomName = room?.groupName ?: "Personal Tasks"
            "$roomName - ${task.assignedByName}"
        }
        _uiState.update { it.copy(groupedTasks = grouped) }
    }

    override fun onResume() {
        // Refresh logic if needed when coming back to screen
    }

    fun createRoom(roomName: String) {
        val currentUser = auth.currentUser ?: return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                // =================================================================
                // SPAM PROTECTION
                // =================================================================
                val userDoc = db.collection("users").document(currentUser.uid)
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()

                @Suppress("UNCHECKED_CAST")
                val groupsJoined = userDoc.get("groupsJoined") as? List<HashMap<String, String>> ?: emptyList()

                if (groupsJoined.size >= 4) {
                    var ownedCount = 0
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

    fun markTaskComplete(task: Task) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val batch = db.batch()
                val taskRef = db.collection("tasks").document(task.id)

                batch.update(taskRef, "status", "completed", "handledAt", Timestamp.now())

                val userRef = db.collection("users").document(currentUser.uid)

                if (task.groupId.isNullOrEmpty()) {
                    val userProfile = _uiState.value.userProfile
                    if (userProfile != null) {
                        val newTotalSelfPoints = userProfile.totalSelfPoints + task.points
                        IconRepository.MilestoneIconsMap.forEach { (iconId, score) ->
                            if (newTotalSelfPoints >= score && !userProfile.unlockedIconIds.contains(iconId)) {
                                batch.update(userRef, "unlockedIconIds", FieldValue.arrayUnion(iconId))
                            }
                        }
                    }
                    batch.update(userRef, "totalSelfPoints", FieldValue.increment(task.points.toLong()))
                } else {
                    batch.update(userRef, "totalPoints", FieldValue.increment(task.points.toLong()))
                    task.groupId.let { roomId ->
                        if (roomId.isNotEmpty()) {
                            val memberRef = db.collection("groups").document(roomId)
                                .collection("groupMembers").document(currentUser.uid)
                            batch.update(memberRef, "totalPointsInGroup", FieldValue.increment(task.points.toLong()))
                        }
                    }
                }
                batch.commit().await()
            } catch (e: Exception) {
                Log.e("Marking Complete", "Failed: ${e.message}")
                // Handle error
                FirebaseCrashlytics.getInstance().log("Error in homeViewModel: Task mark as complete")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("task mark", task.id)

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)

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
                    val tokenResult = currentUser.getIdToken(true).await()
                    val rawToken = tokenResult.token
                    val data = hashMapOf("groupId" to room.groupId, "debugToken" to rawToken)

                    functions.getHttpsCallable("deleteGroup")
                        .call(data)
                        .await()

                    Log.d("DeleteRoom", "Success!")

                } else {
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
                // Handle error
                FirebaseCrashlytics.getInstance().log("Error in homeViewModel: Leave or Delete")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Leave or Delete room", room.groupId)

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)

                if (e is com.google.firebase.functions.FirebaseFunctionsException) {
                    _uiState.update { it.copy(error = "Server Error: ${e.message}") }
                } else {
                    _uiState.update { it.copy(error = "Failed: ${e.message}") }
                }
            }finally {
                _uiState.update { it.copy(roomBeingDeletedId = null) }
            }
        }
    }
}