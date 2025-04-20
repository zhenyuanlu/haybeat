package com.example.haybeat.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import com.google.firebase.firestore.PropertyName

// Represents progress for a single participant in a challenge.

// Represents a habit challenge involving multiple users.
data class Challenge(
    @DocumentId
    var id: String = "", // Document ID from Firestore

    var name: String = "", // Name of the challenge (e.g., "30-Day Meditation Challenge")
    var description: String? = null, // Optional description
    var ownerId: String = "", // User ID of the challenge creator
    var goalDescription: String = "", // Text describing the goal (e.g., "Highest consistency %")

    // List of user IDs participating in the challenge
    var participantIds: List<String> = listOf(),

    // List containing progress details for each participant
    // Storing this directly simplifies leaderboard display but requires careful updates.
    var participantsProgress: List<ParticipantProgress> = listOf(),

    @ServerTimestamp
    var startDate: Date? = null, // When the challenge started or was created
    var endDate: Date? = null, // When the challenge is scheduled to end

    var isActive: Boolean = true // Flag to mark challenge as active or finished
) {
    // No-arg constructor for Firestore
    // constructor() : this("", "", null, "", "", listOf(), listOf(), null, null, true)
}