package com.spam.tasksandwich

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialogDefaults.containerColor
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import com.google.firebase.Timestamp
import java.util.concurrent.TimeUnit


// Changes from original:
//   1. MyRoomGroup replaced with RoomsRow — horizontal LazyRow
//      of compact chips. Saves vertical space, rooms are glanceable.
//   2. Removed heavy Spacer+Divider+Spacer between sections.
//   3. HomeDashboard is now a single LazyColumn (no nested scroll).
//   4. Greeting header with user name and task count summary.
//   5. TaskItem Done button uses theme colors (dark mode safe).
//   6. Points badge uses secondaryContainer for visible pill.
//   7. RoomChip padding 8dp -> 14dp, shows admin crown emoji.
//   8. EmptyStateProfile now has real action buttons.
//   9. Expired timer gets a red error chip instead of plain text.
//  10. AssignerTaskGroup header shows task count badge.
// ============================================================

@Composable
fun HomeScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var roomToAction by remember { mutableStateOf<UserRoom?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (roomToAction != null) {
        val room = roomToAction!!
        val title = if (room.isAdmin) "Delete Room" else "Leave Room"
        val text = if (room.isAdmin)
            "You are the admin of this room. Deleting it will permanently remove it for all members. Are you sure?"
        else
            "Are you sure you want to leave '${room.groupName}'?"
        val confirmText = if (room.isAdmin) "Delete" else "Leave"
        AlertDialog(
            onDismissRequest = { roomToAction = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = { homeViewModel.leaveOrDeleteRoom(room); roomToAction = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(confirmText) }
            },
            dismissButton = { TextButton(onClick = { roomToAction = null }) { Text("Cancel") } }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) homeViewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long) }
    }
    LaunchedEffect(uiState.createdRoomId) {
        uiState.createdRoomId?.let { roomId ->
            navController.navigate(Screen.RoomDetail.createRoute(roomId))
            homeViewModel.onRoomCreationHandled()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { scaffoldPadding ->
        Surface(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.rooms.isEmpty() && uiState.groupedTasks.isEmpty() -> {
                    EmptyStateProfile(
                        userProfile = uiState.userProfile,
                        onAddTaskClick = { navController.navigate(Screen.ManageSelfTasks.route) },
                        onJoinRoomClick = { navController.navigate(Screen.JoinRoom.route) },
                        onCreateRoomClick = { homeViewModel.createRoom("My New Room") }
                    )
                }
                else -> {
                    HomeDashboard(
                        uiState = uiState,
                        onCompleteTask = { task -> homeViewModel.markTaskComplete(task) },
                        onRoomClick = { roomId -> navController.navigate(Screen.RoomDetail.createRoute(roomId)) },
                        onRoomLongPress = { room -> roomToAction = room }
                    )
                }
            }
        }
    }
}

