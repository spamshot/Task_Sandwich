package com.spam.tasksandwich

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class SendReportsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    fun sendReportToFirebase(
        reporterId: String,
        reportedContent: String,
        reportType: String,
        roomId: String? = null
    ) {
        val reportData = hashMapOf(
            "reporterId" to reporterId,
            "content" to reportedContent,
            "type" to reportType,
            "roomId" to roomId,
            "timestamp" to Timestamp.now(),
            "status" to "pending" // Allows you to track "Reviewed" vs "Pending"
        )

        db.collection("reports")
            .add(reportData)
            .addOnSuccessListener {
                Log.d("UGC_REPORT", "Report successfully sent to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("UGC_REPORT", "Error sending report", e)
            }
    }
}