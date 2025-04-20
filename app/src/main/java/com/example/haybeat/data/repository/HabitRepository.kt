package com.example.haybeat.data.repository
import com.google.firebase.firestore.Source
import android.util.Log
import com.example.haybeat.data.model.Habit
import com.example.haybeat.data.model.HabitCompletion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
// No ktx.snapshots import needed now

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow // Keep Flow if used elsewhere
import kotlinx.coroutines.flow.flow // Keep flow if used elsewhere
import kotlinx.coroutines.flow.flowOn // Keep flowOn if used elsewhere
import kotlinx.coroutines.flow.mapNotNull // Keep mapNotNull if used elsewhere
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HabitRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val habitsCollection = firestore.collection("habits")
    private val completionsCollection = firestore.collection("completions")

    private fun getUserId(): String? = auth.currentUser?.uid
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun getCompletionDocId(habitId: String, date: Date): String { /* ... */
        return "${habitId}_${dateFormat.format(date)}"
    }


    suspend fun fetchUserHabits(): Result<List<Habit>> = withContext(Dispatchers.IO) {
        val userId = getUserId()
        Log.d("HabitRepo_DEBUG", "fetchUserHabits (SIMPLIFIED QUERY) called for userId: $userId")
        if (userId == null) { return@withContext Result.success(emptyList()) }

        try {
            val querySnapshot = habitsCollection
                .whereEqualTo("userId", userId)
                .get(Source.SERVER)
                .await()

            val habits = querySnapshot.toObjects(Habit::class.java)
            Log.d("HabitRepo_DEBUG", "fetchUserHabits (SIMPLIFIED QUERY) fetched ${habits.size} habits (Source: Server).")
            Result.success(habits)

        } catch (e: Exception) {
            Log.e("HabitRepo_DEBUG", "Error fetching habits (SIMPLIFIED QUERY)", e)
            Result.failure(e)
        }
    }



    suspend fun getHabit(habitId: String): Habit? {
        return withContext(Dispatchers.IO) {
            try {
                val documentSnapshot = habitsCollection.document(habitId).get().await()
                documentSnapshot.toObject(Habit::class.java)
            } catch (e: Exception) {
                Log.e("HabitRepository", "Error getting habit $habitId", e)
                null
            }
        }
    }
    suspend fun addHabit(habit: Habit): Result<String> {
        return withContext(Dispatchers.IO) {
            val userId = getUserId() ?: return@withContext Result.failure(Exception("User not logged in"))
            try {
                habit.userId = userId
                Log.d("HabitRepo_DEBUG", "Attempting to ADD habit:")
                Log.d("HabitRepo_DEBUG", "  Name: ${habit.name}")
                Log.d("HabitRepo_DEBUG", "  UserId: ${habit.userId}")
                Log.d("HabitRepo_DEBUG", "  IsArchived: ${habit.isArchived}")
                Log.d("HabitRepo_DEBUG", "  Category: ${habit.category}")
                Log.d("HabitRepo_DEBUG", "  Timestamp: ${habit.createdAt}")
                val docRef = habitsCollection.add(habit).await()
                Log.d("HabitRepo_DEBUG", "Habit added successfully with ID: ${docRef.id}")
                Result.success(docRef.id)
            } catch (e: Exception) {
                Log.e("HabitRepository", "Error adding habit", e)
                Result.failure(e)
            }
        }
    }
    suspend fun updateHabit(habit: Habit): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val userId = getUserId() ?: return@withContext Result.failure(Exception("User not logged in"))
            if (habit.id.isBlank()) return@withContext Result.failure(Exception("Habit ID is missing for update"))
            try {
                habitsCollection.document(habit.id).set(habit).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("HabitRepository", "Error updating habit ${habit.id}", e)
                Result.failure(e)
            }
        }
    }
    suspend fun getCompletionsForHabit(habitId: String, startDate: Date, endDate: Date): List<HabitCompletion> { /* ... same as before ... */
        return withContext(Dispatchers.IO) {
            val userId = getUserId() ?: return@withContext emptyList()
            val startStr = dateFormat.format(startDate)
            val endStr = dateFormat.format(endDate)
            try {
                val querySnapshot = completionsCollection
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("habitId", habitId)
                    .whereGreaterThanOrEqualTo("dateString", startStr)
                    .whereLessThanOrEqualTo("dateString", endStr)
                    .get()
                    .await()
                querySnapshot.toObjects(HabitCompletion::class.java)
            } catch (e: Exception) {
                Log.e("HabitRepository", "Error getting completions for $habitId between $startStr and $endStr", e)
                emptyList()
            }
        }
    }
    suspend fun getCompletionStatus(habitId: String, date: Date): HabitCompletion? { /* ... same as before ... */
        return withContext(Dispatchers.IO) {
            val docId = getCompletionDocId(habitId, date)
            try {
                val documentSnapshot = completionsCollection.document(docId).get().await()
                documentSnapshot.toObject(HabitCompletion::class.java)
            } catch (e: Exception) {
                Log.e("HabitRepository", "Error getting completion status for $docId", e)
                null
            }
        }
    }
    suspend fun toggleHabitCompletion(habitId: String, date: Date, currentlyCompleted: Boolean): Result<Unit> { /* ... same as before ... */
        return withContext(Dispatchers.IO) {
            val userId = getUserId() ?: return@withContext Result.failure(Exception("User not logged in"))
            val dateString = dateFormat.format(date)
            val completionDocId = getCompletionDocId(habitId, date)
            val completionDocRef = completionsCollection.document(completionDocId)
            val habitDocRef = habitsCollection.document(habitId)
            try {
                firestore.runTransaction { transaction ->
                    val habitSnapshot = transaction.get(habitDocRef)
                    val habit = habitSnapshot.toObject(Habit::class.java)
                        ?: throw Exception("Habit $habitId not found during transaction")
                    val shouldBeCompleted = !currentlyCompleted
                    var newStreak = habit.streak; var newTotal = habit.totalCompletions
                    var newLongestStreak = habit.longestStreak; var newLastCompletionDate: Date? = habit.lastCompletionDate
                    val todayCal = Calendar.getInstance().apply { time = date }
                    val yesterdayCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
                    if (shouldBeCompleted) {
                        val completion = HabitCompletion(completionDocId, habitId, userId, dateString, true)
                        transaction.set(completionDocRef, completion)
                        newTotal++
                        if (habit.lastCompletionDate != null) {
                            val lastCompCal = Calendar.getInstance().apply { time = habit.lastCompletionDate!! }
                            if (isSameDay(lastCompCal, yesterdayCal)) newStreak++
                            else if (!isSameDay(lastCompCal, todayCal)) newStreak = 1
                        } else newStreak = 1
                        newLastCompletionDate = date
                        if (newStreak > newLongestStreak) newLongestStreak = newStreak
                    } else {
                        transaction.delete(completionDocRef)
                        newTotal = (newTotal - 1).coerceAtLeast(0)
                        val todayDateOnly = startOfDay(date)
                        val lastCompDateOnly = habit.lastCompletionDate?.let { startOfDay(it) }
                        if (lastCompDateOnly == todayDateOnly) {
                            newStreak = (newStreak - 1).coerceAtLeast(0)
                        }
                    }
                    transaction.update(habitDocRef, mapOf(
                        "streak" to newStreak, "totalCompletions" to newTotal,
                        "longestStreak" to newLongestStreak, "lastCompletionDate" to newLastCompletionDate
                    ))
                }.await()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("HabitRepository", "Error toggling completion for $completionDocId", e)
                Result.failure(e)
            }
        }
    }
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
    private fun startOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
    }
    suspend fun archiveHabit(habitId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val userId = getUserId() ?: return@withContext Result.failure(Exception("User not logged in"))
            if (habitId.isBlank()) return@withContext Result.failure(Exception("Habit ID is missing"))
            try {
                habitsCollection.document(habitId).update("isArchived", true).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("HabitRepository", "Error archiving habit $habitId", e)
                Result.failure(e)
            }
        }
    }
    suspend fun deleteHabitPermanently(habitId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val userId = getUserId() ?: return@withContext Result.failure(Exception("User not logged in"))
            if (habitId.isBlank()) return@withContext Result.failure(Exception("Habit ID is missing"))
            try {
                Log.w("HabitRepository", "Permanent delete needs batch deletion of completions for $habitId")
                // TODO: Implement batch deletion of completion records here
                habitsCollection.document(habitId).delete().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("HabitRepository", "Error permanently deleting habit $habitId", e)
                Result.failure(e)
            }
        }
    }
}