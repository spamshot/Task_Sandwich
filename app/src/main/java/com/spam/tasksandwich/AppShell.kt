package com.spam.tasksandwich

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Create
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

// Data class to represent an item in our Navigation Drawer
data class NavDrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell() {
    val navController = rememberNavController()
    // --- THIS IS THE FIX ---
    // We explicitly tell the drawer to start in the "Closed" state.
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // ----------------------
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route


    LaunchedEffect(currentRoute) {
        // If the drawer is open when we navigate, close it.
        if (drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        }
    }

    // Define the items that will appear in the navigation drawer
    val drawerItems = listOf(
        NavDrawerItem(Screen.Home.route, "Home", Icons.Default.Home),
        NavDrawerItem(
            Screen.AddSelfTask.route,
            "Add Personal Task",
            Icons.Default.AccountBox
        ),
        NavDrawerItem(
            Screen.JoinRoom.route,
            "Join Room",
            Icons.Default.AccountBox
        ),
        NavDrawerItem(
            Screen.ProfileSettings.route,
            "Profile Settings",
            Icons.Default.Settings
        ),
//        NavDrawerItem(
//            Screen.CreateRoom.route,
//            "Create Room",
//            Icons.Default.Create
//        ),

    )

    // Determine the current screen title based on the route
    val currentScreenTitle = when (currentRoute) {
        Screen.Home.route -> "Home Dashboard"
        Screen.RoomDetail.route -> "Room Details"
        Screen.AddSelfTask.route -> "Add Personal Task"
        Screen.JoinRoom.route -> "Join a Room"
        else -> "Chores App" // Default title
    }

    // Decide whether to show the drawer and top bar (e.g., not on Splash/Login)
    val showNavigationDrawer = currentRoute !in listOf(
        Screen.Splash.route,
        Screen.Auth.route,
        Screen.ProfileSetup.route
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showNavigationDrawer, // Only allow swiping if the drawer is shown
        drawerContent = {
            if (showNavigationDrawer) {
                ModalDrawerSheet {
                    // Your drawer's content goes here
                    drawerItems.forEach { item ->
                        NavigationDrawerItem(
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(item.route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large stack of destinations
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (showNavigationDrawer) {
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
            // Our existing AppNavHost goes here, inside the Scaffold's content area
            AppNavHost(
                navController = navController,
                paddingValues = paddingValues
            )
        }
    }
}