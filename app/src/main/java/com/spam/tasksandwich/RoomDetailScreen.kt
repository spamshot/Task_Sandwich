package com.spam.tasksandwich



import android.R.id.tabs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
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
import com.spam.tasksandwich.UserProfileCard


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onCreateShopClick: () -> Unit,
    onViewShopClick: () -> Unit,
    onNavigateToManageTasks: () -> Unit,
    viewModel: RoomDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var memberToKick by remember { mutableStateOf<RoomMember?>(null) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Leaderboard", "Top Task")

    // --- Kick Confirmation Dialog ---
    if (memberToKick != null) {
        AlertDialog(
            onDismissRequest = { memberToKick = null },
            title = { Text("Kick Member") },
            text = { Text("Are you sure you want to kick ${memberToKick!!.name} from the room?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.kickMember(memberToKick!!.userId)
                        memberToKick = null
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

    // This effect will navigate back when the ViewModel signals the room is deleted
    // (In case you add delete functionality back later, keeping this doesn't hurt)
    LaunchedEffect(uiState.isRoomDeleted) {
        if (uiState.isRoomDeleted) {
            onNavigateBack()
        }
    }

    if (uiState.selectedUserProfile != null || uiState.isLoadingProfileForDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUserProfileView() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUserProfileView() }) {
                    Text("Close")
                }
            },
            // The content of the dialog
            text = {
                if (uiState.isLoadingProfileForDialog) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    uiState.selectedUserProfile?.let { profile ->

                        val memberInRoom = uiState.members.find { it.userId == profile.uid }
                        val pointsInThisRoom = memberInRoom?.totalPointsInGroup ?: 0

                        UserProfileCard(
                            name = profile.name,
//                            email = profile.email ?: "",
                            pointsInRoom = pointsInThisRoom,
                            totalPoints = profile.totalPoints,
                            iconId = profile.selectedIconId ?: "avatar_1"
                        )
                    }
                }
            }
        )
    }

    Scaffold { paddingValues ->
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top "bar" with room details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
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

                when (selectedTabIndex) {
                    0 -> LeaderboardTabContent(
                        uiState = uiState,
                        onViewShopClick = onViewShopClick,
                        onNavigateToManageTasks = onNavigateToManageTasks,
                        onCreateShopClick = onCreateShopClick,
                        onMemberLongPress = { memberToKick = it },
                        onMemberClick = { member ->
                            viewModel.selectUserForProfileView(member.userId)
                        }
                    )

                    1 -> TopTasksTab(uiState = uiState)
                }
            }
        }
    }
}

@Composable
fun InfoBox(label: String, value: String, isPrimary: Boolean = false) {
    val backgroundColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.padding(4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

@Composable
fun MemberListItem(
    rank: Int,
    member: RoomMember,
    isFirstPlace: Boolean,
    isCurrentUser: Boolean,
    onLongPress: () -> Unit,
    onClick: () -> Unit
) {
    val cardColors = when {
        isFirstPlace -> CardDefaults.cardColors(containerColor = colorResource(id = R.color.first_greenLight))
        isCurrentUser -> CardDefaults.cardColors(containerColor = colorResource(id = R.color.second_bluePurple))
        else -> CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onTap = { onClick() }
                )
            },
        colors = cardColors
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$rank.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp)
            )
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${member.totalPointsInGroup} pts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun LeaderboardTabContent(
    uiState: RoomDetailUiState,
    onViewShopClick: () -> Unit,
    onNavigateToManageTasks: () -> Unit,
    onCreateShopClick: () -> Unit,
    onMemberLongPress: (RoomMember) -> Unit,
    onMemberClick: (RoomMember) -> Unit
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
                        isCurrentUser = member.userId == uiState.currentUserId,
                        onLongPress = {
                            // Only allow Admin to kick others (and not themselves)
                            if (uiState.isAdmin && member.userId != uiState.currentUserId) {
                                onMemberLongPress(member)
                            }
                        },
                        onClick = { onMemberClick(member) }
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
fun TopTasksTab(uiState: RoomDetailUiState) {
    val topTasks = uiState.topTasks
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "In Progress",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                        HorizontalDivider()
                        if (pendingTasks.isEmpty()) {
                            Box(Modifier.padding(12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("None")
                            }
                        } else {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                pendingTasks.forEach { task ->
                                    CompactTaskItem(task = task, isAdmin = uiState.isAdmin)
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "100% Completed",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                        HorizontalDivider()
                        if (completedTasks.isEmpty()) {
                            Box(Modifier.padding(12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("None")
                            }
                        } else {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                completedTasks.forEach { task ->
                                    CompactTaskItem(task = task, isAdmin = uiState.isAdmin)
                                    HorizontalDivider()
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
fun CompactTaskItem(task: AggregatedTask, isAdmin: Boolean) {
    var isExpanded by remember { mutableStateOf(false) }
    val totalAssignments = task.completedCount + task.pendingCount
    val completionPercentage = if (totalAssignments > 0) {
        (task.completedCount.toFloat() / totalAssignments.toFloat() * 100).roundToInt()
    } else {
        0
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
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
        AnimatedVisibility(visible = isExpanded && isAdmin) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)) {
                task.completions.forEach { completion ->
                    Text("${completion.userName}: ${completion.status}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

//Todo we no longer need delete room in this part, we don't have Edit room here any more