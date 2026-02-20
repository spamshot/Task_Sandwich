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
import androidx.compose.ui.unit.dp
import com.spam.tasksandwich.IconRepository.AllIconsMap

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
    pointsInRoom: Int,
    totalPoints: Int,
    iconId: String,
    modifier: Modifier = Modifier // this param was being ignored
) {
    val resId = AllIconsMap[iconId] ?: R.drawable.carrotdog

    Card(
        // FIX 1: Chain the caller's modifier instead of ignoring it.
        // Before: modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        // The passed-in `modifier` was silently dropped, so callers couldn't
        // customize positioning, padding, or size.
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp), // ✅ caller's modifier respected first
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // FIX 2: Surface behind avatar handles transparent icon images.
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant // ✅ fallback bg for transparent icons
            ) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = "Avatar for $name", // ✅ more descriptive for accessibility
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$pointsInRoom pts (in this room)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$totalPoints pts (total)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

