package com.spam.tasksandwich

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var showTaskSheet by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var templateToDelete by remember { mutableStateOf<AutoAssignTaskTemplate?>(null) }

    // Delete dialogs
    taskToDelete?.let { task ->
        DeleteConfirmationDialog(
            itemName = task.title,
            warningMessage = "This will remove this task for EVERYONE listed: ${task.assigneeNames.joinToString(", ")}.",
            onDismiss = { taskToDelete = null },
            onConfirm = { viewModel.deleteTaskGroup(task); taskToDelete = null }
        )
    }

    templateToDelete?.let { template ->
        DeleteConfirmationDialog(
            itemName = template.title,
            warningMessage = "This is a TEMPLATE. Deleting it will stop this task from being automatically assigned to NEW members who join the room.",
            onDismiss = { templateToDelete = null },
            onConfirm = { viewModel.deleteAutoAssignTemplate(template.id); templateToDelete = null }
        )
    }

    // Task creation bottom sheet
    if (showTaskSheet) {
        AssignTaskBottomSheet(
            members = uiState.members,
            isSaving = uiState.isSaving,
            saveSuccess = uiState.saveSuccess,
            onDismiss = { showTaskSheet = false },
            onSave = { title, points, repeat, assignedTo, expiresInDays, autoAssign ->
                viewModel.saveTask(title, points, repeat, assignedTo, expiresInDays, autoAssign)
            },
            onSaveHandled = {
                viewModel.onSaveHandled()
                showTaskSheet = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.roomName.isNotBlank()) "${uiState.roomName} · Tasks"
                        else "Manage Tasks",
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                Icon(Icons.Default.Add, contentDescription = "Assign Task", tint = Color.White)
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Repeating Tasks accordion ──
                item {
                    TaskSection(
                        icon = "🔁",
                        title = "Repeating Tasks",
                        count = uiState.repeatingTasks.size,
                        countColor = Color(0xFF3B6BDC),
                        countBg = Color(0xFFE8F0FE),
                        tasks = uiState.repeatingTasks,
                        templates = emptyList(),
                        onDeleteTask = { taskToDelete = it },
                        onDeleteTemplate = {}
                    )
                }
                // ── One-Time Tasks accordion ──
                item {
                    TaskSection(
                        icon = "⚡",
                        title = "One-Time Tasks",
                        count = uiState.oneTimeTasks.size,
                        countColor = Color(0xFFD97706),
                        countBg = Color(0xFFFEF3C7),
                        tasks = uiState.oneTimeTasks,
                        templates = emptyList(),
                        onDeleteTask = { taskToDelete = it },
                        onDeleteTemplate = {}
                    )
                }
                // ── Auto-Assign Templates accordion ──
                item {
                    TemplateSection(
                        count = uiState.autoAssignTemplates.size,
                        templates = uiState.autoAssignTemplates,
                        onDeleteTemplate = { templateToDelete = it }
                    )
                }
            }
        }
    }
}

