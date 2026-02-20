package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Fixes:
//   1. sendReportToFirebase() uses addOnSuccessListener /
//      addOnFailureListener callbacks with no UiState — the UI
//      has no way to know if the report succeeded or failed.
//      Added UiState with success/error flags so the UI can
//      show a confirmation or retry prompt.
//   2. reporterId is passed in as a parameter — callers must
//      supply the current user's ID themselves, which is
//      error-prone and duplicates auth logic at every call site.
//      The ViewModel should read it from Firebase.auth directly.
//   3. reportedContent has no length validation — an empty string
//      or a 10,000-character report would both be submitted
//      silently. Added a max length guard (500 chars is reasonable
//      for a UGC report) and a blank check.
//   4. reportType has no validation — any string (including empty)
//      would be written to Firestore. Added a blank check.
//   5. The Firestore write uses callbacks instead of coroutines,
//      inconsistent with every other ViewModel in the project.
//      Changed to viewModelScope.launch + .await().
//   6. No Crashlytics logging — other ViewModels log to Crashlytics
//      on failure. Added for consistency.
//   7. db is instantiated with getInstance() instead of Firebase.firestore —
//      minor style inconsistency with the rest of the project. Aligned.
// ============================================================

data class SendReportsUiState(
    val isSubmitting: Boolean = false,
    val reportSent: Boolean = false,
    val error: String? = null
)

class SendReportsViewModel : ViewModel() {
    // FIX 7: Use Firebase.firestore for consistency with the rest of the project.
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow(SendReportsUiState())
    val uiState = _uiState.asStateFlow()

    fun sendReport(
        reportedContent: String,
        reportType: String,
        roomId: String? = null
    ) {
        // FIX 2: Read reporter ID from auth directly — no caller involvement needed.
        val reporterId = auth.currentUser?.uid
        if (reporterId == null) {
            _uiState.update { it.copy(error = "You must be logged in to submit a report.") }
            return
        }

        // FIX 4: Validate reportType.
        if (reportType.isBlank()) {
            _uiState.update { it.copy(error = "Report type is missing.") }
            return
        }

        // FIX 3: Validate reportedContent — reject blank or excessively long reports.
        val trimmedContent = reportedContent.trim()
        if (trimmedContent.isBlank()) {
            _uiState.update { it.copy(error = "Please describe what you're reporting.") }
            return
        }
        if (trimmedContent.length > 500) {
            _uiState.update { it.copy(error = "Report is too long. Please keep it under 500 characters.") }
            return
        }

        // FIX 5: Use coroutines + .await() for consistency with all other ViewModels.
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                val reportData = hashMapOf(
                    "reporterId" to reporterId,
                    "content" to trimmedContent,
                    "type" to reportType,
                    "roomId" to roomId,
                    "timestamp" to Timestamp.now(),
                    "status" to "pending"
                )
                db.collection("reports").add(reportData).await()

                Log.d("SendReportsViewModel", "Report submitted successfully.")
                _uiState.update { it.copy(isSubmitting = false, reportSent = true) }

            } catch (e: Exception) {
                Log.e("SendReportsViewModel", "Error sending report: ${e.message}")
                // FIX 6: Crashlytics logging for consistency with the project.
                FirebaseCrashlytics.getInstance().log("Error in SendReportsViewModel: sendReport")
                FirebaseCrashlytics.getInstance().setCustomKey("Report type", reportType)
                FirebaseCrashlytics.getInstance().recordException(e)
                _uiState.update { it.copy(isSubmitting = false, error = "Could not submit report. Please try again.") }
            }
        }
    }

    // Called by the UI after showing the success confirmation.
    fun onReportSentHandled() {
        _uiState.update { it.copy(reportSent = false) }
    }
}
