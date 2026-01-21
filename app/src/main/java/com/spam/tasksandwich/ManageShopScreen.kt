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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageShopScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageShopViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // State to manage which tab is currently selected
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Pending", "History", "Shop Items")

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // State for the dialogs
    var showAddItemDialog by remember { mutableStateOf(false) }
    var itemToAction by remember { mutableStateOf<ShopItem?>(null) }
    var itemToEdit by remember { mutableStateOf<ShopItem?>(null) }

    // The "Add New Item" dialog is now triggered by the FAB
    if (showAddItemDialog) {
        EditShopItemDialog(
            item = ShopItem(name = "", cost = 0, mysteryText = ""), // Use your field name
            onDismiss = { showAddItemDialog = false },
            onConfirm = { newName, newCost, newMysteryText, newAutoRedeem ->
                viewModel.saveShopItem(newName, newCost, newMysteryText, newAutoRedeem)
                showAddItemDialog = false
            },
            isCreating = true
        )
    }

    // The other dialogs for Edit/Delete are unchanged
    if (itemToAction != null) {
        AlertDialog(
            onDismissRequest = { itemToAction = null }, // Close if the user clicks outside
            title = { Text("Item Options") },
            text = { Text("What would you like to do with '${itemToAction!!.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        itemToEdit = itemToAction // Set the state to open the second dialog
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
    if (itemToEdit != null) {
        EditShopItemDialog(
            item = itemToEdit!!,
            onDismiss = { itemToEdit = null },
            // --- RENAMED: 'updatedMysteryText' ---
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
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Manage Shop") },
//                navigationIcon = { IconButton(onClick = onNavigateBack) {
//                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go Back")
//                } },
//                actions = {
//                    // Only show the "Clear History" button when the History tab is selected
//                    if (selectedTabIndex == 1) {
//                        IconButton(onClick = { showClearHistoryDialog = true }) {
//                            Icon(Icons.Default.Clear, contentDescription = "Clear History")
//                        }
//                    }
//                }
//            )
//                 }
//        ,

        // The FAB is only shown when the "Shop Items" tab is selected
        floatingActionButton = {
            if (selectedTabIndex == 2) {
                FloatingActionButton(onClick = { showAddItemDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Shop Item")
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // The TabRow for navigating between sections
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            // The content of the selected tab
            when (selectedTabIndex) {
                0 -> PendingPurchasesList(uiState.pendingPurchases, viewModel)
                1 -> PurchaseHistoryList(uiState.purchaseHistory)
                2 -> ShopItemsList(uiState.existingItems) { itemToAction = it }
            }
        }
    }
}

// --- NEW DEDICATED COMPOSABLES FOR EACH TAB ---

@Composable
fun PendingPurchasesList(purchases: List<PurchaseLogItem>, viewModel: ManageShopViewModel) {
    if (purchases.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pending rewards to approve.")
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Add a key to each item, using its unique ID from Firestore.
            items(items = purchases, key = { it.id }) { purchase ->
                PendingPurchaseCard(
                    purchase = purchase,
                    onApprove = { viewModel.approvePurchase(purchase) },
                    onRefund = { viewModel.refundPurchase(purchase) }
                )
            }
        }
    }
}


@Composable
fun PurchaseHistoryList(history: List<PurchaseLogItem>) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No past purchases.")
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            // Add a key to each item, using its unique ID from Firestore.
            items(items = history, key = { it.id }) { purchase ->
                PurchaseHistoryItem(purchase = purchase)
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            }
        }
    }
}

@Composable
fun ShopItemsList(items: List<ShopItem>, onLongPress: (ShopItem) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No shop items created yet. Press the '+' button to add one.")
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(items) { item ->
                ShopItemLogItem(item = item, onLongPress = { onLongPress(item) })
                Divider()
            }
        }
    }
}

@Composable
fun PendingPurchaseCard(
    purchase: PurchaseLogItem,
    onApprove: () -> Unit,
    onRefund: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
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

@Composable
fun PurchaseHistoryItem(purchase: PurchaseLogItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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

@Composable
fun ShopItemLogItem(item: ShopItem, onLongPress: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            detectTapGestures(onLongPress = { onLongPress() })
        }.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.name, style = MaterialTheme.typography.bodyLarge)
        Text("${item.cost} pts", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EditShopItemDialog(
    item: ShopItem,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit,
    isCreating: Boolean = false
) {
    var editName by remember { mutableStateOf(item.name) }
    var editCost by remember { mutableStateOf(item.cost.toString()) }
    var editMysteryText by remember { mutableStateOf(item.mysteryText) }
    var editAutoRedeem by remember { mutableStateOf(item.autoRedeem) }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreating) "Add New Item" else "Edit Item") },
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editMysteryText,
                    onValueChange = { editMysteryText = it },
                    label = { Text("Mystery Text (Optional)") },
                    singleLine = false,
                    maxLines = 3
                )
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
        confirmButton = { TextButton(onClick = { onConfirm(editName, editCost, editMysteryText, editAutoRedeem) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}