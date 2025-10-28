package com.spam.tasksandwich

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // When the screen RESUMES (i.e., you navigate back to it)
            if (event == Lifecycle.Event.ON_RESUME) {
                // Call the refresh function on the ViewModel.
                homeViewModel.onResume()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        // When the composable is disposed, remove the observer.
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    // This side effect handles the navigation event after a user creates a new room.
    LaunchedEffect(uiState.createdRoomId) {
        uiState.createdRoomId?.let { roomId ->
            navController.navigate(Screen.RoomDetail.createRoute(roomId))
            homeViewModel.onRoomCreationHandled() // Reset the event to prevent re-navigation
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        // If the user has not joined or created any rooms, show the initial welcome screen.
        else if (uiState.rooms.isEmpty()) {
            EmptyStateProfile(
                userProfile = uiState.userProfile,
                onAddTaskClick = { navController.navigate(Screen.AddSelfTask.route) },
                onJoinRoomClick = { navController.navigate(Screen.JoinRoom.route) },
                onCreateRoomClick = { homeViewModel.createRoom("My New Room")}
                // The create room action is now handled by the drawer

            )
        }
        // If the user is part of one or more rooms, show the main dashboard view.
        else {
            HomeDashboard(
                uiState = uiState,
                onCompleteTask = { task -> homeViewModel.markTaskComplete(task) },
                onRoomClick = { roomId -> navController.navigate(Screen.RoomDetail.createRoute(roomId)) }
            )
        }
    }
}
@Composable
fun HomeDashboard(
    uiState: HomeUiState,
    onCompleteTask: (Task) -> Unit,
    onRoomClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // --- ROOMS SECTION ---
        Text("My Rooms", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        // This LazyColumn will only grow up to 200.dp in height.
        LazyColumn(
            modifier = Modifier.heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.rooms) { room ->
                RoomCard(room = room, onClick = { onRoomClick(room.groupId) })
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
        Spacer(modifier = Modifier.height(24.dp))

        // --- TASKS SECTION ---
        Text("My Tasks", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (uiState.groupedTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("You have no pending tasks! \n Add task from the top left", style = MaterialTheme.typography.bodyLarge)

            }
        } else {
            // This reuses the TaskList composable we built in a previous step.
            TaskList(groupedTasks = uiState.groupedTasks, onCompleteTask = onCompleteTask)
        }
    }
}
@Composable
fun RoomCard(room: UserRoom, onClick: () -> Unit) {
    val cardColors = if (room.isAdmin) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                modifier = Modifier.weight(1f) // Takes up available space
            )

            // Points on the right
            Text(
                text = "${room.userPointsInRoom} pts",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = if (room.isAdmin) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
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
        // User Welcome and Profile Info
        Text("Welcome, ${userProfile?.name ?: "User"}!", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Total Points: ${userProfile?.totalPoints ?: 0}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Action Buttons Section
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
                    text = assignerName, // AssignerName is Room name aka "Room 2"
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
            Text(
                "${task.points} Points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            // Add the new CountdownTimer composable here
            CountdownTimer(dueDate = task.dueDate)
        }
        Button(onClick = onCompleteClick) {
            Text("Done")
        }
    }
}

// --- NEW: Countdown Timer Composable ---
/**
 * A self-updating composable that displays the time remaining until a due date.
 * It recomposes itself every minute to show a live countdown.
 */
@Composable
fun CountdownTimer(dueDate: Timestamp?) {
    // If there's no due date, compose nothing.
    if (dueDate == null) return

    // This state holds the *current* time, and we'll update it on a timer.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // This is the core of the timer. LaunchedEffect runs a coroutine that
    // can be suspended without blocking the UI.
    LaunchedEffect(Unit) {
        while (true) {
            // Wait for one minute. (delay is a suspend function)
            delay(60_000L) // 60,000 milliseconds = 1 minute
            // Update the 'now' state, which will trigger a recomposition of this composable.
            now = System.currentTimeMillis()
        }
    }

    // Calculate the formatted duration string based on the current time.
    val timeLeftString = formatDuration(now, dueDate.toDate().time)

    Text(
        text = timeLeftString,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        // Use a different color to indicate status
        color = if (timeLeftString == "Expired") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// --- NEW: Helper Function for Formatting ---
/**
 * Formats a duration in milliseconds into a human-readable string like "2d 5h left".
 */
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