// ============================================================
// HomeDashboard - single LazyColumn, greeting header
// ============================================================
@Composable
fun HomeDashboard(
    uiState: HomeUiState,
    onCompleteTask: (Task) -> Unit,
    onRoomClick: (String) -> Unit,
    onRoomLongPress: (UserRoom) -> Unit
) {
    val totalTasks = uiState.groupedTasks.values.sumOf { it.size }
    val firstName = uiState.userProfile?.name?.split(" ")?.firstOrNull() ?: "there"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Greeting
        item {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp)) {
                Text(
                    text = "Hey, $firstName",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (totalTasks > 0)
                        "You have $totalTasks task${if (totalTasks != 1) "s" else ""} waiting"
                    else "All caught up!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Rooms row
        if (uiState.rooms.isNotEmpty()) {
            item {
                RoomsRow(
                    rooms = uiState.rooms,
                    roomBeingDeletedId = uiState.roomBeingDeletedId,
                    onRoomClick = onRoomClick,
                    onRoomLongPress = onRoomLongPress
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        // Tasks header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Tasks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (totalTasks > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$totalTasks",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Tasks empty state
        if (uiState.groupedTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No pending tasks!", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Add tasks from the menu on the top left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(uiState.groupedTasks.entries.toList()) { (assignerName, tasks) ->
                AssignerTaskGroup(
                    assignerName = assignerName,
                    tasks = tasks,
                    onCompleteTask = onCompleteTask
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ============================================================
// RoomsRow - replaces MyRoomGroup collapsible card
// ============================================================
@Composable
fun RoomsRow(
    rooms: List<UserRoom>,
    roomBeingDeletedId: String?,
    onRoomClick: (String) -> Unit,
    onRoomLongPress: (UserRoom) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Rooms",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = Color(0xFFE0F7F4)
            ) {
                Text(
                    text = "${rooms.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0A9E89),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = rooms, key = { it.groupId }) { room ->
                RoomChip(
                    room = room,
                    isBeingDeleted = roomBeingDeletedId == room.groupId,
                    onClick = { onRoomClick(room.groupId) },
                    onLongPress = { onRoomLongPress(room) }
                )
            }
        }
    }
}

// ============================================================
// RoomChip - replaces RoomCard, more compact with better padding
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoomChip(
    room: UserRoom,
    isBeingDeleted: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val containerColor = if (room.isAdmin)
        MaterialTheme.colorScheme.primaryContainer
    else
        Color(0xFFF0F0F0)

    val contentColor = if (room.isAdmin)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        Color(0xFF444444)

    Card(
        modifier = Modifier
            .width(150.dp)
            .alpha(if (isBeingDeleted) 0.5f else 1f)
            .combinedClickable(
                enabled = !isBeingDeleted,
                onClick = onClick,
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (room.isAdmin) {
                Text(
                    text = "Admin",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = room.groupName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            if (isBeingDeleted) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Text(
                    text = "${room.userPointsInRoom} pts",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

// ============================================================
// AssignerTaskGroup - task count badge added to header
// ============================================================
@Composable
fun AssignerTaskGroup(
    assignerName: String,
    tasks: List<Task>,
    onCompleteTask: (Task) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(true) }
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "Arrow Animation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp) // aligns with section headers above
            .animateContentSize(),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = assignerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = "${tasks.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotationState)
                )
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tasks.forEach { task ->
                        TaskItem(task = task, onCompleteClick = { onCompleteTask(task) })
                    }
                }
            }
        }
    }
}

// ============================================================
// TaskItem - theme-aware Done button, secondaryContainer badge
// ============================================================
@Composable
fun TaskItem(task: Task, onCompleteClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                    ) {
                        Text(
                            text = "${task.points} pts",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    CountdownTimer(dueDate = task.dueDate)
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onCompleteClick,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Done", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ============================================================
// CountdownTimer - expired gets a red chip
// ============================================================
@Composable
fun CountdownTimer(dueDate: Timestamp?) {
    if (dueDate == null) return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }
    val timeLeftString = formatDuration(now, dueDate.toDate().time)
    if (timeLeftString == "Expired") {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Text(
                text = "Expired",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    } else {
        Text(
            text = timeLeftString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================
// EmptyStateProfile - now has real action buttons
// ============================================================
@Composable
fun EmptyStateProfile(
    userProfile: UserProfile?,
    onAddTaskClick: () -> Unit,
    onJoinRoomClick: () -> Unit,
    onCreateRoomClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome, ${userProfile?.name ?: "there"}!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = "${userProfile?.totalPoints ?: 0} group pts",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = "${userProfile?.totalSelfPoints ?: 0} personal pts",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(48.dp))
        Text("Get started", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAddTaskClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add a personal task")
            }
            OutlinedButton(onClick = onJoinRoomClick, modifier = Modifier.fillMaxWidth()) {
                Text("Join a room with a code")
            }
            OutlinedButton(onClick = onCreateRoomClick, modifier = Modifier.fillMaxWidth()) {
                Text("Create a new room")
            }
        }
    }
}

private fun formatDuration(now: Long, future: Long): String {
    val diff = future - now
    if (diff <= 0) return "Expired"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
    return when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h ${minutes}m left"
        minutes > 0 -> "${minutes}m left"
        else -> "< 1m left"
    }
}


//-Admin
//fake3@gmail.com
//Password123!!!

//New User
//fake88@gmail.com
//Password123!!!