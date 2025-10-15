package com.spam.tasksandwich

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Settings
//import androidx.compose.material.icons.filled.Storefront

import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType


data class NavDrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * The main UI shell for the application. It contains the navigation drawer,
 * the top app bar, and the main content area where all other screens are displayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    appShellViewModel: AppShellViewModel = viewModel()
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val uiState by appShellViewModel.uiState.collectAsState()

    // --- State for the "Create Room" Dialog ---
    var showCreateRoomDialog by remember { mutableStateOf(false) }

    // This effect listens for a signal from the ViewModel. When a room is successfully
    // created, it triggers the navigation to the new room's detail screen.
    LaunchedEffect(uiState.newlyCreatedRoomId) {
        uiState.newlyCreatedRoomId?.let { roomId ->
            navController.navigate(Screen.RoomDetail.createRoute(roomId))
            appShellViewModel.onRoomCreationHandled() // Reset the event
        }
    }

    // This effect ensures the drawer is always closed after navigating to a new screen.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        if (drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }
    }

    // --- The "Create Room" Dialog Composable ---
    if (showCreateRoomDialog) {
        var roomName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateRoomDialog = false },
            title = { Text("Create a New Room") },
            text = {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        appShellViewModel.createRoom(roomName)
                        showCreateRoomDialog = false // Close the dialog
                    },
                    enabled = roomName.isNotBlank() // Disable button if name is empty
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRoomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // This is the single source of truth for what appears in the navigation drawer.
    val drawerItems = listOf(
        NavDrawerItem(Screen.Home.route, "Home", Icons.Default.Home),
        NavDrawerItem(Screen.AddSelfTask.route, "Add Personal Task", Icons.Default.Person),
        NavDrawerItem(Screen.CreateRoom.route, "Create Room", Icons.Default.Edit),
        NavDrawerItem(Screen.JoinRoom.route, "Join Room", Icons.Default.Warning),
        NavDrawerItem(Screen.ProfileSettings.route, "Profile Settings", Icons.Default.Settings)
    )

    // Dynamically set the title of the top app bar based on the current screen.
    val currentScreenTitle = when (currentRoute) {
        Screen.Home.route -> "Home Dashboard"
        Screen.RoomDetail.route -> "Room Details"
        Screen.AddSelfTask.route -> "Add Personal Task"
        Screen.JoinRoom.route -> "Join a Room"
        Screen.ProfileSettings.route -> "Profile Settings"
        Screen.AssignTask.route -> "Assign Task"
        Screen.CreateShopItem.route -> "Manage Shop"
        Screen.ViewShop.route -> "Room Shop"
        Screen.TaskSettings.route -> "Task Settings"
        else -> "Chores App" // A sensible default title
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
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (item.route == Screen.CreateRoom.route) {
                                // Instead of navigating, just show the dialog.
                                showCreateRoomDialog = true
                            } else {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
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