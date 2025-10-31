package com.spam.tasksandwich



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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spam.tasksandwich.ui.theme.TaskSandwichTheme

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.CheckboxDefaults.colors
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.colorResource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onEditRoomClick: () -> Unit,
    onCreateShopClick: () -> Unit,
    onViewShopClick: () -> Unit,
    viewModel: RoomDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var memberToKick by remember { mutableStateOf<RoomMember?>(null) }
    var showEditRoomDialog by remember { mutableStateOf(false) }

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
//        topBar = {
//            TopAppBar(
//                title = { Text(uiState.roomName) },
//                navigationIcon = {
//                    IconButton(onClick = onNavigateBack) {
//                        Icon(
//                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
//                            contentDescription = "Go Back"
//                        )
//                    } },
//                actions = {
//                    if (uiState.isAdmin) {
//                        IconButton(onClick = { showEditRoomDialog = true }) {
//                            Icon(Icons.Default.Edit, contentDescription = "Edit Room")
//                        }
//                    }
//                }
//            )
//        }
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
                ) {
                    InfoBox(label = "Members", value = uiState.memberCount.toString())
                    InfoBox(label = "Room Code", value = uiState.joinCode, isPrimary = true)
                    InfoBox(label = "Earnable Pts", value = uiState.totalEarnablePoints.toString())
                }


                Button(
                    onClick = onViewShopClick,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("View Room Shop")
                }
                Spacer(modifier = Modifier.height(24.dp))

                // LEADERBOARD
                Text("Leaderboard", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f), // Allow the list to take up available space
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Use itemsIndexed to get both the index (for rank) and the member data
                    itemsIndexed(uiState.members, key = { _, member -> member.userId }) { index, member ->
                        MemberListItem(
                            rank = index + 1,
                            member = member,
                            // Pass a boolean flag if this is the first item in the list.
                            isFirstPlace = (index == 0),
                            onLongPress = {
                                memberToKick = member
                            }
                        )
                    }
                }
                // Admin Buttons
                if (uiState.isAdmin) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onEditRoomClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Manage Tasks")
                    }
                    OutlinedButton(onClick = onCreateShopClick, modifier = Modifier.fillMaxWidth()) {
                        Text("Manage Shop")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

        }
    }
}@Composable
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
