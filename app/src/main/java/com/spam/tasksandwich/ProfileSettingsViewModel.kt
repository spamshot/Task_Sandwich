package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Using the UserProfile data class from HomeData.kt

/**
 * Data model for an individual purchase log entry.
 * Note: 'roomId' is mapped from 'groupId' in the flattened Firestore document.
 */
data class UserPurchaseLogItem(
    val id: String = "",
    val itemName: String = "",
    val itemCost: Int = 0,
    val roomName: String = "A Room",
    val status: String = "",
    val mysteryText: String = "",
    val purchasedAt: Timestamp? = null,
    val roomId: String = "",
)

/**
 * State representation for the Profile Settings Screen.
 */
data class ProfileSettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val userProfile: UserProfile? = null,
    val saveSuccess: Boolean = false,
    val logoutSuccess: Boolean = false,
    val error: String? = null,
    val purchaseHistory: List<UserPurchaseLogItem> = emptyList(),
    val tasks: List<Task> = emptyList()
)

class ProfileSettingsViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val currentUser = auth.currentUser

    private val _uiState = MutableStateFlow(ProfileSettingsUiState())
    val uiState = _uiState.asStateFlow()

    private var tasksCache: List<Task> = emptyList()
    private var historyCache: List<UserPurchaseLogItem> = emptyList()

    init {
        loadAllData()
    }

    /**
     * Loads Profile, History, and Tasks using real-time Snapshot Listeners.
     */
    private fun loadAllData() {
        _uiState.update { it.copy(isLoading = true) }
        val uid = currentUser?.uid ?: return

        var userLoaded = false
        var historyLoaded = false
        var tasksLoaded = false

        fun checkCompletion() {
            if (userLoaded && historyLoaded && tasksLoaded) _uiState.update { it.copy(isLoading = false) }
        }

        // 1. REAL-TIME USER PROFILE
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)?.copy(uid = snapshot.id)
                _uiState.update { it.copy(userProfile = profile) }
            }
            userLoaded = true; checkCompletion()
        }

        // 2. Purchase History (Flattened)
        db.collection("users").document(uid).collection("purchaseHistory")
            .orderBy("purchasedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    historyCache = snapshot.documents.mapNotNull { doc ->
                        UserPurchaseLogItem(
                            id = doc.id,
                            itemName = doc.getString("itemName") ?: "",
                            itemCost = doc.getLong("itemCost")?.toInt() ?: 0,
                            roomName = doc.getString("roomName") ?: "Unnamed Room",
                            status = doc.getString("status") ?: "",
                            purchasedAt = doc.getTimestamp("purchasedAt"),
                            mysteryText = doc.getString("mysteryText") ?: "",
                            roomId = doc.getString("groupId") ?: ""
                        )
                    }
                    updateHistoryUi()
                }
                historyLoaded = true; checkCompletion()
            }

        // 3. Assigned Tasks
        db.collection("tasks").whereEqualTo("assignedToUserId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    tasksCache = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }
                    updateTasksUi()
                }
                tasksLoaded = true; checkCompletion()
            }
    }

    private fun updateTasksUi() {
        _uiState.update { it.copy(tasks = tasksCache) }
    }

    private fun updateHistoryUi() {
        _uiState.update { it.copy(purchaseHistory = historyCache) }
    }

    /**
     * DELETE TASK: Uses OPTIMISTIC UPDATE
     * Removes the task from the local list immediately so the UI feels instant.
     */
    fun deleteTask(taskId: String) {
        if (taskId.isBlank()) return

        // 1. CLEAR CACHE & UI IMMEDIATELY
        // This is the most important part. We remove it from our local
        // lists so the "Ghost" disappears from the screen instantly.
        tasksCache = tasksCache.filter { it.id != taskId }
        updateTasksUi()

        viewModelScope.launch {
            try {
                // 2. TELL FIREBASE TO WIPE PERSISTENCE FOR THIS ID
                // This forces the phone to forget the "Ghost" document.
                db.collection("tasks").document(taskId).delete().await()

                Log.d("DELETE_DEBUG", "Task $taskId wiped from server and cache")
            } catch (e: Exception) {
                // If it was already deleted (the ghost scenario), this catch
                // prevents the app from crashing or "putting the item back."
                Log.e("DELETE_DEBUG", "Server ignored delete (likely already gone): ${e.message}")
            }
        }
    }

    /**
     * DELETE TRANSACTION: Uses OPTIMISTIC UPDATE
     * Wipes both the flattened copy and the original group log.
     */

    fun deleteTransaction(purchaseId: String, groupId: String) {
        val uid = currentUser?.uid ?: return
        if (purchaseId.isBlank()) return

        // 1. Wipe from Cache
        historyCache = historyCache.filter { it.id != purchaseId }
        // 2. Wipe from UI
        updateHistoryUi()

        viewModelScope.launch {
            try {
                db.collection("users").document(uid).collection("purchaseHistory").document(purchaseId).delete().await()
                if (groupId.isNotBlank()) {
                    db.collection("groups").document(groupId).collection("purchaseLog").document(purchaseId).delete().await()
                }
            } catch (e: Exception) {
                Log.e("DELETE", "Error: ${e.message}")
            }
        }
    }

    /**
     * DELETE ACCOUNT: Wipes profile and Auth account.
     * Cloud Function 'cleanupUserDataOnDelete' handles remaining room/task cleanup.
     */
    fun deleteAccount() {
        val user = auth.currentUser ?: return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                // 1. Wipe the Firestore user document
                db.collection("users").document(user.uid).delete().await()

                // 2. Wipe the Auth account (triggers background server cleanup)
                user.delete().await()

                // 3. MANDATORY LOGOUT
                // Explicitly sign out to clear local caches and listeners
                auth.signOut()
                _uiState.update { it.copy(logoutSuccess = true) }

                // 4. Navigate away
                _uiState.update { it.copy(isSaving = false, logoutSuccess = true) }
            } catch (e: Exception) {
                val errorMessage = if (e.message?.contains("recent-login") == true) {
                    "Security check failed. Please log out and log back in before deleting."
                } else {
                    e.message ?: "Account deletion failed."
                }
                _uiState.update { it.copy(isSaving = false, error = errorMessage) }
            }
        }
    }

    /**
     * SAVE PROFILE: Standard data update for Name, Age, Email, and Icon
     */
    fun saveProfile(name: String, age: String, email: String, iconId: String) {
        val uid = currentUser?.uid ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (email != currentUser.email) currentUser.updateEmail(email).await()

                val updatedData = mapOf(
                    "name" to name,
                    "age" to age.toIntOrNull(),
                    "email" to email,
                    "selectedIconId" to iconId
                )
                db.collection("users").document(uid).update(updatedData).await()
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun onSaveHandled() { _uiState.update { it.copy(saveSuccess = false) } }

    fun logout() {
        auth.signOut()
        _uiState.update { it.copy(logoutSuccess = true) }
    }
}