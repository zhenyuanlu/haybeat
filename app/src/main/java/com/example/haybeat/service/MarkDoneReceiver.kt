package com.example.haybeat.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.haybeat.data.repository.HabitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.*

// Receiver to handle the "Mark as Done" action from a notification.
class MarkDoneReceiver : BroadcastReceiver() {

    // Use SupervisorJob so one failure doesn't cancel others, plus IO dispatcher
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_ID)
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1) // Get notification ID passed from reminder

        Log.d("MarkDoneReceiver", "Received Mark Done action for Habit ID: $habitId, Notification ID: $notificationId")

        if (habitId == null || habitId.isBlank()) {
            Log.e("MarkDoneReceiver", "Habit ID is missing or blank.")
            return
        }

        if (notificationId == -1) {
            Log.e("MarkDoneReceiver", "Notification ID is missing.")

        }


        // Launch a coroutine to perform the database operation
        scope.launch {
            val repository = HabitRepository() // Direct instantiation; use DI ideally
            // Mark the habit as complete for TODAY's date
            // The 'currentlyCompleted' state is assumed to be false when this action is available
            val result = repository.toggleHabitCompletion(habitId, Date(), false)

            if (result.isSuccess) {
                Log.d("MarkDoneReceiver", "Successfully marked habit $habitId as done from notification.")
                // Dismiss the notification if ID is valid
                if (notificationId != -1) {
                    NotificationManagerCompat.from(context).cancel(notificationId)
                    Log.d("MarkDoneReceiver", "Dismissed notification $notificationId.")
                }
            } else {
                Log.e("MarkDoneReceiver", "Failed to mark habit $habitId as done: ${result.exceptionOrNull()?.message}")

            }
        }
    }
}