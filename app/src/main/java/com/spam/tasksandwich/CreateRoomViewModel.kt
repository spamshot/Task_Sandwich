package com.spam.tasksandwich

import android.util.Log
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
//todo we don't need this file anymore
//data class CreateRoomUiState(
//    val isLoading: Boolean = false,
//    val createdRoomId: String? = null,
//    val error: String? = null
//)
//
//class CreateRoomViewModel : ViewModel() {
//    private val db = Firebase.firestore
//    private val auth = Firebase.auth
//
//    private val _uiState = MutableStateFlow(CreateRoomUiState())
//    val uiState = _uiState.asStateFlow()
//
//    fun createRoom(roomName: String) {
//        val currentUser = auth.currentUser
//
//        // --- Diagnostic Logging ---
//        Log.d("CreateRoom", "Attempting to create room. UserID: ${currentUser?.uid}")
//        if (currentUser == null) {
//            Log.e("CreateRoom", "Error: currentUser is null. Cannot proceed.")
//            _uiState.update { it.copy(error = "You must be logged in to create a room.") }
//            return
//        }
//
//        _uiState.update { it.copy(isLoading = true, error = null) }
//        viewModelScope.launch {
//            try {
//                val newRoomRef = db.collection("groups").document()
//                val joinCode = (100000..999999).random().toString()
//
//                val newRoom = hashMapOf(
//                    "name" to roomName,
//                    "adminUserId" to currentUser.uid,
//                    "joinCode" to joinCode,
//                    "autoAcceptMembers" to true,
//                    "createdAt" to Timestamp.now(),
//                    "memberIds" to listOf(currentUser.uid)
//                )
//
//                val adminMember = hashMapOf(
//                    "userId" to currentUser.uid,
//                    "role" to "admin",
//                    "status" to "approved",
//                    "totalPointsInGroup" to 0,
//                    "joinedAt" to Timestamp.now()
//                )
//
//                val userRef = db.collection("users").document(currentUser.uid)
//                val roomInfo = hashMapOf(
//                    "groupId" to newRoomRef.id,
//                    "groupName" to roomName
//                )
//
//                // This batch write requires three separate permissions to succeed.
//                db.runBatch { batch ->
//                    // 1. Requires 'create' permission on '/groups/{groupId}'
//                    batch.set(newRoomRef, newRoom)
//                    // 2. Requires 'create' permission on '/groups/{groupId}/groupMembers/{userId}'
//                    batch.set(newRoomRef.collection("groupMembers").document(currentUser.uid), adminMember)
//                    // 3. Requires 'update' permission on '/users/{userId}'
//                    batch.update(userRef, "groupsJoined", FieldValue.arrayUnion(roomInfo))
//                }.await()
//
//                _uiState.update { it.copy(isLoading = false, createdRoomId = newRoomRef.id) }
//
//            } catch (e: Exception) {
//                // --- Diagnostic Logging for Failure ---
//                Log.e("CreateRoom", "Firestore transaction failed with exception.", e)
//                // ------------------------------------
//                _uiState.update { it.copy(isLoading = false, error = "Failed to create room: ${e.message}") }
//            }
//        }
//    }
//
//    fun onNavigationHandled() {
//        _uiState.update { it.copy(createdRoomId = null) }
//    }
//}