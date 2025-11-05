package com.spam.tasksandwich



import android.R.id.tabs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
//    onEditRoomClick: () -> Unit,
    onCreateShopClick: () -> Unit,
    onViewShopClick: () -> Unit,
    onNavigateToManageTasks: () -> Unit,
    viewModel: RoomDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var memberToKick by remember { mutableStateOf<RoomMember?>(null) }
    var showEditRoomDialog by remember { mutableStateOf(false) }


    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Leaderboard", "Top Task") // Placeholder for the second tab

    // --- NEW: Confirmation Dialog ---
    if (memberToKick != null) {
        AlertDialog(
            onDismissRequest = { memberToKick = null }, // Dismiss if user clicks outside
            title = { Text("Kick Member") },
            text = { Text("Are you sure you want to kick ${memberToKick!!.name} from the room?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.kickMember(memberToKick!!.userId)
                        memberToKick = null // Close the dialog
                    }
                ) {
                    Text("Yes, Kick")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToKick = null }) {
                    Text("No, Cancel")
                }
            }
        )
    }

    // This effect will navigate back when the ViewModel signals the room is deleted.
    LaunchedEffect(uiState.isRoomDeleted) {
        if (uiState.isRoomDeleted) {
            onNavigateBack()
        }
    }

    // --- NEW: The Edit Room Dialog ---
    if (showEditRoomDialog) {
        EditRoomDialog(
            currentRoomName = uiState.roomName,
            onDismiss = { showEditRoomDialog = false },
            onSave = { newName ->
                viewModel.updateRoomName(newName)
                showEditRoomDialog = false
            },
            onDelete = {
                viewModel.deleteRoom()
                showEditRoomDialog = false
            }
        )
    }



    Scaffold(

    ) { paddingValues ->
        // Handle the loading state first
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Main content column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                    //Top "bar" with room details
                ) {
                    InfoBox(label = "Members", value = uiState.memberCount.toString())
                    InfoBox(label = "Room Code", value = uiState.joinCode, isPrimary = true)
                    InfoBox(label = "Earnable Pts", value = uiState.totalEarnablePoints.toString())
                }
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
                // Display the content for the selected tab
                when (selectedTabIndex) {
                    0 -> LeaderboardTabContent(
                        uiState = uiState,
                        onViewShopClick = onViewShopClick,
                        onNavigateToManageTasks = onNavigateToManageTasks,
                        onCreateShopClick = onCreateShopClick,
                        onMemberLongPress = { memberToKick = it }
                    )

                    1 -> TopTasksTab(topTasks = uiState.topTasks)
                }
            }
        }
    }
}

@Composable
fun LeaderboardTabContent(
    uiState: RoomDetailUiState,
    onViewShopClick: () -> Unit,
    onNavigateToManageTasks: () -> Unit,
    onCreateShopClick: () -> Unit,
    onMemberLongPress: (RoomMember) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Button(
                onClick = onViewShopClick,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Text("View Room Shop")
            }

            Text("Leaderboard", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items = uiState.members, key = { _, member -> member.userId }) { index, member ->
                    MemberListItem(
                        rank = index + 1,
                        member = member,
                        isFirstPlace = (index == 0),
                        onLongPress = { onMemberLongPress(member) }
                    )
                }
            }

            if (uiState.isAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onNavigateToManageTasks, modifier = Modifier.weight(1f)) {
                        Text("Manage Tasks")
                    }
                    Button(onClick = onCreateShopClick, modifier = Modifier.weight(1f)) {
                        Text("Manage Shop")
                    }
                }
            }
        }
    }
}

