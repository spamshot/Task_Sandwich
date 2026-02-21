package com.spam.tasksandwich

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageShopScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageShopViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showAddItemSheet by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ShopItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ShopItem?>(null) }

    val pendingCount = uiState.pendingPurchases.size

    // Delete confirmation dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Item") },
            text = { Text("Delete '${itemToDelete!!.name}' from the shop? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteShopItem(itemToDelete!!.id); itemToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Clear history confirmation
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Purchase History?") },
            text = { Text("This will permanently delete all completed and refunded items from the log. This cannot be undone.") },
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

    // Add item bottom sheet
    if (showAddItemSheet) {
        ShopItemBottomSheet(
            roomName = uiState.roomName,
            item = null,
            onDismiss = { showAddItemSheet = false },
            onConfirm = { name, cost, mysteryText, autoRedeem ->
                viewModel.addShopItem(name, cost, mysteryText, autoRedeem)
                showAddItemSheet = false
            }
        )
    }

    // Edit item bottom sheet
    itemToEdit?.let { item ->
        ShopItemBottomSheet(
            roomName = uiState.roomName,
            item = item,
            onDismiss = { itemToEdit = null },
            onConfirm = { name, cost, mysteryText, autoRedeem ->
                viewModel.updateShopItem(item.id, name, cost, mysteryText, autoRedeem)
                itemToEdit = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.roomName.isNotBlank()) uiState.roomName else "Manage Shop",
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    scrolledContainerColor = Color(0xFF1A1A2E)
                )
            )
        },
        floatingActionButton = {
            if (selectedTabIndex == 1) {
                FloatingActionButton(
                    onClick = { showAddItemSheet = true },
                    containerColor = Color(0xFF1A1A2E)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Shop Item", tint = Color.White)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ShopTabSelector(
                selectedIndex = selectedTabIndex,
                pendingCount = pendingCount,
                onTabSelected = { selectedTabIndex = it }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> PendingPurchasesList(
                        purchases = uiState.pendingPurchases,
                        onApprove = { viewModel.completePurchase(it) },
                        onRefund = { viewModel.refundPurchase(it) }
                    )
                    1 -> ShopItemsList(
                        items = uiState.existingItems,
                        onEditClick = { itemToEdit = it },
                        onDeleteClick = { itemToDelete = it }
                    )
                    2 -> PurchaseHistoryList(
                        history = uiState.purchaseHistory,
                        onClearHistory = { showClearHistoryDialog = true }
                    )
                }
            }
        }
    }
}

// ============================================================
// ShopTabSelector — pill style with pending badge on tab 0
// ============================================================
@Composable
fun ShopTabSelector(
    selectedIndex: Int,
    pendingCount: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("⏳ Pending", "🛍 Items", "📜 History")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface
                            else Color.Transparent
                        )
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (index == 0 && pendingCount > 0) {
                        BadgedBox(badge = { Badge { Text(pendingCount.toString()) } }) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// PendingPurchasesList
// ============================================================
@Composable
fun PendingPurchasesList(
    purchases: List<PurchaseLogItem>,
    onApprove: (PurchaseLogItem) -> Unit,
    onRefund: (PurchaseLogItem) -> Unit
) {
    if (purchases.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✅", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text("No pending approvals!", style = MaterialTheme.typography.titleMedium)
                Text(
                    "You're all caught up.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = purchases, key = { it.id }) { purchase ->
                PendingPurchaseCard(
                    purchase = purchase,
                    onApprove = { onApprove(purchase) },
                    onRefund = { onRefund(purchase) }
                )
            }
        }
    }
}

@Composable
fun PendingPurchaseCard(
    purchase: PurchaseLogItem,
    onApprove: () -> Unit,
    onRefund: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Amber left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Color(0xFFF59E0B),
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🎁", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = purchase.itemName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Requested by ${purchase.purchasedByUserName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Points pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Text(
                            text = "${purchase.itemCost} pts",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefund,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Refund") }

                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Approve")
                    }
                }
            }
        }
    }
}

