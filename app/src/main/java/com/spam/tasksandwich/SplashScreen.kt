package com.spam.tasksandwich

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController

@Composable
fun SplashScreen(
    navController: NavController,
    splashViewModel: SplashViewModel = viewModel()
) {
    val authState by splashViewModel.authState.collectAsState()

    // LaunchedEffect will run once when the composable enters the screen
    // and re-run if the `authState` key changes.
    LaunchedEffect(authState) {
        // We don't want to navigate while still loading
        if (authState is AuthState.Loading) return@LaunchedEffect

        // Determine the destination based on the state
        val destination = when (authState) {
            is AuthState.AuthenticatedAndProfileComplete -> Screen.Home.route
            is AuthState.AuthenticatedButProfileIncomplete -> Screen.ProfileSetup.route
            is AuthState.Unauthenticated -> Screen.Auth.route
            else -> return@LaunchedEffect // Still loading
        }

        // Navigate to the correct screen and clear the back stack
        // so the user can't press "back" to the splash screen.
        navController.navigate(destination) {
            popUpTo(Screen.Splash.route) {
                inclusive = true
            }
        }
    }

    // The UI for the splash screen is just a loading indicator
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}