package com.spam.tasksandwich

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import com.google.firebase.Timestamp
import java.util.concurrent.TimeUnit


@Composable
fun HomeScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var roomToAction by remember { mutableStateOf<UserRoom?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog for Leave/Delete Confirmation
    if (roomToAction != null) {
        val room = roomToAction!!
        val title = if (room.isAdmin) "Delete Room" else "Leave Room"
        val text = if (room.isAdmin) {
            "You are the admin of this room. Deleting it will permanently remove it for all members. Are you sure?"
        } else {
            "Are you sure you want to leave the room '${room.groupName}'?"
        }
        val confirmText = if (room.isAdmin) "Delete" else "Leave"

        AlertDialog(
            onDismissRequest = { roomToAction = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        homeViewModel.leaveOrDeleteRoom(room)
                        roomToAction = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(confirmText) }
            },
            dismissButton = {
                TextButton(onClick = { roomToAction = null }) { Text("Cancel") }
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
        }
    }

    LaunchedEffect(uiState.createdRoomId) {
        uiState.createdRoomId?.let { roomId ->
            navController.navigate(Screen.RoomDetail.createRoute(roomId))
            homeViewModel.onRoomCreationHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                // FIX: Only show Empty State if BOTH Rooms AND Tasks are empty.
                // If you have a Self Task, it will skip this and go to the Dashboard.
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
                        onRoomClick = { roomId ->
                            navController.navigate(
                                Screen.RoomDetail.createRoute(
                                    roomId
                                )
                            )
                        },
                        onRoomLongPress = { room -> roomToAction = room }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeDashboard(
    uiState: HomeUiState,
    onCompleteTask: (Task) -> Unit,
    onRoomClick: (String) -> Unit,
    onRoomLongPress: (UserRoom) -> Unit
) {
    val totalTasks = uiState.groupedTasks.values.sumOf { it.size }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("My Rooms (${uiState.rooms.size})", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.rooms) { room ->
                // Check if this specific room is the one being deleted
                val isBeingDeleted = (uiState.roomBeingDeletedId == room.groupId)

                RoomCard(
                    room = room,
                    isBeingDeleted = isBeingDeleted, // Pass the status
                    onClick = { onRoomClick(room.groupId) },
                    onLongPress = { onRoomLongPress(room) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        Spacer(modifier = Modifier.height(24.dp))

        Text("My Tasks ($totalTasks)", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.groupedTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("You have no pending tasks! \n Lets add tasks from the top left", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            TaskList(groupedTasks = uiState.groupedTasks, onCompleteTask = onCompleteTask)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoomCard(
    room: UserRoom,
    isBeingDeleted: Boolean, // New parameter
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val cardColors = if (room.isAdmin) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 1. Change Opacity: Dim it if deleting (0.5f), normal otherwise (1.0f)
            .alpha(if (isBeingDeleted) 0.5f else 1f)
            .combinedClickable(
                // 2. Disable Clicks: Only clickable if NOT being deleted
                enabled = !isBeingDeleted,
                onClick = onClick,
                onLongClick = onLongPress
            ),
        colors = cardColors,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = room.groupName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Optional: Show a little loading spinner instead of points if deleting
            if (isBeingDeleted) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "${room.userPointsInRoom} pts",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (room.isAdmin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
fun EmptyStateProfile(
    userProfile: UserProfile?,
    onAddTaskClick: () -> Unit,
    onJoinRoomClick: () -> Unit,
    onCreateRoomClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome, ${userProfile?.name ?: "User"}!", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Total Group Points: ${userProfile?.totalPoints ?: 0}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Total Personal Points: ${userProfile?.totalSelfPoints ?: 0}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary // Use a different color to distinguish
        )

        Spacer(modifier = Modifier.height(48.dp))
        Text(
            "No task for today? Let's add one from settings!",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun TaskList(
    groupedTasks: Map<String, List<Task>>,
    onCompleteTask: (Task) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(groupedTasks.entries.toList()) { (assignerName, tasks) ->
            AssignerTaskGroup(
                assignerName = assignerName,
                tasks = tasks,
                onCompleteTask = onCompleteTask
            )
        }
    }
}

@Composable
fun AssignerTaskGroup(
    assignerName: String,
    tasks: List<Task>,
    onCompleteTask: (Task) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = assignerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    tasks.forEach { task ->
                        TaskItem(task = task, onCompleteClick = { onCompleteTask(task) })
                        HorizontalDivider(
                            Modifier,
                            DividerDefaults.Thickness,
                            DividerDefaults.color
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, onCompleteClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)

            Text("${task.points} Points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            CountdownTimer(dueDate = task.dueDate)
        }
        Button(onClick = onCompleteClick) {
            Text("Done")
        }
    }
}

@Composable
fun CountdownTimer(dueDate: Timestamp?) { //todo time isn't right
    if (dueDate == null) return
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }
    val timeLeftString = formatDuration(now, dueDate.toDate().time)
    Text(
        text = timeLeftString,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = if (timeLeftString == "Expired") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun formatDuration(now: Long, future: Long): String {
    val diff = future - now
    if (diff <= 0) {
        return "Expired"
    }
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

// C:\Users\kylan\AndroidStudioProjects\TaskSandwich