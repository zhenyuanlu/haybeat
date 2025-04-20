package com.example.haybeat.data.model

import com.google.firebase.firestore.ServerTimestamp // Keep if timestamp needed elsewhere, not strictly here
import java.util.Date

// Represents progress for a single participant in a challenge.
data class ParticipantProgress(
    var userId: String = "",          // ID of the participant
    var userName: String? = null,     // User's display name (denormalized)
    var progress: Int = 0,            // Existing progress value (can represent score, etc.)
    var currentStreak: Int = 0,       // NEW: Consecutive days participated in *this* challenge
    var longestStreak: Int = 0,
    var lastParticipationDate: Date? = null // NEW: Last date participation was recorded
) {
    // No-arg constructor required by Firestore for deserialization
    constructor() : this("", null, 0, 0, 0, null)
}

