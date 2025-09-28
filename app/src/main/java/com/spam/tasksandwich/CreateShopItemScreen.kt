package com.spam.tasksandwich

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateShopItemScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateShopItemViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var rewardName by rememberSaveable { mutableStateOf("") }
    var pointsCost by rememberSaveable { mutableStateOf("") }

    var itemToAction by remember { mutableStateOf<ShopItem?>(null) }
    // Holds the item being actively edited in the second dialog
    var itemToEdit by remember { mutableStateOf<ShopItem?>(null) }



    if (itemToAction != null) {
        AlertDialog(
            onDismissRequest = { itemToAction = null },
            title = { Text("Item Options") },
            text = { Text("What would you like to do with '${itemToAction!!.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToEdit = itemToAction // Open the edit dialog
                        itemToAction = null       // Close this dialog
                    }
                ) { Text("Edit") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.deleteShopItem(itemToAction!!.id)
                            itemToAction = null // Close the dialog
                        }
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { itemToAction = null }) { Text("Cancel") }
                }
            }
        )
    }

    // --- DIALOG 2: "Edit Item Details" ---
    if (itemToEdit != null) {
        EditShopItemDialog(
            item = itemToEdit!!,
            onDismiss = { itemToEdit = null },
            onConfirm = { updatedName, updatedCost ->
                viewModel.updateShopItem(itemToEdit!!.id, updatedName, updatedCost)
                itemToEdit = null // Close the dialog
            }
        )
    }


    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            rewardName = ""
            pointsCost = ""
            viewModel.onSaveHandled() // Reset the event
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Shop Items") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(10.dp)
        ) {
            // List of Existing Items
            Text("Existing Items", style = MaterialTheme.typography.titleLarge)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.existingItems) { item ->
                    // Replace the simple Row with a new, gesture-aware composable
                    ShopItemLogItem(
                        item = item,
                        onLongPress = { itemToAction = item }
                    )
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                }
            }

            // Input Form
            Spacer(Modifier.height(4.dp))
            Text("Add New Item", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = rewardName,
                onValueChange = { rewardName = it },
                label = { Text("Reward Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pointsCost,
                onValueChange = { pointsCost = it },
                label = { Text("Points Cost") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.saveShopItem(rewardName, pointsCost)
                    // Clear fields on successful save (listener will handle UI update)
                    rewardName = ""
                    pointsCost = ""
                },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving)
                    CircularProgressIndicator(Modifier.size(24.dp))
                else Text("Save Item")
            }
        }
    }
}
    @Composable
    fun ShopItemLogItem(item: ShopItem, onLongPress: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(item.name)
            Text("${item.cost} pts", fontWeight = FontWeight.Bold)
        }
    }


    @Composable
    fun EditShopItemDialog(
        item: ShopItem,
        onDismiss: () -> Unit,
        onConfirm: (String, String) -> Unit
    ) {
        var editName by remember { mutableStateOf(item.name) }
        var editCost by remember { mutableStateOf(item.cost.toString()) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Item") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Reward Name") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editCost,
                        onValueChange = { editCost = it },
                        label = { Text("Points Cost") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(editName, editCost) }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }