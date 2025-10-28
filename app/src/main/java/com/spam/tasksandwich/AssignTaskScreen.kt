package com.spam.tasksandwich

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
    onTaskSaved: () -> Unit, // This is used for the "Go Back" action in the TopAppBar
    viewModel: AssignTaskViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- State for input fields ---
    var title by rememberSaveable { mutableStateOf("") }
    var points by rememberSaveable { mutableStateOf("") }

    // --- State for "Repeat" dropdown ---
    val repeatOptions = listOf("Never", "Every Day", "Once a Week", "Once a Month")
    var selectedRepeatOption by rememberSaveable { mutableStateOf(repeatOptions[0]) }
    var isRepeatDropdownExpanded by remember { mutableStateOf(false) }

    // --- State for "Expires In" dropdown ---
    val expiresInOptions = listOf(
        "Never" to 0, "1 Day" to 1, "2 Days" to 2, "3 Days" to 3,
        "4 Days" to 4, "5 Days" to 5, "6 Days" to 6, "7 Days" to 7
    )
    var selectedExpiration by rememberSaveable { mutableStateOf(expiresInOptions[0]) }
    var isExpirationDropdownExpanded by remember { mutableStateOf(false) }

    // --- State for "Assign To" dropdown ---
    var isAssigneeDropdownExpanded by remember { mutableStateOf(false) }
    var selectedAssignee by remember { mutableStateOf<RoomMember?>(null) }
    val assigneeOptions = remember(uiState.members) {
        listOf(RoomMember(userId = "all", name = "All Members")) + uiState.members
    }

    // --- Business Logic in UI ---
    // The expiration dropdown is only enabled if the task does not repeat.
    val isExpirationEnabled = selectedRepeatOption == "Never"

    // This effect ensures that if a user selects a repeating task, the expiration is reset to "Never".
    LaunchedEffect(selectedRepeatOption) {
        if (!isExpirationEnabled) {
            selectedExpiration = expiresInOptions[0]
        }
    }

    // This effect shows a snackbar and clears the form upon successful save.
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            scope.launch { snackbarHostState.showSnackbar("Task(s) assigned successfully!") }
            viewModel.onSaveHandled()
            title = ""
            points = ""
            selectedAssignee = null
            selectedRepeatOption = repeatOptions[0]
            selectedExpiration = expiresInOptions[0]
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()), // Makes the form scrollable
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
                        modifier = Modifier.fillMaxWidth().menuAnchor()
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

                // Expires In Dropdown
                ExposedDropdownMenuBox(
                    expanded = isExpirationDropdownExpanded,
                    onExpandedChange = { if (isExpirationEnabled) isExpirationDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedExpiration.first,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expires In") },
                        enabled = isExpirationEnabled, // Visually grays out if disabled
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpirationDropdownExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isExpirationDropdownExpanded,
                        onDismissRequest = { isExpirationDropdownExpanded = false }
                    ) {
                        expiresInOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.first) },
                                onClick = {
                                    selectedExpiration = option
                                    isExpirationDropdownExpanded = false
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
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
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

                // Spacer to push the button to the bottom
                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        viewModel.saveTask(
                            title = title,
                            pointsStr = points,
                            repeatOption = selectedRepeatOption,
                            assignedTo = selectedAssignee,
                            expiresInDays = selectedExpiration.second
                        )

                    },
                    enabled = !uiState.isSaving && selectedAssignee != null && title.isNotBlank() && points.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Assign Task")
                }
            }
        }
    }
}