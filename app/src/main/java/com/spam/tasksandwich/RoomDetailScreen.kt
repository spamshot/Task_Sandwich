package com.spam.tasksandwich




import android.widget.Toast
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onCreateShopClick: () -> Unit,
    onViewShopClick: () -> Unit,
    onNavigateToManageTasks: () -> Unit,
    viewModel: RoomDetailViewModel = viewModel(),
    reportsViewModel: SendReportsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Dialog States
    var memberToKick by remember { mutableStateOf<RoomMember?>(null) }
    var memberToReport by remember { mutableStateOf<RoomMember?>(null) }
    var memberToCensorGlobal by remember { mutableStateOf<RoomMember?>(null) }
    var memberToCensorLocal by remember { mutableStateOf<RoomMember?>(null) }

    var isReportingRoom by remember { mutableStateOf(false) }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Leaderboard", "Top Task")

    // --- 1. Kick Member Confirmation Dialog ---
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
                ) { Text("Yes, Kick") }
            },
            dismissButton = {
                TextButton(onClick = { memberToKick = null }) { Text("No, Cancel") }
            }
        )
    }

// --- 2. Admin Global Censor/Uncensor Dialog ---
    if (memberToCensorGlobal != null) {
        val isCurrentlyCensored = uiState.globallyCensoredUserIds.contains(memberToCensorGlobal!!.userId)
        val actionText = if (isCurrentlyCensored) "Uncensor" else "Censor Globally"

        AlertDialog(
            onDismissRequest = { memberToCensorGlobal = null },
            title = { Text(actionText) },
            text = { Text("$actionText ${memberToCensorGlobal!!.name} for everyone in this room?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleCensorUserGlobally(memberToCensorGlobal!!.userId)
                    memberToCensorGlobal = null
                }) { Text(if (isCurrentlyCensored) "Uncensor" else "Censor All") }
            },
            dismissButton = { TextButton(onClick = { memberToCensorGlobal = null }) { Text("Cancel") } }
        )
    }


    // --- 3. User Local Censor/Uncensor Dialog ---
    if (memberToCensorLocal != null) {
        val isCurrentlyCensored = uiState.locallyCensoredUserIds.contains(memberToCensorLocal!!.userId)
        val actionText = if (isCurrentlyCensored) "Show Name" else "Hide Name"

        AlertDialog(
            onDismissRequest = { memberToCensorLocal = null },
            title = { Text(actionText) },
            text = { Text("$actionText ${memberToCensorLocal!!.name} for just you?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleCensorUserLocally(memberToCensorLocal!!.userId)
                    memberToCensorLocal = null
                }) { Text(if (isCurrentlyCensored) "Show" else "Hide for Me") }
            },
            dismissButton = { TextButton(onClick = { memberToCensorLocal = null }) { Text("Cancel") } }
        )
    }
    // --- 2. Report Room Dialog ---
    if (isReportingRoom) {
        ReportDialog(
            itemContent = "Room Name: ${uiState.roomName}",
            onDismiss = { isReportingRoom = false },
            onConfirm = {
                // 2. Call the reporting ViewModel
                reportsViewModel.sendReportToFirebase(
                    reporterId = uiState.currentUserId,
                    reportedContent = uiState.roomName,
                    reportType = "room_name",
                    roomId = roomId
                )
                Toast.makeText(context, "Room Name reported.", Toast.LENGTH_SHORT).show()
                isReportingRoom = false
            }
        )
    }

    // --- 3. Report Member Dialog ---
    memberToReport?.let { member ->
        ReportDialog(
            itemContent = "User Name: ${member.name}",
            onDismiss = { memberToReport = null },
            onConfirm = {
                // 3. Call the reporting ViewModel
                reportsViewModel.sendReportToFirebase(
                    reporterId = uiState.currentUserId,
                    reportedContent = member.name,
                    reportType = "user_name",
                    roomId = roomId
                )
                Toast.makeText(context, "User reported.", Toast.LENGTH_SHORT).show()
                memberToReport = null
            }
        )
    }

    // --- 4. User Profile View Dialog ---
    if (uiState.selectedUserProfile != null || uiState.isLoadingProfileForDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUserProfileView() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUserProfileView() }) { Text("Close") }
            },
            text = {
                if (uiState.isLoadingProfileForDialog) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    uiState.selectedUserProfile?.let { profile ->
                        val memberInRoom = uiState.members.find { it.userId == profile.uid }
                        val pointsInThisRoom = memberInRoom?.totalPointsInGroup ?: 0
                        UserProfileCard(
                            name = profile.name,
                            pointsInRoom = pointsInThisRoom,
                            totalPoints = profile.totalPoints,
                            iconId = profile.selectedIconId ?: "avatar_1"
                        )
                    }
                }
            }
        )
    }

    LaunchedEffect(uiState.isRoomDeleted) {
        if (uiState.isRoomDeleted) onNavigateBack()
    }

    Scaffold { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize()
                .padding(paddingValues)
                , contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- TOP BAR WITH ROOM INFO & REPORT FLAG ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        InfoBox(label = "Members", value = uiState.memberCount.toString())
                        InfoBox(label = "Room Code", value = uiState.joinCode, isPrimary = true)
                        InfoBox(label = "Earnable Pts", value = uiState.totalEarnablePoints.toString())
                        if (uiState.isAdmin && uiState.memberCount >= 3) { //For locking the room, must have 3 total members
                            IconButton(
                                onClick = { viewModel.toggleRoomLock(uiState.isLocked) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Toggle Lock",
                                    tint = if (uiState.isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                    }

                    // The Room Report Button
                    IconButton(onClick = { isReportingRoom = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = "Report Room",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                    }
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
                        onMemberClick = { viewModel.selectUserForProfileView(it.userId) },
                        onMemberReportClick = { memberToReport = it },
                        onMemberCensorGlobal = { memberToCensorGlobal = it },
                        onMemberCensorLocal = { memberToCensorLocal = it }
                    )
                    1 -> TopTasksTab(uiState = uiState)
                }
            }
        }
    }
}

