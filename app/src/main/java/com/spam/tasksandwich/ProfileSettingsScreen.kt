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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager



@Composable
fun ProfileSettingsScreen(
    onLogoutSuccess: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: ProfileSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // State to manage which tab is currently selected
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Settings", "Transaction Log", "Task History")

    // This effect handles the navigation callback on successful logout.
    LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) {
            onLogoutSuccess()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        // Display the content for the currently selected tab
        when (selectedTabIndex) {
            0 -> ProfileSettingsForm(uiState = uiState, viewModel = viewModel)
            1 -> TransactionLogList(history = uiState.purchaseHistory)
            2 -> TaskHistoryList(uiState = uiState, viewModel = viewModel, onNavigateToEdit = onNavigateToEdit)
        }
    }
}

/**
 * The content for the "Settings" tab, containing the user profile form.
 */
@Composable
fun ProfileSettingsForm(uiState: ProfileSettingsUiState, viewModel: ProfileSettingsViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Form State
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedIconId by remember { mutableStateOf("") }

    // --- NEW: Logout Dialog State ---
    var showLogoutDialog by remember { mutableStateOf(false) }

    // This effect populates the form fields once the user's profile is loaded.
    LaunchedEffect(uiState.userProfile?.uid) {
        uiState.userProfile?.let {
            name = it.name
            age = it.age?.toString() ?: ""
            email = it.email ?: ""
            selectedIconId = it.selectedIconId ?: "avatar_1"
        }
    }

    // This effect shows a confirmation snackbar when a profile save is successful.
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            scope.launch {
                snackbarHostState.showSnackbar("Profile saved successfully!")
            }
            viewModel.onSaveHandled()
        }
    }

    // --- NEW: Confirmation Dialog ---
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Choose your Icon", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                IconSelector(
                    userProfile = uiState.userProfile, // Pass the full profile
                    selectedIconId = selectedIconId,
                    onIconSelected = { selectedIconId = it }
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())

                uiState.userProfile?.let {
                    UserProfileCard(
                        name = it.name,
//                        email = it.email ?: "",
                        pointsInRoom = 0,
                        totalPoints = it.totalPoints,
                        iconId = it.selectedIconId?: "avatar_1"
                    )
                }

                Spacer(Modifier.weight(1f)) // Pushes buttons to the bottom

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.saveProfile(name, age, email, selectedIconId) },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Save Changes")
                }
                Spacer(Modifier.height(8.dp))

                // --- UPDATED BUTTON ---
                TextButton(
                    onClick = { showLogoutDialog = true } // Triggers the dialog
                ) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }

            }
        }
    }
}

/**
 * The content for the "Transaction Log" tab, displaying a list of past purchases.
 */
