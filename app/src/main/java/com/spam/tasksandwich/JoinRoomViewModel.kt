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
//   1. joinRoom() updates joinSuccessRoomId twice — once before
//      adding the user to groupMembers, and again after. This
//      means the UI could navigate to the room before the member
//      document is created, causing the room to appear empty or
//      the user to appear unauthorized. Removed the first premature
//      update and kept only the final one after all writes.
//   2. joinRoom() does not check if the room is locked — a user
//      could join a locked room. AppShellViewModel has this check
//      but JoinRoomViewModel doesn't. Added the lock check.
//   3. joinRoom() does not check if the user is already a member —
//      re-joining would overwrite their member doc (resetting points
//      to 0) and add a duplicate entry to groupsJoined. Added a
//      membership check before proceeding.
//   4. joinCode.length != 6 validation doesn't trim whitespace —
//      "123456 " (trailing space from keyboard) fails the check.
//      Added trim().
//   5. Error message exposes raw e.message to user. Replaced with
//      a friendly message.
// ============================================================

data class JoinRoomUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
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

        // FIX 4: Trim whitespace before validating length.
        val trimmedCode = joinCode.trim()
        if (trimmedCode.length != 6) {
            _uiState.update { it.copy(error = "Please enter a valid 6-digit code.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val groupQuery = db.collection("groups")
                    .whereEqualTo("joinCode", trimmedCode) // ✅ use trimmed code
                    .limit(1)
                    .get()
                    .await()

                if (groupQuery.isEmpty) {
                    _uiState.update { it.copy(isLoading = false, error = "Invalid room code. Please try again.") }
                    return@launch
                }

                val groupDoc = groupQuery.documents.first()
                val groupId = groupDoc.id

                // FIX 2: Check if room is locked before allowing join.
                val isLocked = groupDoc.getBoolean("isLocked") ?: false
                if (isLocked) {
                    _uiState.update { it.copy(isLoading = false, error = "This room is locked. No new members can join.") }
                    return@launch
                }

                // FIX 3: Check if user is already a member — prevent duplicate join.
                val existingMember = db.collection("groups").document(groupId)
                    .collection("groupMembers").document(currentUser.uid)
                    .get().await()
                if (existingMember.exists()) {
                    _uiState.update { it.copy(isLoading = false, error = "You are already a member of this room.") }
                    return@launch
                }

                val userRef = db.collection("users").document(currentUser.uid)
                val roomInfo = hashMapOf(
                    "groupId" to groupId,
                    "groupName" to groupDoc.getString("name")
                )

                // Update user's joined rooms list
                userRef.update("groupsJoined", FieldValue.arrayUnion(roomInfo)).await()

                // Add member document
                val newMemberData = hashMapOf(
                    "userId" to currentUser.uid,
                    "role" to "member",
                    "status" to "approved",
                    "totalPointsInGroup" to 0,
                    "joinedAt" to Timestamp.now()
                )
                db.collection("groups").document(groupId)
                    .collection("groupMembers").document(currentUser.uid)
                    .set(newMemberData)
                    .await()

                // FIX 1: Only signal success AFTER all writes are complete.
                // The original code set joinSuccessRoomId before the member doc was written.
                _uiState.update { it.copy(isLoading = false, joinSuccessRoomId = groupId) } // ✅ single, final update

            } catch (e: Exception) {
                Log.e("JoinRoomViewModel", "Join failed: ${e.message}")
                FirebaseCrashlytics.getInstance().log("Error in JoinRoomViewModel: Joining room")
                FirebaseCrashlytics.getInstance().setCustomKey("Joining room code", trimmedCode)
                FirebaseCrashlytics.getInstance().recordException(e)
                // FIX 5: Friendly error message
                _uiState.update { it.copy(isLoading = false, error = "Could not join room. Please try again.") }
            }
        }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(joinSuccessRoomId = null) }
    }
}