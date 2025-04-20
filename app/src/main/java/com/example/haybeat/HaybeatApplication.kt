package com.example.haybeat

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import android.util.Log

class HaybeatApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Log.i("HaybeatApplication", "FirebaseApp initialized successfully.")

            // Optional but Recommended: Initialize App Check
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.i("HaybeatApplication", "Firebase App Check with Play Integrity installed.")

        } catch (e: Exception) {
            Log.e("HaybeatApplication", "Error initializing Firebase", e)
            // Handle initialization error if necessary
        }

    }
}