@Composable
fun TransactionLogList(history: List<UserPurchaseLogItem>) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No purchase history found.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(items = history, key = { it.id }) { purchase ->
                TransactionHistoryItem(purchase = purchase)
                Divider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryList(
    uiState: ProfileSettingsUiState,
    viewModel: ProfileSettingsViewModel,
    onNavigateToEdit: (String) -> Unit
) {
    //todo Empty History goes here

    var taskToAction by remember { mutableStateOf<Task?>(null) }

    Scaffold(
    ) { paddingValues ->

        if (uiState.tasks.isEmpty()){
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No history found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.isLoading) {

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.tasks) { task ->
                    TaskLogItem(
                        task = task,
                        onLongPress = { taskToAction = task }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskLogItem(task: Task, onLongPress: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Points: ${task.points}", style = MaterialTheme.typography.bodyMedium)
            Text("Repeats: ${task.repeatOption}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * A Row that displays a single item in the user's transaction history.
 */
@Composable
fun TransactionHistoryItem(purchase: UserPurchaseLogItem) {

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val pointsText: String
    val pointsColor: Color

    when (purchase.status) {
        "completed" -> {
            pointsText = "-${purchase.itemCost} pts"
            pointsColor = colorResource(id = R.color.approved_red_dark)
        }
        "refunded" -> {
            pointsText = "+${purchase.itemCost} pts" // Show a plus for refunds
            pointsColor = colorResource(id = R.color.refund_green_dark) // Use a positive color (like green/blue)
        }
        else -> { // "pending"
            pointsText = "-+${purchase.itemCost} pts"
            pointsColor =  colorResource(id = R.color.pending_blue_dark)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "'${purchase.itemName}' from ${purchase.roomName}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            if (purchase.mysteryText.isNotBlank() && purchase.status == "completed") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = purchase.mysteryText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    // The new Copy Icon Button
                    IconButton(
                        modifier = Modifier.size(20.dp), // Make the button small and unobtrusive
                        onClick = {
                            // 1. Copy the text to the clipboard
                            clipboardManager.setText(AnnotatedString(purchase.mysteryText))
                            // 2. Show a confirmation message to the user
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.content_copy_24px),
                            contentDescription = "Copy Mystery Text",
                            tint = MaterialTheme.colorScheme.primary
                        )

                    }
                }
            }
            purchase.purchasedAt?.let {
                Text(
                    formatTimestamp(it), // Helper function to format the date
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (purchase.status == "pending") {
                Text(
                    "Status: Pending Approval",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = pointsText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = pointsColor
        )
    }
}

/**
 * A composable that displays a scrollable row of selectable preset icons.
 */


@Composable
fun IconSelector(
    // The unlockedIconIds parameter is no longer needed, as we get it from the userProfile
    userProfile: UserProfile?,
    selectedIconId: String,
    onIconSelected: (String) -> Unit
) {
    if (userProfile == null) return // Guard against null profile

    // 1. Get the lists and maps from our single source of truth, the IconRepository.
    val defaultIcons = IconRepository.DefaultIconIds
    val milestoneIcons = IconRepository.MilestoneIconsMap
    val allIconsMap = IconRepository.AllIconsMap
    val roleIconsMap = IconRepository.RoleIconsMap



    // --- NEW LOGIC TO BUILD THE LIST ---
    // 2. Start with a mutable list to dynamically build the available icons.
    val availableIcons = mutableListOf<String>()
    val specialIcons = roleIconsMap[userProfile.role]
    if (specialIcons != null) {
        // If a list is found, add all of its icons.
        availableIcons.addAll(specialIcons)
    }

    // 3. If the user has the "super_admin" role, add the special admin icon first.
//    if (userProfile.role == "super_admin") {
//        availableIcons.add(IconRepository.ADMIN_ICON_1)
//
//    }

    // 4. Add the default icons.
    availableIcons.addAll(defaultIcons)

    // 5. Add the icons the user has unlocked through achievements.
    availableIcons.addAll(userProfile.unlockedIconIds)

    // Use .distinct() to create the final list, removing any duplicates.
    val finalAvailableIcons = availableIcons.distinct()
    // --- END OF NEW LOGIC ---

    val lockedIcons = milestoneIcons.keys.filter { it !in userProfile.unlockedIconIds }

    Column {
        Text("Unlocked", style = MaterialTheme.typography.titleSmall)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Use the new, dynamically built list.
            items(items = finalAvailableIcons, key = { it }) { iconId ->
                val resId = allIconsMap[iconId]
                if (resId != null) {
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = "$iconId icon",
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
            Spacer(Modifier.height(16.dp)) // Changed from 4.dp for better spacing
            Text("Unlockable", style = MaterialTheme.typography.titleSmall)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(lockedIcons) { iconId ->
                    Image(
                        painter = painterResource(id = R.drawable.lockedimg),
                        contentDescription = "Locked Icon for $iconId",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

// Helper function to format a Timestamp into a readable date string.
private fun formatTimestamp(timestamp: Timestamp): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(timestamp.toDate())
}
