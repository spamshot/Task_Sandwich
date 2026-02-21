package com.spam.tasksandwich

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    modifier: Modifier = Modifier
) {
    val resId = AllIconsMap[iconId] ?: R.drawable.carrotdog

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Image(
                painter = painterResource(id = resId),
                contentDescription = "Avatar for $name",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Name
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(12.dp))

        // Points row — two badge pills side by side
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Room points pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFE8F0FE),
                border = BorderStroke(1.dp, Color(0xFFC7D7FC))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "$pointsInRoom pts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF3B6BDC)
                    )
                    Text(
                        "in this room",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF3B6BDC).copy(alpha = 0.7f)
                    )
                }
            }

            // Total points pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "$totalPoints pts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "all time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

