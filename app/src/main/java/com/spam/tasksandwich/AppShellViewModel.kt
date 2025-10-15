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
    val newlyCreatedRoomId: String? = null
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