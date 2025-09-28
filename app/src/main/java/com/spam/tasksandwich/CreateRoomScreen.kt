package com.spam.tasksandwich

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

//@Composable
//fun CreateRoomScreen(
//    onRoomCreated: (String) -> Unit, // Callback with the new room's ID
//    viewModel: CreateRoomViewModel = viewModel()
//) {
//    val uiState by viewModel.uiState.collectAsState()
//
//    var roomName by rememberSaveable { mutableStateOf("") }
////    var yourRole by rememberSaveable { mutableStateOf("Parent") }
////    var memberRole by rememberSaveable { mutableStateOf("Child") }
//
//    // This effect will trigger navigation when the ViewModel signals success
//    LaunchedEffect(uiState.createdRoomId) {
//        uiState.createdRoomId?.let { roomId ->
//            onRoomCreated(roomId)
//            viewModel.onNavigationHandled() // Reset the event
//        }
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        verticalArrangement = Arrangement.spacedBy(16.dp)
//    ) {
//        Text("Create a New Room", style = MaterialTheme.typography.headlineSmall)
//
//        OutlinedTextField(
//            value = roomName,
//            onValueChange = { roomName = it },
//            label = { Text("Room Name (e.g., Family Chores)") },
//            modifier = Modifier.fillMaxWidth()
//        )
////        OutlinedTextField(
////            value = yourRole,
////            onValueChange = { yourRole = it },
////            label = { Text("Your Role Title (e.g., Parent, Teacher)") },
////            modifier = Modifier.fillMaxWidth()
////        )
////        OutlinedTextField(
////            value = memberRole,
////            onValueChange = { memberRole = it },
////            label = { Text("Member Role Title (e.g., Child, Student)") },
////            modifier = Modifier.fillMaxWidth()
////        )
//
//        if (uiState.error != null) {
//            Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
//        }
//
//        Button(
//            onClick = { viewModel.createRoom(roomName) },
//            enabled = !uiState.isLoading,
//            modifier = Modifier.fillMaxWidth()
//        ) {
//            if (uiState.isLoading) {
//                CircularProgressIndicator(Modifier.size(24.dp))
//            } else {
//                Text("Create Room")
//            }
//        }
//    }
//}