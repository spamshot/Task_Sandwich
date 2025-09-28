package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ViewShopUiState(
    val isLoading: Boolean = true,
    val roomName: String = "Shop",
    val shopItems: List<ShopItem> = emptyList(),
    val error: String? = null
)

class ViewShopViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val roomId: String = savedStateHandle.get("roomId")!!

    private val _uiState = MutableStateFlow(ViewShopUiState())
    val uiState = _uiState.asStateFlow()

    init {
        fetchRoomName()
        listenForShopItems()
    }

    private fun fetchRoomName() {
        db.collection("groups").document(roomId).get()
            .addOnSuccessListener { doc ->
                _uiState.update { it.copy(roomName = doc.getString("name") ?: "Shop") }
            }
    }

    private fun listenForShopItems() {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Room ID is missing.") }
            return
        }

        db.collection("groups").document(roomId).collection("shopItems")
            .orderBy("cost", Query.Direction.ASCENDING) // The query that needs an index
            .addSnapshotListener { snapshot, error ->
                // Explicit error handling
                if (error != null) {
                    // This log will appear in your Logcat in Android Studio
                    Log.w("ViewShop", "Listen for shop items failed.", error)
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load shop.") }
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
}