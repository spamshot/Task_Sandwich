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

data class JoinRoomUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    // This will hold the ID of the room to navigate to upon success
    val joinSuccessRoomId: String? = null
)

class JoinRoomViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(JoinRoomUiState())
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
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 1. Find the group with the matching join code
                val groupQuery = db.collection("groups")
                    .whereEqualTo("joinCode", joinCode)
                    .limit(1) // We only expect one
                    .get()
                    .await()

                if (groupQuery.isEmpty) {
                    _uiState.update { it.copy(isLoading = false, error = "Invalid room code. Please try again.") }
                    return@launch
                }


                val groupDoc = groupQuery.documents.first()
                val groupId = groupDoc.id
                val userRef = db.collection("users").document(currentUser.uid)
                val roomInfo = hashMapOf(
                    "groupId" to groupId,
                    "groupName" to groupDoc.getString("name") // Store the name for easy display
                )
                val groupRef = db.collection("groups").document(groupId)

                userRef.update("groupsJoined", FieldValue.arrayUnion(roomInfo)).await()
                _uiState.update { it.copy(isLoading = false, joinSuccessRoomId = groupId) }

                // 2. Add the current user to the groupMembers subcollection
                val newMemberData = hashMapOf(
                    "userId" to currentUser.uid,
                    "role" to "member",
                    "status" to "approved", // Assuming auto-approval for now
                    "totalPointsInGroup" to 0,
                    "joinedAt" to Timestamp.now()
                )

                db.collection("groups").document(groupId)
                    .collection("groupMembers").document(currentUser.uid)
                    .set(newMemberData) // .set() will create or overwrite, which is fine here
                    .await()

//                groupRef.update("memberIds", FieldValue.arrayUnion(currentUser.uid)).await()
                // 3. Signal success to the UI with the groupId
                _uiState.update { it.copy(isLoading = false, joinSuccessRoomId = groupId) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "An error occurred: ${e.message}") }
            }
        }
    }

    // To be called by the UI after it has handled the navigation
    fun onNavigationHandled() {
        _uiState.update { it.copy(joinSuccessRoomId = null) }
    }
}