package com.spam.tasksandwich

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSelfTaskScreen(
    addSelfTaskViewModel: AddSelfTaskViewModel = viewModel(),
    onGoBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by addSelfTaskViewModel.uiState.collectAsState()

    // --- Local UI State ---
    var title by rememberSaveable { mutableStateOf("") }
    var points by rememberSaveable { mutableStateOf("") }
    val repeatOptions = listOf("Never", "Every Day", "Once a Week", "Once a Month")
    var selectedRepeatOption by rememberSaveable { mutableStateOf(repeatOptions[0]) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // For showing the "Task Saved!" snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- Side Effects ---
    LaunchedEffect(uiState.saveResult) {
        if (uiState.saveResult is SaveResult.SuccessAndStay) {
            scope.launch {
                snackbarHostState.showSnackbar("Task Saved!")
            }
            // Clear the input fields for the next task
            title = ""
            points = ""
            selectedRepeatOption = repeatOptions[0]
            addSelfTaskViewModel.resetSaveState() // Reset state after handling
        }
    }

    // --- UI Layout ---
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Surface(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Add a Personal Task", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(32.dp))

                // Task Name Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Points Field
                OutlinedTextField(
                    value = points,
                    onValueChange = { points = it },
                    label = { Text("Points") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Repeat Option Dropdown
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedRepeatOption,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        repeatOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedRepeatOption = option
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                // Error Message Display
                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Save Button
                Button(
                    onClick = {
                        addSelfTaskViewModel.saveTask(title, points, selectedRepeatOption)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text("Save and Add Another")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        // This button DOES NOT save. It just triggers the navigation callback.
                        onGoBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading // Also disable while saving
                ) {
                    Text("Go Back")
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Task Settings Button, This is more of A Task Log
                //todo doesn't make much sense to have a button for this here
                //Change to tabs and only show personal task
                TextButton(
                    onClick = { onNavigateToSettings() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Task Settings")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            }
    }
}