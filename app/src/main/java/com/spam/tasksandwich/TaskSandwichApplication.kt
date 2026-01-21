package com.spam.tasksandwich

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory


class TaskSandwichApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. FORCE YOUR SPECIFIC KEY
        // This ensures the app uses the exact same key you just saved in the console.
        System.setProperty(
            "com.google.firebase.appcheck.debug.testing.firebaseAppCheckDebugSecret",
            "D8119CA6-B17B-4CD0-A517-71EF2E8A7895"
        )

        // 2. Initialize
        FirebaseApp.initializeApp(this)

        // 3. Install Debug Provider (Unconditionally)
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        Log.e("TaskSandwich", "!!! FORCED KEY: D8119CA6... INSTALLED !!!")
    }
}