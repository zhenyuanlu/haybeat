package com.example.haybeat.data.model

import com.google.firebase.firestore.DocumentId

// Optional: Represents additional user profile data stored in Firestore /users/{uid}
data class User(
    @DocumentId
    var uid: String = "", // Matches Firebase Auth UID
    var displayName: String? = null,
    var email: String? = null,
    var ageGroup: String? = null, // e.g., "25-34"
    var photoUrl: String? = null,
    var membershipStatus: String? = "free" // e.g., "free", "pro"
    // Add other fields as needed
) {
    // constructor() : this("", null, null, null, null, "free")
}