// ============================================================
// TaskSection — collapsible accordion for Repeating / One-Time
// ============================================================
@Composable
fun TaskSection(
    icon: String,
    title: String,
    count: Int,
    countColor: Color,
    countBg: Color,
    tasks: List<Task>,
    templates: List<AutoAssignTaskTemplate>,
    onDeleteTask: (Task) -> Unit,
    onDeleteTemplate: (AutoAssignTaskTemplate) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val accentColor = if (title.startsWith("Repeat")) Color(0xFF3B6BDC) else Color(0xFFF59E0B)

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
                // Count badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = countBg
                ) {
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
                            "No tasks here yet. Tap + to assign one.",
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
                            AssignedTaskCard(
                                task = task,
                                accentColor = accentColor,
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
// TemplateSection — collapsible accordion for Auto-Assign
// ============================================================
@Composable
fun TemplateSection(
    count: Int,
    templates: List<AutoAssignTaskTemplate>,
    onDeleteTemplate: (AutoAssignTaskTemplate) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("🤖", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Auto-Assign Templates",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFAF5FF)
                ) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7C3AED),
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
                if (templates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No templates yet. Assign a task to Everyone with Auto-Assign on.",
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
                        templates.forEach { template ->
                            TemplateCard(
                                template = template,
                                onDelete = { onDeleteTemplate(template) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// AssignedTaskCard — colored left border, pills, assignee chips
// ============================================================
@Composable
fun AssignedTaskCard(task: Task, accentColor: Color, onDelete: () -> Unit) {
    val repeatOptions = listOf("Every Day", "Once a Week", "Once a Month")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Colored left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
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

                Spacer(Modifier.height(6.dp))

                // Pills row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Points
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFE8F0FE),
                        border = BorderStroke(1.dp, Color(0xFFC7D7FC))
                    ) {
                        Text(
                            "${task.points} pts",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B6BDC),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    // Repeat badge
                    if (task.repeatOption != null && task.repeatOption in repeatOptions) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                        ) {
                            Text(
                                task.repeatOption,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    // Expiry badge
                    task.dueDate?.let {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Text(
                                "Exp ${formatTimestamp(it)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Assignee chips
                if (task.assigneeNames.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        task.assigneeNames.take(4).forEach { name ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (task.assigneeNames.size > 4) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    "+${task.assigneeNames.size - 4} more",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// TemplateCard — purple accent, distinct from task cards
// ============================================================
@Composable
fun TemplateCard(template: AutoAssignTaskTemplate, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Purple left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        Color(0xFF7C3AED),
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFFEE2E2), RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Template",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFE8F0FE),
                        border = BorderStroke(1.dp, Color(0xFFC7D7FC))
                    ) {
                        Text(
                            "${template.points} pts",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B6BDC),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFAF5FF),
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                    ) {
                        Text(
                            "Auto",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C3AED),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "🤖 Assigned to all new members on join",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7C3AED),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ============================================================
// AssignTaskBottomSheet — full create form in a bottom sheet
// ============================================================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssignTaskBottomSheet(
    members: List<RoomMember>,
    isSaving: Boolean,
    saveSuccess: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, RoomMember?, Int, Boolean) -> Unit,
    onSaveHandled: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("") }
    var repeatOption by remember { mutableStateOf("Never") }
    var assignedTo by remember { mutableStateOf<RoomMember?>(null) }
    var expiresInDays by remember { mutableStateOf(0) }
    var isAutoAssign by remember { mutableStateOf(false) }

    val repeatOptions = listOf("Never", "Every Day", "Once a Week", "Once a Month")
    val expiryOptions = listOf(0 to "None", 1 to "1 day", 3 to "3 days", 7 to "7 days", 30 to "30 days")

    val isRepeatEnabled = expiresInDays == 0
    val canAutoAssign = assignedTo?.userId == "all"
    val isFormValid = title.isNotBlank() && points.toIntOrNull() != null
            && (points.toIntOrNull() ?: 0) > 0 && assignedTo != null

    // Close sheet on successful save
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) onSaveHandled()
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Assign New Task",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Task title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(12.dp))

            // Points
            OutlinedTextField(
                value = points,
                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) points = it },
                label = { Text("Points") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))

            // Assign To — member chips
            Text(
                "ASSIGN TO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Everyone chip
                val everyoneSelected = assignedTo?.userId == "all"
                FilterChip(
                    selected = everyoneSelected,
                    onClick = {
                        assignedTo = RoomMember(userId = "all", name = "Everyone")
                    },
                    label = {
                        Text(
                            "👥 Everyone",
                            fontWeight = if (everyoneSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(20.dp)
                )
                // Individual member chips
                members.forEach { member ->
                    val isSelected = assignedTo?.userId == member.userId
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            assignedTo = member
                            isAutoAssign = false
                        },
                        label = {
                            Text(
                                member.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // Repeats — chips
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
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                repeatOptions.forEach { option ->
                    val isSelected = repeatOption == option
                    val enabled = option == "Never" || isRepeatEnabled
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            repeatOption = option
                            if (option != "Never") expiresInDays = 0
                        },
                        enabled = enabled,
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

            // Expires In — chips (only when repeat is Never)
            if (repeatOption == "Never") {
                Text(
                    "EXPIRES IN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    expiryOptions.forEach { (days, label) ->
                        val isSelected = expiresInDays == days
                        FilterChip(
                            selected = isSelected,
                            onClick = { expiresInDays = days },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF59E0B),
                                selectedLabelColor = Color.White
                            ),
                            label = {
                                Text(
                                    label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // Auto-Assign — only when Everyone is selected
            AnimatedVisibility(visible = canAutoAssign) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto-Assign to new members",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Assign automatically when someone joins",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutoAssign,
                            onCheckedChange = { isAutoAssign = it }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

            // Actions
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
                    onClick = {
                        onSave(title, points, repeatOption, assignedTo, expiresInDays, isAutoAssign)
                    },
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
                        Text("Create & Assign")
                    }
                }
            }
        }
    }
}

// ============================================================
// Helpers (unchanged logic)
// ============================================================
fun getAssigneeText(names: List<String>): String {
    if (names.isEmpty()) return "Assigned to: Loading..."
    val sortedNames = names.sorted()
    val firstThree = sortedNames.take(3).joinToString(", ")
    return if (sortedNames.size > 3) "Assigned to: $firstThree + ${sortedNames.size - 3} more"
    else "Assigned to: $firstThree"
}

private fun formatTimestamp(timestamp: Timestamp): String =
    SimpleDateFormat("MMM dd", Locale.getDefault()).format(timestamp.toDate())