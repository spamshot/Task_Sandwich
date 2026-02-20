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
// Fixes:
//   1. lastActive update in init uses .set() with merge —
//      a write fires every time the ViewModel is created.
//      Moved into a separate private function and called after
//      loadAllData() to keep init clean and explicit.
//   2. loadAllData() Listener 1 launches a coroutine inside
//      a snapshot callback on EVERY snapshot update — including
//      minor profile field changes. Each launch fires N parallel
//      Firestore reads (2 per room). For a user with 4 rooms,
//      every profile change triggers 8 extra reads. This is the
//      correct architecture for now but flagged with a comment.
//   3. createRoom() spam protection fires N async reads per room
//      to count owned rooms. The AppShellViewModel already has
//      a real-time listener for owned room count that stays up-
//      to-date without extra reads. In HomeViewModel, we re-
//      implement this with on-demand reads. Acceptable but noted
//      as a duplication to consolidate later.
//   4. createRoom() has no input validation on roomName —
//      blank names would be saved to Firestore. Added trim + blank check.
//   5. markTaskComplete() uses task.groupId.let { roomId -> }
//      on a value already checked .isNullOrEmpty() — redundant
//      let block. Simplified to direct usage.
//   6. leaveOrDeleteRoom() error messages expose raw e.message.
//      Replaced with friendly messages.
//   7. updateGroupedTasks() is called from two different places
//      that update different caches (userRooms vs userTasks)
//      potentially with stale data from the other. This is
//      correct as-is (both caches are class properties) but
//      fragile. Noted with a comment.
//   8. onResume() is empty — the interface RefreshesViewModel
//      is implemented but does nothing. Acceptable for now,
//      left with a comment.
// ============================================================

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
        updateLastActive() // FIX 1: extracted from init for clarity
    }

    // FIX 1: Isolated so it's clear what this write does and easy to remove/change.
    private fun updateLastActive() {
        val uid = auth.currentUser?.uid ?: return
        val update = hashMapOf("lastActive" to com.google.firebase.Timestamp.now())
        db.collection("users").document(uid)
            .set(update, com.google.firebase.firestore.SetOptions.merge())
        // Note: fire-and-forget is intentional here — no need to await or handle errors
        // for a non-critical lastActive timestamp.
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
            if (userListenerLoaded && tasksListenerLoaded)
                _uiState.update { it.copy(isLoading = false) }
        }

        // Listener 1: User profile + room enrichment
        // FIX 2: NOTE — every profile change triggers 2 reads per room (group doc + member doc).
        // For 4 rooms = 8 reads per minor profile update. Acceptable for now.
        // Future optimisation: listen only to groupsJoined changes and skip if unchanged.
        db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(error = "Failed to load user profile.") }
                    userListenerLoaded = true; checkCompletion()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    var user = snapshot.toObject(UserProfile::class.java)
                    if (user?.role == "super_admin") {
                        user = user.copy(totalSelfPoints = 999)
                    }
                    _uiState.update { it.copy(userProfile = user) }

                    @Suppress("UNCHECKED_CAST")
                    val roomsData = snapshot.get("groupsJoined") as? List<HashMap<String, String>> ?: emptyList()

                    viewModelScope.launch {
                        try {
                            val roomJobs = roomsData.map { roomMap ->
                                async {
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
                            userRooms = roomsListWithPoints
                            _uiState.update { it.copy(rooms = roomsListWithPoints) }
                            updateGroupedTasks()
                        } catch (e: Exception) {
                            _uiState.update { it.copy(error = "Error loading room data.") }
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

        // Listener 2: Tasks
        db.collection("tasks")
            .whereEqualTo("assignedToUserId", currentUser.uid)
            .whereEqualTo("status", "assigned")
            .addSnapshotListener { snapshot, error ->
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

                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val cutoffTime = cal.time

                    userTasks = allTasks.filter { task ->
                        val due = task.dueDate?.toDate()
                        // Rule 1: Always show one-time tasks
                        if (task.repeatOption == "Never" || task.repeatOption == null) return@filter true
                        // Rule 2: Repeating tasks — show if due before cutoff
                        due == null || due.before(cutoffTime)
                    }
                    updateGroupedTasks()
                }
                tasksListenerLoaded = true
                checkCompletion()
            }
    }

    // FIX 7: Both listeners call this — it always uses the current class-level caches.
    // This is safe because userRooms and userTasks are only written from the main
    // coroutine dispatcher via viewModelScope. Note: if both listeners fire near-
    // simultaneously, one update may briefly show stale grouped results.
    private fun updateGroupedTasks() {
        val grouped = userTasks.groupBy { task ->
            val room = userRooms.find { it.groupId == task.groupId }
            val roomName = room?.groupName ?: "Personal Tasks"
            "$roomName - ${task.assignedByName}"
        }
        _uiState.update { it.copy(groupedTasks = grouped) }
    }

    // FIX 8: onResume() is required by RefreshesViewModel but currently a no-op.
    // The real-time listeners keep data fresh automatically.
    override fun onResume() { /* Real-time listeners handle refresh automatically */ }

    // FIX 4: Added input validation on roomName.
    fun createRoom(roomName: String) {
        val currentUser = auth.currentUser ?: return

        // FIX 4: Validate before making any network calls.
        val trimmedName = roomName.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(error = "Room name cannot be empty.") }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                // Spam protection: check owned room count server-side
                // FIX 3: This duplicates AppShellViewModel's listenForOwnedRoomCount.
                // Consider sharing this state or replacing with a Cloud Function guard.
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
                            it.copy(isLoading = false, error = "Limit reached: You can only create up to 4 rooms.")
                        }
                        return@launch
                    }
                }

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

                _uiState.update { it.copy(isLoading = false, createdRoomId = newRoomRef.id) }

            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in HomeViewModel: createRoom")
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to create room. Please try again.") }
            }
        }
    }

    fun onRoomCreationHandled() {
        _uiState.update { it.copy(createdRoomId = null) }
    }

    // FIX 5: Removed redundant .let { } wrapper around groupId after isNullOrEmpty() check.
    fun markTaskComplete(task: Task) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val batch = db.batch()
                val taskRef = db.collection("tasks").document(task.id)
                batch.update(taskRef, "status", "completed", "handledAt", Timestamp.now())

                val userRef = db.collection("users").document(currentUser.uid)

                if (task.groupId.isNullOrEmpty()) {
                    // Personal task — award self points + check milestone icons
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
                    // Group task — award group points
                    batch.update(userRef, "totalPoints", FieldValue.increment(task.points.toLong()))
                    // FIX 5: Direct usage — no need for .let since we've already checked isNullOrEmpty()
                    val memberRef = db.collection("groups").document(task.groupId)
                        .collection("groupMembers").document(currentUser.uid)
                    batch.update(memberRef, "totalPointsInGroup", FieldValue.increment(task.points.toLong()))
                }
                batch.commit().await()
            } catch (e: Exception) {
                Log.e("HomeViewModel", "markTaskComplete failed: ${e.message}")
                FirebaseCrashlytics.getInstance().log("Error in HomeViewModel: Task mark as complete")
                FirebaseCrashlytics.getInstance().setCustomKey("task id", task.id)
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(error = "Could not complete task. Please try again.") }
            }
        }
    }

    // FIX 6: Friendly error messages instead of raw e.message.
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
                    functions.getHttpsCallable("deleteGroup").call(data).await()
                    Log.d("HomeViewModel", "Room deleted: ${room.groupId}")
                } else {
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
                Log.e("HomeViewModel", "leaveOrDeleteRoom failed: ${e.message}")
                FirebaseCrashlytics.getInstance().log("Error in HomeViewModel: Leave or Delete")
                FirebaseCrashlytics.getInstance().setCustomKey("Leave or Delete room id", room.groupId)
                FirebaseCrashlytics.getInstance().recordException(e)
                // FIX 6: Friendly messages — no raw e.message exposed to UI
                val friendlyError = if (e is com.google.firebase.functions.FirebaseFunctionsException) {
                    "Server error. Please try again."
                } else {
                    "Could not ${if (room.isAdmin) "delete" else "leave"} room. Please try again."
                }
                _uiState.update { it.copy(error = friendlyError) }
            } finally {
                _uiState.update { it.copy(roomBeingDeletedId = null) }
            }
        }
    }
}






