package com.spam.tasksandwich

import com.google.firebase.Timestamp
/**
 * Represents the user's core profile information.
 * Used in the HomeScreen and fetched from the 'users' collection.
 * The default values are crucial for Firestore's automatic data conversion.
 */
data class UserProfile(
    val uid: String = "",
    val name: String = "User",
    val totalPoints: Int = 0,
    val age: Int? = null,
    val email: String? = null,
    val selectedIconId: String? = null,
    // We can even add the fields we planned for the future here.
    val selectedBackgroundId: String? = null,
    val role: String = "child", // Default role
    val groupsJoined: List<Map<String, Any>> = emptyList()

)

/**
 * Represents a single task.
 * Used in the HomeScreen and fetched from the 'tasks' collection.
 */
data class Task(
    val id: String = "",
    val title: String = "",
    val points: Int = 0,
    val assignedByName: String = "Someone",
    val repeatOption: String = "Never",
    val groupId: String? = null,
    val dueDate: Timestamp? = null,
    val sharedTaskId: String? = null,
    val assignedToUserId: String = "",
    val assignedByUserId: String = "",
    val status: String = "assigned",
    val isPersonal: Boolean = false,
    val createdAt: Timestamp? = null
)

/**
 * Represents a simplified view of a room for display on the Home screen.
 * This data is fetched from the 'groupsJoined' array in the user's document.
 */
data class UserRoom(
    val groupId: String = "",
    val groupName: String = "",
    val userPointsInRoom: Int = 0,
    val isAdmin: Boolean = false
)