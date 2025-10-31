package com.spam.tasksandwich

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import kotlin.collections.mapOf
import com.spam.tasksandwich.R
import java.text.SimpleDateFormat
import java.util.Locale


@Composable
fun ProfileSettingsScreen(
    onLogoutSuccess: () -> Unit,
    viewModel: ProfileSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // State to manage which tab is currently selected
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Settings", "Transaction Log")

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

    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedIconId by remember { mutableStateOf("") }

    // This effect populates the form fields once the user's profile is loaded.
    LaunchedEffect(uiState.userProfile) {
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
                    selectedIconId = selectedIconId,
                    onIconSelected = { selectedIconId = it }
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.weight(1f)) // Pushes buttons to the bottom

                if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.saveProfile(name, age, email, selectedIconId) },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Save Changes")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.logout() }) {
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

/**
 * A Row that displays a single item in the user's transaction history.
 */
@Composable
fun TransactionHistoryItem(purchase: UserPurchaseLogItem) {
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
            purchase.purchasedAt?.let {
                Text(
                    formatTimestamp(it), // Helper function to format the date
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "-${purchase.itemCost} pts",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * A composable that displays a scrollable row of selectable preset icons.
 */
@Composable
fun IconSelector(selectedIconId: String, onIconSelected: (String) -> Unit) {
    val presetIconMap = remember {
        mapOf(
            "avatar_1" to R.drawable.carrotdog,
            "avatar_2" to R.drawable.dallebabyface,
            "avatar_3" to R.drawable.fglasses,
            "avatar_4" to R.drawable.firehairguy,
            "avatar_5" to R.drawable.vgfbhbluehair
        )
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(presetIconMap.entries.toList()) { (iconId, resId) ->
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable { onIconSelected(iconId) }
                    .border(
                        width = if (selectedIconId == iconId) 3.dp else 0.dp,
                        color = if (selectedIconId == iconId) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = "$iconId icon",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// Helper function to format a Timestamp into a readable date string.
private fun formatTimestamp(timestamp: Timestamp): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(timestamp.toDate())
}