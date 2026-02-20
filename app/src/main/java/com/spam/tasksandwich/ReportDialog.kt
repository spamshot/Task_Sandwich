package com.spam.tasksandwich

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Fixes:
//   1. The dialog shows the raw reported content inline in the
//      message — if the content is very long (e.g. a task title
//      that was set to a paragraph), the dialog becomes unwieldy.
//      Added a maxLines / overflow clip on the displayed content.
//   2. onConfirm has no loading state — tapping "Report" multiple
//      times quickly could fire multiple submissions. Added an
//      isSubmitting parameter so the calling screen can disable
//      the button while the ViewModel processes the request.
//   3. The "Report" button uses Color.Red directly — this hardcoded
//      color won't adapt to dark mode. Changed to
//      MaterialTheme.colorScheme.error.
// ============================================================

@Composable
fun ReportDialog(
    itemContent: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isSubmitting: Boolean = false // FIX 2: caller passes this from ViewModel's uiState
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Content") },
        text = {
            Column {
                Text("Are you sure you want to report the following content for violating safety policies?")
                Spacer(modifier = Modifier.height(8.dp))
                // FIX 1: Clamp long content so the dialog doesn't grow unbounded.
                Text(
                    text = "\"$itemContent\"",
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSubmitting // FIX 2: prevent double-tap submission
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    // FIX 3: Use theme error color instead of hardcoded Color.Red
                    Text("Report", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}