package com.spam.tasksandwich


import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSelfTasksScreen(
    viewModel: ManageSelfTasksViewModel = viewModel(),
    onGoBack: () -> Unit,
) {

    val uiState by viewModel.uiState.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Self Task", "Manage Self")



    Column(modifier = Modifier.fillMaxSize()) {
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
            0 -> AddSelfTaskForm(uiState = uiState, viewModel = viewModel, onGoBack = onGoBack)
            1 -> ManageSelfTasksList(uiState = uiState, viewModel = viewModel)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSelfTaskForm(
    uiState: ManageSelfTasksUiState,
    viewModel: ManageSelfTasksViewModel,
    onGoBack: () -> Unit
) {
//    val uiState by manageSelfTasksViewModel.uiState.collectAsState()

    // --- Local UI State ---
    var title by rememberSaveable { mutableStateOf("") }
//    var points by rememberSaveable { mutableStateOf("") }
    val repeatOptions = listOf("Never", "Every Day", "Once a Week", "Once a Month")
    var selectedRepeatOption by rememberSaveable { mutableStateOf(repeatOptions[0]) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // For showing the "Task Saved!" snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- Side Effects ---
    LaunchedEffect(uiState.isTaskSaved) {
        if (uiState.isTaskSaved) {
            scope.launch {
                snackbarHostState.showSnackbar("Task Saved!")
            }
            title = ""
//            points = ""
            selectedRepeatOption = repeatOptions[0]
            viewModel.resetSaveState()
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
//                OutlinedTextField(
//                    value = points,
//                    onValueChange = { points = it },
//                    label = { Text("Points (Optional)") },
//                    modifier = Modifier.fillMaxWidth(),
//                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
//                    singleLine = true
//                )
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
                        text = uiState.error,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Save Button
                Button(
                    onClick = {
                        viewModel.saveTask(title, selectedRepeatOption)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && title.isNotBlank()
                ) {
                    Text("Save and Add Another")
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Go Back Button
                OutlinedButton(
                    onClick = onGoBack, // It simply calls the callback
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text("Go Back")
                }
            }
        }
    }
}
@Composable
fun ManageSelfTasksList(uiState: ManageSelfTasksUiState, viewModel: ManageSelfTasksViewModel) {
    var taskToAction by remember { mutableStateOf<Task?>(null) }

    if (taskToAction != null) {
        AlertDialog(
            onDismissRequest = { taskToAction = null },
            title = { Text("Task Options") },
            text = { Text("Delete the task '${taskToAction!!.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTask(taskToAction!!.id)
                        taskToAction = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { taskToAction = null }) { Text("Cancel") } }
        )
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (uiState.personalTasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("You have not created any personal tasks yet.")
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items = uiState.personalTasks, key = { it.id }) { task ->
                SelfTaskItem(
                    task = task,
                    onLongPress = { taskToAction = task }
                )
            }
        }
    }
}
@Composable
fun SelfTaskItem(task: Task, onLongPress: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
        detectTapGestures(onLongPress = { onLongPress() })
    }) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Repeats: ${task.repeatOption}", style = MaterialTheme.typography.bodySmall)
            }
            Text("${task.points} pts", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}