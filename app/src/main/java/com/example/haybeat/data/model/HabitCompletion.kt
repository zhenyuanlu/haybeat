package com.example.haybeat.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Represents a single completion record for a habit on a specific date.
// Can be stored in a top-level 'completions' collection or as a subcollection under each habit.
// Using a top-level collection with a specific ID format here.
data class HabitCompletion(
    // Example: Document ID could be structured like "habitId_yyyy-MM-dd"
    @DocumentId
    var id: String = "", // Combination of habit ID and date (e.g., "habit123_2025-03-24")

    var habitId: String = "", // ID of the habit this completion belongs to
    var userId: String = "", // ID of the user who completed it
    var dateString: String = "", // Date of completion formatted as "yyyy-MM-dd" for querying

    var completed: Boolean = true, // Always true when created, record deleted if un-marked

    @ServerTimestamp // Timestamp when this completion record was logged
    var timestamp: Date? = null
) {
    // No-arg constructor for Firestore
    // constructor() : this("", "", "", "", true, null)
}