package com.spam.tasksandwich


import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageSelfTasksScreen(
    onGoBack: () -> Unit,
    viewModel: ManageSelfTasksViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTaskSheet by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    // Show snackbar on error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    // Delete dialog
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Task?") },
            text = {
                Text("\"${task.title}\" will be removed from your list and stop repeating.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTask(task.id)
                        taskToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Add task bottom sheet
    if (showTaskSheet) {
        AddSelfTaskBottomSheet(
            isSaving = uiState.isLoading,
            isTaskSaved = uiState.isTaskSaved,
            onDismiss = { showTaskSheet = false },
            onSave = { title, repeat -> viewModel.saveTask(title, repeat) },
            onSaveHandled = {
                viewModel.resetSaveState()
                showTaskSheet = false
            }
        )
    }

    val repeatingTasks = uiState.personalTasks.filter {
        it.repeatOption != null && it.repeatOption != "Never"
    }
    val oneTimeTasks = uiState.personalTasks.filter {
        it.repeatOption == null || it.repeatOption == "Never"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "My Personal Tasks",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    scrolledContainerColor = Color(0xFF1A1A2E)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTaskSheet = true },
                containerColor = Color(0xFF1A1A2E)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Task", tint = Color.White)
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.personalTasks.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Repeating section
                item {
                    SelfTaskSection(
                        icon = "🔁",
                        title = "Repeating",
                        count = repeatingTasks.size,
                        countColor = Color(0xFF16A34A),
                        countBg = Color(0xFFF0FDF4),
                        tasks = repeatingTasks,
                        emptyMessage = "No repeating tasks. Tap + to add one.",
                        onDeleteTask = { taskToDelete = it }
                    )
                }
                // One-time section
                item {
                    SelfTaskSection(
                        icon = "⚡",
                        title = "One-Time",
                        count = oneTimeTasks.size,
                        countColor = Color(0xFFD97706),
                        countBg = Color(0xFFFEF3C7),
                        tasks = oneTimeTasks,
                        emptyMessage = "No one-time tasks. Tap + to add one.",
                        onDeleteTask = { taskToDelete = it }
                    )
                }
            }
        }
    }
}

// ============================================================
// SelfTaskSection — collapsible accordion
// ============================================================
@Composable
fun SelfTaskSection(
    icon: String,
    title: String,
    count: Int,
    countColor: Color,
    countBg: Color,
    tasks: List<Task>,
    emptyMessage: String,
    onDeleteTask: (Task) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(icon, style = MaterialTheme.typography.titleMedium)
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(8.dp), color = countBg) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = countColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            emptyMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(
                            start = 14.dp, end = 14.dp, bottom = 14.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tasks.forEach { task ->
                            SelfTaskManageCard(
                                task = task,
                                onDelete = { onDeleteTask(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// SelfTaskManageCard — green accent, repeat pill, trash button
// ============================================================
@Composable
fun SelfTaskManageCard(task: Task, onDelete: () -> Unit) {
    // Green accent — visually distinct from room tasks (blue/amber)
    val accentColor = Color(0xFF16A34A)
    val isRepeating = task.repeatOption != null && task.repeatOption != "Never"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Green left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        accentColor,
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(5.dp))
                    // Repeat badge pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isRepeating) Color(0xFFF0FDF4) else Color(0xFFF3F4F6),
                        border = BorderStroke(
                            1.dp,
                            if (isRepeating) Color(0xFFBBF7D0) else Color(0xFFE5E7EB)
                        )
                    ) {
                        Text(
                            text = if (isRepeating) task.repeatOption!! else "One-time",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isRepeating) Color(0xFF16A34A) else Color(0xFF6B7280),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                // Trash icon button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFFFEE2E2), RoundedCornerShape(50))
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Task",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ============================================================
// AddSelfTaskBottomSheet — title + repeat chips only
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSelfTaskBottomSheet(
    isSaving: Boolean,
    isTaskSaved: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onSaveHandled: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var repeatOption by remember { mutableStateOf("Never") }
    val repeatOptions = listOf("Never", "Every Day", "Once a Week", "Once a Month")
    val isFormValid = title.isNotBlank()

    LaunchedEffect(isTaskSaved) {
        if (isTaskSaved) onSaveHandled()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .imePadding()
        ) {
            Text(
                "New Personal Task",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Only you can see and complete this task.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 30) title = it },
                label = { Text("Task Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (title.isNotEmpty()) {
                        Text(
                            "${title.length}/30",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
            Spacer(Modifier.height(16.dp))

            // Repeat chips
            Text(
                "REPEATS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                repeatOptions.forEach { option ->
                    val isSelected = repeatOption == option
                    FilterChip(
                        selected = isSelected,
                        onClick = { repeatOption = option },
                        label = {
                            Text(
                                option,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Cancel") }

                Button(
                    onClick = { onSave(title, repeatOption) },
                    modifier = Modifier.weight(2f),
                    enabled = isFormValid && !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Add to My List")
                    }
                }
            }
        }
    }
}