// ============================================================
// ShopItemsList — tap to reveal edit/delete inline
// ============================================================
@Composable
fun ShopItemsList(
    items: List<ShopItem>,
    onEditClick: (ShopItem) -> Unit,
    onDeleteClick: (ShopItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🛍", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text("No shop items yet.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Press + to add your first reward.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        var expandedItemId by remember { mutableStateOf<String?>(null) }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Shop Items",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${items.size} item${if (items.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = items, key = { it.id }) { item ->
                    ShopItemCard(
                        item = item,
                        isExpanded = expandedItemId == item.id,
                        onClick = { expandedItemId = if (expandedItemId == item.id) null else item.id },
                        onEditClick = { onEditClick(item); expandedItemId = null },
                        onDeleteClick = { onDeleteClick(item); expandedItemId = null }
                    )
                }
            }
        }
    }
}

@Composable
fun ShopItemCard(
    item: ShopItem,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(if (isExpanded) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🎁", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "${item.cost} pts",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (item.autoRedeem) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                        ) {
                            Text(
                                "Auto",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (item.mysteryText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFAF5FF),
                            border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                        ) {
                            Text(
                                "Mystery",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            // Actions revealed on tap
            AnimatedVisibility(visible = isExpanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFE8F0FE), RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF3B6BDC),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFEE2E2), RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// PurchaseHistoryList — colored status chips
// ============================================================
@Composable
fun PurchaseHistoryList(
    history: List<PurchaseLogItem>,
    onClearHistory: () -> Unit
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📜", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text("No purchase history yet.", style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Purchase History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    items(items = history, key = { it.id }) { purchase ->
                        PurchaseHistoryItem(purchase = purchase)
                        if (purchase != history.last()) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun PurchaseHistoryItem(purchase: PurchaseLogItem) {
    val (chipBg, chipBorder, chipLabel, chipColor) = when (purchase.status) {
        "completed" -> listOf(Color(0xFFF0FDF4), Color(0xFFBBF7D0), "Approved",  Color(0xFF16A34A))
        "refunded"  -> listOf(Color(0xFFFEE2E2), Color(0xFFFECACA), "Refunded",  Color(0xFFEF4444))
        else        -> listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A), purchase.status.replaceFirstChar { it.uppercase() }, Color(0xFFD97706))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = purchase.itemName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${purchase.purchasedByUserName} · ${purchase.itemCost} pts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = chipBg as Color,
            border = BorderStroke(1.dp, chipBorder as Color)
        ) {
            Text(
                text = chipLabel as String,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = chipColor as Color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// ============================================================
// ShopItemBottomSheet — replaces EditShopItemDialog
// ModalBottomSheet + imePadding = keyboard never covers fields
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopItemBottomSheet(
    roomName: String,
    item: ShopItem?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit
) {
    val isCreating = item == null
    var editName        by remember { mutableStateOf(item?.name        ?: "") }
    var editCost        by remember { mutableStateOf(if (isCreating) "" else item!!.cost.toString()) }
    var editMysteryText by remember { mutableStateOf(item?.mysteryText  ?: "") }
    var editAutoRedeem  by remember { mutableStateOf(item?.autoRedeem   ?: false) }

    val isFormValid = editName.isNotBlank()
            && editCost.toIntOrNull() != null
            && (editCost.toIntOrNull() ?: 0) > 0

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .imePadding()
        ) {
            Text(
                text = if (isCreating) "Add New Item" else "Edit Item",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Room: $roomName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )

            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("Reward Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = editCost,
                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) editCost = it },
                label = { Text("Points Cost") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = editCost.isNotEmpty() && editCost.toIntOrNull() == null,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = editMysteryText,
                onValueChange = { editMysteryText = it },
                label = { Text("Mystery Text (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto Redeem",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Approve automatically when purchased",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = editAutoRedeem, onCheckedChange = { editAutoRedeem = it })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Cancel") }

                Button(
                    onClick = { onConfirm(editName, editCost, editMysteryText, editAutoRedeem) },
                    modifier = Modifier.weight(2f),
                    enabled = isFormValid,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
                ) { Text("Save Item") }
            }
        }
    }
}