@Composable
fun TopTasksTab(topTasks: List<AggregatedTask>) {
    val completedTasks = topTasks
        .filter { it.pendingCount == 0 && it.completedAt != null }
        .sortedByDescending { it.completedAt }
        .take(7)

    val pendingTasks = topTasks
        .filter { it.pendingCount > 0 && it.createdAt != null }
        .sortedByDescending { it.createdAt }
        .take(7)

    if (topTasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tasks have been assigned in this room yet.")
        }
    } else {
        // Use a LazyColumn as the root for the entire tab. This is the most efficient
        // way to display multiple sections that might scroll off-screen.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Section 1: In Progress Card ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "In Progress",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                        Divider()
                        if (pendingTasks.isEmpty()) {
                            Box(Modifier.padding(12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("None")
                            }
                        } else {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                pendingTasks.forEach { task ->
                                    CompactTaskItem(task = task)
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }

            // --- Section 2: Completed Card ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "100% Completed",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                        Divider()
                        if (completedTasks.isEmpty()) {
                            Box(Modifier.padding(12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("None")
                            }
                        } else {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                completedTasks.forEach { task ->
                                    CompactTaskItem(task = task)
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactTaskItem(task: AggregatedTask) {
    // Calculate completion percentage
    val totalAssignments = task.completedCount + task.pendingCount
    val completionPercentage = if (totalAssignments > 0) {
        (task.completedCount.toFloat() / totalAssignments.toFloat() * 100).roundToInt()
    } else {
        0
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${task.points} pts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$completionPercentage%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (completionPercentage == 100) MaterialTheme.colorScheme.primary else LocalContentColor.current
        )
    }
}



@Composable
fun TopTaskCard(task: AggregatedTask) {
    var isExpanded by remember { mutableStateOf(false) }

    val totalAssignments = task.completedCount + task.pendingCount

    // Avoid division by zero if there are no assignments
    val completionPercentage = if (totalAssignments > 0) {
        (task.completedCount.toFloat() / totalAssignments.toFloat() * 100).roundToInt()
    } else { 0 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top, always-visible part
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${task.points} pts", style = MaterialTheme.typography.bodyMedium)
                }
                // --- NEW: Display the Percentage ---
                Text("$completionPercentage%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            // Expandable content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Divider()
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, "Completed", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Completed: ${task.completedCount}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, "Pending", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Pending: ${task.pendingCount}")
                    }

                    if (task.completedAt != null) {
                        // If a completion date exists, show it.
                        Text(
                            "100% Completed on: ${formatTimestamp(task.completedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // Otherwise, show the pending message.
                        Text(
                            "Pending 100% Completion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }


                    // --- NEW: Display the Timestamp ---
                    task.createdAt?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Created: ${formatTimestamp(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
private fun formatTimestamp(timestamp: Timestamp): String {
    return SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        .format(timestamp.toDate())
}


@Composable
fun InfoBox(label: String, value: String, isPrimary: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = if (isPrimary) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * A composable that displays a single member in the room list.
 */
@Composable
fun MemberListItem(
    rank: Int,
    member: RoomMember,
    isFirstPlace: Boolean,
    onLongPress: () -> Unit) {
//Change the color of the card based on the rank
    val cardColors = if (isFirstPlace) {
        CardDefaults.cardColors(
            // Use a distinct, theme-aware color for emphasis.
            containerColor = colorResource(id = R.color.first_greenLight), // Changes Card / box color
//            contentColor = colorResource(id = R.color.second_blueDark) // Changes name and rank color
        )
    } else {
        // Use the default card colors for everyone else.
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // --- NEW: Gesture Detection ---
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() })
            },
        colors = cardColors

    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Text(
                "#$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp) // Give it a fixed width for alignment
            )
            // Name
            Text(
                member.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f) // Name takes up the remaining space
            )
            // Points
            Text(
                "${member.totalPointsInGroup} pts",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * A new, dedicated composable for the Edit Room dialog.
 */
@Composable
fun EditRoomDialog(
    currentRoomName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var roomName by remember { mutableStateOf(currentRoomName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Room") },
        text = {
            Column {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                // The delete button is placed inside the dialog
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete This Room")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(roomName) },
                enabled = roomName.isNotBlank() && roomName != currentRoomName
            ) {
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