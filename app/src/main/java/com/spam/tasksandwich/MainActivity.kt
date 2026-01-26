package com.spam.tasksandwich

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.firebase.appcheck.FirebaseAppCheck
import com.spam.tasksandwich.ui.theme.TaskSandwichTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnSuccessListener { tokenResponse ->
                Log.e("TaskSandwich", "FORCE TOKEN: ${tokenResponse.token}")
            }
            .addOnFailureListener { e ->
                Log.e("TaskSandwich", "FORCE TOKEN FAILED: ${e.message}")
            }
        setContent {
            TaskSandwichTheme {
                val isAdVisible by remember { mutableStateOf(true) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),

                    topBar = {
                        if (isAdVisible) {
                            AdmobBanner(modifier = Modifier.statusBarsPadding())
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)

                    ) {
                        AppShell()
                    }
                }
            }
        }
    }
}

@Composable
fun AdmobBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current

    // Create a remembered AdView instance
    val adView = remember { AdView(context) }

    // Use a DisposableEffect to tie the AdView's lifecycle to the composable's lifecycle
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_DESTROY -> adView.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the composable is disposed, remove the observer and destroy the ad.
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            adView.apply {
                // Determine the adaptive banner size.
                val screenWidthDp = configuration.screenWidthDp.toFloat()
                val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    screenWidthDp.toInt()
                )
                setAdSize(adSize)

                // IMPORTANT: Use the TEST Ad Unit ID for development.
                // Replace with your REAL Ad Unit ID before publishing.
                adUnitId = "ca-app-pub-3940256099942544/9214589741"

                // Create an ad request and load the ad.
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
