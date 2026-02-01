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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.appcheck.FirebaseAppCheck
import com.spam.tasksandwich.ui.theme.TaskSandwichTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // 1. Configure AdMob for Families Policy (COPPA) - Set GLOBALLY before initialization
        val requestConfiguration = MobileAds.getRequestConfiguration().toBuilder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            .build()
        MobileAds.setRequestConfiguration(requestConfiguration)

        // 2. Initialize the Mobile Ads SDK
        MobileAds.initialize(this) { status ->
            Log.d("TaskSandwich", "AdMob Initialized: $status")
        }

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
//                            .padding(paddingValues)
                            .padding(top = 74.dp)
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
                val screenWidthDp = configuration.screenWidthDp
                val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
                setAdSize(adSize)

                // --- DEVELOPMENT SETTING ---
                // Using Google's universal TEST ID to prevent Error Code 0 on emulators.
                adUnitId = "ca-app-pub-3940256099942544/6300978111"

                // Create an ad request and load the ad.
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}