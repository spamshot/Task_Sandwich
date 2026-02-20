package com.spam.tasksandwich

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageShopScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageShopViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Pending", "History", "Shop Items")
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var itemToAction by remember { mutableStateOf<ShopItem?>(null) }
    var itemToEdit by remember { mutableStateOf<ShopItem?>(null) }

    if (showAddItemDialog) {
        EditShopItemDialog(
            roomName = uiState.roomName,
            item = ShopItem(name = "", cost = 0, mysteryText = ""),
            onDismiss = { showAddItemDialog = false },
            onConfirm = { newName, newCost, newMysteryText, newAutoRedeem ->
                viewModel.addShopItem(newName, newCost, newMysteryText, newAutoRedeem)
                showAddItemDialog = false
            },
            isCreating = true
        )
    }

    if (itemToAction != null) {
        AlertDialog(
            onDismissRequest = { itemToAction = null },
            title = { Text("Item Options") },
            text = { Text("What would you like to do with '${itemToAction!!.name}'?") },
            confirmButton = {
                TextButton(onClick = { itemToEdit = itemToAction; itemToAction = null }) {
                    Text("Edit")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { viewModel.deleteShopItem(itemToAction!!.id); itemToAction = null }
                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { itemToAction = null }) { Text("Cancel") }
                }
            }
        )
    }

    if (itemToEdit != null) {
        EditShopItemDialog(
            roomName = uiState.roomName,
            item = itemToEdit!!,
            onDismiss = { itemToEdit = null },
            onConfirm = { updatedName, updatedCost, updatedMysteryText, updatedAutoRedeem ->
                viewModel.updateShopItem(itemToEdit!!.id, updatedName, updatedCost, updatedMysteryText, updatedAutoRedeem)
                itemToEdit = null
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Purchase History?") },
            text = { Text("This will permanently delete all completed and refunded items from the log. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearRoomHistory(); showClearHistoryDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear History") }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTabIndex == 2) {
                FloatingActionButton(onClick = { showAddItemDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Shop Item")
                }
            }
        }
    ) { paddingValues ->
        // FIX 1 + 2: paddingValues was commented out.
        // This caused:
        //   - The TabRow to render under the status bar (top inset ignored)
        //   - The FAB to overlap the last list item (bottom inset ignored)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // ✅ restored — was commented out
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            // FIX: Give tab content a weight so it fills remaining space,
            // preventing LazyColumns from having unbounded height.
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> PendingPurchasesList(uiState.pendingPurchases, viewModel)
                    1 -> PurchaseHistoryList(
                        history = uiState.purchaseHistory,
                        onClearHistory = { showClearHistoryDialog = true }
                    )
                    2 -> ShopItemsList(uiState.existingItems) { itemToAction = it }
                }
            }
        }
    }
}

// ============================================================
// PendingPurchasesList
// FIX 4: Added fillMaxSize to LazyColumn for bounded height.
// ============================================================
@Composable
fun PendingPurchasesList(purchases: List<PurchaseLogItem>, viewModel: ManageShopViewModel) {
    if (purchases.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending rewards to approve.")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), // ✅ bounded height
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = purchases, key = { it.id }) { purchase ->
                PendingPurchaseCard(
                    purchase = purchase,
                    onApprove = { viewModel.completePurchase(purchase) },
                    onRefund = { viewModel.refundPurchase(purchase) }
                )
            }
        }
    }
}

// ============================================================
// PurchaseHistoryList
// FIX 4: Added fillMaxSize to LazyColumn.
// Bonus: Moved "Clear History" button into this tab where it
// belongs — it was previously wired up outside via a dialog
// state var but had no trigger button in the UI.
// ============================================================
@Composable
fun PurchaseHistoryList(
    history: List<PurchaseLogItem>,
    onClearHistory: () -> Unit
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No past purchases.")
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Clear history button at the top of the history tab
            TextButton(
                onClick = onClearHistory,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Clear History", color = MaterialTheme.colorScheme.error)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(), // ✅ bounded height
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(items = history, key = { it.id }) { purchase ->
                    PurchaseHistoryItem(purchase = purchase)
                    HorizontalDivider() // ✅ was missing entirely — added for visual separation
                }
            }
        }
    }
}

