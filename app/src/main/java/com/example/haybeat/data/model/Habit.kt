package com.example.haybeat.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Represents a single habit tracked by the user.
data class Habit(
    @DocumentId // Tells Firestore to populate this field with the document's ID
    var id: String = "", // Document ID from Firestore

    var userId: String = "", // ID of the user who owns this habit

    var name: String = "", // User-defined name of the habit (e.g., "Meditate")
    var category: String = "Other", // User-selected category (e.g., "Mindfulness")
    var colorHex: String = "#FF14B8A6", // Hex color code for UI indicator (Default Teal)
    var priority: String = "medium", // Priority level ("high", "medium", "low")

    // Frequency details
    var frequencyType: String = "daily", // How often ("daily", "weekly", "specific_days")
    var weeklyGoal: Int = 7, // Target completions per week (used if frequencyType is "weekly")
    var specificDays: List<Int>? = null, // Days of week (1=Mon, 7=Sun) (used if frequencyType is "specific_days")

    var reminderTime: String? = null, // Reminder time in "HH:mm" format (e.g., "08:00"), or null if none

    // Tracking stats
    var streak: Int = 0, // Current consecutive completion streak
    var longestStreak: Int = 0, // Longest streak ever achieved for this habit
    var totalCompletions: Int = 0, // Total number of times this habit was completed

    @ServerTimestamp // Firestore automatically sets this when the document is created
    var createdAt: Date? = null, // Timestamp when the habit was first created

    var lastCompletionDate: Date? = null, // Timestamp of the last recorded completion (used for streak calculation)

    var isArchived: Boolean = false // Flag to hide habit without deleting data
) {
    // Add a no-argument constructor for Firestore deserialization if needed,
    // though data classes often work directly.
    // constructor() : this("", "", "", "Other", "#FF14B8A6", "medium", "daily", 7, null, null, 0, 0, 0, null, null, false)
}