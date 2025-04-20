package com.example.haybeat.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService // KTX for getSystemService
import com.example.haybeat.data.model.Habit // Make sure Habit is imported
import com.example.haybeat.data.repository.HabitRepository
import kotlinx.coroutines.Dispatchers
// No Flow imports needed if only using fetchUserHabits
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Singleton object responsible for scheduling and canceling habit reminder alarms
 * using Android's AlarmManager.
 */
object AlarmScheduler {

    // Intent Extras Keys
    const val EXTRA_HABIT_ID = "com.example.haybeat.HABIT_ID"
    const val EXTRA_HABIT_NAME = "com.example.haybeat.HABIT_NAME"

    // Action for the reminder intent (helps differentiate intents)
    private const val ACTION_REMIND = "com.example.haybeat.ACTION_REMIND"

    // Base value for PendingIntent request codes to help ensure uniqueness per habit.
    private const val REQUEST_CODE_PREFIX = 1000

    /**
     * Schedules a repeating reminder notification for a specific habit.
     * NOTE: Current implementation uses setRepeating, which becomes inexact on newer Android versions.
     * For precise delivery and complex frequencies (weekly/specific days), use setExactAndAllowWhileIdle
     * and reschedule the *next* alarm from the HabitReminderReceiver.
     *
     * @param context Context
     * @param habitId Unique ID of the habit.
     * @param habitName Name of the habit (for notification content).
     * @param hour Hour of the day (0-23) for the reminder.
     * @param minute Minute of the hour (0-59) for the reminder.
     */
    @SuppressLint("ScheduleExactAlarm") // Suppress lint warning, permission checked below
    fun scheduleHabitReminder(context: Context, habitId: String, habitName: String, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService<AlarmManager>() // Use KTX extension
        if (alarmManager == null) {
            Log.e("AlarmScheduler", "AlarmManager not available.")
            return
        }

        // --- Permission Check (Android 12+ for Exact Alarms) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("AlarmScheduler", "Cannot schedule exact alarms. Reminder for '$habitName' ($habitId) might be delayed or not fire.")
                // TODO: Guide user to grant SCHEDULE_EXACT_ALARM permission in app settings.
            }
        }

        // --- Create Intent for the BroadcastReceiver ---
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_REMIND // Set action
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_HABIT_NAME, habitName)
        }

        // --- Create PendingIntent ---
        val requestCode = generateRequestCode(habitId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // --- Calculate Trigger Time ---
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis() // Start with current time
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
                Log.d("AlarmScheduler", "Reminder time for $habitId ($hour:$minute) already passed today. Scheduling for tomorrow.")
            }
        }
        val triggerTimeMillis = calendar.timeInMillis

        // --- Schedule the Alarm ---
        try {
            // Using setRepeating for simplicity (Daily only)
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            Log.d("AlarmScheduler", "Scheduled REPEATING reminder for '$habitName' ($habitId) at ${Date(triggerTimeMillis)} (requestCode $requestCode)")

        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "SecurityException: Cannot schedule alarm for $habitId. Check permissions (SCHEDULE_EXACT_ALARM?).", e)
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Exception scheduling alarm for $habitId", e)
        }
    }

    /**
     * Cancels any scheduled reminder for the given habit ID.
     *
     * @param context Context
     * @param habitId Unique ID of the habit whose reminder should be cancelled.
     */
    fun cancelHabitReminder(context: Context, habitId: String) {
        val alarmManager = context.getSystemService<AlarmManager>()
        if (alarmManager == null) {
            Log.e("AlarmScheduler", "AlarmManager not available for cancellation.")
            return
        }
        val intent = Intent(context, HabitReminderReceiver::class.java).apply {
            action = ACTION_REMIND
        }
        val requestCode = generateRequestCode(habitId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            try {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d("AlarmScheduler", "Canceled reminder for habit $habitId with request code $requestCode")
            } catch (e: Exception) {
                Log.e("AlarmScheduler", "Error cancelling alarm for habit $habitId", e)
            }
        } else {
            Log.d("AlarmScheduler", "No active reminder found to cancel for habit $habitId with request code $requestCode.")
        }
    }

    /**
     * Generates a reasonably unique request code for a PendingIntent based on the habit ID.
     */
    fun generateRequestCode(habitId: String): Int {
        // Simple hash - Keep the code positive and within a reasonable range
        return REQUEST_CODE_PREFIX + (habitId.hashCode() and 0x7FFFFFFF % 50000)
    }

    /**
     * Reschedules alarms for all active habits that have reminders set.
     * Should be called on device boot or app update.
     * Performs database access and should be called from a background thread/coroutine.
     *
     * @param context Application context.
     */
    suspend fun rescheduleAllAlarms(context: Context) = withContext(Dispatchers.IO) {
        Log.i("AlarmScheduler", "Attempting to reschedule all alarms...")
        val repository = HabitRepository() // Use DI ideally
        try {
            // Fetch the habits list using the new one-time fetch method
            val habitResult = repository.fetchUserHabits() // Use fetchUserHabits

            if (habitResult.isFailure) {
                Log.e("AlarmScheduler", "Error fetching habits for rescheduling", habitResult.exceptionOrNull())
                return@withContext // Exit if fetch failed
            }

            val habits: List<Habit> = habitResult.getOrNull() ?: emptyList() // Get list on success

            if (habits.isEmpty()) {
                Log.i("AlarmScheduler", "No habits found to reschedule.")
                return@withContext
            }

            Log.d("AlarmScheduler", "Found ${habits.size} habits to check for rescheduling.")
            var rescheduledCount = 0
            // Iterate over the fetched list
            habits.forEach { habit -> // Iterate over the fetched list
                // Access fields directly from the 'habit' object in the loop
                if (!habit.isArchived && !habit.reminderTime.isNullOrBlank()) { // Use habit.isArchived, habit.reminderTime
                    try {
                        val parts = habit.reminderTime!!.split(":") // Use habit.reminderTime
                        if (parts.size == 2) {
                            val hour = parts[0].toIntOrNull()
                            val minute = parts[1].toIntOrNull()

                            if (hour != null && minute != null) {
                                // Cancel first to avoid duplicates if time changed
                                cancelHabitReminder(context, habit.id) // Use habit.id
                                // Schedule with correct details
                                scheduleHabitReminder(context, habit.id, habit.name, hour, minute) // Use habit.id, habit.name
                                rescheduledCount++
                            } else {
                                Log.w("AlarmScheduler", "Invalid reminder format for habit ${habit.id}: ${habit.reminderTime}")
                            }
                        } else {
                            Log.w("AlarmScheduler", "Invalid reminder format for habit ${habit.id}: ${habit.reminderTime}")
                        }
                    } catch (e: Exception) {
                        Log.e("AlarmScheduler", "Error parsing/rescheduling reminder for habit ${habit.id} ('${habit.name}')", e)
                    }
                } else {
                    // If no reminder or archived, ensure any old alarm is cancelled
                    cancelHabitReminder(context, habit.id) // Use habit.id
                }
            }
            Log.i("AlarmScheduler", "Rescheduling finished. Rescheduled $rescheduledCount alarms.")
        } catch (e: Exception) { // Catch potential errors during the overall process
            Log.e("AlarmScheduler", "Unexpected error during rescheduling process", e)
        }
    }
}