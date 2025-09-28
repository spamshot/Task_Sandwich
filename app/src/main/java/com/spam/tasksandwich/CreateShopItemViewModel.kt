package com.spam.tasksandwich

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

data class CreateShopItemUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val existingItems: List<ShopItem> = emptyList(),
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class CreateShopItemViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val db = Firebase.firestore
    private val roomId: String = savedStateHandle.get("roomId")!!

    private val _uiState = MutableStateFlow(CreateShopItemUiState())
    val uiState = _uiState.asStateFlow()

    init {
        listenForShopItems()
    }

    private fun listenForShopItems() {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Room ID is missing.") }
            return
        }

        db.collection("groups").document(roomId).collection("shopItems")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(ShopItem::class.java)?.copy(id = doc.id)
                    }
                    _uiState.update { it.copy(isLoading = false, existingItems = items) }
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
    fun updateShopItem(itemId: String, newName: String, newCostStr: String) {
        val newCost = newCostStr.toIntOrNull()
        if (itemId.isBlank() || newName.isBlank() || newCost == null || newCost <= 0) {
            _uiState.update { it.copy(error = "Please enter a valid name and cost.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) } // Reuse isSaving for the update operation
        viewModelScope.launch {
            try {
                val updatedData = mapOf(
                    "name" to newName,
                    "cost" to newCost
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

    fun saveShopItem(name: String, costStr: String) {
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
}