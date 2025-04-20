package com.example.haybeat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp // Import FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Import withContext

class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.e("BootReceiver", "Received null context or intent.")
            return
        }

        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i("BootReceiver", "Boot completed event received ($action). Initiating alarm rescheduling.")

            // Launch background task
            scope.launch {
                // **** Ensure Firebase is initialized before proceeding ****
                val firebaseInitialized = ensureFirebaseInitialized(context.applicationContext)

                if (firebaseInitialized) {
                    // Firebase is ready, proceed with rescheduling
                    Log.d("BootReceiver", "Firebase confirmed initialized. Calling rescheduleAllAlarms.")
                    AlarmScheduler.rescheduleAllAlarms(context.applicationContext)
                } else {
                    // Firebase failed to initialize, cannot reschedule reliably
                    Log.e("BootReceiver", "Firebase initialization failed in BootReceiver. Cannot reschedule alarms.")
                }
            }
        } else {
            Log.d("BootReceiver", "Received unrelated broadcast action: $action")
        }
    }

    /**
     * Checks if Firebase is initialized, attempts initialization if not.
     * Runs on the caller's dispatcher (expected to be IO).
     * Returns true if Firebase is initialized, false otherwise.
     */
    private suspend fun ensureFirebaseInitialized(appContext: Context): Boolean {
        // Use withContext(Dispatchers.IO) explicitly if needed, though scope is already IO
        return try {
            // Check if the default app already exists
            FirebaseApp.getInstance() // This throws if not initialized
            Log.d("BootReceiver", "FirebaseApp.getInstance() succeeded (already initialized).")
            true
        } catch (e: IllegalStateException) {
            // Not initialized, attempt to initialize now
            Log.w("BootReceiver", "FirebaseApp not initialized, attempting initialization...")
            try {
                FirebaseApp.initializeApp(appContext)
                Log.i("BootReceiver", "FirebaseApp initialized successfully from BootReceiver.")
                // Optional: Initialize App Check here as well if needed in this context
                // val firebaseAppCheck = FirebaseAppCheck.getInstance()
                // firebaseAppCheck.installAppCheckProviderFactory(...)
                true
            } catch (initError: Exception) {
                Log.e("BootReceiver", "CRITICAL: Error initializing FirebaseApp from BootReceiver", initError)
                false // Initialization failed
            }
        } catch (otherError: Exception) {
            Log.e("BootReceiver", "Unexpected error checking FirebaseApp instance", otherError)
            false // Assume not initialized if other error occurs
        }
    }
}