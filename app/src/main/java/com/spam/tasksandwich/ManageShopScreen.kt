package com.spam.tasksandwich

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageShopScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageShopViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var rewardName by rememberSaveable { mutableStateOf("") }
    var pointsCost by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // State for the Edit/Delete dialogs
    var itemToAction by remember { mutableStateOf<ShopItem?>(null) }
    var itemToEdit by remember { mutableStateOf<ShopItem?>(null) }

    // This effect handles clearing the input fields after a new item is successfully saved.
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            rewardName = ""
            pointsCost = ""
            scope.launch { snackbarHostState.showSnackbar("Item saved successfully!") }
            viewModel.onSaveHandled()
        }
    }

    // Dialog 1: "Edit or Delete?"
    if (itemToAction != null) {
        AlertDialog(
            onDismissRequest = { itemToAction = null },
            title = { Text("Item Options") },
            text = { Text("What would you like to do with '${itemToAction!!.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    itemToEdit = itemToAction
                    itemToAction = null
                }) { Text("Edit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.deleteShopItem(itemToAction!!.id)
                        itemToAction = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { itemToAction = null }) { Text("Cancel") }
                }
            }
        )
    }

    // Dialog 2: "Edit Item Details"
    if (itemToEdit != null) {
        EditShopItemDialog(
            item = itemToEdit!!,
            onDismiss = { itemToEdit = null },
            onConfirm = { updatedName, updatedCost ->
                viewModel.updateShopItem(itemToEdit!!.id, updatedName, updatedCost)
                itemToEdit = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Manage Shop") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Section 1: Pending Purchases ---
            Text("Pending Rewards", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.pendingPurchases.isEmpty()) {
                Text(
                    "No pending rewards to approve.",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // This Column is not scrollable itself, relying on the parent Column's scroll
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.pendingPurchases.forEach { purchase ->
                        PendingPurchaseCard(
                            purchase = purchase,
                            onApprove = { viewModel.approvePurchase(purchase) },
                            onRefund = { viewModel.refundPurchase(purchase) }
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 24.dp))

            // --- Section 2: Manage Available Items ---
            Text("Manage Shop Items", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            // This Column is also not a LazyColumn to fit within the parent scrollable Column
            Column {
                uiState.existingItems.forEach { item ->
                    ShopItemLogItem(item = item, onLongPress = { itemToAction = item })
                    Divider()
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Section 3: Add New Item Form ---
            Text("Add New Item", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
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
                onClick = { viewModel.saveShopItem(rewardName, pointsCost) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Save Item")
            }
        }
    }
}

/**
 * A Card to display a single pending purchase with Approve/Refund actions.
 */
@Composable
fun PendingPurchaseCard(
    purchase: PurchaseLogItem,
    onApprove: () -> Unit,
    onRefund: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "'${purchase.itemName}' for ${purchase.purchasedByUserName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("${purchase.itemCost} pts", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRefund, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Refund")
                }
                Button(onClick = onApprove) {
                    Text("Approve")
                }
            }
        }
    }
}

/**
 * A Row for an item in the management log, with long-press detection.
 */
@Composable
fun ShopItemLogItem(item: ShopItem, onLongPress: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge)
        Text("${item.cost} pts", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * The dialog for editing a shop item's name and cost.
 */
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
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}