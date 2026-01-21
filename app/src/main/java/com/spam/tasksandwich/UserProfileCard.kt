package com.spam.tasksandwich

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spam.tasksandwich.R

/**
 * A reusable Card that displays a user's profile information.
 *
 * @param name The user's name.
// * @param email The user's email.
 * @param pointsInRoom The user's points for the specific context (e.g., a room).
 * @param totalPoints The user's total lifetime points.
 * @param iconId The string identifier for the user's selected icon.
 * @param modifier An optional Modifier.
 */

@Composable
fun UserProfileCard(
    name: String,
//    email: String,
    pointsInRoom: Int,
    totalPoints: Int,
    iconId: String,
    modifier: Modifier = Modifier
) {
    val presetIconMap = remember {
        mapOf(
            "avatar_1" to R.drawable.carrotdog,
            "avatar_2" to R.drawable.dallebabyface,
            "avatar_3" to R.drawable.fglasses,
            "avatar_4" to R.drawable.firehairguy,
            "avatar_5" to R.drawable.vgfbhbluehair
        )
    }

    val resId = presetIconMap[iconId] ?: R.drawable.carrotdog // Fallback to a default
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = "User Avatar",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
//                Text(text = email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Display the points for the current room
                Text(
                    text = "$pointsInRoom pts (in this room)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Display the total lifetime points
                Text(
                    text = "$totalPoints pts (total)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary // Use a different color to distinguish
                )
            }
        }
    }
}

