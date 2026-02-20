package com.spam.tasksandwich


import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSelfTasksScreen(
    onGoBack: () -> Unit,
    viewModel: ManageSelfTasksViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Form State
    var title by remember { mutableStateOf("") }
    var repeatOption by remember { mutableStateOf("Never") }
    var expandedRepeat by remember { mutableStateOf(false) }
    val repeatOptions = listOf("Never", "Every Day", "Once a Week", "Once a Month")

    // Delete State
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    // Reset form on success
    LaunchedEffect(uiState.isTaskSaved) {
        if (uiState.isTaskSaved) {
            title = ""
            repeatOption = "Never"
            viewModel.resetSaveState()
        }
    }

    Scaffold { paddingValues ->
        val paddingValues = PaddingValues(top = 14.dp, bottom = 14.dp, start = 10.dp, end = 10.dp)

        if (uiState.isLoading && uiState.personalTasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Using LazyColumn for the whole screen so the form and list scroll together
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Gray.copy(alpha = 0.1f)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- SECTION 1: ADD TASK CARD ---
                item {
                    Text(
                        text = "My Personal Tasks",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Create New Task", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(16.dp))

                            OutlinedTextField(
                                value = title,
                                onValueChange = { if (it.length <= 30) title = it },
                                label = { Text("Task Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(Modifier.height(8.dp))

                            // Repeat Dropdown
                            ExposedDropdownMenuBox(
                                expanded = expandedRepeat,
                                onExpandedChange = { expandedRepeat = !expandedRepeat }
                            ) {
                                OutlinedTextField(
                                    value = repeatOption,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Repeats") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRepeat) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
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

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.saveTask(title, repeatOption) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = title.isNotBlank() && !uiState.isLoading
                            ) {
                                if (uiState.isLoading) CircularProgressIndicator(Modifier.size(24.dp))
                                else Text("Add to My List")
                            }
                        }
                    }
                }

                // --- SECTION 2: ACTIVE TASKS LIST ---
                item {
                    Text(
                        text = "Active Tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (uiState.personalTasks.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No personal tasks set.", color = Color.Gray)
                        }
                    }
                } else {
                    items(items = uiState.personalTasks, key = { it.id }) { task ->
                        SelfTaskManageCard(
                            task = task,
                            onDelete = { taskToDelete = task }
                        )
                    }
                }
            }
        }
    }

    // --- DELETE CONFIRMATION ---
    taskToDelete?.let { task ->
        DeleteConfirmationDialog(
            itemName = task.title,
            warningMessage = "This will remove this task from your daily list and stop it from repeating.",
            onDismiss = { taskToDelete = null },
            onConfirm = {
                viewModel.deleteTask(task.id)
                taskToDelete = null
                Toast.makeText(context, "Task Deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun SelfTaskManageCard(task: Task, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = if (task.repeatOption == "Never") "One-time task" else "Repeats: ${task.repeatOption}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Using the same "Cancel Task" style as ManageTasksScreen
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Cancel Task")
            }
        }
    }
}