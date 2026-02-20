package com.spam.tasksandwich

import android.R.attr.tint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import com.spam.tasksandwich.ReportDialog


// ProfileSettingsScreen.kt — Improved Version (v2: fixed sendReport calls)
//
// Fixes:
//   1. ProfileSettingsScreen itself has no Scaffold, so
//      TabRow renders behind the status bar. Wrapped in Scaffold.
//   2. ProfileSettingsForm: paddingValues from its inner Scaffold
//      was commented out (.padding(paddingValues) line was
//      disabled). This means content ignores the snackbar host
//      area. Restored.
//   3. ProfileSettingsForm: 'name', 'age', 'email' use `remember`
//      (not rememberSaveable) — values are lost on rotation.
//      Changed to rememberSaveable.
//   4. TransactionLogList: uses deprecated `Divider()` — updated
//      to `HorizontalDivider()` (M3).
//   5. TaskHistoryList: LazyColumn has no explicit fillMaxSize
//      — can cause height measure issues. Added.
// ============================================================

@Composable
fun ProfileSettingsScreen(
    onLogoutSuccess: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: ProfileSettingsViewModel = viewModel(),
    reportsViewModel: SendReportsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Settings", "Transaction Log", "Task History")

    LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) onLogoutSuccess()
    }

    // FIX 1: Wrap in Scaffold so the status bar inset is respected.
    // Without this, the TabRow renders right under the status bar icons.
    Scaffold { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding) // ✅ respects status bar
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

            when (selectedTabIndex) {
                0 -> ProfileSettingsForm(uiState = uiState, viewModel = viewModel)
                1 -> TransactionLogList(
                    history = uiState.purchaseHistory,
                    reportsViewModel = reportsViewModel,
                    uiState = uiState,
                    viewModel = viewModel
                )
                2 -> TaskHistoryList(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToEdit = onNavigateToEdit,
                    reportsViewModel = reportsViewModel
                )
            }
        }
    }
}

