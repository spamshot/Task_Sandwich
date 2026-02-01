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

data class UserPurchaseLogItem(
    val id: String = "",
    val itemName: String = "",
    val itemCost: Int = 0,
    val roomName: String = "A Room",
    val status: String = "",
    val mysteryText: String = "",
    val purchasedAt: Timestamp? = null,
    val roomId: String = "", // Used for deletion path
)

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

        var userLoaded = false
        var historyLoaded = false
        var tasksLoaded = false

        fun checkCompletion() {
            if (userLoaded && historyLoaded && tasksLoaded) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // 1. User Profile Listener
        db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toObject(UserProfile::class.java)?.copy(uid = snapshot.id)
                    _uiState.update { it.copy(userProfile = profile) }
                }
                userLoaded = true
                checkCompletion()
            }

        // 2. Purchase History Listener (Collection Group)
        db.collectionGroup("purchaseLog")
            .whereEqualTo("purchasedByUserId", currentUser.uid)
            .orderBy("purchasedAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    viewModelScope.launch {
                        val historyJobs = snapshot.documents.map { doc ->
                            async {
                                val groupRef = doc.reference.parent.parent
                                var roomName = "Unknown Room"
                                val currentRoomId = groupRef?.id ?: ""

                                if (groupRef != null) {
                                    try {
                                        val groupDoc = groupRef.get().await()
                                        roomName = groupDoc.getString("name") ?: "Unnamed Room"
                                    } catch (e: Exception) { /* use default */ }
                                }

                                UserPurchaseLogItem(
                                    id = doc.id,
                                    itemName = doc.getString("itemName") ?: "",
                                    itemCost = doc.getLong("itemCost")?.toInt() ?: 0,
                                    roomName = roomName,
                                    status = doc.getString("status") ?: "",
                                    purchasedAt = doc.getTimestamp("purchasedAt"),
                                    mysteryText = doc.getString("mysteryText") ?: "",
                                    roomId = currentRoomId
                                )
                            }
                        }
                        val historyList = historyJobs.awaitAll()
                        _uiState.update { it.copy(purchaseHistory = historyList) }
                        historyLoaded = true
                        checkCompletion()
                    }
                } else {
                    historyLoaded = true
                    checkCompletion()
                }
            }

        // 3. Assigned Tasks Listener
        db.collection("tasks")
            .whereEqualTo("assignedToUserId", currentUser.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    val taskList = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Task::class.java)?.copy(id = doc.id)
                    }
                    _uiState.update { it.copy(tasks = taskList) }
                }
                tasksLoaded = true
                checkCompletion()
            }
    }

    fun deleteAccount() {
        val user = auth.currentUser ?: return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                // 1. Delete their data from Firestore first
                db.collection("users").document(user.uid).delete().await()

                // 2. Delete the actual Authentication account
                user.delete().await()

                // 3. SUCCESS: Reuse the logoutSuccess flag to trigger navigation
                _uiState.update { it.copy(isSaving = false, logoutSuccess = true) }

            } catch (e: Exception) {
                // Check for the specific "Requires Recent Login" error from Firebase
                val errorMessage = if (e.message?.contains("recent-login") == true) {
                    "Security: Please log out and log back in to verify your identity before deleting your account."
                } else {
                    e.message ?: "Account deletion failed."
                }

                _uiState.update { it.copy(isSaving = false, error = errorMessage) }
            }
        }
    }

    /**
     * DELETE TASK: Uses OPTIMISTIC UPDATE pattern from ManageTasksViewModel
     */
    fun deleteTask(taskId: String) {
        if (taskId.isBlank()) return

        // 1. OPTIMISTIC UPDATE: Remove from UI immediately
        _uiState.update { currentState ->
            currentState.copy(
                tasks = currentState.tasks.filter { it.id != taskId }
            )
        }

        // 2. BACKEND REQUEST: Background deletion
        viewModelScope.launch {
            try {
                db.collection("tasks").document(taskId).delete().await()
                Log.d("DELETE", "Task successfully deleted from Firestore")
            } catch (e: Exception) {
                // If it fails, you can optionally reload or show error
                _uiState.update { it.copy(error = "Delete failed: ${e.message}") }
            }
        }
    }

    /**
     * DELETE TRANSACTION: Uses OPTIMISTIC UPDATE pattern from ManageTasksViewModel
     */
    fun deleteTransaction(purchaseId: String, groupId: String) {
        if (purchaseId.isBlank() || groupId.isBlank()) return

        // 1. OPTIMISTIC UPDATE: Remove from UI immediately
        _uiState.update { currentState ->
            currentState.copy(
                purchaseHistory = currentState.purchaseHistory.filter { it.id != purchaseId }
            )
        }

        // 2. BACKEND REQUEST: Background deletion
        viewModelScope.launch {
            try {
                db.collection("groups").document(groupId)
                    .collection("purchaseLog").document(purchaseId)
                    .delete().await()
                Log.d("DELETE", "Transaction successfully deleted from Firestore")
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Delete failed: ${e.message}") }
            }
        }
    }

    fun saveProfile(name: String, age: String, email: String, iconId: String) {
        if (currentUser == null) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                if (email != currentUser.email) currentUser.updateEmail(email).await()
                val updatedData = mapOf("name" to name, "age" to age.toIntOrNull(), "email" to email, "selectedIconId" to iconId)
                db.collection("users").document(currentUser.uid).update(updatedData).await()
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun onSaveHandled() { _uiState.update { it.copy(saveSuccess = false) } }
    fun logout() { auth.signOut(); _uiState.update { it.copy(logoutSuccess = true) } }
}