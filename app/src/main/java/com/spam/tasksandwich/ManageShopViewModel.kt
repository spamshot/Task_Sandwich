package com.spam.tasksandwich

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll


// Fixes:
//   1. Listener 2 (pending) and Listener 3 (history) both use
//      whereIn("status", ...) — this has a 30-item Firestore limit.
//      For a busy shop this silently truncates results. For pending
//      this uses a single whereEqualTo which is safe. History
//      uses whereIn with 2 values which is also safe. Noted.
//   2. fetchUserNamesForLogs() fires one Firestore read per log
//      entry (to get user name). For a purchase log with 50 items
//      that's 50 reads on every update. Consider storing userName
//      at write time (which checkout() already does via 'purchasedByUserName')
//      and reading it directly from the doc instead. Added fallback
//      to use stored name before fetching.
//   3. completePurchase() makes two sequential awaited writes
//      (group log + user history). If the second fails, the first
//      already committed — data inconsistency. Changed to a batch.
//   4. addShopItem() and updateShopItem() have no input validation —
//      blank names or 0-cost items would be saved. Added validation
//      with error state updates.
//   5. updateShopItem() optimistic update rollback missing — if
//      the Firestore write fails, the local cache shows the new
//      values but the DB has the old ones. Added rollback.
//   6. deleteShopItem() has no rollback on failure — same issue
//      as above. Added rollback.
//   7. clearRoomHistory() uses whereIn which silently truncates
//      at 30 items. Fixed with two separate queries (same fix as
//      ProfileSettingsViewModel.clearAllTaskHistory).
//   8. Crashlytics keys say "ManageSelfTasksViewModel" — wrong
//      class name. Fixed to "ManageShopViewModel".
//   9. ManageShopUiState has both shopItems and existingItems —
//      shopItems is never populated. Kept existingItems only,
//      marked shopItems as unused for removal later.
// ============================================================

data class ManageShopUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    // NOTE: shopItems is never populated — existingItems is used instead.
    // shopItems can be removed in a future cleanup.
    val existingItems: List<ShopItem> = emptyList(),
    val pendingPurchases: List<PurchaseLogItem> = emptyList(),
    val purchaseHistory: List<PurchaseLogItem> = emptyList(),
    val saveSuccess: Boolean = false,
    val error: String? = null,
    val roomName: String = ""
)

class ManageShopViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val roomId: String = savedStateHandle.get<String>("roomId") ?: ""

    private val _uiState = MutableStateFlow(ManageShopUiState())
    val uiState = _uiState.asStateFlow()

    private var catalogCache: List<ShopItem> = emptyList()
    private var pendingPurchasesCache: List<PurchaseLogItem> = emptyList()
    private var historyPurchasesCache: List<PurchaseLogItem> = emptyList()

    init {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Room ID missing.") }
        } else {
            loadAllData()
        }
    }

    private fun loadAllData() {
        _uiState.update { it.copy(isLoading = true) }

        var shopLoaded = false
        var pendingLoaded = false
        var historyLoaded = false
        var roomNameLoaded = false

        fun checkCompletion() {
            if (shopLoaded && pendingLoaded && historyLoaded && roomNameLoaded)
                _uiState.update { it.copy(isLoading = false) }
        }

        // 1. Shop catalog
        db.collection("groups").document(roomId).collection("shopItems")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirebaseCrashlytics.getInstance().recordException(error)
                    shopLoaded = true; checkCompletion()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    catalogCache = snapshot.documents.mapNotNull { doc ->
                        ShopItem(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            cost = doc.getLong("cost")?.toInt() ?: 0,
                            mysteryText = doc.getString("mysteryText") ?: "",
                            autoRedeem = doc.getBoolean("autoRedeem") ?: false
                        )
                    }
                    _uiState.update { it.copy(existingItems = catalogCache) }
                }
                shopLoaded = true; checkCompletion()
            }

        // 2. Pending purchases — single whereEqualTo is safe (no 30-item limit issue)
        db.collection("groups").document(roomId).collection("purchaseLog")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirebaseCrashlytics.getInstance().recordException(error)
                    pendingLoaded = true; checkCompletion()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch {
                        pendingPurchasesCache = fetchUserNamesForLogs(snapshot.documents)
                        _uiState.update { it.copy(pendingPurchases = pendingPurchasesCache) }
                        pendingLoaded = true; checkCompletion()
                    }
                } else {
                    pendingLoaded = true; checkCompletion()
                }
            }

        // 3. History — whereIn with 2 values is safe
        db.collection("groups").document(roomId).collection("purchaseLog")
            .whereIn("status", listOf("completed", "refunded"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirebaseCrashlytics.getInstance().recordException(error)
                    historyLoaded = true; checkCompletion()
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch {
                        historyPurchasesCache = fetchUserNamesForLogs(snapshot.documents)
                        _uiState.update { it.copy(purchaseHistory = historyPurchasesCache) }
                        historyLoaded = true; checkCompletion()
                    }
                } else {
                    historyLoaded = true; checkCompletion()
                }
            }

        // 4. Room name
        db.collection("groups").document(roomId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    _uiState.update { it.copy(roomName = snapshot.getString("name") ?: "Room") }
                }
                roomNameLoaded = true; checkCompletion()
            }
    }

    // FIX 2: Try to use the stored purchasedByUserName from the document first,
    // only fetching from users collection if missing. Reduces read count significantly.
    private suspend fun fetchUserNamesForLogs(
        documents: List<com.google.firebase.firestore.DocumentSnapshot>
    ): List<PurchaseLogItem> {
        val jobs = documents.map { doc ->
            viewModelScope.async {
                val userId = doc.getString("purchasedByUserId") ?: ""
                // FIX 2: Use stored name if available — avoids a user doc read per item
                val storedName = doc.getString("purchasedByUserName")
                val userName = if (!storedName.isNullOrBlank()) {
                    storedName // ✅ no extra read needed
                } else if (userId.isNotBlank()) {
                    db.collection("users").document(userId).get().await()
                        .getString("name") ?: "Unknown User"
                } else {
                    "Unknown User"
                }
                PurchaseLogItem(
                    id = doc.id,
                    itemName = doc.getString("itemName") ?: "Unknown",
                    itemCost = doc.getLong("itemCost")?.toInt() ?: 0,
                    purchasedByUserId = userId,
                    purchasedByUserName = userName,
                    status = doc.getString("status") ?: "pending",
                    mysteryText = doc.getString("mysteryText") ?: ""
                )
            }
        }
        return jobs.awaitAll()
    }

    // FIX 3: Changed from two sequential writes to a single batch for atomicity.
    fun completePurchase(purchase: PurchaseLogItem) {
        // Optimistic update
        pendingPurchasesCache = pendingPurchasesCache.filter { it.id != purchase.id }
        _uiState.update { it.copy(pendingPurchases = pendingPurchasesCache) }

        viewModelScope.launch {
            try {
                val batch = db.batch()
                // FIX 3: Both updates in one atomic batch ✅
                batch.update(
                    db.collection("groups").document(roomId)
                        .collection("purchaseLog").document(purchase.id),
                    "status", "completed"
                )
                batch.update(
                    db.collection("users").document(purchase.purchasedByUserId)
                        .collection("purchaseHistory").document(purchase.id),
                    "status", "completed"
                )
                batch.commit().await()
            } catch (e: Exception) {
                Log.e("ManageShopViewModel", "Complete purchase failed: ${e.message}")
                FirebaseCrashlytics.getInstance().log("Error in ManageShopViewModel: complete purchase")
                FirebaseCrashlytics.getInstance().setCustomKey("Complete Purchase id", purchase.id)
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(error = "Failed to approve purchase. Please try again.") }
            }
        }
    }

    fun refundPurchase(purchase: PurchaseLogItem) {
        pendingPurchasesCache = pendingPurchasesCache.filter { it.id != purchase.id }
        _uiState.update { it.copy(pendingPurchases = pendingPurchasesCache) }

        viewModelScope.launch {
            try {
                val batch = db.batch()
                val logRef = db.collection("groups").document(roomId)
                    .collection("purchaseLog").document(purchase.id)
                val memberRef = db.collection("groups").document(roomId)
                    .collection("groupMembers").document(purchase.purchasedByUserId)

                batch.update(logRef, "status", "refunded")
                batch.update(memberRef, "totalPointsInGroup", FieldValue.increment(purchase.itemCost.toLong()))
                batch.commit().await()
                Log.d("ManageShopViewModel", "Refund complete for ${purchase.id}")
            } catch (e: Exception) {
                // FIX 8: Correct class name in Crashlytics log
                FirebaseCrashlytics.getInstance().log("Error in ManageShopViewModel: Refund purchase")
                FirebaseCrashlytics.getInstance().setCustomKey("Refund purchase id", purchase.id)
                FirebaseCrashlytics.getInstance().recordException(e)
                Log.e("ManageShopViewModel", "Refund failed: ${e.message}")
                _uiState.update { it.copy(error = "Failed to refund purchase. Please try again.") }
            }
        }
    }

    // FIX 4: Added input validation — blank names or zero cost would write bad data.
    fun addShopItem(name: String, costStr: String, mysteryText: String, autoRedeem: Boolean) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(error = "Item name cannot be empty.") }
            return
        }
        val cost = costStr.toIntOrNull()
        if (cost == null || cost <= 0) {
            _uiState.update { it.copy(error = "Cost must be a positive number.") }
            return
        }

        viewModelScope.launch {
            try {
                val newItem = hashMapOf(
                    "name" to trimmedName,
                    "cost" to cost,
                    "mysteryText" to mysteryText,
                    "autoRedeem" to autoRedeem,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("groups").document(roomId).collection("shopItems").add(newItem).await()
            } catch (e: Exception) {
                Log.e("ManageShopViewModel", "Add item failed: ${e.message}")
                // FIX 8: Correct class name
                FirebaseCrashlytics.getInstance().log("Error in ManageShopViewModel: Add shop item")
                FirebaseCrashlytics.getInstance().setCustomKey("Add shop item roomId", roomId)
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(error = "Failed to add item. Please try again.") }
            }
        }
    }

    // FIX 4: Added validation. FIX 5: Added rollback on failure.
    fun updateShopItem(itemId: String, name: String, costStr: String, mysteryText: String, autoRedeem: Boolean) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(error = "Item name cannot be empty.") }
            return
        }
        val cost = costStr.toIntOrNull()
        if (cost == null || cost <= 0) {
            _uiState.update { it.copy(error = "Cost must be a positive number.") }
            return
        }

        // Snapshot for rollback
        val previousCache = catalogCache

        // Optimistic update
        catalogCache = catalogCache.map {
            if (it.id == itemId) it.copy(name = trimmedName, cost = cost, mysteryText = mysteryText, autoRedeem = autoRedeem)
            else it
        }
        _uiState.update { it.copy(existingItems = catalogCache) }

        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId).collection("shopItems").document(itemId)
                    .update("name", trimmedName, "cost", cost, "mysteryText", mysteryText, "autoRedeem", autoRedeem)
                    .await()
            } catch (e: Exception) {
                Log.e("ManageShopViewModel", "Update item failed: ${e.message}")
                FirebaseCrashlytics.getInstance().log("Error in ManageShopViewModel: Update shop item")
                FirebaseCrashlytics.getInstance().setCustomKey("Update shop item roomId", roomId)
                FirebaseCrashlytics.getInstance().recordException(e)
                // FIX 5: Rollback optimistic update ✅
                catalogCache = previousCache
                _uiState.update { it.copy(existingItems = previousCache, error = "Failed to update item. Please try again.") }
            }
        }
    }

    // FIX 6: Added rollback on failure.
    fun deleteShopItem(itemId: String) {
        if (itemId.isBlank()) return
        val previousCache = catalogCache

        catalogCache = catalogCache.filter { it.id != itemId }
        _uiState.update { it.copy(existingItems = catalogCache) }

        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId).collection("shopItems").document(itemId).delete().await()
            } catch (e: Exception) {
                Log.e("ManageShopViewModel", "Delete item failed: ${e.message}")
                FirebaseCrashlytics.getInstance().log("Error in ManageShopViewModel: Delete shop item")
                FirebaseCrashlytics.getInstance().setCustomKey("Delete shop item roomId", roomId)
                FirebaseCrashlytics.getInstance().recordException(e)
                // FIX 6: Rollback optimistic update ✅
                catalogCache = previousCache
                _uiState.update { it.copy(existingItems = previousCache, error = "Failed to delete item. Please try again.") }
            }
        }
    }

    // FIX 7: whereIn silently truncates at 30 items. Use two separate queries instead.
    fun clearRoomHistory() {
        viewModelScope.launch {
            try {
                val completedSnapshot = db.collection("groups").document(roomId)
                    .collection("purchaseLog").whereEqualTo("status", "completed").get().await()
                val refundedSnapshot = db.collection("groups").document(roomId)
                    .collection("purchaseLog").whereEqualTo("status", "refunded").get().await()

                val allDocs = completedSnapshot.documents + refundedSnapshot.documents
                if (allDocs.isEmpty()) return@launch

                // Delete in chunks of 500 to respect Firestore batch limit
                allDocs.chunked(500).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
                Log.d("ManageShopViewModel", "Cleared ${allDocs.size} history records.")
            } catch (e: Exception) {
                Log.e("ManageShopViewModel", "Clear history failed: ${e.message}")
                FirebaseCrashlytics.getInstance().log("Error in ManageShopViewModel: Clear room history")
                FirebaseCrashlytics.getInstance().setCustomKey("Clear room history roomId", roomId)
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(error = "Failed to clear history. Please try again.") }
            }
        }
    }
}