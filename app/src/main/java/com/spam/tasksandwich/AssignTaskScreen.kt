package com.spam.tasksandwich

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskScreen(
    onTaskSaved: () -> Unit,
    viewModel: AssignTaskViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var points by rememberSaveable { mutableStateOf("") }
    val repeatOptions = listOf("Never", "Every Day", "Once a Week", "Once a Month")
    var selectedRepeatOption by rememberSaveable { mutableStateOf(repeatOptions[0]) }

    //stat for drop down 2
    var isRepeatDropdownExpanded by remember { mutableStateOf(false) }


    // State for the new dropdown
    var isAssigneeDropdownExpanded by remember { mutableStateOf(false) }
    var selectedAssignee by remember { mutableStateOf<RoomMember?>(null) }

    // Create the list for the dropdown, including the "All Members" option
    val assigneeOptions = remember(uiState.members) {
        listOf(RoomMember(userId = "all", name = "All Members")) + uiState.members
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            scope.launch { snackbarHostState.showSnackbar("Task(s) assigned successfully!") }
            viewModel.onSaveHandled()
            title = ""
            points = ""
            selectedAssignee = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assign New Task") },
                navigationIcon = {
                    IconButton(onClick = onTaskSaved) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = points,
                    // --- FIX 1: Corrected the typo ---
                    onValueChange = { points = it },
                    label = { Text("Points") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                // Repeat Dropdown
                ExposedDropdownMenuBox(
                    expanded = isRepeatDropdownExpanded,
                    onExpandedChange = { isRepeatDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedRepeatOption,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRepeatDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isRepeatDropdownExpanded,
                        onDismissRequest = { isRepeatDropdownExpanded = false }
                    ) {
                        repeatOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    selectedRepeatOption = option
                                    isRepeatDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Assignee Dropdown
                ExposedDropdownMenuBox(
                    expanded = isAssigneeDropdownExpanded,
                    onExpandedChange = { isAssigneeDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedAssignee?.name ?: "Select Member",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Assign To") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAssigneeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    // --- FIX 2: Used the correct state variable ---
                    ExposedDropdownMenu(
                        expanded = isAssigneeDropdownExpanded,
                        onDismissRequest = { isAssigneeDropdownExpanded = false }
                    ) {
                        assigneeOptions.forEach { member ->
                            DropdownMenuItem(
                                text = { Text(member.name) },
                                onClick = {
                                    selectedAssignee = member
                                    isAssigneeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = { viewModel.saveTask(title, points, selectedRepeatOption, selectedAssignee) },
                    enabled = !uiState.isSaving && selectedAssignee != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Assign Task")
                }
            }
        }
    }
}