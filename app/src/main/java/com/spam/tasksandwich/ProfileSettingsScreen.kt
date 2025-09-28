package com.spam.tasksandwich

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.collections.mapOf
import com.spam.tasksandwich.R


@Composable
fun ProfileSettingsScreen(
    onLogoutSuccess: () -> Unit,
    viewModel: ProfileSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Local state for the text fields and icon, which will be populated from the ViewModel.
    // Using `remember` here is fine because LaunchedEffect will update them when the profile loads.
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedIconId by remember { mutableStateOf("") }

    // This effect populates the form fields once the user's profile is loaded from Firestore.
    LaunchedEffect(uiState.userProfile) {
        uiState.userProfile?.let {
            name = it.name
            age = it.age?.toString() ?: ""
            email = it.email ?: ""
            selectedIconId = it.selectedIconId ?: "avatar_1" // Provide a default
        }
    }

    // This effect triggers the navigation callback when the ViewModel confirms a successful logout.
    LaunchedEffect(uiState.logoutSuccess) {
        if (uiState.logoutSuccess) {
            onLogoutSuccess()
        }
    }

    // This effect shows a confirmation snackbar when a profile save is successful.
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            scope.launch {
                snackbarHostState.showSnackbar("Profile saved successfully!")
            }
            viewModel.onSaveHandled() // Reset the event state
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        // Show a loading indicator while the profile is being fetched.
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Main content column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()), // Make the column scrollable
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Selector Section
                Text("Choose your Icon", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                IconSelector(
                    selectedIconId = selectedIconId,
                    onIconSelected = { selectedIconId = it }
                )
                Spacer(Modifier.height(24.dp))

                // User Info Text Fields
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Age") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())

                // Spacer to push the buttons to the bottom of the screen
                Spacer(Modifier.weight(1f))

                // Display error messages from the ViewModel
                if (uiState.error != null) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))

                // Action Buttons Section
                Button(
                    onClick = { viewModel.saveProfile(name, age, email, selectedIconId) },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Save Changes")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { viewModel.logout() }) {
                    Text("Logout", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * A composable that displays a scrollable row of selectable preset icons.
 * This version uses a compile-time safe map to access drawable resources.
 */
@Composable
fun IconSelector(selectedIconId: String, onIconSelected: (String) -> Unit) {
    // A map that links string identifiers to their compile-time safe resource IDs (R.drawable...).
    // This is the recommended approach to avoid runtime reflection.
    val presetIconMap = remember {
        mapOf(
            "avatar_1" to R.drawable.carrotdog,
            "avatar_2" to R.drawable.firehairguy,
            "avatar_3" to R.drawable.dallebabyface,
            "avatar_4" to R.drawable.vgfbhbluehair,
            "avatar_5" to R.drawable.fglasses
        )
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(presetIconMap.entries.toList()) { (iconId, resId) ->
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable { onIconSelected(iconId) } // The callback uses the string identifier
                    .border(
                        width = if (selectedIconId == iconId) 3.dp else 0.dp,
                        color = if (selectedIconId == iconId) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                Image(
                    painter = painterResource(id = resId), // The image uses the safe R.drawable ID
                    contentDescription = "$iconId icon",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}