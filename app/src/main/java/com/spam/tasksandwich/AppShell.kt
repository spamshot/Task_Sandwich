package com.spam.tasksandwich

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import com.spam.tasksandwich.AppNavHost
import com.spam.tasksandwich.Screen

data class NavDrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    appShellViewModel: AppShellViewModel = viewModel()
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uiState by appShellViewModel.uiState.collectAsState()
    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var showJoinRoomDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.newlyCreatedRoomId, uiState.newlyJoinedRoomId) {
        uiState.newlyCreatedRoomId?.let { roomId ->
            navController.navigate(Screen.RoomDetail.createRoute(roomId))
            appShellViewModel.onRoomNavigationHandled()
        }
        uiState.newlyJoinedRoomId?.let { roomId ->
            showJoinRoomDialog = false
            navController.navigate(Screen.RoomDetail.createRoute(roomId))
            appShellViewModel.onRoomNavigationHandled()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        if (drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }
    }

    if (showCreateRoomDialog) {
        var roomName by remember { mutableStateOf("") }
        // --- OPTIONAL: Double Check Error here in case dialog opens anyway ---
        val errorText = if (!uiState.canCreateRoom) "Limit reached (Max 4 rooms)" else null

        AlertDialog(
            onDismissRequest = { showCreateRoomDialog = false },
            title = { Text("Create a New Room") },
            text = {
                Column {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { roomName = it },
                        label = { Text("Room Name") },
                        singleLine = true,
                        isError = errorText != null
                    )
                    if (errorText != null) {
                        Text(text = errorText, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appShellViewModel.createRoom(roomName)
                        showCreateRoomDialog = false
                    },
                    // Disable confirm button if limit reached
                    enabled = roomName.isNotBlank() && uiState.canCreateRoom
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRoomDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showJoinRoomDialog) {
        var joinCode by remember { mutableStateOf("") }
        val error by remember { derivedStateOf { uiState.error } }
        AlertDialog(
            onDismissRequest = {
                showJoinRoomDialog = false
                appShellViewModel.clearError()
            },
            title = { Text("Join a Room") },
            text = {
                Column {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { if (it.length <= 6) joinCode = it },
                        label = { Text("6-Digit Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = error != null
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { appShellViewModel.joinRoom(joinCode) },
                    enabled = joinCode.length == 6
                ) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJoinRoomDialog = false
                    appShellViewModel.clearError()
                }) { Text("Cancel") }
            }
        )
    }

    val drawerItems = listOf(
        NavDrawerItem(Screen.Home.route, "Home", Icons.Default.Home),
        NavDrawerItem(Screen.AddSelfTask.route, "Add Personal Task", Icons.Default.Person),
        NavDrawerItem(Screen.CreateRoom.route, "Create Room", Icons.Default.Check),
        NavDrawerItem(Screen.JoinRoom.route, "Join Room", Icons.Default.Home),
        NavDrawerItem(Screen.ProfileSettings.route, "Profile Settings", Icons.Default.Settings)
    )

    val currentScreenTitle = when (currentRoute) {
        Screen.Home.route -> "Home Dashboard"
        Screen.RoomDetail.route -> "Room Details"
        Screen.AddSelfTask.route -> "Add Personal Task"
        Screen.JoinRoom.route -> "Join a Room"
        Screen.ProfileSettings.route -> "Profile Settings"
        Screen.ManageTasks.route -> "Manage Tasks"
        Screen.CreateShopItem.route -> "Manage Shop Items"
        Screen.ViewShop.route -> "Room Shop"
        Screen.EditTask.route -> "Edit Task"
        else -> "Chores App"
    }

    val showNavigationUi = currentRoute !in listOf(
        Screen.Splash.route,
        Screen.Auth.route,
        Screen.ProfileSetup.route
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showNavigationUi,
        drawerContent = {
            ModalDrawerSheet {
                drawerItems.forEach { item ->

                    // --- SPAM LOGIC ---
                    // If this is the "Create Room" button AND the user cannot create more rooms,
                    // skip rendering this item completely.
                    if (item.route == Screen.CreateRoom.route && !uiState.canCreateRoom) {
                        return@forEach
                    }
                    // ------------------

                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            when (item.route) {
                                Screen.CreateRoom.route -> showCreateRoomDialog = true
                                Screen.JoinRoom.route -> showJoinRoomDialog = true
                                else -> {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }

                                }
                            }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (showNavigationUi) {
                    TopAppBar(
                        title = { Text(currentScreenTitle) },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            AppNavHost(
                navController = navController,
                paddingValues = paddingValues
            )
        }
    }
}