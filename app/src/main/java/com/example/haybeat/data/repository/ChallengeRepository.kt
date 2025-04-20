package com.example.haybeat.data.repository

import android.util.Log
import com.example.haybeat.data.model.Challenge
import com.example.haybeat.data.model.ParticipantProgress
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

// Handles data operations for Challenges.
class ChallengeRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val challengesCollection = firestore.collection("challenges")
    private val logTag = "ChallengeRepository"

    private fun getUserId(): String? = auth.currentUser?.uid
    private fun getUserName(): String? = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
        ?: auth.currentUser?.email?.substringBefore('@')
        ?: "Anonymous"

    /** Fetches ALL active challenges ONCE. */
    suspend fun fetchAllActiveChallenges(): Result<List<Challenge>> = withContext(Dispatchers.IO) {
        Log.d(logTag, "fetchAllActiveChallenges: Fetching.")
        return@withContext try {
            val querySnapshot = challengesCollection
                .whereEqualTo("active", true) // Ensure correct field name 'isActive'
                .orderBy("startDate", Query.Direction.DESCENDING) // Assumes index exists
                .get()
                .await()
            val challenges = querySnapshot.toObjects(Challenge::class.java)
            Log.i(logTag, "fetchAllActiveChallenges: Success, fetched ${challenges.size}.")
            Result.success(challenges)
        } catch (e: Exception) {
            Log.e(logTag, "fetchAllActiveChallenges: Error", e)
            Result.failure(e)
        }
    }

    /** Creates a new challenge document. */
    suspend fun createChallenge(challenge: Challenge): Result<String> = withContext(Dispatchers.IO) {
        val userId = getUserId()
        val userName = getUserName()
        if (userId == null) return@withContext Result.failure(Exception("User not logged in"))
        Log.d(logTag, "createChallenge: User=$userId, Name=${challenge.name}")

        return@withContext try {
            // --- Prepare Challenge Data ---
            challenge.ownerId = userId

            // *** FIX: Call ParticipantProgress constructor with ALL required fields ***
            val ownerProgress = ParticipantProgress(
                userId = userId,
                userName = userName,
                progress = 0,
                currentStreak = 0, // Initialize streaks
                longestStreak = 0,
                lastParticipationDate = null
            )

            // Ensure owner is participant and has progress entry
            val initialParticipants = (challenge.participantIds + userId).toSet().toList()
            // Filter out any existing entry for owner, then add the new ownerProgress
            val existingProgressWithoutOwner = challenge.participantsProgress.filterNot { it.userId == userId }

            val initialProgress = (existingProgressWithoutOwner + ownerProgress).distinctBy { participant -> participant.userId }


            challenge.participantIds = initialParticipants
            challenge.participantsProgress = initialProgress
            challenge.startDate = Date() // Set start date now
            challenge.isActive = true    // Start as active

            val docRef = challengesCollection.add(challenge).await()
            Log.i(logTag, "createChallenge: Success, ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(logTag, "createChallenge: Error creating challenge", e)
            Result.failure(e)
        }
    }

    suspend fun joinChallenge(challengeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getUserId()
        val userName = getUserName()
        if (userId == null) return@withContext Result.failure(Exception("User not logged in"))
        Log.d(logTag, "joinChallenge: User=$userId, ChallengeID=$challengeId")

        return@withContext try {
            val challengeRef = challengesCollection.document(challengeId)

            val newParticipantProgress = ParticipantProgress(
                userId = userId,
                userName = userName,
                progress = 0,
                currentStreak = 0, // Initialize streaks
                longestStreak = 0,
                lastParticipationDate = null
            )

            // Use FieldValue.arrayUnion for atomic addition
            challengeRef.update(
                "participantIds", FieldValue.arrayUnion(userId),
                "participantsProgress", FieldValue.arrayUnion(newParticipantProgress)
            ).await()
            Log.i(logTag, "joinChallenge: User $userId successfully joined $challengeId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(logTag, "joinChallenge: Error joining $challengeId", e)
            Result.failure(e)
        }
    }

    /** Allows the current user to leave a challenge. (Revised for Progress Removal) */
    suspend fun leaveChallenge(challengeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getUserId() ?: return@withContext Result.failure(Exception("User not logged in"))
        Log.d(logTag, "leaveChallenge: User=$userId, ChallengeID=$challengeId")

        return@withContext try {
            val challengeRef = challengesCollection.document(challengeId)
            // 1. Fetch current data (Needed to filter progress list non-atomically)
            val challenge = challengeRef.get().await().toObject(Challenge::class.java)
                ?: return@withContext Result.failure(Exception("Challenge $challengeId not found"))

            // 2. Check if owner (disallow leaving for simplicity)
            if (challenge.ownerId == userId) {
                Log.w(logTag, "leaveChallenge: Owner cannot leave challenge $challengeId directly.")
                return@withContext Result.failure(Exception("Challenge owner cannot leave."))
            }

            // 3. Check participation
            if (!challenge.participantIds.contains(userId)) {
                Log.w(logTag, "leaveChallenge: User $userId not in challenge $challengeId.")
                return@withContext Result.success(Unit) // Already left or never joined
            }

            // 4. Prepare updates: Atomically remove ID, non-atomically remove progress entry
            val updatedProgressList = challenge.participantsProgress.filterNot { it.userId == userId }
            val updates = mapOf(
                "participantIds" to FieldValue.arrayRemove(userId), // Atomic removal
                "participantsProgress" to updatedProgressList      // OVERWRITE with filtered list
            )

            // 5. Apply updates
            challengeRef.update(updates).await()
            Log.i(logTag, "leaveChallenge: User $userId successfully left $challengeId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(logTag, "leaveChallenge: Error leaving $challengeId for user $userId", e)
            Result.failure(e)
        }
    }


    /** Fetches a single challenge document by its ID once. */
    suspend fun getChallenge(challengeId: String): Challenge? = withContext(Dispatchers.IO) {
        Log.d(logTag, "getChallenge: Fetching ID $challengeId")
        return@withContext try {
            val documentSnapshot = challengesCollection.document(challengeId).get().await()
            documentSnapshot.toObject(Challenge::class.java)
        } catch (e: Exception) {
            Log.e(logTag, "getChallenge: Error getting challenge $challengeId", e)
            null
        }
    }
    /** Updates progress. WARNING: Non-atomic fetch-modify-write. */
    suspend fun updateUserChallengeProgress(challengeId: String, userIdToUpdate: String, newProgress: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val currentUserName = getUserName()
        Log.d(logTag, "updateUserChallengeProgress: User=$userIdToUpdate, Challenge=$challengeId, Progress=$newProgress")
        return@withContext try {
            val challengeRef = challengesCollection.document(challengeId)
            val challengeSnapshot = challengeRef.get().await()
            val challenge = challengeSnapshot.toObject(Challenge::class.java)
                ?: return@withContext Result.failure(Exception("Challenge $challengeId not found"))
            if (!challenge.participantIds.contains(userIdToUpdate)) {
                return@withContext Result.failure(Exception("User $userIdToUpdate not in challenge $challengeId"))
            }
            var userProgressFound = false
            val updatedProgressList = challenge.participantsProgress.map { participant ->
                if (participant.userId == userIdToUpdate) {
                    userProgressFound = true
                    participant.copy(
                        progress = newProgress,
                        userName = participant.userName?.takeIf { it.isNotBlank() } ?: currentUserName
                        // Note: Streak fields are NOT updated here
                    )
                } else participant
            }.toMutableList()
            if (!userProgressFound) {
                Log.w(logTag,"Inconsistency: Adding missing progress entry for $userIdToUpdate")
                updatedProgressList.add(ParticipantProgress(userIdToUpdate, currentUserName, newProgress))
            }
            challengeRef.update("participantsProgress", updatedProgressList).await()
            Log.i(logTag, "updateUserChallengeProgress: Success for $userIdToUpdate in $challengeId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(logTag, "updateUserChallengeProgress: Error for $userIdToUpdate in $challengeId", e)
            Result.failure(e)
        }
    }

}