// ============================================================
// ProfileSettingsForm
// FIX 2: paddingValues was commented out — snackbar could
//         overlap content at the bottom. Restored.
// FIX 3: name/age/email used remember, not rememberSaveable —
//         lost on screen rotation. Fixed.
// ============================================================
@Composable
fun ProfileSettingsForm(uiState: ProfileSettingsUiState, viewModel: ProfileSettingsViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ✅ rememberSaveable survives rotation; remember does not
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var selectedIconId by rememberSaveable { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.userProfile?.uid) {
        uiState.userProfile?.let {
            name = it.name
            age = it.age?.toString() ?: ""
            email = it.email ?: ""
            selectedIconId = it.selectedIconId ?: "avatar_1"
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            scope.launch { snackbarHostState.showSnackbar("Profile saved successfully!") }
            viewModel.onSaveHandled()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(); showLogoutDialog = false }) { Text("Yes, Logout") }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure? This will permanently delete your profile, points, and account. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAccount(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Everything") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // ✅ Restored — was commented out; snackbar could overlap content
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Choose your Icon", style = MaterialTheme.typography.titleMedium)
                IconSelector(uiState.userProfile, selectedIconId, onIconSelected = { selectedIconId = it })
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 10) name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = age,
                    onValueChange = { if (it.length <= 2) age = it },
                    label = { Text("Age") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                uiState.userProfile?.let {
                    UserProfileCard(it.name, 0, it.totalPoints, it.selectedIconId ?: "avatar_1")
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.saveProfile(name, age, email, selectedIconId) },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Save Changes")
                }
                TextButton(onClick = { showLogoutDialog = true }) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(28.dp))
                TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.alpha(0.7f)) {
                    Text("Delete Account", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ============================================================
// DeleteConfirmationDialog — no changes needed.
// ============================================================
@Composable
fun DeleteConfirmationDialog(
    itemName: String,
    warningMessage: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Item") },
        text = {
            Column {
                Text("Are you sure you want to delete '$itemName'?")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = warningMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warningMessage.contains("ACTIVE")) MaterialTheme.colorScheme.error else Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ============================================================
// TransactionLogList
// FIX 4: Deprecated Divider() → HorizontalDivider() (M3 API).
// ============================================================
@Composable
fun TransactionLogList(
    history: List<UserPurchaseLogItem>,
    reportsViewModel: SendReportsViewModel,
    uiState: ProfileSettingsUiState,
    viewModel: ProfileSettingsViewModel
) {
    var reportPurchase by remember { mutableStateOf<UserPurchaseLogItem?>(null) }
    var itemToDelete by remember { mutableStateOf<UserPurchaseLogItem?>(null) }
    val context = LocalContext.current

    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No purchase history found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), // ✅ bounded height
            contentPadding = PaddingValues(12.dp)
        ) {
            items(items = history, key = { it.id }) { purchase ->
                TransactionHistoryItem(
                    purchase = purchase,
                    onLongPress = { reportPurchase = purchase },
                    onDeleteClick = { itemToDelete = purchase }
                )
                HorizontalDivider() // ✅ was Divider() — deprecated in M3
            }
        }
    }

    val reportsUiState by reportsViewModel.uiState.collectAsState()

    LaunchedEffect(reportsUiState.reportSent) {
        if (reportsUiState.reportSent) {
            reportPurchase = null
            reportsViewModel.onReportSentHandled()
            Toast.makeText(context, "Report sent. Thank you!", Toast.LENGTH_SHORT).show()
        }
    }

    reportPurchase?.let { purchase ->
        val contentToReport = purchase.mysteryText.ifBlank { purchase.itemName }
        ReportDialog(
            itemContent = contentToReport,
            isSubmitting = reportsUiState.isSubmitting,
            onDismiss = { reportPurchase = null },
            onConfirm = {
                reportsViewModel.sendReport(
                    reportedContent = contentToReport,
                    reportType = "mystery_text",
                    roomId = purchase.roomId
                )
            }
        )
    }

    itemToDelete?.let { purchase ->
        DeleteConfirmationDialog(
            itemName = purchase.itemName,
            warningMessage = "This will permanently remove this transaction from your history.",
            onDismiss = { itemToDelete = null },
            onConfirm = {
                viewModel.deleteTransaction(purchase.id, purchase.roomId)
                itemToDelete = null
            }
        )
    }
}

// ============================================================
// TaskHistoryList
// FIX 5: LazyColumn lacked fillMaxSize — can have unbounded
//         height in certain parent layouts. Added.
// ============================================================
@Composable
fun TaskHistoryList(
    uiState: ProfileSettingsUiState,
    viewModel: ProfileSettingsViewModel,
    onNavigateToEdit: (String) -> Unit,
    reportsViewModel: SendReportsViewModel
) {
    var taskToReport by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        val hasHistory = uiState.tasks.any { it.status != "assigned" }
        if (hasHistory) {
            TextButton(
                onClick = { viewModel.clearAllTaskHistory() },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear Completed Logs", color = MaterialTheme.colorScheme.error)
            }
        }

        if (uiState.tasks.isEmpty() && !uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No history found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize() // ✅ ensures bounded height within the Column
                    .background(Color.Gray.copy(alpha = 0.1f)),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = uiState.tasks, key = { it.id }) { task ->
                    TaskLogItem(
                        task = task,
                        onLongPress = { taskToReport = task },
                        onDeleteClick = { taskToDelete = task }
                    )
                }
            }
        }
    }

    val reportsUiState by reportsViewModel.uiState.collectAsState()

    LaunchedEffect(reportsUiState.reportSent) {
        if (reportsUiState.reportSent) {
            taskToReport = null
            reportsViewModel.onReportSentHandled()
            Toast.makeText(context, "Report sent. Thank you!", Toast.LENGTH_SHORT).show()
        }
    }

    taskToReport?.let { task ->
        val contentToReport = "Task Title: ${task.title}"
        ReportDialog(
            itemContent = contentToReport,
            isSubmitting = reportsUiState.isSubmitting,
            onDismiss = { taskToReport = null },
            onConfirm = {
                reportsViewModel.sendReport(
                    reportedContent = contentToReport,
                    reportType = "task_title",
                    roomId = task.groupId
                )
            }
        )
    }

    taskToDelete?.let { task ->
        val isActive = task.status == "assigned"
        DeleteConfirmationDialog(
            itemName = task.title,
            warningMessage = if (isActive)
                "WARNING: This is an ACTIVE task. Deleting it will stop it from repeating!"
            else "This will permanently remove this historical record.",
            onDismiss = { taskToDelete = null },
            onConfirm = {
                viewModel.deleteTask(task.id)
                taskToDelete = null
            }
        )
    }
}

