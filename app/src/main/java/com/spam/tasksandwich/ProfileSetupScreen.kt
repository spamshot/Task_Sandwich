package com.spam.tasksandwich

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spam.tasksandwich.ui.theme.TaskSandwichTheme
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    profileSetupViewModel: ProfileSetupViewModel = viewModel(),
    onProfileSaved: () -> Unit
) {
    val uiState by profileSetupViewModel.uiState.collectAsState()

    var name by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf(0) }
    var hasAge by rememberSaveable { mutableStateOf(false) }
    // "giver" = Task Giver, "doer" = Task Doer, "" = not selected
    var selectedRole by rememberSaveable { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val isFormValid = name.isNotBlank() && selectedRole.isNotEmpty()

    LaunchedEffect(uiState.isProfileSaved) {
        if (uiState.isProfileSaved) {
            showSuccess = true
            delay(2000)
            onProfileSaved()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .verticalScroll(scrollState)
                    .imePadding()
            ) {
                // ── Hero ──────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A2E))
                        .padding(horizontal = 28.dp, vertical = 28.dp)
                ) {
                    // Decorative background circle
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .offset(x = 120.dp, y = (-40).dp)
                            .background(
                                Color(0xFF3B6BDC).copy(alpha = 0.12f),
                                CircleShape
                            )
                            .align(Alignment.TopEnd)
                    )

                    Column {
                        // App badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Text(
                                "🥪  Task Sandwich",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        Text("👋", style = MaterialTheme.typography.displaySmall)

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "Welcome!\nLet's set up",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            lineHeight = 34.sp
                        )
                        Text(
                            "your profile.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF6B9EFF),
                            lineHeight = 34.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "Just a few quick things and you'll be earning points in no time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.55f),
                            lineHeight = 20.sp
                        )
                    }
                }

                // ── Form card ─────────────────────────────────────
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {

                        // Name field
                        Text(
                            "YOUR NAME",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 10) name = it },
                            placeholder = { Text("e.g. Alex") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            trailingIcon = {
                                if (name.isNotEmpty()) {
                                    Text(
                                        "${name.length}/10",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            name.length >= 10 -> MaterialTheme.colorScheme.error
                                            name.length >= 7  -> Color(0xFFF59E0B)
                                            else              -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                }
                            }
                        )

                        Spacer(Modifier.height(24.dp))

                        // Role picker
                        Text(
                            "MY ROLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RoleChip(
                                icon = "📋",
                                title = "Task Giver",
                                description = "Create & assign tasks",
                                isSelected = selectedRole == "giver",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedRole = "giver" }
                            )
                            RoleChip(
                                icon = "⭐",
                                title = "Task Doer",
                                description = "Complete & earn points",
                                isSelected = selectedRole == "doer",
                                modifier = Modifier.weight(1f),
                                onClick = { selectedRole = "doer" }
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        // Age stepper
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                "AGE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                "(optional)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        AgeStepperRow(
                            age = age,
                            hasAge = hasAge,
                            onDecrement = {
                                if (hasAge) age = (age - 1).coerceAtLeast(1)
                            },
                            onIncrement = {
                                if (!hasAge) { hasAge = true; age = 5 }
                                else age = (age + 1).coerceAtMost(99)
                            }
                        )

                        Spacer(Modifier.height(28.dp))

                        // Error text
                        if (uiState.error != null) {
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )
                        }

                        // CTA button
                        Button(
                            onClick = {
                                profileSetupViewModel.saveProfile(
                                    name = name,
                                    age = if (hasAge) age.toString() else "",
                                    role = selectedRole
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = isFormValid && !uiState.isLoading,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1A1A2E),
                                disabledContainerColor = Color(0xFF1A1A2E).copy(alpha = 0.38f)
                            )
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Let's Go",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Success overlay ───────────────────────────────────
        AnimatedVisibility(
            visible = showSuccess,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A2E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text("🎉", style = MaterialTheme.typography.displayLarge)
                    Text(
                        "Hey ${name.trim()}!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        "Your profile is all set.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF3B6BDC).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFF3B6BDC).copy(alpha = 0.3f))
                    ) {
                        Text(
                            "🏆  Ready to earn your first points",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B9EFF),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// RoleChip — tappable role selection card
// ============================================================
@Composable
fun RoleChip(
    icon: String,
    title: String,
    description: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF1A1A2E) else Color(0xFFE5E7EB)
    val bgColor = if (isSelected) Color(0xFFF0F4FF) else MaterialTheme.colorScheme.surface

    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(2.dp, borderColor),
        tonalElevation = if (isSelected) 0.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                icon,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ============================================================
// AgeStepperRow — +/− stepper, optional
// ============================================================
@Composable
fun AgeStepperRow(
    age: Int,
    hasAge: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier.size(52.dp)
            ) {
                Text(
                    "−",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (hasAge) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (hasAge) "$age" else "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (hasAge) "years old" else "tap + to set",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onIncrement,
                modifier = Modifier.size(52.dp)
            ) {
                Text(
                    "+",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}