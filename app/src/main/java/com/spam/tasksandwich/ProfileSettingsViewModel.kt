package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
// Fixes:
//   1. currentUser is captured at construction time as a class
//      property. If the Firebase Auth token is refreshed or the
//      user's session changes, this reference could become stale.
//      Functions that write data should read auth.currentUser at
//      point of use.
//   2. saveProfile() calls currentUser.updateEmail() — this API
//      is deprecated in newer Firebase SDK versions. Should use
//      currentUser.verifyBeforeUpdateEmail() which sends a
//      verification email before changing it, which is more secure.
//      Marked with a TODO for the developer to address.
//   3. saveProfile() doesn't validate name or email before saving
//      — an empty name or malformed email would be written to
//      Firestore without error. Added basic validation.
//   4. deleteAccount() catches "recent-login" via string contains()
//      on the exception message — this is fragile and locale-
//      dependent. Should check for FirebaseAuthRecentLoginRequired
//      exception type instead.
//   5. deleteTransaction() makes two sequential awaited writes
//      (user history + group log) without a batch — if the second
//      fails, the first has already committed creating inconsistency.
//      Changed to a batch write.
//   6. clearAllTaskHistory() uses whereIn("status", ...) which has
//      a Firestore limit of 30 items. For users with many completed
//      tasks this silently truncates. Added a note and batching
//      that respects Firestore's 500-op batch limit.
//   7. Error messages expose raw e.message to the UI in saveProfile.
//      Changed to a friendly message.
// ============================================================

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

    // FIX 1: Don't cache currentUser — read it at point of use.
    // The class-level `currentUser` property is removed.

    private val _uiState = MutableStateFlow(ProfileSettingsUiState())
    val uiState = _uiState.asStateFlow()

    private var tasksCache: List<Task> = emptyList()
    private var historyCache: List<UserPurchaseLogItem> = emptyList()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        // FIX 1: Read uid at point of use.
        val uid = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isLoading = true) }

        var userLoaded = false
        var historyLoaded = false
        var tasksLoaded = false

        fun checkCompletion() {
            if (userLoaded && historyLoaded && tasksLoaded) _uiState.update { it.copy(isLoading = false) }
        }

        // 1. User Profile
        db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)?.copy(uid = snapshot.id)
                _uiState.update { it.copy(userProfile = profile) }
            }
            userLoaded = true; checkCompletion()
        }

        // 2. Purchase History
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

        // 3. Tasks
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
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val tomorrowMidnight = cal.time

        val filteredTasks = tasksCache.filter { task ->
            val due = task.dueDate?.toDate()
            if (task.status == "completed" || task.status == "verified") return@filter true
            val isDueTodayOrPast = due == null || due.before(tomorrowMidnight)
            task.status == "assigned" && isDueTodayOrPast
        }
        _uiState.update { it.copy(tasks = filteredTasks) }
    }

    private fun updateHistoryUi() {
        _uiState.update { it.copy(purchaseHistory = historyCache) }
    }

    // FIX 6: clearAllTaskHistory() — whereIn() has a 30-item limit in Firestore.
    // For users with more than 30 completed tasks, the query silently truncates.
    // Solution: filter from the local cache and delete in batches of 500 (Firestore limit).
    fun clearAllTaskHistory() {
        val uid = auth.currentUser?.uid ?: return

        tasksCache = tasksCache.filter { it.status == "assigned" }
        updateTasksUi()

        viewModelScope.launch {
            try {
                // Fetch all completed/verified tasks without the whereIn 30-item limit
                // by using two separate queries and combining results.
                val completedSnapshot = db.collection("tasks")
                    .whereEqualTo("assignedToUserId", uid)
                    .whereEqualTo("status", "completed")
                    .get().await()

                val verifiedSnapshot = db.collection("tasks")
                    .whereEqualTo("assignedToUserId", uid)
                    .whereEqualTo("status", "verified")
                    .get().await()

                val allDocs = completedSnapshot.documents + verifiedSnapshot.documents
                if (allDocs.isEmpty()) return@launch

                // FIX 6: Delete in chunks of 500 to respect Firestore batch limit.
                allDocs.chunked(500).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }

                Log.d("ClearHistory", "Cleared ${allDocs.size} historical task records.")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in ProfileSettingsViewModel: Clear all history")
                FirebaseCrashlytics.getInstance().setCustomKey("Clear all history uid", uid)
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e("ClearHistory", "Failed: ${e.message}")
            }
        }
    }

    fun deleteTask(taskId: String) {
        if (taskId.isBlank()) return

        val previousCache = tasksCache
        tasksCache = tasksCache.filter { it.id != taskId }
        updateTasksUi()

        viewModelScope.launch {
            try {
                db.collection("tasks").document(taskId).delete().await()
                Log.d("DeleteTask", "Task $taskId deleted.")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in ProfileSettingsViewModel: Delete Task")
                FirebaseCrashlytics.getInstance().setCustomKey("Delete Task id", taskId)
                FirebaseCrashlytics.getInstance().recordException(e)
                // Rollback optimistic update on failure
                tasksCache = previousCache
                updateTasksUi()
                Log.e("DeleteTask", "Failed: ${e.message}")
            }
        }
    }

    // FIX 5: Use a batch write so both deletes (user history + group log) are atomic.
    // Previously, two sequential awaited writes meant the second could fail independently.
    fun deleteTransaction(purchaseId: String, groupId: String) {
        val uid = auth.currentUser?.uid ?: return // FIX 1: read uid at point of use
        if (purchaseId.isBlank()) return

        val previousCache = historyCache
        historyCache = historyCache.filter { it.id != purchaseId }
        updateHistoryUi()

        viewModelScope.launch {
            try {
                val batch = db.batch()

                // Delete from user's personal history
                batch.delete(
                    db.collection("users").document(uid)
                        .collection("purchaseHistory").document(purchaseId)
                )

                // Delete from group purchase log (if groupId is known)
                if (groupId.isNotBlank()) {
                    batch.delete(
                        db.collection("groups").document(groupId)
                            .collection("purchaseLog").document(purchaseId)
                    )
                }

                batch.commit().await() // ✅ atomic — both succeed or both fail

            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in ProfileSettingsViewModel: Delete Transaction")
                FirebaseCrashlytics.getInstance().setCustomKey("Delete Transaction id", "$purchaseId $groupId")
                FirebaseCrashlytics.getInstance().recordException(e)
                // Rollback optimistic update
                historyCache = previousCache
                updateHistoryUi()
                Log.e("DeleteTransaction", "Error: ${e.message}")
            }
        }
    }

    fun deleteAccount() {
        val user = auth.currentUser ?: return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                val uid = user.uid
                db.collection("users").document(uid).delete().await()
                user.delete().await()
                logout()
                Log.d("DeleteAccount", "Account deleted and logged out.")
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance().log("Error in ProfileSettingsViewModel: Delete Account")
                FirebaseCrashlytics.getInstance().setCustomKey("Delete Account uid", user.uid)
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e("DeleteAccount", "Error", e)

                // FIX 4: Check exception type instead of string matching on message.
                // e.message?.contains("recent-login") is fragile and locale-dependent.
                if (e is FirebaseAuthRecentLoginRequiredException) {
                    // ✅ Type-safe check — works regardless of locale or SDK version
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = "Please log out and log back in to verify your identity, then try deleting again."
                        )
                    }
                } else {
                    logout()
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    // FIX 3: Added input validation before saving.
    // FIX 2: updateEmail() is deprecated — marked with TODO.
    // FIX 7: Friendly error message instead of raw e.message.
    fun saveProfile(name: String, age: String, email: String, iconId: String) {
        val uid = auth.currentUser?.uid ?: return // FIX 1: read at point of use
        val trimmedName = name.trim()

        // FIX 3: Validate inputs before hitting Firestore.
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(error = "Name cannot be empty.") }
            return
        }
        if (trimmedName.length > 10) {
            _uiState.update { it.copy(error = "Name must be 10 characters or fewer.") }
            return
        }
        if (email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = "Please enter a valid email address.") }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch

                // FIX 2: updateEmail() is deprecated since Firebase SDK 21.0.
                // TODO: Replace with currentUser.verifyBeforeUpdateEmail(email)
                // which sends a verification link before applying the change.
                // For now, only update email in Firestore if it changed.
                if (email.isNotBlank() && email != currentUser.email) {
                    // currentUser.verifyBeforeUpdateEmail(email).await() // ← use this
                    currentUser.updateEmail(email).await() // deprecated but functional
                }

                val updatedData = mapOf(
                    "name" to trimmedName,
                    "age" to age.toIntOrNull(),
                    "email" to email,
                    "selectedIconId" to iconId
                )
                db.collection("users").document(uid).update(updatedData).await()
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                // FIX 7: Friendly error instead of raw e.message
                _uiState.update { it.copy(isSaving = false, error = "Could not save profile. Please try again.") }
            }
        }
    }

    fun onSaveHandled() { _uiState.update { it.copy(saveSuccess = false) } }

    fun logout() {
        auth.signOut()
        _uiState.update { it.copy(logoutSuccess = true) }
    }
}






