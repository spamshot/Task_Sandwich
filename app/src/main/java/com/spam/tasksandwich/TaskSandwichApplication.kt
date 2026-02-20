package com.spam.tasksandwich

import android.app.Application
import android.util.Log
import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory


class TaskSandwichApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        val appCheck = FirebaseAppCheck.getInstance()

        if (BuildConfig.DEBUG) {
            // Safe in debug builds — the debug provider only works with
            // secrets registered in the Firebase console per-device.
            // Do NOT set the secret via System.setProperty in code.
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            // PRODUCTION: Use Play Integrity (Android) for real attestation.
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }
    }
}