@Composable
fun MemberListItem(
    rank: Int,
    member: RoomMember,
    isFirstPlace: Boolean,
    isCurrentUser: Boolean,
    isAdmin: Boolean,
    isGloballyCensored: Boolean,
    isLocallyCensored: Boolean,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
    onReportClick: () -> Unit,
    onCensorGlobalClick: () -> Unit,
    onCensorLocalClick: () -> Unit
) {
    val cardColors = when {
        isFirstPlace -> CardDefaults.cardColors(containerColor = colorResource(id = R.color.first_greenLight))
        isCurrentUser -> CardDefaults.cardColors(containerColor = colorResource(id = R.color.second_bluePurple))
        else -> CardDefaults.cardColors()
    }

    // If censored, we replace the name
    val displayName = if (isGloballyCensored || isLocallyCensored) {
        "**** (Censored)"
    } else {
        member.name
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onLongPress() }, onTap = { onClick() })
            },
        colors = cardColors
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$rank.", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                color = if (isGloballyCensored || isLocallyCensored) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else Color.Unspecified
            )

            Text(
                text = "${member.totalPointsInGroup} pts",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Hide ALL moderation icons for the current user's own row ---
            if (!isCurrentUser) {
                // 1. Local Censor Button
                IconButton(onClick = onCensorLocalClick) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = "Hide Name",
                        tint = if (isLocallyCensored) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 2. Admin Global Censor Button (Only visible if Admin is looking at someone else)
                if (isAdmin) {
                    IconButton(onClick = onCensorGlobalClick) {
                        Icon(
                            imageVector = Icons.Outlined.Gavel,
                            contentDescription = "Censor Globally",
                            tint = if (isGloballyCensored) MaterialTheme.colorScheme.error else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 3. Report Button
                IconButton(onClick = onReportClick) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = "Report",
                        modifier = Modifier.size(20.dp)
                    )
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
        onMemberLongPress: (RoomMember) -> Unit,
        onMemberClick: (RoomMember) -> Unit,
        onMemberReportClick: (RoomMember) -> Unit,
        onMemberCensorGlobal: (RoomMember) -> Unit,
        onMemberCensorLocal: (RoomMember) -> Unit

) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = onViewShopClick, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("View Room Shop")
        }

        Text("Leaderboard", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

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
                    isAdmin = uiState.isAdmin,
                    isGloballyCensored = uiState.globallyCensoredUserIds.contains(member.userId),
                    isLocallyCensored = uiState.locallyCensoredUserIds.contains(member.userId),
                    onLongPress = {
                        if (uiState.isAdmin && member.userId != uiState.currentUserId) onMemberLongPress(member)
                    },
                    onClick = { onMemberClick(member) },
                    onReportClick = { onMemberReportClick(member) },
                    onCensorGlobalClick = { onMemberCensorGlobal(member) },
                    onCensorLocalClick = { onMemberCensorLocal(member) }
                )
            }
        }

        if (uiState.isAdmin) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNavigateToManageTasks, modifier = Modifier.weight(1f)) { Text("Manage Tasks") }
                Button(onClick = onCreateShopClick, modifier = Modifier.weight(1f)) { Text("Manage Shop") }
                Spacer(Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

@Composable
fun InfoBox(label: String, value: String, isPrimary: Boolean = false) {
    val backgroundColor = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = Modifier.padding(4.dp), colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

@Composable
fun TopTasksTab(uiState: RoomDetailUiState) {
    val topTasks = uiState.topTasks
    val completedTasks = topTasks.filter { it.pendingCount == 0 && it.completedAt != null }.sortedByDescending { it.completedAt }.take(10)

    if (topTasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No tasks assigned yet.") }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("100% Completed", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        if (completedTasks.isEmpty()) {
                            Box(Modifier.padding(12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("None") }
                        } else {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
    val total = task.completedCount + task.pendingCount
    val percent = if (total > 0) (task.completedCount.toFloat() / total * 100).roundToInt() else 0

    Column {
        Row(modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = task.title, fontWeight = FontWeight.Bold)
                Text(text = "${task.points} pts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = "$percent%", fontWeight = FontWeight.Bold, color = if (percent == 100) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
        AnimatedVisibility(visible = isExpanded && isAdmin) {
            Column(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)) {
                task.completions.forEach { Text("${it.userName}: ${it.status}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}