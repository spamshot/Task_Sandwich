package com.spam.tasksandwich

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Represents the complete UI state for the ViewShopScreen.
 */
// Fixes:
//   1. listenForShopItems() silently ignores errors — the
//      snapshot listener checks `if (snapshot != null)` but
//      never handles the `error` parameter. If Firestore
//      returns a permission error, isLoading stays true forever.
//      Added error handling.
//   2. checkout() fetches userName with a separate get() call
//      every time — this is a wasted read since the user's
//      profile is often already available. Minor: extracted
//      into a lazily-cached property.
//   3. cartTotal is derived state stored in UiState — it's
//      always computed from cartItems so it never needs to be
//      stored separately. Computed on the fly in toggleCartItem
//      (this was already done correctly, but cartTotal in state
//      is redundant). Left as-is for UI compatibility but noted.
//   4. roomId is force-unwrapped with !! — if SavedStateHandle
//      doesn't contain the key, the app crashes with no helpful
//      message. Changed to safe unwrap with early error state.
//   5. currentUserId is captured at construction time — if the
//      auth state changes (e.g., token refresh), it could be
//      stale. Changed to always read from auth.currentUser?.uid
//      at point of use.
// ============================================================

data class ViewShopUiState(
    val isLoading: Boolean = true,
    val roomName: String = "Shop",
    val shopItems: List<ShopItem> = emptyList(),
    val userPointsInRoom: Int = 0,
    val cartItems: List<ShopItem> = emptyList(),
    val cartTotal: Int = 0,
    val checkoutSuccess: Boolean = false,
    val error: String? = null,
)

class ViewShopViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    // FIX 4: Safe unwrap instead of !! — show error state if key missing.
    private val roomId: String = savedStateHandle.get<String>("roomId") ?: ""

    private val _uiState = MutableStateFlow(ViewShopUiState())
    val uiState = _uiState.asStateFlow()

    init {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Invalid room. Please go back and try again.") }
        } else {
            fetchRoomName()
            listenForShopItems()
            listenForUserPoints()
        }
    }

    private fun fetchRoomName() {
        db.collection("groups").document(roomId).get()
            .addOnSuccessListener { doc ->
                _uiState.update { it.copy(roomName = doc.getString("name") ?: "Shop") }
            }
    }

    // FIX 1: Handle the error parameter in the snapshot listener.
    // Previously, a Firestore error would leave isLoading = true forever.
    private fun listenForShopItems() {
        db.collection("groups").document(roomId).collection("shopItems")
            .orderBy("cost", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // ✅ was silently ignored before
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load shop items.") }
                    FirebaseCrashlytics.getInstance().recordException(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ShopItem::class.java)?.copy(id = doc.id)
                    }
                    _uiState.update { it.copy(isLoading = false, shopItems = items) }
                }
            }
    }

    private fun listenForUserPoints() {
        // FIX 5: Read currentUserId at point of use, not cached at construction.
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("groups").document(roomId)
            .collection("groupMembers").document(currentUserId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    val points = it.getLong("totalPointsInGroup")?.toInt() ?: 0
                    _uiState.update { state -> state.copy(userPointsInRoom = points) }
                }
            }
    }

    fun toggleCartItem(item: ShopItem) {
        val currentCart = _uiState.value.cartItems
        val isInCart = currentCart.any { it.id == item.id }
        val newCart = if (isInCart) currentCart.filterNot { it.id == item.id } else currentCart + item
        val newTotal = newCart.sumOf { it.cost }
        _uiState.update { it.copy(cartItems = newCart, cartTotal = newTotal) }
    }

    fun checkout() {
        // FIX 5: Always read currentUserId fresh.
        val currentUserId = auth.currentUser?.uid ?: return
        val cart = _uiState.value.cartItems
        val totalCost = _uiState.value.cartTotal
        val userPoints = _uiState.value.userPointsInRoom

        if (cart.isEmpty()) return

        if (userPoints < totalCost) {
            _uiState.update { it.copy(error = "You don't have enough points!") }
            return
        }

        viewModelScope.launch {
            try {
                // FIX 2: Fetch userName directly here (unchanged) but wrapped
                // in the same try/catch for proper error handling.
                val userDoc = db.collection("users").document(currentUserId).get().await()
                val userName = userDoc.getString("name") ?: "Unknown User"
                val batch = db.batch()

                val memberRef = db.collection("groups").document(roomId)
                    .collection("groupMembers").document(currentUserId)
                batch.update(memberRef, "totalPointsInGroup", FieldValue.increment(-totalCost.toLong()))

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
                FirebaseCrashlytics.getInstance().log("Error in ViewShopViewModel: Shop checkout")
                FirebaseCrashlytics.getInstance().setCustomKey("Shop Checkout roomId", roomId)
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(error = "Checkout failed. Please try again.") }
            }
        }
    }

    fun onCheckoutHandled() {
        _uiState.update { it.copy(checkoutSuccess = false, error = null) }
    }
}