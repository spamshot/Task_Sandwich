package com.spam.tasksandwich

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


data class ManageShopUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val existingItems: List<ShopItem> = emptyList(),
    val pendingPurchases: List<PurchaseLogItem> = emptyList(),
    val purchaseHistory: List<PurchaseLogItem> = emptyList(),
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class ManageShopViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val roomId: String = savedStateHandle.get("roomId")!!

    private val _uiState = MutableStateFlow(ManageShopUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenForShopItems()
        listenForPendingPurchases()
        listenForPurchaseHistory()
    }


    private fun listenForShopItems() {
        db.collection("groups").document(roomId).collection("shopItems")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ShopItem::class.java)?.copy(id = doc.id)
                    }
                    _uiState.update { it.copy(isLoading = false, existingItems = items) }
                }
            }
    }

    private fun listenForPendingPurchases() {
        db.collection("groups").document(roomId).collection("purchaseLog")
            .whereEqualTo("status", "pending")
            .orderBy("purchasedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val purchases = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(PurchaseLogItem::class.java)?.copy(id = doc.id)
                    }
                    _uiState.update { it.copy(pendingPurchases = purchases) }
                }
            }
    }


    private fun listenForPurchaseHistory() {
        db.collection("groups").document(roomId).collection("purchaseLog")
            // Query for items that are NOT pending
            .whereIn("status", listOf("completed", "refunded"))
            // Show the most recently handled items first
            .orderBy("handledAt", Query.Direction.DESCENDING)
            .limit(20) // Limit to the last 20 handled items for performance
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val history = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(PurchaseLogItem::class.java)?.copy(id = doc.id)
                    }
                    _uiState.update { it.copy(purchaseHistory = history) }
                }
            }
    }

    // --- NEW FUNCTION: APPROVE ---
    fun approvePurchase(purchase: PurchaseLogItem) {
        val adminId = auth.currentUser?.uid ?: return

        // 1. Create an updated version of the item for the history list.
        val approvedItem = purchase.copy(status = "completed")

        // 2. Perform a complete, two-sided optimistic UI update.
        _uiState.update { currentState ->
            currentState.copy(
                // Remove the item from the pending list.
                pendingPurchases = currentState.pendingPurchases.filterNot { it.id == purchase.id },
                // Add the updated item to the top of the history list.
                purchaseHistory = listOf(approvedItem) + currentState.purchaseHistory
            )
        }

        // 3. Perform the backend operation in the background.
        val purchaseRef = db.collection("groups").document(roomId)
            .collection("purchaseLog").document(purchase.id)

        purchaseRef.update(
            "status", "completed",
            "handledByUserId", adminId,
            "handledAt", Timestamp.now()
        ).addOnFailureListener {
            _uiState.update { it.copy(error = "Failed to approve purchase.") }
            // In a production app, you might add logic here to revert the UI state change.
        }
    }

        // --- NEW FUNCTION: REFUND ---
        fun refundPurchase(purchase: PurchaseLogItem) {
            val adminId = auth.currentUser?.uid ?: return

            // 1. Create an updated version of the item for the history list.
            val refundedItem = purchase.copy(status = "refunded")

            // 2. Perform a complete, two-sided optimistic UI update.
            _uiState.update { currentState ->
                currentState.copy(
                    // Remove the item from the pending list.
                    pendingPurchases = currentState.pendingPurchases.filterNot { it.id == purchase.id },
                    // Add the updated item to the top of the history list.
                    purchaseHistory = listOf(refundedItem) + currentState.purchaseHistory
                )
            }

            // 3. Perform the backend operation in the background.
            viewModelScope.launch {
                try {
                    val batch = db.batch()
                    val purchaseRef = db.collection("groups").document(roomId)
                        .collection("purchaseLog").document(purchase.id)
                    batch.update(
                        purchaseRef,
                        "status", "refunded",
                        "handledByUserId", adminId,
                        "handledAt", Timestamp.now()
                    )
                    val memberRef = db.collection("groups").document(roomId)
                        .collection("groupMembers").document(purchase.purchasedByUserId)
                    batch.update(memberRef, "totalPointsInGroup", FieldValue.increment(purchase.itemCost.toLong()))
                    batch.commit().await()
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Refund failed: ${e.message}") }
                    // In a production app, you might add logic here to revert the UI state change.
                }
            }
        }

        fun deleteShopItem(itemId: String) {
            if (itemId.isBlank()) return
            viewModelScope.launch {
                try {
                    db.collection("groups").document(roomId)
                        .collection("shopItems").document(itemId)
                        .delete().await()
                    // The real-time listener will automatically update the UI.
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Failed to delete item: ${e.message}") }
                }
            }
        }

        // --- NEW FUNCTION: UPDATE ---
        fun updateShopItem(itemId: String, newName: String, newCostStr: String, newMysteryText: String, newAutoRedeem: Boolean) {
            val newCost = newCostStr.toIntOrNull()
            if (itemId.isBlank() || newName.isBlank() || newCost == null || newCost <= 0) {
                _uiState.update { it.copy(error = "Please enter a valid name and cost.") }
                return
            }

            _uiState.update {
                it.copy(
                    isSaving = true,
                    error = null
                )
            } // Reuse isSaving for the update operation
            viewModelScope.launch {
                try {
                    val updatedData = mapOf(
                        "name" to newName,
                        "cost" to newCost,
                        "mysteryText" to newMysteryText,
                        "autoRedeem" to newAutoRedeem
                    )
                    db.collection("groups").document(roomId)
                        .collection("shopItems").document(itemId)
                        .update(updatedData).await()
                    // The real-time listener will handle the UI update.
                    _uiState.update { it.copy(isSaving = false) }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
            }
        }

        fun saveShopItem(name: String, costStr: String, mysteryText: String, autoRedeem: Boolean) {
            val cost = costStr.toIntOrNull()
            if (name.isBlank() || cost == null || cost <= 0) {
                _uiState.update { it.copy(error = "Please enter a valid name and positive cost.") }
                return
            }

            _uiState.update { it.copy(isSaving = true, error = null) }
            viewModelScope.launch {
                try {
                    val newItemData = hashMapOf(
                        "name" to name,
                        "cost" to cost,
                        "mysteryText" to mysteryText,
                        "autoRedeem" to autoRedeem,
                        "createdAt" to Timestamp.now()
                    )

                    db.collection("groups").document(roomId).collection("shopItems")
                        .add(newItemData).await()

                    // REMOVED THE MANUAL LIST UPDATE. The listener will handle it.
                    // We just need to signal success to the UI to clear the text fields.
                    _uiState.update { currentState ->
                        currentState.copy(
                            isSaving = false,
                            saveSuccess = true
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
            }
        }

        // Add this function to reset the event state
        fun onSaveHandled() {
            _uiState.update { it.copy(saveSuccess = false) }
        }

    fun clearHistory() {
        _uiState.update { it.copy(purchaseHistory = emptyList()) }

        viewModelScope.launch {
            try {
                // Query for all documents that are considered "history".
                val historyQuery = db.collection("groups").document(roomId)
                    .collection("purchaseLog")
                    .whereIn("status", listOf("completed", "refunded"))
                    .get()
                    .await()

                // Create a new batch write to delete each document found.
                val batch = db.batch()
                historyQuery.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }

                // Commit the batch.
                batch.commit().await()
                // If this succeeds, the real-time listener will eventually get the
                // same empty list we already set, so the UI won't change again.
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to clear history: ${e.message}") }
                // Optional: In a production app, you might trigger a full refresh here
                // to get the "un-deleted" items back into the list.
            }
        }
    }

}
