package com.spam.tasksandwich

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.appcheck.FirebaseAppCheck
import com.spam.tasksandwich.ui.theme.TaskSandwichTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnSuccessListener { tokenResponse ->
                Log.e("TaskSandwich", "FORCE TOKEN: ${tokenResponse.token}")
            }
            .addOnFailureListener { e ->
                Log.e("TaskSandwich", "FORCE TOKEN FAILED: ${e.message}")
            }
        setContent {
            TaskSandwichTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.statusBarsPadding()){
                        AppShell()
                    }
                    // Call the NavHost, which will handle showing the correct screen
                }
            }
        }
    }
}