package com.spam.tasksandwich

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
//import com.spam.tasksandwich.CreateRoomScreen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavHostController



sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Auth : Screen("auth_screen")
    object ProfileSetup : Screen("profile_setup_screen")
    object Home : Screen("home_screen")
    object AddSelfTask : Screen("add_self_task_screen")
    object JoinRoom : Screen("join_room_screen")
    object ProfileSettings : Screen("profile_settings_screen")
    object TaskSettings : Screen("task_settings_screen")
    object CreateRoom : Screen("create_room_screen")


    object AssignTask : Screen("assign_task_screen/{roomId}") {
        fun createRoute(roomId: String) = "assign_task_screen/$roomId"
    }
    object CreateShopItem : Screen("create_shop_item_screen/{roomId}") {
        fun createRoute(roomId: String) = "create_shop_item_screen/$roomId"
    }

    object RoomDetail : Screen("room_detail_screen/{roomId}") {
        fun createRoute(roomId: String) = "room_detail_screen/$roomId"
    }

    object EditTask : Screen("edit_task_screen/{taskId}") {
        fun createRoute(taskId: String) = "edit_task_screen/$taskId"
    }
    object ViewShop : Screen("view_shop_screen/{roomId}") { // Renamed for clarity
        fun createRoute(roomId: String) = "view_shop_screen/$roomId"
    }
}

    /**
     * The main navigation component for the app. It is now a modular component
     * that is placed inside the AppShell.
     *
     * @param navController The NavHostController that manages navigation, passed from AppShell.
     * @param paddingValues The padding provided by the Scaffold in AppShell, to avoid content
     *                      overlapping with the TopAppBar.
     */
    @Composable
    fun AppNavHost(
        navController: NavHostController,
        paddingValues: PaddingValues
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(navController = navController)
            }

            composable(Screen.Auth.route) {
                AuthScreen(onAuthSuccess = {
                    navController.navigate(Screen.Splash.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                })
            }

            composable(Screen.ProfileSetup.route) {
                ProfileSetupScreen(onProfileSaved = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.ProfileSetup.route) { inclusive = true }
                    }
                })
            }

            composable(Screen.Home.route) {
                HomeScreen(navController = navController)
            }

            composable(Screen.ProfileSettings.route) {
                ProfileSettingsScreen(onLogoutSuccess = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                })
            }

            composable(Screen.AddSelfTask.route) {
                AddSelfTaskScreen(
                    onGoBack = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Screen.TaskSettings.route) }
                )
            }

            composable(Screen.TaskSettings.route) {
                TaskSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { taskId ->
                        navController.navigate(Screen.EditTask.createRoute(taskId))
                    }
                )
            }

            composable(Screen.JoinRoom.route) {
                JoinRoomScreen(onJoinSuccess = { roomId ->
                    navController.navigate(Screen.RoomDetail.createRoute(roomId)) {
                        popUpTo(Screen.JoinRoom.route) { inclusive = true }
                    }
                })
            }

//            composable(Screen.CreateRoom.route) {
//                CreateRoomScreen(
//                    onRoomCreated = { roomId ->
//                        // This is the new, safer navigation logic.
//                        navController.navigate(Screen.RoomDetail.createRoute(roomId)) {
//                            // Pop up to the CreateRoom screen and remove it from the back stack.
//                            // This prevents the user from pressing "back" and ending up
//                            // on the form to create the same room again.
//                            popUpTo(Screen.CreateRoom.route) {
//                                inclusive = true
//                            }
//                        }
//                    }
//                )
//            }


            composable(
                route = Screen.RoomDetail.route,
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                RoomDetailScreen(
                    roomId = roomId,
                    onNavigateBack = { navController.popBackStack() },
                    onEditRoomClick = { navController.navigate(Screen.AssignTask.createRoute(roomId)) },
                    onCreateShopClick = { navController.navigate(Screen.CreateShopItem.createRoute(roomId)) },
                    // Wire up the new button to the correct navigation action
                    onViewShopClick = { navController.navigate(Screen.ViewShop.createRoute(roomId)) }
                )
            }

            composable(
                route = Screen.EditTask.route,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Edit Task Screen (Placeholder)")
                }
            }

            composable(
                route = Screen.AssignTask.route,
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) {
                AssignTaskScreen(
                    onTaskSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.ViewShop.route,
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) {
                // We will build this screen next
                ViewShopScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.CreateShopItem.route,
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) {
                CreateShopItemScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }