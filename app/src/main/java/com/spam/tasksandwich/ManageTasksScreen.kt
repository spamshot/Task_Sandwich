package com.spam.tasksandwich

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTasksScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManageTasksViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // State to manage which tab is currently selected
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Assign New", "Manage Assigned")

    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("Manage Tasks") },
//                navigationIcon = {
//                    IconButton(onClick = onNavigateBack) {
//                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go Back")
//                    }
//                }
//            )
//        }
    ) { paddingValues ->

        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            // The content of the selected tab
            when (selectedTabIndex) {
                0 -> AssignTaskForm(uiState = uiState, viewModel = viewModel)
                1 -> AssignedTasksList(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

/**
 * The content for the "Assign New" tab, containing the form to create new tasks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignTaskForm(uiState: ManageTasksUiState, viewModel: ManageTasksViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var points by rememberSaveable { mutableStateOf("") }
    val repeatOptions = listOf("Never", "Every Day", "Once a Week", "Once a Month")
    var selectedRepeatOption by rememberSaveable { mutableStateOf(repeatOptions[0]) }
    var isRepeatDropdownExpanded by remember { mutableStateOf(false) }

    val expiresInOptions = listOf(
        "Never" to 0, "1 Day" to 1, "2 Days" to 2, "3 Days" to 3,
        "4 Days" to 4, "5 Days" to 5, "6 Days" to 6, "7 Days" to 7
    )
    var selectedExpiration by rememberSaveable { mutableStateOf(expiresInOptions[0]) }
    var isExpirationDropdownExpanded by remember { mutableStateOf(false) }

    var isAssigneeDropdownExpanded by remember { mutableStateOf(false) }
    var selectedAssignee by remember { mutableStateOf<RoomMember?>(null) }
    val assigneeOptions = remember(uiState.members) {
        listOf(RoomMember(userId = "all", name = "All Members")) + uiState.members
    }

    val isExpirationEnabled = selectedRepeatOption == "Never"
    LaunchedEffect(selectedRepeatOption) {
        if (!isExpirationEnabled) {
            selectedExpiration = expiresInOptions[0]
        }
    }

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

    // A nested Scaffold is used here to provide a SnackbarHost just for this form.
    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = points, onValueChange = { points = it }, label = { Text("Points") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            // Repeat Dropdown
            ExposedDropdownMenuBox(
                expanded = isRepeatDropdownExpanded,
                onExpandedChange = { isRepeatDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedRepeatOption,
                    onValueChange = {}, // onValueChange is empty because it's a read-only field
                    readOnly = true,
                    label = { Text("Repeat") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isRepeatDropdownExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor() // This modifier is crucial for the dropdown to anchor correctly
                )

                // This is the content of the dropdown menu itself
                ExposedDropdownMenu(
                    expanded = isRepeatDropdownExpanded,
                    onDismissRequest = { isRepeatDropdownExpanded = false } // Close when clicking outside
                ) {
                    // Create a menu item for each option in our list
                    repeatOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedRepeatOption = option // Update the state with the new selection
                                isRepeatDropdownExpanded = false // Close the menu
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))



            // Expires In Dropdown
            ExposedDropdownMenuBox(
                expanded = isExpirationDropdownExpanded,
                // The dropdown can only be expanded if it's enabled
                onExpandedChange = { if (isExpirationEnabled) isExpirationDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedExpiration.first, // Display the string part of the Pair (e.g., "1 Day")
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Expires In") },
                    // This visually grays out the text field when the "Repeat" option is not "Never"
                    enabled = isExpirationEnabled,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpirationDropdownExpanded)
                    },

                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                // This is the content of the dropdown menu itself
                ExposedDropdownMenu(
                    expanded = isExpirationDropdownExpanded,
                    onDismissRequest = { isExpirationDropdownExpanded = false } // Close when clicking outside
                ) {
                    // Create a menu item for each option in our list of Pairs
                    expiresInOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.first) }, // Display the string part
                            onClick = {
                                selectedExpiration = option // Update the state with the selected Pair
                                isExpirationDropdownExpanded = false // Close the menu
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
                    // Display the selected member's name, or "Select Member" if none is chosen yet.
                    value = selectedAssignee?.name ?: "Select Member",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Assign To") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAssigneeDropdownExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor() // Anchors the dropdown to this text field
                )

                // This is the content of the dropdown menu itself
                ExposedDropdownMenu(
                    expanded = isAssigneeDropdownExpanded,
                    onDismissRequest = { isAssigneeDropdownExpanded = false } // Close when clicking outside
                ) {
                    // Create a menu item for each option in our assigneeOptions list
                    // (which includes "All Members" plus the actual members)
                    assigneeOptions.forEach { member ->
                        DropdownMenuItem(
                            text = { Text(member.name) },
                            onClick = {
                                selectedAssignee = member // Update the state with the selected RoomMember object
                                isAssigneeDropdownExpanded = false // Close the menu
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    viewModel.saveTask(
                        title = title, pointsStr = points, repeatOption = selectedRepeatOption,
                        assignedTo = selectedAssignee, expiresInDays = selectedExpiration.second
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
/**
 * The content for the "Manage Assigned" tab, showing a list of active tasks.
 */
@Composable
fun AssignedTasksList(uiState: ManageTasksUiState, viewModel: ManageTasksViewModel) {
    val groupedTasks = remember(uiState.assignedTasks) {
        uiState.assignedTasks.groupBy { it.sharedTaskId ?: it.id }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (groupedTasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tasks are currently assigned.")
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = groupedTasks.entries.toList(), key = { it.key }) { (_, tasks) ->
                val representativeTask = tasks.first()
                AssignedTaskCard(
                    task = representativeTask,
                    assignedCount = tasks.size,
                    onDelete = {
                        tasks.forEach { taskToDelete ->
                            viewModel.deleteTask(taskToDelete.id)
                        }
                    }
                )
            }
        }
    }
}

/**
 * A card for displaying a single assigned task in the management list.
 */
@Composable
fun AssignedTaskCard(task: Task, assignedCount: Int, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Points: ${task.points}", style = MaterialTheme.typography.bodyMedium)
            Text("Repeats: ${task.repeatOption}", style = MaterialTheme.typography.bodyMedium)
            task.dueDate?.let {
                Text("Expires: ${formatTimestamp(it)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Assigned to: ${if (assignedCount > 1) "$assignedCount members" else "1 member"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                // TODO: Add an Edit button here later
                OutlinedButton(onClick = onDelete) {
                    Text("Cancel Task")
                }
            }
        }
    }
}


// A helper function to format the dueDate Timestamp
private fun formatTimestamp(timestamp: Timestamp): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(timestamp.toDate())
}