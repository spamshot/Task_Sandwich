package com.spam.tasksandwich

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun TaskSettingsScreen(
//    onNavigateBack: () -> Unit,
//    onNavigateToEdit: (String) -> Unit,
//    viewModel: TaskSettingsViewModel = viewModel()
//) {
//    val uiState by viewModel.uiState.collectAsState()
//    var taskToAction by remember { mutableStateOf<Task?>(null) }
//
//    // Confirmation Dialog for Edit/Delete
//    if (taskToAction != null) {
//        AlertDialog(
//            onDismissRequest = { taskToAction = null },
//            title = { Text("Task Options") },
//            text = { Text("What would you like to do with '${taskToAction!!.title}'?") },
//            confirmButton = {
//                TextButton(
//                    onClick = {
//                        onNavigateToEdit(taskToAction!!.id)
//                        taskToAction = null
//                    }
//                ) { Text("Edit") }
//            },
//            dismissButton = {
//                Row {
//                    TextButton(
//                        onClick = {
//                            viewModel.deleteTask(taskToAction!!.id)
//                            taskToAction = null
//                        }
//                    ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
//                    TextButton(onClick = { taskToAction = null }) { Text("Cancel") }
//                }
//            }
//        )
//    }
//
//    Scaffold(
//    ) { paddingValues ->
//        if (uiState.isLoading) {
//            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
//        } else {
//            LazyColumn(
//                modifier = Modifier.fillMaxSize().padding(paddingValues),
//                contentPadding = PaddingValues(16.dp),
//                verticalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                items(uiState.tasks) { task ->
//                    TaskLogItem(
//                        task = task,
//                        onLongPress = { taskToAction = task }
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun TaskLogItem(task: Task, onLongPress: () -> Unit) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .pointerInput(Unit) {
//                detectTapGestures(onLongPress = { onLongPress() })
//            }
//    ) {
//        Column(Modifier.padding(16.dp)) {
//            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
//            Spacer(Modifier.height(4.dp))
//            Text("Points: ${task.points}", style = MaterialTheme.typography.bodyMedium)
//            Text("Repeats: ${task.repeatOption}", style = MaterialTheme.typography.bodyMedium)
//        }
//    }
//}