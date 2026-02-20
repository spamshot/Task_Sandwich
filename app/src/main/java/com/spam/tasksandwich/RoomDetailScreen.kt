package com.spam.tasksandwich




import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt


// Layout changes:
//   1. Dark top bar with room name, lock icon, and report icon
//      — room identity is immediately clear on entry.
//   2. InfoBox cramped SpaceAround row → full-width RoomStatsCard
//      with proper breathing room per stat.
//   3. Admin buttons (Manage Tasks / Manage Shop) moved into
//      RoomStatsCard so they are always visible, not buried at
//      the bottom of the leaderboard tab.
//   4. TabRow → pill-style tab selector (rounded, segmented).
//   5. "View Room Shop" plain button → gradient banner card.
//   6. "Leaderboard" plain Text → section header with member
//      count badge matching HomeScreen style.
//   7. MemberListItem: action icons hidden by default, revealed
//      on tap. Keeps rows clean. Long-press still kicks (admin).
//   8. Medal emojis for top 3 instead of hardcoded resource
//      colors. Gold card tint for 1st, blue tint for current user.
//   9. Progress bar added to CompactTaskItem in Top Tasks tab.
//  10. Fixed both sendReportToFirebase → sendReport calls.
// ============================================================

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
    val reportsUiState by reportsViewModel.uiState.collectAsState()

    var memberToKick by remember { mutableStateOf<RoomMember?>(null) }
    var memberToReport by remember { mutableStateOf<RoomMember?>(null) }
    var memberToCensorGlobal by remember { mutableStateOf<RoomMember?>(null) }
    var memberToCensorLocal by remember { mutableStateOf<RoomMember?>(null) }
    var isReportingRoom by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    // Auto-dismiss report dialogs on success
    LaunchedEffect(reportsUiState.reportSent) {
        if (reportsUiState.reportSent) {
            isReportingRoom = false
            memberToReport = null
            reportsViewModel.onReportSentHandled()
            Toast.makeText(context, "Report sent. Thank you!", Toast.LENGTH_SHORT).show()
        }
    }

    // Kick dialog
    if (memberToKick != null) {
        AlertDialog(
            onDismissRequest = { memberToKick = null },
            title = { Text("Kick Member") },
            text = { Text("Are you sure you want to kick ${memberToKick!!.name} from the room?") },
            confirmButton = {
                TextButton(onClick = { viewModel.kickMember(memberToKick!!.userId); memberToKick = null }) {
                    Text("Yes, Kick", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { memberToKick = null }) { Text("Cancel") } }
        )
    }

    // Censor globally dialog
    if (memberToCensorGlobal != null) {
        val isCensored = uiState.globallyCensoredUserIds.contains(memberToCensorGlobal!!.userId)
        AlertDialog(
            onDismissRequest = { memberToCensorGlobal = null },
            title = { Text(if (isCensored) "Uncensor" else "Censor Globally") },
            text = { Text("${if (isCensored) "Uncensor" else "Censor"} ${memberToCensorGlobal!!.name} for everyone in this room?") },
            confirmButton = {
                TextButton(onClick = { viewModel.toggleCensorUserGlobally(memberToCensorGlobal!!.userId); memberToCensorGlobal = null }) {
                    Text(if (isCensored) "Uncensor" else "Censor All")
                }
            },
            dismissButton = { TextButton(onClick = { memberToCensorGlobal = null }) { Text("Cancel") } }
        )
    }

    // Censor locally dialog
    if (memberToCensorLocal != null) {
        val isCensored = uiState.locallyCensoredUserIds.contains(memberToCensorLocal!!.userId)
        AlertDialog(
            onDismissRequest = { memberToCensorLocal = null },
            title = { Text(if (isCensored) "Show Name" else "Hide Name") },
            text = { Text("${if (isCensored) "Show" else "Hide"} ${memberToCensorLocal!!.name} for just you?") },
            confirmButton = {
                TextButton(onClick = { viewModel.toggleCensorUserLocally(memberToCensorLocal!!.userId); memberToCensorLocal = null }) {
                    Text(if (isCensored) "Show" else "Hide for Me")
                }
            },
            dismissButton = { TextButton(onClick = { memberToCensorLocal = null }) { Text("Cancel") } }
        )
    }

    // Room report dialog — FIX 10: uses sendReport
    if (isReportingRoom) {
        ReportDialog(
            itemContent = "Room Name: ${uiState.roomName}",
            isSubmitting = reportsUiState.isSubmitting,
            onDismiss = { isReportingRoom = false },
            onConfirm = {
                reportsViewModel.sendReport(
                    reportedContent = uiState.roomName,
                    reportType = "room_name",
                    roomId = roomId
                )
            }
        )
    }

    // Member report dialog — FIX 10: uses sendReport
    memberToReport?.let { member ->
        ReportDialog(
            itemContent = "User Name: ${member.name}",
            isSubmitting = reportsUiState.isSubmitting,
            onDismiss = { memberToReport = null },
            onConfirm = {
                reportsViewModel.sendReport(
                    reportedContent = member.name,
                    reportType = "user_name",
                    roomId = roomId
                )
            }
        )
    }

    // Profile dialog
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
                        UserProfileCard(
                            name = profile.name,
                            pointsInRoom = memberInRoom?.totalPointsInGroup ?: 0,
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

    // REDESIGN 1: TopAppBar owns the room name, lock icon, and report icon
    // so they appear in the actual app bar — not as a separate surface below it.
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.roomName.ifBlank { "Room" },
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Lock toggle — admin only, 3+ members
                    if (uiState.isAdmin && uiState.memberCount >= 3) {
                        IconButton(onClick = { viewModel.toggleRoomLock(uiState.isLocked) }) {
                            Icon(
                                imageVector = if (uiState.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (uiState.isLocked) "Unlock room" else "Lock room",
                                tint = if (uiState.isLocked) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    // Report room
                    IconButton(onClick = { isReportingRoom = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = "Report room",
                            tint = Color(0xFFF87171)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    scrolledContainerColor = Color(0xFF1A1A2E)
                )
            )
        }
    ) { scaffoldPadding ->
        if (uiState.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(scaffoldPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
            ) {
                // ── REDESIGN 2: Full-width stats card + admin buttons ──
                RoomStatsCard(
                    memberCount = uiState.memberCount,
                    joinCode = uiState.joinCode,
                    earnablePoints = uiState.totalEarnablePoints,
                    isAdmin = uiState.isAdmin,
                    onManageTasks = onNavigateToManageTasks,
                    onManageShop = onCreateShopClick
                )

                // ── REDESIGN 4: Pill-style tab selector ──
                RoomTabSelector(
                    selectedIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it }
                )

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTabIndex) {
                        0 -> LeaderboardTabContent(
                            uiState = uiState,
                            onViewShopClick = onViewShopClick,
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
}

// ============================================================
// RoomStatsCard — replaces cramped InfoBox SpaceAround row
// Admin buttons live here so they are always accessible
// ============================================================
@Composable
fun RoomStatsCard(
    memberCount: Int,
    joinCode: String,
    earnablePoints: Int,
    isAdmin: Boolean,
    onManageTasks: () -> Unit,
    onManageShop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Members
                StatBox(
                    label = "Members",
                    value = memberCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                // Join code — primary/hero stat
                StatBox(
                    label = "Join Code",
                    value = joinCode,
                    isPrimary = true,
                    modifier = Modifier.weight(1f)
                )
                // Earnable points
                StatBox(
                    label = "Earnable",
                    value = "$earnablePoints pts",
                    modifier = Modifier.weight(1f)
                )
            }

            // Admin action buttons inside the card
            if (isAdmin) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onManageTasks,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A1A2E)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("⚙ Tasks", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onManageShop,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🛍 Shop", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(
    label: String,
    value: String,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isPrimary) Color(0xFF1A1A2E) else MaterialTheme.colorScheme.surfaceVariant
    val labelColor = if (isPrimary) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = if (isPrimary) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================
// RoomTabSelector — pill-style segmented tabs
// ============================================================
@Composable
fun RoomTabSelector(selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("🏆 Leaderboard", "📋 Top Tasks")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedIndex == index
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onTabSelected(index) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================
// LeaderboardTabContent
// REDESIGN 5: Shop button → gradient banner
// REDESIGN 6: Plain "Leaderboard" text → section header + badge
// ============================================================
@Composable
fun LeaderboardTabContent(
    uiState: RoomDetailUiState,
    onViewShopClick: () -> Unit,
    onMemberLongPress: (RoomMember) -> Unit,
    onMemberClick: (RoomMember) -> Unit,
    onMemberReportClick: (RoomMember) -> Unit,
    onMemberCensorGlobal: (RoomMember) -> Unit,
    onMemberCensorLocal: (RoomMember) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // REDESIGN 5: Shop gradient banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable { onViewShopClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🛍", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "View Room Shop",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        // REDESIGN 6: Section header with member count badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Leaderboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${uiState.members.size} members",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(items = uiState.members, key = { _, m -> m.userId }) { index, member ->
                MemberListItem(
                    rank = index + 1,
                    member = member,
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
    }
}

// ============================================================
// MemberListItem
// REDESIGN 7: Actions hidden by default, revealed on tap
// REDESIGN 8: Medal emojis for top 3, gold/blue card tints
// ============================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemberListItem(
    rank: Int,
    member: RoomMember,
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
    var actionsVisible by remember { mutableStateOf(false) }

    val cardColor = when {
        rank == 1    -> Color(0xFFFFFBEB) // warm gold tint
        isCurrentUser -> Color(0xFFEFF6FF) // blue tint for current user
        else          -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        rank == 1     -> Color(0xFFFDE68A)
        isCurrentUser -> Color(0xFFBFDBFE)
        else          -> Color.Transparent
    }

    val rankLabel = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "$rank"
    }

    val displayName = when {
        isGloballyCensored || isLocallyCensored -> "**** (Censored)"
        else -> member.name
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (rank <= 1 || isCurrentUser) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = {
                    // Tap toggles actions for other users; tapping self opens profile
                    if (isCurrentUser) onClick()
                    else actionsVisible = !actionsVisible
                },
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(if (rank == 1) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Medal / rank
            Text(
                text = rankLabel,
                style = if (rank <= 3) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center
            )

            // Name
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                color = if (isGloballyCensored || isLocallyCensored)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // "YOU" chip
            if (isCurrentUser) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = "YOU",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // Points
            Text(
                text = "${member.totalPointsInGroup} pts",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (rank == 1) Color(0xFFD97706) else MaterialTheme.colorScheme.primary
            )

            // REDESIGN 7: Action icons revealed on tap, hidden by default
            if (!isCurrentUser && actionsVisible) {
                Spacer(Modifier.width(4.dp))
                // Hide/show locally
                IconButton(
                    onClick = { onCensorLocalClick(); actionsVisible = false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = "Hide name",
                        tint = if (isLocallyCensored) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Censor globally — admin only
                if (isAdmin) {
                    IconButton(
                        onClick = { onCensorGlobalClick(); actionsVisible = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Gavel,
                            contentDescription = "Censor globally",
                            tint = if (isGloballyCensored) MaterialTheme.colorScheme.error
                            else Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // Report
                IconButton(
                    onClick = { onReportClick(); actionsVisible = false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = "Report user",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ============================================================
// TopTasksTab — added progress bar to each task row
// ============================================================
@Composable
fun TopTasksTab(uiState: RoomDetailUiState) {
    val topTasks = uiState.topTasks
    val completedTasks = topTasks
        .filter { it.pendingCount == 0 && it.completedAt != null }
        .sortedByDescending { it.completedAt }
        .take(10)

    if (topTasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📋", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text("No tasks assigned yet.", style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Completed Tasks",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Text(
                                    text = "100% done",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                        if (completedTasks.isEmpty()) {
                            Box(
                                Modifier.padding(16.dp).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) { Text("None yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                completedTasks.forEachIndexed { i, task ->
                                    CompactTaskItem(task = task, isAdmin = uiState.isAdmin)
                                    if (i < completedTasks.lastIndex) HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// CompactTaskItem — REDESIGN 9: progress bar added
// ============================================================
@Composable
fun CompactTaskItem(task: AggregatedTask, isAdmin: Boolean) {
    var isExpanded by remember { mutableStateOf(false) }
    val total = task.completedCount + task.pendingCount
    val percent = if (total > 0) (task.completedCount.toFloat() / total * 100).roundToInt() else 0
    val progressColor = if (percent == 100) Color(0xFF16A34A) else Color(0xFFF59E0B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${task.points} pts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$percent%",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.bodyMedium,
                color = progressColor
            )
        }
        // REDESIGN 9: Progress bar
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        AnimatedVisibility(visible = isExpanded && isAdmin) {
            Column(Modifier.padding(start = 8.dp, top = 8.dp)) {
                task.completions.forEach {
                    Text(
                        "• ${it.userName}: ${it.status}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}