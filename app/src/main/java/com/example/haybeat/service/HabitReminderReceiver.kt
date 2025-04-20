package com.example.haybeat.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.haybeat.MainActivity // Activity to open when notification is tapped
import com.example.haybeat.R
import java.util.concurrent.TimeUnit

class HabitReminderReceiver : BroadcastReceiver() {

    // Unique ID for the notification channel
    private val CHANNEL_ID = "HABIT_REMINDERS_CHANNEL"
    // Action string for the Mark Done intent
    private val ACTION_MARK_DONE = "com.example.haybeat.ACTION_MARK_DONE"


    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val habitId = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_ID)
        val habitName = intent.getStringExtra(AlarmScheduler.EXTRA_HABIT_NAME) ?: "your habit"

        Log.d("HabitReminderReceiver", "Received broadcast: Action=$action, HabitId=$habitId")

        if (habitId == null) {
            Log.e("HabitReminderReceiver", "Habit ID is missing in the received intent.")
            return
        }

        // --- TODO: Implement Rescheduling Logic ---
        // If using setExactAndAllowWhileIdle in AlarmScheduler, you *must* reschedule
        // the *next* alarm occurrence here based on habit frequency. This requires:
        // 1. Running a background task (CoroutineScope/WorkManager)
        // 2. Fetching the habit details (using HabitRepository)
        // 3. Calculating the next valid trigger time based on frequencyType/specificDays
        // 4. Calling AlarmScheduler.scheduleHabitReminder(...) for the next time.
        // For simplicity, this example assumes setRepeating was used or rescheduling is handled elsewhere.
        Log.w("HabitReminderReceiver", "Rescheduling logic for next occurrence is NOT implemented.")


        // Proceed to show the notification
        createNotificationChannel(context)
        showNotification(context, habitId, habitName)

    }

    // Creates the notification channel (required for Android 8.0 Oreo and above)
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.habit_reminder_channel_name)
            val descriptionText = context.getString(R.string.habit_reminder_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH // High importance for reminders
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                // Configure other channel properties like light color, sound, etc. if needed
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Check if channel already exists before creating
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                notificationManager.createNotificationChannel(channel)
                Log.d("HabitReminderReceiver", "Notification channel created: $CHANNEL_ID")
            } else {
                Log.d("HabitReminderReceiver", "Notification channel already exists: $CHANNEL_ID")
            }
        }
    }

    // Builds and displays the notification
    private fun showNotification(context: Context, habitId: String, habitName: String) {
        // --- Intent when notification is tapped ---
        // Opens MainActivity (you could add extras to navigate to the specific habit)
        val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NAVIGATE_TO_HABIT_ID", habitId) // Add extra to handle in MainActivity
        }
        // Unique request code for the main content PendingIntent
        val contentRequestCode = AlarmScheduler.generateRequestCode(habitId) + 1 // Offset from alarm code
        val pendingIntent = PendingIntent.getActivity(
            context,
            contentRequestCode,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markDoneIntent = Intent(context, MarkDoneReceiver::class.java).apply { // Target the new MarkDoneReceiver
            action = ACTION_MARK_DONE
            putExtra(AlarmScheduler.EXTRA_HABIT_ID, habitId)
            // Pass notification ID so MarkDoneReceiver can cancel it
            putExtra("NOTIFICATION_ID", AlarmScheduler.generateRequestCode(habitId))
        }
        // Unique request code for the action PendingIntent
        val markDoneRequestCode = AlarmScheduler.generateRequestCode(habitId) + 2 // Different offset
        val markDonePendingIntent = PendingIntent.getBroadcast(
            context,
            markDoneRequestCode,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_square) // App icon for notification
            .setContentTitle(context.getString(R.string.reminder_title, habitName)) // e.g., "Time for Meditate!"
            .setContentText(context.getString(R.string.reminder_text)) // Generic reminder text
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Ensure it pops up
            .setContentIntent(pendingIntent) // Set tap action
            .setAutoCancel(true) // Dismiss notification when tapped
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Use default sound, vibrate, lights
            .addAction(R.drawable.ic_check_circle_teal, context.getString(R.string.mark_done), markDonePendingIntent) // Add action button



        // --- Show Notification (Check Permission) ---
        // Check for POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        {
            Log.w("HabitReminderReceiver", "POST_NOTIFICATIONS permission not granted. Cannot show reminder for $habitId.")
          return
        }

        // Get unique notification ID based on habit
        val notificationId = AlarmScheduler.generateRequestCode(habitId)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.d("HabitReminderReceiver", "Notification shown for '$habitName' (ID: $notificationId)")
        } catch (e: SecurityException) {
            Log.e("HabitReminderReceiver", "SecurityException showing notification: ${e.message}")
            // This might happen if permission check fails unexpectedly
        } catch (e: Exception) {
            Log.e("HabitReminderReceiver", "Error showing notification: ${e.message}")
        }
    }
}