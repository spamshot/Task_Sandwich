package com.spam.tasksandwich

import android.util.Log
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
import com.spam.tasksandwich.ShopItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Represents the complete UI state for the ViewShopScreen.
 */
data class ViewShopUiState(
    val isLoading: Boolean = true,
    val roomName: String = "Shop",
    val shopItems: List<ShopItem> = emptyList(),
    val userPointsInRoom: Int = 0,
    val cartItems: List<ShopItem> = emptyList(),
    val cartTotal: Int = 0,
    val checkoutSuccess: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for the ViewShopScreen. It is responsible for fetching shop items,
 * tracking the user's points, managing the shopping cart, and handling the checkout logic.
 */
class ViewShopViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val roomId: String = savedStateHandle.get("roomId")!!
    private val currentUserId = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(ViewShopUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchRoomName()
        listenForShopItems()
        listenForUserPoints()
    }

    /**
     * Fetches the name of the current room for the TopAppBar title.
     */
    private fun fetchRoomName() {
        if (roomId.isBlank()) return
        db.collection("groups").document(roomId).get()
            .addOnSuccessListener { doc ->
                _uiState.update { it.copy(roomName = doc.getString("name") ?: "Shop") }
            }
    }

    /**
     * Listens for real-time updates to the items available in this room's shop.
     */
    private fun listenForShopItems() {
        if (roomId.isBlank()) return
        db.collection("groups").document(roomId).collection("shopItems")
            .orderBy("cost", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ShopItem::class.java)?.copy(id = doc.id)
                    }
                    _uiState.update { it.copy(isLoading = false, shopItems = items) }
                }
            }
    }

    /**
     * Listens for real-time updates to the current user's point total in this room.
     */
    private fun listenForUserPoints() {
        if (currentUserId == null || roomId.isBlank()) return
        db.collection("groups").document(roomId)
            .collection("groupMembers").document(currentUserId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    val points = it.getLong("totalPointsInGroup")?.toInt() ?: 0
                    _uiState.update { it.copy(userPointsInRoom = points) }
                }
            }
    }

    /**
     * Adds or removes a ShopItem from the cart and recalculates the total cost.
     */
    fun toggleCartItem(item: ShopItem) {
        val currentCart = _uiState.value.cartItems
        val isInCart = currentCart.any { it.id == item.id }

        val newCart = if (isInCart) {
            currentCart.filterNot { it.id == item.id }
        } else {
            currentCart + item
        }

        val newTotal = newCart.sumOf { it.cost }
        _uiState.update { it.copy(cartItems = newCart, cartTotal = newTotal) }
    }

    /**
     * Handles the checkout process. It verifies the user has enough points,
     * deducts the points, and creates a purchase log for each item.
     */
    fun checkout() {
        if (currentUserId == null) return
        val cart = _uiState.value.cartItems
        val totalCost = _uiState.value.cartTotal
        val userPoints = _uiState.value.userPointsInRoom

        if (userPoints < totalCost) {
            _uiState.update { it.copy(error = "You don't have enough points!") }
            return
        }

        viewModelScope.launch {
            try {
                val userDoc = db.collection("users").document(currentUserId).get().await()
                val userName = userDoc.getString("name") ?: "Unknown User"
                val batch = db.batch()
                val memberRef = db.collection("groups").document(roomId)
                    .collection("groupMembers").document(currentUserId)

                // 1. Deduct points
                batch.update(memberRef, "totalPointsInGroup", FieldValue.increment(-totalCost.toLong()))

                // 2. Create purchase log entries
                cart.forEach { item ->
                    val purchaseRef = db.collection("groups").document(roomId)
                        .collection("purchaseLog").document()

                    val purchaseData = hashMapOf(
                        "itemId" to item.id,
                        "itemName" to item.name,
                        "itemCost" to item.cost,
                        "mysteryText" to item.mysteryText,
                        "purchasedByUserId" to currentUserId,
                        "purchasedByUserName" to userName,
                        "purchasedAt" to Timestamp.now(),
                        "status" to if (item.autoRedeem) "completed" else "pending"
                    )
                    batch.set(purchaseRef, purchaseData)
                }

                batch.commit().await()
                _uiState.update { it.copy(cartItems = emptyList(), cartTotal = 0, checkoutSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Checkout failed: ${e.message}") }
            }
        }
    }

    /**
     * Resets one-time event flags (like success or error) after they've been handled by the UI.
     */
    fun onCheckoutHandled() {
        _uiState.update { it.copy(checkoutSuccess = false, error = null) }
    }
}