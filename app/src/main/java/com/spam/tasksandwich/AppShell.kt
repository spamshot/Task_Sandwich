package com.spam.tasksandwich

import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp


data class NavDrawerItem(
    val route: String,
    val label: String,
    // FIX 5: Any is used for type flexibility. Consider a sealed class in a future refactor:
    // sealed class DrawerIcon { data class Vector(val icon: ImageVector) : DrawerIcon()
    //                            data class Res(val resId: Int) : DrawerIcon() }
    val icon: Any
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

    // FIX 1: Split into two independent LaunchedEffects so both can react if both fire.
    LaunchedEffect(uiState.newlyCreatedRoomId) {
        uiState.newlyCreatedRoomId?.let { roomId ->
            navController.navigate(Screen.RoomDetail.createRoute(roomId))
            appShellViewModel.onRoomNavigationHandled()
        }
    }
    LaunchedEffect(uiState.newlyJoinedRoomId) {
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
            scope.launch { drawerState.close() }
        }
    }

    if (showCreateRoomDialog) {
        var roomName by remember { mutableStateOf("") }
        val errorText = if (!uiState.canCreateRoom) "Limit reached (Max 4 rooms)" else null

        AlertDialog(
            onDismissRequest = { showCreateRoomDialog = false },
            title = { Text("Create a New Room") },
            text = {
                Column {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = { if (it.length <= 17) roomName = it },
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
                    enabled = roomName.isNotBlank() && uiState.canCreateRoom
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRoomDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showJoinRoomDialog) {
        // joinCode state hoisted here so the TextField can read and update it
        var joinCode by remember { mutableStateOf("") }
        val error = uiState.error

        AlertDialog(
            onDismissRequest = {
                showJoinRoomDialog = false
                joinCode = ""
                appShellViewModel.clearError()
            },
            title = { Text("Join a Room") },
            text = {
                Column {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { input ->
                            // Only allow digits, max 6 characters
                            if (input.all { it.isDigit() } && input.length <= 6) {
                                joinCode = input
                            }
                        },
                        label = { Text("6-Digit Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = error != null
                    )
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
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
                    joinCode = ""
                    appShellViewModel.clearError()
                }) { Text("Cancel") }
            }
        )
    }

    val drawerItems = listOf(
        NavDrawerItem(Screen.Home.route, "Home", Icons.Default.Home),
        NavDrawerItem(Screen.ManageSelfTasks.route, "My Tasks", R.drawable.person_raised_hand_24px),
        NavDrawerItem(Screen.CreateRoom.route, "Create Room", R.drawable.groups_24px),
        NavDrawerItem(Screen.JoinRoom.route, "Join Room", R.drawable.group_add_24px),
        NavDrawerItem(Screen.ProfileSettings.route, "Profile Settings", Icons.Default.Settings)
    )

    // FIX 6: Use startsWith() for parameterized routes — exact match against a template
    // like "room_detail_screen/{roomId}" will never equal "room_detail_screen/abc123".
    val currentScreenTitle = when {
        currentRoute == Screen.Home.route -> "Home Dashboard"
        currentRoute?.startsWith("room_detail_screen/") == true -> "Room Details"
        currentRoute == Screen.ManageSelfTasks.route -> "My Tasks"
        currentRoute == Screen.ProfileSettings.route -> "Profile Settings"
        currentRoute?.startsWith("manage_tasks_screen/") == true -> "Manage Tasks"
        currentRoute?.startsWith("create_shop_item_screen/") == true -> "Manage Shop Items"
        currentRoute?.startsWith("view_shop_screen/") == true -> "Room Shop"
        currentRoute?.startsWith("edit_task_screen/") == true -> "Edit Task"
        else -> "Chores App"
    }

    // Screens with no shell chrome at all (no drawer, no top bar)
    val noShellRoutes = listOf(
        Screen.Splash.route,
        Screen.Auth.route,
        Screen.ProfileSetup.route
    )
    // Screens that have their OWN top bar with a back button — shell top bar
    // must be hidden so they don't stack. Drawer gesture also disabled since
    // these are detail screens the user navigated INTO, not top-level tabs.
    val hasOwnTopBar = listOf(
        "room_detail_screen/",
        "manage_tasks_screen/",
        "view_shop_screen/",
        "create_shop_item_screen/",
        "edit_task_screen/"
    ).any { currentRoute?.startsWith(it) == true }
            || currentRoute == Screen.ManageSelfTasks.route
            || currentRoute == Screen.ProfileSettings.route

    val showShellTopBar = currentRoute !in noShellRoutes && !hasOwnTopBar
    val showDrawerGesture = currentRoute !in noShellRoutes && !hasOwnTopBar

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showDrawerGesture,
        drawerContent = {
            ModalDrawerSheet {
                drawerItems.forEach { item ->
                    if (item.route == Screen.CreateRoom.route && !uiState.canCreateRoom) {
                        return@forEach
                    }
                    NavigationDrawerItem(
                        icon = {
                            when (val icon = item.icon) {
                                is ImageVector -> Icon(icon, contentDescription = null)
                                is Int -> Icon(painterResource(id = icon), contentDescription = null)
                                else -> {}
                            }
                        },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            when (item.route) {
                                Screen.CreateRoom.route -> showCreateRoomDialog = true
                                Screen.JoinRoom.route -> showJoinRoomDialog = true
                                else -> {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            // FIX 7: Save and restore state when switching drawer items.
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true // ✅ restores scroll position, form state etc.
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
                if (showShellTopBar) {
                    TopAppBar(
                        title = { Text(currentScreenTitle) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                paddingValues = innerPadding,
                onShowCreateRoomDialog = { showCreateRoomDialog = true },
                onShowJoinRoomDialog = { showJoinRoomDialog = true }
            )
        }
    }
}