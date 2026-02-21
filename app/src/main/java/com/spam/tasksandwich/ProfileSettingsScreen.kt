package com.spam.tasksandwich

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.spam.tasksandwich.IconRepository.AllIconsMap


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    onNavigateBack: () -> Unit,
    onLogoutSuccess: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: ProfileSettingsViewModel = viewModel(),
    reportsViewModel: SendReportsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Settings", "Transactions", "Task Log")
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) onLogoutSuccess()
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            scope.launch { snackbarHostState.showSnackbar("Profile saved!") }
            viewModel.onSaveHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Profile & Settings",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.Default.ArrowBack,
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
                // Pill tabs inside the dark bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A2E))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTabIndex == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (isSelected) Color.White
                                        else Color.Transparent
                                    )
                                    .clickable { selectedTabIndex = index }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF1A1A2E)
                                    else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        when (selectedTabIndex) {
            0 -> ProfileSettingsForm(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
            1 -> TransactionLogList(
                history = uiState.purchaseHistory,
                reportsViewModel = reportsViewModel,
                uiState = uiState,
                viewModel = viewModel,
                onSnack = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                modifier = Modifier.padding(paddingValues)
            )
            2 -> TaskHistoryList(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateToEdit = onNavigateToEdit,
                reportsViewModel = reportsViewModel,
                onSnack = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

// ============================================================
// ProfileSettingsForm — profile header + icon + fields + danger
// ============================================================
@Composable
fun ProfileSettingsForm(
    uiState: ProfileSettingsUiState,
    viewModel: ProfileSettingsViewModel,
    modifier: Modifier = Modifier
) {
    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf(0) }
    var hasAge by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var selectedIconId by rememberSaveable { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.userProfile?.uid) {
        uiState.userProfile?.let {
            name = it.name
            val profileAge = it.age
            if (profileAge != null && profileAge > 0) {
                age = profileAge; hasAge = true
            }
            email = it.email ?: ""
            selectedIconId = it.selectedIconId ?: "avatar_1"
        }
    }

    // Dialogs
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(onClick = { viewModel.logout(); showLogoutDialog = false }) {
                    Text("Yes, Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = {
                Text("This will permanently delete your profile, points, and account. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteAccount(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (uiState.isLoading) {
        Box(
            Modifier.fillMaxSize().then(modifier),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // ── Profile header card ──────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Avatar
                val avatarResId = AllIconsMap[selectedIconId] ?: R.drawable.carrotdog
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(60.dp),
                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Image(
                        painter = painterResource(id = avatarResId),
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                    )
                }
                Column {
                    Text(
                        uiState.userProfile?.name ?: "User",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🏆", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${uiState.userProfile?.totalPoints ?: 0} pts",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("·", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("⭐", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${uiState.userProfile?.totalSelfPoints ?: 0} pts",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // ── Icon selector + Edit fields ──────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CHOOSE YOUR ICON",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                IconSelector(
                    userProfile = uiState.userProfile,
                    selectedIconId = selectedIconId,
                    onIconSelected = { selectedIconId = it }
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Text(
                    "EDIT PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 10) name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (name.isNotEmpty()) {
                            Text(
                                "${name.length}/10",
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    name.length >= 10 -> MaterialTheme.colorScheme.error
                                    name.length >= 7  -> Color(0xFFF59E0B)
                                    else              -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    }
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    "AGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AgeStepperRow(
                    age = age,
                    hasAge = hasAge,
                    onDecrement = { if (hasAge) age = (age - 1).coerceAtLeast(1) },
                    onIncrement = {
                        if (!hasAge) { hasAge = true; age = 5 }
                        else age = (age + 1).coerceAtMost(99)
                    }
                )
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.saveProfile(
                            name, if (hasAge) age.toString() else "", email, selectedIconId
                        )
                    },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Danger Zone card ─────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(0.dp),
            border = BorderStroke(1.5.dp, Color(0xFFFEE2E2))
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF2F2))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "⚠️  DANGER ZONE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFEF4444),
                        letterSpacing = 0.8.sp
                    )
                }
                // Logout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogoutDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🚪", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Log Out",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFEF4444).copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                HorizontalDivider(color = Color(0xFFFEE2E2))
                // Delete Account
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🗑", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Delete Account",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFEF4444).copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ============================================================
// TransactionLogList — card items with pts badge pills
// ============================================================
@Composable
fun TransactionLogList(
    history: List<UserPurchaseLogItem>,
    reportsViewModel: SendReportsViewModel,
    uiState: ProfileSettingsUiState,
    viewModel: ProfileSettingsViewModel,
    onSnack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var reportPurchase by remember { mutableStateOf<UserPurchaseLogItem?>(null) }
    var itemToDelete by remember { mutableStateOf<UserPurchaseLogItem?>(null) }
    val reportsUiState by reportsViewModel.uiState.collectAsState()

    LaunchedEffect(reportsUiState.reportSent) {
        if (reportsUiState.reportSent) {
            reportPurchase = null
            reportsViewModel.onReportSentHandled()
            onSnack("Report sent. Thank you!")
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

    if (history.isEmpty()) {
        Box(
            Modifier.fillMaxSize().then(modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🛍", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text("No transactions yet.", style = MaterialTheme.typography.titleSmall)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = history, key = { it.id }) { purchase ->
                TransactionHistoryItem(
                    purchase = purchase,
                    onLongPress = { reportPurchase = purchase },
                    onDeleteClick = { itemToDelete = purchase }
                )
            }
        }
    }
}

// ============================================================
// TransactionHistoryItem — card with pts badge pill
// ============================================================
@Composable
fun TransactionHistoryItem(
    purchase: UserPurchaseLogItem,
    onLongPress: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    val (ptsBg, ptsColor, ptsText) = when (purchase.status) {
        "completed" -> Triple(Color(0xFFFEE2E2), Color(0xFFEF4444), "-${purchase.itemCost} pts")
        "refunded"  -> Triple(Color(0xFFF0FDF4), Color(0xFF16A34A), "+${purchase.itemCost} pts")
        else        -> Triple(Color(0xFFE8F0FE), Color(0xFF3B6BDC), "~${purchase.itemCost} pts")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Icon area
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🎁", style = MaterialTheme.typography.titleMedium)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    purchase.itemName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${purchase.roomName} · ${purchase.purchasedAt?.let { formatTimestamp(it) } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Mystery text with copy button
                if (purchase.mysteryText.isNotBlank() && purchase.status == "completed") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🎁 ${purchase.mysteryText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7C3AED),
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(purchase.mysteryText))
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.content_copy_24px),
                                contentDescription = "Copy",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Pts badge pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ptsBg
            ) {
                Text(
                    ptsText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = ptsColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Delete
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFFFEE2E2), CircleShape)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ============================================================
// TaskHistoryList — status chips, clear button, no gray bg
// ============================================================
@Composable
fun TaskHistoryList(
    uiState: ProfileSettingsUiState,
    viewModel: ProfileSettingsViewModel,
    onNavigateToEdit: (String) -> Unit,
    reportsViewModel: SendReportsViewModel,
    onSnack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var taskToReport by remember { mutableStateOf<Task?>(null) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    val reportsUiState by reportsViewModel.uiState.collectAsState()

    LaunchedEffect(reportsUiState.reportSent) {
        if (reportsUiState.reportSent) {
            taskToReport = null
            reportsViewModel.onReportSentHandled()
            onSnack("Report sent. Thank you!")
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
            onConfirm = { viewModel.deleteTask(task.id); taskToDelete = null }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        val hasHistory = uiState.tasks.any { it.status != "assigned" }

        // Clear button row
        if (hasHistory) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                    modifier = Modifier.clickable { viewModel.clearAllTaskHistory() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Clear Completed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }

        if (uiState.tasks.isEmpty() && !uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("No task history yet.", style = MaterialTheme.typography.titleSmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
}

// ============================================================
// TaskLogItem — status chip, no more plain text status
// ============================================================
@Composable
fun TaskLogItem(task: Task, onLongPress: () -> Unit, onDeleteClick: () -> Unit) {
    val isDone = task.status == "completed" || task.status == "verified"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDone) 0.7f else 1f)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(if (isDone) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                if (!isDone) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFE8F0FE),
                            border = BorderStroke(1.dp, Color(0xFFC7D7FC))
                        ) {
                            Text(
                                "${task.points} pts",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B6BDC),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (task.repeatOption != null && task.repeatOption != "Never") {
                            Text(
                                task.repeatOption,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        task.dueDate?.let { formatTimestamp(it) } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status chip
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDone) Color(0xFFF0FDF4) else Color(0xFFFEF3C7),
                border = BorderStroke(
                    1.dp,
                    if (isDone) Color(0xFFBBF7D0) else Color(0xFFFDE68A)
                )
            ) {
                Text(
                    if (isDone) "Completed" else "Active",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) Color(0xFF16A34A) else Color(0xFFD97706),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFFFEE2E2), CircleShape)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ============================================================
// DeleteConfirmationDialog — unchanged logic
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
                Text("Are you sure you want to delete \"$itemName\"?")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = warningMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warningMessage.contains("ACTIVE") || warningMessage.contains("permanently"))
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
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
// IconSelector — unchanged logic, minor label styling
// ============================================================
@Composable
fun IconSelector(
    userProfile: UserProfile?,
    selectedIconId: String,
    onIconSelected: (String) -> Unit
) {
    if (userProfile == null) return
    val defaultIcons = IconRepository.DefaultIconIds
    val allIconsMap = IconRepository.AllIconsMap
    val milestoneIcons = IconRepository.MilestoneIconsMap
    val availableIcons = (IconRepository.RoleIconsMap[userProfile.role]
        ?: emptyList()) + defaultIcons + userProfile.unlockedIconIds
    val finalIcons = availableIcons.distinct()
    val lockedIcons = milestoneIcons.keys.filter { it !in userProfile.unlockedIconIds }

    Column {
        Text(
            "Unlocked",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(items = finalIcons, key = { it }) { iconId ->
                allIconsMap[iconId]?.let { resId ->
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = iconId,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .clickable { onIconSelected(iconId) }
                            .border(
                                width = if (selectedIconId == iconId) 3.dp else 0.dp,
                                color = if (selectedIconId == iconId) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
        if (lockedIcons.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))

            // ── Next milestone progress indicator ────────────────
            val currentPts = userProfile.totalSelfPoints
            val nextMilestoneEntry = IconRepository.MilestoneIconsMap.entries
                .filter { it.key !in userProfile.unlockedIconIds }
                .minByOrNull { it.value }

            if (nextMilestoneEntry != null) {
                val nextPts = nextMilestoneEntry.value
                val prevPts = IconRepository.MilestoneIconsMap.entries
                    .filter { it.key in userProfile.unlockedIconIds }
                    .maxByOrNull { it.value }?.value ?: 0
                val progress = ((currentPts - prevPts).toFloat() / (nextPts - prevPts).toFloat()).coerceIn(0f, 1f)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0F4FF),
                    border = BorderStroke(1.dp, Color(0xFFC7D7FC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Next unlock at $nextPts pts",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B6BDC)
                            )
                            Text(
                                "$currentPts / $nextPts",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3B6BDC)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            color = Color(0xFF3B6BDC),
                            trackColor = Color(0xFFE8EFFE)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                "Locked",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(lockedIcons) {
                    Image(
                        painter = painterResource(id = R.drawable.lockedimg),
                        contentDescription = "Locked",
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .alpha(0.4f)
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Timestamp): String =
    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(timestamp.toDate())