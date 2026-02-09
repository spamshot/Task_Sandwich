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


data class ManageShopUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val shopItems: List<ShopItem> = emptyList(), // We will use 'shopItems'
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
    private val roomId: String = savedStateHandle["roomId"] ?: ""

    private val _uiState = MutableStateFlow(ManageShopUiState())
    val uiState = _uiState.asStateFlow()

    private var catalogCache: List<ShopItem> = emptyList()
    private var pendingPurchasesCache: List<PurchaseLogItem> = emptyList()
    private var historyPurchasesCache: List<PurchaseLogItem> = emptyList()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Room ID missing") }
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        var shopLoaded = false
        var pendingLoaded = false
        var historyLoaded = false

        fun checkCompletion() {
            if (shopLoaded && pendingLoaded && historyLoaded) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // 1. SHOP CATALOG LISTENER (existingItems)
        db.collection("groups").document(roomId).collection("shopItems")
            .addSnapshotListener { snapshot, error ->
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
                    // FIXED: Now mapping to 'existingItems'
                    _uiState.update { it.copy(existingItems = catalogCache) }
                }
                shopLoaded = true
                checkCompletion()
            }

        // 2. PENDING PURCHASES LISTENER
        db.collection("groups").document(roomId).collection("purchaseLog")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    viewModelScope.launch {
                        pendingPurchasesCache = fetchUserNamesForLogs(snapshot.documents)
                        _uiState.update { it.copy(pendingPurchases = pendingPurchasesCache) }
                        pendingLoaded = true
                        checkCompletion()
                    }
                } else {
                    pendingLoaded = true; checkCompletion()
                }
            }

        // 3. HISTORY PURCHASES LISTENER
        db.collection("groups").document(roomId).collection("purchaseLog")
            .whereIn("status", listOf("completed", "refunded"))
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    viewModelScope.launch {
                        historyPurchasesCache = fetchUserNamesForLogs(snapshot.documents)
                        _uiState.update { it.copy(purchaseHistory = historyPurchasesCache) }
                        historyLoaded = true
                        checkCompletion()
                    }
                } else {
                    historyLoaded = true; checkCompletion()
                }
            }

        //Listen for room name
        db.collection("groups").document(roomId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val name = snapshot.getString("name") ?: "Room"
                    _uiState.update { it.copy(roomName = name) }
                }
            }
    }

    private suspend fun fetchUserNamesForLogs(documents: List<com.google.firebase.firestore.DocumentSnapshot>): List<PurchaseLogItem> {
        val jobs = documents.map { doc ->
            viewModelScope.async {
                val userId = doc.getString("purchasedByUserId") ?: ""
                val userDoc = db.collection("users").document(userId).get().await()
                val userName = userDoc.getString("name") ?: "Unknown User"

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

    // --- ACTIONS ---

    fun completePurchase(purchase: PurchaseLogItem) {
        pendingPurchasesCache = pendingPurchasesCache.filter { it.id != purchase.id }
        _uiState.update { it.copy(pendingPurchases = pendingPurchasesCache) }

        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId).collection("purchaseLog").document(purchase.id)
                    .update("status", "completed").await()
                db.collection("users").document(purchase.purchasedByUserId)
                    .collection("purchaseHistory").document(purchase.id)
                    .update("status", "completed").await()
            } catch (e: Exception) { Log.e("SHOP_VM", "Complete failed")

                FirebaseCrashlytics.getInstance().log("Error in ManageShopViewModel: complete purchase")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Complete Purchase", purchase.id)

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun refundPurchase(purchase: PurchaseLogItem) {
        pendingPurchasesCache = pendingPurchasesCache.filter { it.id != purchase.id }
        _uiState.update { it.copy(pendingPurchases = pendingPurchasesCache) }

        viewModelScope.launch {
            try {
                val batch = db.batch()
                val logRef = db.collection("groups").document(roomId).collection("purchaseLog").document(purchase.id)
                val memberRef = db.collection("groups").document(roomId).collection("groupMembers").document(purchase.purchasedByUserId)

                // 1. Update Room Log Status
                batch.update(logRef, "status", "refunded")

                // 2. Update Room-Specific Points (Admin has permission for this)
                batch.update(memberRef, "totalPointsInGroup", FieldValue.increment(purchase.itemCost.toLong()))

                batch.commit().await()
                Log.d("SHOP_VM", "Refund initiated in Room Log")
            } catch (e: Exception) {

                FirebaseCrashlytics.getInstance().log("Error in ManageSelfTasksViewModel: Refund item ")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Refund purchase", purchase.id)

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)

                Log.e("SHOP_VM", "Refund failed: ${e.message}")
            }
        }
    }

    fun addShopItem(name: String, costStr: String, mysteryText: String, autoRedeem: Boolean) {
        val cost = costStr.toIntOrNull() ?: return
        viewModelScope.launch {
            try {
                val newItem = hashMapOf(
                    "name" to name,
                    "cost" to cost,
                    "mysteryText" to mysteryText,
                    "autoRedeem" to autoRedeem,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("groups").document(roomId).collection("shopItems").add(newItem).await()
            } catch (e: Exception) { Log.e("SHOP_VM", "Add failed")
                FirebaseCrashlytics.getInstance().log("Error in ManageSelfTasksViewModel: Add shop item ")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Add shop item", roomId)

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun updateShopItem(itemId: String, name: String, costStr: String, mysteryText: String, autoRedeem: Boolean) {
        val cost = costStr.toIntOrNull() ?: return

        // Optimistic UI Update using 'existingItems'
        catalogCache = catalogCache.map { if (it.id == itemId) it.copy(name = name, cost = cost, mysteryText = mysteryText, autoRedeem = autoRedeem) else it }
        _uiState.update { it.copy(existingItems = catalogCache) }

        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId).collection("shopItems").document(itemId)
                    .update("name", name, "cost", cost, "mysteryText", mysteryText, "autoRedeem", autoRedeem).await()
            } catch (e: Exception) { Log.e("SHOP_VM", "Update failed")

                FirebaseCrashlytics.getInstance().log("Error in ManageSelfTasksViewModel: Update shop item")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Update shop item", roomId)

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun deleteShopItem(itemId: String) {
        // Optimistic UI Update using 'existingItems'
        catalogCache = catalogCache.filter { it.id != itemId }
        _uiState.update { it.copy(existingItems = catalogCache) }

        viewModelScope.launch {
            try {
                db.collection("groups").document(roomId).collection("shopItems").document(itemId).delete().await()
            } catch (e: Exception) { Log.e("SHOP_VM", "Delete failed")
                FirebaseCrashlytics.getInstance().log("Error in ManageSelfTasksViewModel: Delete shop item ")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Delete shop item", roomId)

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }

    fun clearRoomHistory() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("groups").document(roomId).collection("purchaseLog")
                    .whereIn("status", listOf("completed", "refunded")).get().await()
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            } catch (e: Exception) { Log.e("SHOP_VM", "Clear failed")
                FirebaseCrashlytics.getInstance().log("Error in ManageSelfTasksViewModel: Clear room history ")

                // 2. Add custom context (e.g., which Room ID)
                FirebaseCrashlytics.getInstance().setCustomKey("Clear room history", roomId)

                // 3. Record the actual error (This sends the report to Firebase)
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }
}