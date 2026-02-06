package com.spam.tasksandwich

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Assign New", "Manage Assigned")

    Scaffold(

    ) { paddingValues ->
        Column(modifier = Modifier
//            .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

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
fun AssignTaskForm(uiState: ManageTasksUiState, viewModel: ManageTasksViewModel) { //For assigning Task
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

    val autoAssignOptions = listOf("No", "Yes")
    var selectedAutoAssign by rememberSaveable { mutableStateOf(autoAssignOptions[0]) }
    var isAutoAssignDropdownExpanded by remember { mutableStateOf(false) }

    val isExpirationEnabled = selectedRepeatOption == "Never"
    val isAutoAssignEnabled = selectedAssignee?.userId == "all"

    LaunchedEffect(selectedRepeatOption) { if (!isExpirationEnabled) selectedExpiration = expiresInOptions[0] }
    LaunchedEffect(selectedAssignee) { if (!isAutoAssignEnabled) selectedAutoAssign = autoAssignOptions[0] }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            scope.launch { snackbarHostState.showSnackbar("Action successful!") }
            viewModel.onSaveHandled()
            title = ""; points = ""; selectedAssignee = null; selectedRepeatOption = repeatOptions[0]
            selectedExpiration = expiresInOptions[0]; selectedAutoAssign = autoAssignOptions[0]
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
//                .padding(padding)
                .background(Color.Gray.copy(alpha = 0.1f))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = uiState.roomName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = points, onValueChange = { points = it }, label = { Text("Points") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = isRepeatDropdownExpanded,
                onExpandedChange = { isRepeatDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedRepeatOption,
                    onValueChange = {}, // onValueChange is empty because this field is read-only
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


            //DopDown for expiration
            ExposedDropdownMenuBox(
                expanded = isExpirationDropdownExpanded,
                // The dropdown can only be expanded if the 'isExpirationEnabled' flag is true.
                onExpandedChange = { if (isExpirationEnabled) isExpirationDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedExpiration.first, // Display the string part of the Pair (e.g., "1 Day")
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Expires In") },
                    // This visually grays out the text field and disables interaction
                    // when the "Repeat" option is not "Never".
                    enabled = isExpirationEnabled,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpirationDropdownExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor() // Anchors the dropdown to this text field.
                )

                // This is the content of the dropdown menu itself.
                ExposedDropdownMenu(
                    expanded = isExpirationDropdownExpanded,
                    onDismissRequest = { isExpirationDropdownExpanded = false } // Close when clicking outside.
                ) {
                    // Create a menu item for each option in our list of Pairs.
                    expiresInOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.first) }, // Display the string part (e.g., "7 Days").
                            onClick = {
                                selectedExpiration = option // Update the state with the selected Pair.
                                isExpirationDropdownExpanded = false // Close the menu.
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))


            //DropDown for assignee
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
            Spacer(Modifier.height(16.dp))


            //DropDown for auto assign
            ExposedDropdownMenuBox(
                expanded = isAutoAssignDropdownExpanded,
                // The dropdown can only be expanded if it's enabled.
                onExpandedChange = { if (isAutoAssignEnabled) isAutoAssignDropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedAutoAssign,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Auto Assign to New Members") },
                    // This visually grays out the text field if "All Members" is not selected.
                    enabled = isAutoAssignEnabled,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = isAutoAssignDropdownExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor() // Anchors the dropdown to this text field.
                )

                // This is the content of the dropdown menu itself.
                ExposedDropdownMenu(
                    expanded = isAutoAssignDropdownExpanded,
                    onDismissRequest = { isAutoAssignDropdownExpanded = false } // Close when clicking outside.
                ) {
                    // Create a menu item for each option ("Yes" or "No").
                    autoAssignOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                selectedAutoAssign = option // Update the state with the new selection.
                                isAutoAssignDropdownExpanded = false // Close the menu.
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
                        assignedTo = selectedAssignee, expiresInDays = selectedExpiration.second,
                        isAutoAssign = selectedAutoAssign == "Yes"
                    )
                },
                enabled = !uiState.isSaving && selectedAssignee != null && title.isNotBlank() && points.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) CircularProgressIndicator(Modifier.size(24.dp)) else Text("Save Task")

            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

/**
 * The content for the "Manage Assigned" tab, showing three distinct sections.
 */
@Composable
fun AssignedTasksList(uiState: ManageTasksUiState, viewModel: ManageTasksViewModel) { //Managed Assigned tasks tab items for card
    // 1. Prepare the grouped data
    val groupedOneTimeTasks = remember(uiState.oneTimeTasks) {
        uiState.oneTimeTasks.groupBy { it.sharedTaskId ?: it.id }
    }
    val groupedRepeatingTasks = remember(uiState.repeatingTasks) {
        uiState.repeatingTasks.groupBy { it.sharedTaskId ?: it.id }
    }

    // 2. Check if the list is completely empty
    val isListEmpty = uiState.autoAssignTemplates.isEmpty() &&
            groupedRepeatingTasks.isEmpty() &&
            groupedOneTimeTasks.isEmpty()

    if (uiState.isLoading) {
        // Loading State
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (isListEmpty) {
        // Empty State (This fixes the "Just shows loading wheel" or "Blank screen" issue)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No tasks to manage",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        // Content State
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .background(Color.Gray.copy(alpha = 0.1f)),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Section 1: Auto-Assign Tasks ---
            item {
                Text(
                    text = "Auto-Assign Tasks (for new members)",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (uiState.autoAssignTemplates.isEmpty()) {
                item {
                    Text(
                        text = "None.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            } else {
                items(uiState.autoAssignTemplates, key = { it.id }) { template ->
                    AutoAssignTaskCard(
                        template = template,
                        onDelete = { viewModel.deleteAutoAssignTemplate(template.id) }
                    )
                }
            }

            // --- Section 2: Repeating Tasks ---
            item {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = "Repeating Tasks",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (groupedRepeatingTasks.isEmpty()) {
                item {
                    Text(
                        text = "None.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            } else {
                items(groupedRepeatingTasks.entries.toList(), key = { it.key }) { (_, tasks) ->
                    val representativeTask = tasks.first()
                    AssignedTaskCard(
                        task = representativeTask,
                        onDelete = { viewModel.deleteTaskGroup(tasks) }
                    )
                }
            }

            // --- Section 3: One-Time Assigned Tasks ---
            item {
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = "One-Time Assigned Tasks",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (groupedOneTimeTasks.isEmpty()) {
                item {
                    Text(
                        text = "None.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(groupedOneTimeTasks.entries.toList(), key = { it.key }) { (_, tasks) ->
                    val representativeTask = tasks.first()
                    AssignedTaskCard(
                        task = representativeTask,
                        onDelete = { viewModel.deleteTaskGroup(tasks) }
                    )
                }
            }
        }
    }
}

@Composable
fun AutoAssignTaskCard(template: AutoAssignTaskTemplate, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Points: ${template.points}", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onDelete) {
                Text("Remove")
            }
        }
    }
}

@Composable
fun AssignedTaskCard(task: Task, onDelete: () -> Unit,) { //Task Card for Assigned Tasks
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ){
        Column(Modifier.padding(12.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Points: ${task.points}", style = MaterialTheme.typography.bodyMedium)
            Text("Repeats: ${task.repeatOption}", style = MaterialTheme.typography.bodyMedium)
            task.dueDate?.let {
                Text("Expires: ${formatTimestamp(it)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = getAssigneeText(task.assigneeNames),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )


            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDelete) { Text("Cancel Task") }
            }
        }
    }
}

fun getAssigneeText(names: List<String>): String {
    // If names is empty, it means the ViewModel grouping logic
    // is still processing the snapshot or pulling from the DB.
    if (names.isEmpty()) {
        return "Assigned to: Loading..."
    }

    val sortedNames = names.sorted()
    val firstThree = sortedNames.take(3).joinToString(", ")

    return if (sortedNames.size > 3) {
        "Assigned to: $firstThree + ${sortedNames.size - 3} more"
    } else {
        "Assigned to: $firstThree"
    }
}

private fun formatTimestamp(timestamp: Timestamp): String {
    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(timestamp.toDate())
}