// ============================================================
// ShopItemsList
// FIX 3: Deprecated Divider() → HorizontalDivider()
// FIX 4: Added fillMaxSize to LazyColumn.
// FIX 5: Added key = { it.id } for correct list animations.
// ============================================================
@Composable
fun ShopItemsList(items: List<ShopItem>, onLongPress: (ShopItem) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No shop items created yet.\nPress the '+' button to add one.",
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), // ✅ bounded height
            contentPadding = PaddingValues(12.dp)
        ) {
            items(
                items = items,
                key = { it.id } // ✅ was missing — needed for correct recomposition
            ) { item ->
                ShopItemLogItem(item = item, onLongPress = { onLongPress(item) })
                HorizontalDivider() // ✅ was Divider() — deprecated in M3
            }
        }
    }
}

// ============================================================
// PendingPurchaseCard — no changes needed.
// ============================================================
@Composable
fun PendingPurchaseCard(
    purchase: PurchaseLogItem,
    onApprove: () -> Unit,
    onRefund: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "'${purchase.itemName}' for ${purchase.purchasedByUserName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("${purchase.itemCost} pts", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
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

// ============================================================
// PurchaseHistoryItem — no changes needed.
// ============================================================
@Composable
fun PurchaseHistoryItem(purchase: PurchaseLogItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "'${purchase.itemName}' for ${purchase.purchasedByUserName}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "${purchase.itemCost} pts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            purchase.status.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = when (purchase.status) {
                "completed" -> MaterialTheme.colorScheme.primary
                "refunded" -> MaterialTheme.colorScheme.error
                else -> Color.Unspecified
            }
        )
    }
}

// ============================================================
// ShopItemLogItem — no changes needed.
// ============================================================
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
        Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            "${item.cost} pts",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================
// EditShopItemDialog
// FIX 6: OutlinedTextFields now have fillMaxWidth so they
//         span the full dialog width instead of being narrow.
// FIX 7: Cost field filters non-numeric input immediately
//         instead of only failing at validation on confirm.
// ============================================================
@Composable
fun EditShopItemDialog(
    roomName: String,
    item: ShopItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit,
    isCreating: Boolean = false,
) {
    var editName by remember { mutableStateOf(item.name) }
    var editCost by remember { mutableStateOf(if (item.cost == 0 && isCreating) "" else item.cost.toString()) }
    var editMysteryText by remember { mutableStateOf(item.mysteryText) }
    var editAutoRedeem by remember { mutableStateOf(item.autoRedeem) }

    val isFormValid = editName.isNotBlank() && editCost.toIntOrNull() != null && (editCost.toIntOrNull() ?: 0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreating) "Add New Item" else "Edit Item") },
        text = {
            Column {
                Text(
                    "Room: $roomName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // FIX 6: fillMaxWidth so fields span the full dialog width
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Reward Name") },
                    modifier = Modifier.fillMaxWidth(), // ✅
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = editCost,
                    // FIX 7: Only allow numeric input immediately
                    onValueChange = { newVal ->
                        if (newVal.isEmpty() || newVal.all { it.isDigit() }) {
                            editCost = newVal // ✅ rejects non-numeric characters on input
                        }
                    },
                    label = { Text("Points Cost") },
                    modifier = Modifier.fillMaxWidth(), // ✅
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    // Show an error indicator if the field is non-empty but invalid
                    isError = editCost.isNotEmpty() && editCost.toIntOrNull() == null
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = editMysteryText,
                    onValueChange = { editMysteryText = it },
                    label = { Text("Mystery Text (Optional)") },
                    modifier = Modifier.fillMaxWidth(), // ✅
                    singleLine = false,
                    maxLines = 3
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Auto Redeem", modifier = Modifier.weight(1f))
                    Switch(
                        checked = editAutoRedeem,
                        onCheckedChange = { editAutoRedeem = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(editName, editCost, editMysteryText, editAutoRedeem) },
                enabled = isFormValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}