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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.lifecycle.viewModelScope


// Note: All data classes (UserProfile, Task, UserRoom) should be in HomeData.kt
// and imported at the top of this file.

data class HomeUiState(
    val isLoading: Boolean = true,
    val userProfile: UserProfile? = null,
    val rooms: List<UserRoom> = emptyList(),
    val groupedTasks: Map<String, List<Task>> = emptyMap(),
    val createdRoomId: String? = null,
    val error: String? = null
)

class HomeViewModel : ViewModel() {

    private val auth = Firebase.auth
    private val db = Firebase.firestore

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    /**
     * Loads all necessary data for the home screen from Firestore.
     * It uses two parallel listeners: one for the user's profile and rooms,
     * and one for the user's assigned tasks. A loading flag is managed
     * to ensure the UI waits for both initial loads to complete.
     */
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

        // Listener 1: Fetches user profile data and the list of rooms. For each room,
        // it then fetches the user's specific point total within that room.
        val userDocRef = db.collection("users").document(currentUser.uid)
        userDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                _uiState.update { it.copy(error = "Failed to load user profile.") }
                userListenerLoaded = true
                checkCompletion()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val user = snapshot.toObject(UserProfile::class.java)
                @Suppress("UNCHECKED_CAST")
                val roomsData = snapshot.get("groupsJoined") as? List<HashMap<String, String>> ?: emptyList()

                viewModelScope.launch {
                    try {
                        val roomJobs = roomsData.map { roomMap ->
                            this.async {
                                val groupId = roomMap["groupId"] ?: ""
                                val groupName = roomMap["groupName"] ?: "Unnamed Room"
                                var points = 0
                                if (groupId.isNotEmpty()) {
                                    val memberDoc = db.collection("groups").document(groupId)
                                        .collection("groupMembers").document(currentUser.uid)
                                        .get().await()
                                    if (memberDoc.exists()) {
                                        points = memberDoc.getLong("totalPointsInGroup")?.toInt() ?: 0
                                    }
                                }
                                UserRoom(groupId, groupName, points)
                            }
                        }
                        val roomsListWithPoints = roomJobs.awaitAll()
                        _uiState.update { it.copy(userProfile = user, rooms = roomsListWithPoints) }
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
                _uiState.update { it.copy(groupedTasks = tasks.groupBy { it.assignedByName }) }
            }
            tasksListenerLoaded = true
            checkCompletion()
        }
    }

    fun markTaskComplete(task: Task) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val batch = db.batch()
                val taskRef = db.collection("tasks").document(task.id)
                batch.update(taskRef, "status", "completed")

                val userRef = db.collection("users").document(currentUser.uid)
                batch.update(userRef, "totalPoints", FieldValue.increment(task.points.toLong()))

                task.groupId?.let { roomId ->
                    if (roomId.isNotEmpty()) {
                        val memberRef = db.collection("groups").document(roomId)
                            .collection("groupMembers").document(currentUser.uid)
                        batch.update(memberRef, "totalPointsInGroup", FieldValue.increment(task.points.toLong()))
                    }
                }
                batch.commit().await()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Could not complete task: ${e.message}") }
            }
        }
    }

    fun createRoom(roomName: String) {
        val currentUser = auth.currentUser ?: return
        _uiState.update { it.copy(isLoading = true) }
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
                    "userId" to currentUser.uid, "role" to "admin", "status" to "approved",
                    "totalPointsInGroup" to 0, "joinedAt" to Timestamp.now()
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
}


//fun createRoom(adminRole: String, memberRole: String) {
//    val currentUser = auth.currentUser ?: return
//    val userName = _uiState.value.userProfile?.name ?: "Admin"
//
//    viewModelScope.launch {
//        _uiState.update { it.copy(isLoading = true) }
//        try {
//            // --- THIS WAS THE MISSING LINE ---
//            val newRoomRef = db.collection("groups").document()
//            // ---------------------------------
//
//            val joinCode = (100000..999999).random().toString()
//
//            val newRoom = hashMapOf(
//                "name" to "$userName's Room",
//                "adminUserId" to currentUser.uid,
//                "joinCode" to joinCode,
//                "autoAcceptMembers" to true,
//                "adminRoleName" to adminRole,
//                "memberRoleName" to memberRole,
//                "createdAt" to Timestamp.now()
//            )
//
//            val adminMember = hashMapOf(
//                "userId" to currentUser.uid,
//                "role" to "admin",
//                "status" to "approved",
//                "totalPointsInGroup" to 0,
//                "joinedAt" to Timestamp.now()
//            )
//
//            val userRef = db.collection("users").document(currentUser.uid)
//            val roomInfo = hashMapOf(
//                "groupId" to newRoomRef.id,
//                "groupName" to newRoom["name"]
//            )
//
//            db.runBatch { batch ->
//                batch.set(newRoomRef, newRoom)
//                batch.set(newRoomRef.collection("groupMembers").document(currentUser.uid), adminMember)
//                batch.update(userRef, "groupsJoined", FieldValue.arrayUnion(roomInfo))
//            }.await()
//
//            _uiState.update { it.copy(isLoading = false, createdRoomId = newRoomRef.id) }
//
//        } catch (e: Exception) {
//            _uiState.update { it.copy(isLoading = false, error = "Failed to create room: ${e.message}") }
//        }
//    }
//}