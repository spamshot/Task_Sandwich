package com.spam.tasksandwich

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

data class UserPurchaseLogItem( // A new data class for this screen
    val id: String = "",
    val itemName: String = "",
    val itemCost: Int = 0,
    val roomName: String = "A Room", // We need to know which room it was from
    val status: String = "",
    val mysteryText: String = "",
    val purchasedAt: Timestamp? = null
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
        loadUserProfile()
        listenForPurchaseHistory()
        fetchAssignedTasks()
    }

    private fun loadUserProfile() {
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false, error = "User not logged in.") }
            return
        }
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                val profile = document.toObject(UserProfile::class.java)?.copy(uid = document.id)
                _uiState.update { it.copy(isLoading = false, userProfile = profile) }
            }
            .addOnFailureListener { e ->
                _uiState.update { it.copy(isLoading = false, error = "Failed to load profile: ${e.message}") }
            }
    }


    private fun listenForPurchaseHistory() {
        if (currentUser == null) return

        // This is a Collection Group Query. It queries all collections named "purchaseLog".
        db.collectionGroup("purchaseLog")
            .whereEqualTo("purchasedByUserId", currentUser.uid)
            .orderBy("purchasedAt", Query.Direction.DESCENDING)
            .limit(50) // Limit to the last 50 purchases for performance
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    // This is a complex query. For each purchase, we need to find its parent group to get the room name.
                    viewModelScope.launch {
                        val historyJobs = snapshot.documents.map { doc ->
                            async {
                                // Get the parent group document reference
                                val groupRef = doc.reference.parent.parent
                                var roomName = "Unknown Room"
                                if (groupRef != null) {
                                    val groupDoc = groupRef.get().await()
                                    if (groupDoc.exists()) {
                                        roomName = groupDoc.getString("name") ?: roomName
                                    }
                                }

                                UserPurchaseLogItem(
                                    id = doc.id,
                                    itemName = doc.getString("itemName") ?: "",
                                    itemCost = doc.getLong("itemCost")?.toInt() ?: 0,
                                    roomName = roomName,
                                    status = doc.getString("status") ?: "",
                                    purchasedAt = doc.getTimestamp("purchasedAt"),
                                    mysteryText = doc.getString("mysteryText") ?: ""
                                )
                            }
                        }
                        val historyList = historyJobs.awaitAll()
                        _uiState.update { it.copy(purchaseHistory = historyList) }
                    }
                }
            }
    }

    fun saveProfile(name: String, age: String, email: String, iconId: String) {
        if (currentUser == null) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                // Update email in Firebase Authentication (if changed)
                if (email != currentUser.email) {
                    currentUser.updateEmail(email).await()
                }

                // Update data in Firestore
                val updatedData = mapOf(
                    "name" to name,
                    "age" to age.toIntOrNull(),
                    "email" to email,
                    "selectedIconId" to iconId
                )
                db.collection("users").document(currentUser.uid).update(updatedData).await()
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                // This can fail if email is already in use or requires recent login
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun onSaveHandled() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    private fun fetchAssignedTasks() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _uiState.update { it.copy(isLoading = false, error = "User not logged in.") }
            return
        }

        // Query for all tasks created by the current user, order by most recent
        db.collection("tasks")
            .whereEqualTo("assignedToUserId", currentUser.uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load tasks.") }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val taskList = snapshot.toObjects(Task::class.java).mapIndexed { index, task ->
                        task.copy(id = snapshot.documents[index].id) // Manually add the document ID
                    }
                    _uiState.update { it.copy(isLoading = false, tasks = taskList) }
                }
            }
    }

    fun deleteTask(taskId: String) {
        // 1. Optimistic UI Update: Immediately remove the task from the local state.
        _uiState.update { currentState ->
            currentState.copy(
                tasks = currentState.tasks.filterNot { it.id == taskId }
            )
        }

        // 2. Perform the backend operation.
        viewModelScope.launch {
            try {
                db.collection("tasks").document(taskId).delete().await()
                // If this succeeds, the real-time listener will eventually get the
                // same state we already set, so the UI won't change again.
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete task: ${e.message}") }
                // OPTIONAL: In a more complex app, you could add logic here
                // to add the task back to the list if the deletion fails,
                // and show a "Couldn't delete task" snackbar.
                // For now, just showing the error is sufficient.
            }
        }
    }

    fun logout() {
        auth.signOut()
        _uiState.update { it.copy(logoutSuccess = true) }
    }
}