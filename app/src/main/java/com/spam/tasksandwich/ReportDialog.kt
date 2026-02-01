package com.spam.tasksandwich

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ReportDialog(
    itemContent: String,    // The actual text being reported (e.g. "Mow the lawn")
    onDismiss: () -> Unit,  // What happens when they click Cancel
    onConfirm: () -> Unit   // What happens when they click Report
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Report Content")
        },
        text = {
            Text(text = "Are you sure you want to report the following content for violating safety policies?\n\n\"$itemContent\"")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("Report", color = Color.Red)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}