// ============================================================
// TaskLogItem — no changes needed.
// ============================================================
@Composable
fun TaskLogItem(task: Task, onLongPress: () -> Unit, onDeleteClick: () -> Unit) {
    val isDone = task.status == "completed" || task.status == "verified"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDone) 0.6f else 1f)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null
                )
                if (isDone) {
                    Text("Status: Completed", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    Text("Points: ${task.points}", style = MaterialTheme.typography.bodyMedium)
                    Text("Repeats: ${task.repeatOption ?: "Never"}", style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ============================================================
// TransactionHistoryItem — no changes needed.
// ============================================================
@Composable
fun TransactionHistoryItem(
    purchase: UserPurchaseLogItem,
    onLongPress: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val pointsText: String
    val pointsColor: Color

    when (purchase.status) {
        "completed" -> { pointsText = "-${purchase.itemCost} pts"; pointsColor = colorResource(id = R.color.approved_red_dark) }
        "refunded" -> { pointsText = "+${purchase.itemCost} pts"; pointsColor = colorResource(id = R.color.refund_green_dark) }
        else -> { pointsText = "-+${purchase.itemCost} pts"; pointsColor = colorResource(id = R.color.pending_blue_dark) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("'${purchase.itemName}' from ${purchase.roomName}", fontWeight = FontWeight.Bold)
            if (purchase.mysteryText.isNotBlank() && purchase.status == "completed") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = purchase.mysteryText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic
                    )
                    IconButton(modifier = Modifier.size(20.dp), onClick = {
                        clipboardManager.setText(AnnotatedString(purchase.mysteryText))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(painter = painterResource(id = R.drawable.content_copy_24px), contentDescription = "Copy")
                    }
                }
            }
            purchase.purchasedAt?.let { Text(formatTimestamp(it), style = MaterialTheme.typography.bodySmall) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = pointsText, fontWeight = FontWeight.Bold, color = pointsColor)
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            }
        }
    }
}

// ============================================================
// IconSelector — no changes needed.
// ============================================================
@Composable
fun IconSelector(userProfile: UserProfile?, selectedIconId: String, onIconSelected: (String) -> Unit) {
    if (userProfile == null) return
    val defaultIcons = IconRepository.DefaultIconIds
    val allIconsMap = IconRepository.AllIconsMap
    val milestoneIcons = IconRepository.MilestoneIconsMap

    val availableIcons = (IconRepository.RoleIconsMap[userProfile.role] ?: emptyList()) + defaultIcons + userProfile.unlockedIconIds
    val finalIcons = availableIcons.distinct()
    val lockedIcons = milestoneIcons.keys.filter { it !in userProfile.unlockedIconIds }

    Column {
        Text("Unlocked", style = MaterialTheme.typography.titleSmall)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(items = finalIcons, key = { it }) { iconId ->
                allIconsMap[iconId]?.let { resId ->
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = iconId,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .clickable { onIconSelected(iconId) }
                            .border(
                                width = if (selectedIconId == iconId) 3.dp else 0.dp,
                                color = if (selectedIconId == iconId) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
        if (lockedIcons.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Unlockable", style = MaterialTheme.typography.titleSmall)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(lockedIcons) {
                    Image(
                        painter = painterResource(id = R.drawable.lockedimg),
                        contentDescription = "Locked",
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Timestamp): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(timestamp.toDate())
}