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
import androidx.navigation.navArgument
//import com.spam.tasksandwich.CreateRoomScreen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavHostController
import androidx.navigation.navArgument



// Fixes:
//   1. ManageShopScreen is registered at the CreateShopItem route —
//      the route is named "create_shop_item_screen" but it actually
//      opens the full ManageShopScreen (which handles both create
//      and edit). The route name is misleading. Renamed the Screen
//      object to ManageShop for clarity.
//   2. EditTask composable is a placeholder Box with text — this is
//      fine for development but is easy to forget. Noted with a TODO.
//   3. RoomDetailScreen passes roomId via both the arguments bundle
//      AND as a direct parameter — it's extracted from the bundle
//      manually and re-passed. This is fine but the ViewModel already
//      gets it from SavedStateHandle, so passing it as a parameter
//      to the composable is redundant if RoomDetailScreen just passes
//      it through. Noted with a comment.
//   4. AppNavHost receives paddingValues from AppShell but AppShell
//      manually reduces bottom padding to 0.dp. This means bottom
//      system insets (nav bar) may not be respected on gesture-nav
//      devices. The proper approach is to pass innerPadding fully
//      and let the NavHost content handle it. Flagged in AppShell
//      (see fix in that file).
//   5. Screen.CreateRoom and Screen.JoinRoom are defined as routes
//      but are handled as dialog triggers inside AppShell — they
//      never actually navigate to standalone screens. If a deep link
//      ever targets these routes, it would crash or show nothing.
//      Noted with a comment.
//   6. ManageTasks.arguments is a val on the companion — but the
//      rest of the Screen objects don't define arguments this way,
//      creating inconsistency. Minor but noted.
// ============================================================

sealed class Screen(val route: String) {
    object Splash : Screen("splash_screen")
    object Auth : Screen("auth_screen")
    object ProfileSetup : Screen("profile_setup_screen")
    object Home : Screen("home_screen")
    object ManageSelfTasks : Screen("manage_self_tasks_screen")
    // FIX 5: JoinRoom and CreateRoom are intercepted as dialog triggers in AppShell.
    // These routes are never navigated to directly — they exist only as drawer item keys.
    object JoinRoom : Screen("join_room_screen")
    object CreateRoom : Screen("create_room_screen")
    object ProfileSettings : Screen("profile_settings_screen")

    object ManageTasks : Screen("manage_tasks_screen/{roomId}") {
        fun createRoute(roomId: String) = "manage_tasks_screen/$roomId"
        val arguments = listOf(navArgument("roomId") { type = NavType.StringType })
    }

    // FIX 1: Renamed from CreateShopItem to ManageShop to reflect actual behavior.
    // The route string is kept the same to avoid breaking SavedStateHandle reads in the VM.
    object ManageShop : Screen("create_shop_item_screen/{roomId}") {
        fun createRoute(roomId: String) = "create_shop_item_screen/$roomId"
    }

    object RoomDetail : Screen("room_detail_screen/{roomId}") {
        fun createRoute(roomId: String) = "room_detail_screen/$roomId"
    }

    object EditTask : Screen("edit_task_screen/{taskId}") {
        fun createRoute(taskId: String) = "edit_task_screen/$taskId"
    }

    object ViewShop : Screen("view_shop_screen/{roomId}") {
        fun createRoute(roomId: String) = "view_shop_screen/$roomId"
    }
}

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
            ProfileSettingsScreen(
                onLogoutSuccess = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onNavigateToEdit = { taskId ->
                    navController.navigate(Screen.EditTask.createRoute(taskId))
                }
            )
        }

        composable(Screen.ManageSelfTasks.route) {
            ManageSelfTasksScreen(
                onGoBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RoomDetail.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            // FIX 3: roomId is extracted here and passed to the composable.
            // RoomDetailViewModel already reads it from SavedStateHandle, so passing
            // it as a composable parameter is only needed if the composable itself
            // uses it (e.g., to pass to child composables). Review if this is necessary.
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            RoomDetailScreen(
                roomId = roomId,
                onNavigateBack = { navController.popBackStack() },
                onCreateShopClick = { navController.navigate(Screen.ManageShop.createRoute(roomId)) }, // FIX 1
                onViewShopClick = { navController.navigate(Screen.ViewShop.createRoute(roomId)) },
                onNavigateToManageTasks = { navController.navigate(Screen.ManageTasks.createRoute(roomId)) }
            )
        }

        composable(
            route = Screen.EditTask.route,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) {
            // FIX 2: TODO — Replace this placeholder with the real EditTask screen.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Edit Task Screen (Coming Soon)")
            }
        }

        composable(
            route = Screen.ManageTasks.route,
            arguments = Screen.ManageTasks.arguments
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            ManageTasksScreen(
                roomId = roomId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ViewShop.route,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) {
            ViewShopScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // FIX 1: Route name kept as-is for SavedStateHandle compatibility.
        composable(
            route = Screen.ManageShop.route, // was Screen.CreateShopItem
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) {
            ManageShopScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}