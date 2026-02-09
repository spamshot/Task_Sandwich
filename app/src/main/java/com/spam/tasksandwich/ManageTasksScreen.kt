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
import androidx.compose.material.icons.filled.Delete
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
    roomId: String,
    onNavigateBack: () -> Unit,
    viewModel: ManageTasksViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Form State
    var title by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("") }
    var repeatOption by remember { mutableStateOf("Never") }
    var assignedTo by remember { mutableStateOf<RoomMember?>(null) }
    var expiresInDays by remember { mutableStateOf(0) }
    var isAutoAssign by remember { mutableStateOf(false) }

    var expandedRepeat by remember { mutableStateOf(false) }
    var expandedAssignee by remember { mutableStateOf(false) }

    val repeatOptions = listOf("Never", "Every Day", "Once a Week", "Once a Month")

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            title = ""
            points = ""
            assignedTo = null
            expiresInDays = 0
            isAutoAssign = false
            viewModel.onSaveHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- NEW: Room Name Header ---
                Text(
                    text = uiState.roomName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                // --- ASSIGN NEW TASK CARD ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Assign New Task", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Task Title") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = points,
                                onValueChange = { points = it },
                                label = { Text("Points") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )

                            ExposedDropdownMenuBox(
                                expanded = expandedRepeat,
                                onExpandedChange = { expandedRepeat = !expandedRepeat },
                                modifier = Modifier.weight(1.4f)
                            ) {
                                OutlinedTextField(
                                    value = repeatOption,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Repeats") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRepeat) },
                                    modifier = Modifier.menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedRepeat,
                                    onDismissRequest = { expandedRepeat = false }
                                ) {
                                    repeatOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                repeatOption = option
                                                expandedRepeat = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Assignee Dropdown
                        ExposedDropdownMenuBox(
                            expanded = expandedAssignee,
                            onExpandedChange = { expandedAssignee = !expandedAssignee },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = assignedTo?.name ?: "Select Member",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Assign To") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAssignee) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAssignee,
                                onDismissRequest = { expandedAssignee = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Everyone in Room") },
                                    onClick = {
                                        assignedTo = RoomMember(userId = "all", name = "Everyone")
                                        expandedAssignee = false
                                    }
                                )
                                uiState.members.forEach { member ->
                                    DropdownMenuItem(
                                        text = { Text(member.name) },
                                        onClick = {
                                            assignedTo = member
                                            expandedAssignee = false
                                        }
                                    )
                                }
                            }
                        }

                        if (repeatOption == "Never") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Expires in: $expiresInDays days", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = expiresInDays.toFloat(),
                                onValueChange = { expiresInDays = it.toInt() },
                                valueRange = 0f..30f,
                                steps = 29
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isAutoAssign, onCheckedChange = { isAutoAssign = it })
                            Text("Make this an Auto-Assign template", style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.saveTask(title, points, repeatOption, assignedTo, expiresInDays, isAutoAssign)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) CircularProgressIndicator(Modifier.size(24.dp))
                            else Text("Create & Assign Task")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- REPEATING TASKS SECTION ---
                if (uiState.repeatingTasks.isNotEmpty()) {
                    Text("Repeating Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.repeatingTasks.forEach { task ->
                        AssignedTaskCard(
                            task = task,
                            onDelete = { viewModel.deleteTaskGroup(task) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- ONE-TIME TASKS SECTION ---
                if (uiState.oneTimeTasks.isNotEmpty()) {
                    Text("One-Time Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.oneTimeTasks.forEach { task ->
                        AssignedTaskCard(
                            task = task,
                            onDelete = { viewModel.deleteTaskGroup(task) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- AUTO-ASSIGN TEMPLATES SECTION ---
                if (uiState.autoAssignTemplates.isNotEmpty()) {
                    Text("Auto-Assign Templates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.autoAssignTemplates.forEach { template ->
                        TemplateCard(
                            template = template,
                            onDelete = { viewModel.deleteAutoAssignTemplate(template.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AssignedTaskCard(task: Task, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Displays: "Assigned to: Name, Name + X more"
            Text(
                text = getAssigneeText(task.assigneeNames),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))
            Text("Points: ${task.points}", style = MaterialTheme.typography.bodyMedium)
            Text("Repeats: ${task.repeatOption ?: "Never"}", style = MaterialTheme.typography.bodySmall)

            task.dueDate?.let {
                Text("Expires: ${formatTimestamp(it)}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel Task")
                }
            }
        }
    }
}

@Composable
fun TemplateCard(template: AutoAssignTaskTemplate, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(template.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${template.points} Points", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Template", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * HELPER: Formats assignee names for the Admin UI
 */
fun getAssigneeText(names: List<String>): String {
    if (names.isEmpty()) return "Assigned to: Loading..."

    val sortedNames = names.sorted()
    val firstThree = sortedNames.take(3).joinToString(", ")

    return if (sortedNames.size > 3) {
        "Assigned to: $firstThree + ${sortedNames.size - 3} more"
    } else {
        "Assigned to: $firstThree"
    }
}

private fun formatTimestamp(timestamp: Timestamp): String {
    return SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(timestamp.toDate())
}