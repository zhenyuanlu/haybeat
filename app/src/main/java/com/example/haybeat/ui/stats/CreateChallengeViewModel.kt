package com.example.haybeat.ui.stats

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.haybeat.data.model.Challenge
import com.example.haybeat.data.repository.ChallengeRepository
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs // Import abs for duration calculation

// Represents the state of the challenge creation process
sealed class CreateChallengeResult {
    object Loading : CreateChallengeResult()
    data class Success(val challengeId: String) : CreateChallengeResult()
    data class Error(val message: String) : CreateChallengeResult()
}

class CreateChallengeViewModel : ViewModel() {

    private val repository = ChallengeRepository()
    private val logTag = "CreateChallengeVM"

    // LiveData to report the result back to the Fragment
    private val _creationResult = MutableLiveData<CreateChallengeResult>()
    val creationResult: LiveData<CreateChallengeResult> get() = _creationResult

    /**
     * Attempts to create a new challenge in Firestore.
     *
     * @param name Name of the challenge.
     * @param goalDescription Description of the goal.
     * @param durationDays Optional duration in days. If null or <= 0, no end date is set.
     */
    fun createChallenge(name: String, goalDescription: String, durationDays: Int?) {
        Log.d(logTag, "Attempting to create challenge: Name='$name', Goal='$goalDescription', Duration=$durationDays")
        _creationResult.value = CreateChallengeResult.Loading

        // Simple validation
        if (name.isBlank()) {
            _creationResult.value = CreateChallengeResult.Error("Challenge name cannot be empty.")
            return
        }
        if (goalDescription.isBlank()) {
            _creationResult.value = CreateChallengeResult.Error("Goal description cannot be empty.")
            return
        }

        // Calculate end date if duration is valid
        val endDate: Date? = if (durationDays != null && durationDays > 0) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, durationDays)
            // Optional: Set time to end of day?
            // calendar.set(Calendar.HOUR_OF_DAY, 23)
            // calendar.set(Calendar.MINUTE, 59)
            // calendar.set(Calendar.SECOND, 59)
            calendar.time
        } else {
            null // No end date for ongoing challenges
        }

        // Create the Challenge object (owner/participants added in repo)
        val newChallenge = Challenge(
            name = name,
            goalDescription = goalDescription,
            endDate = endDate
            // ownerId, participantIds, participantsProgress, startDate, isActive set by repo
        )

        viewModelScope.launch {
            val result = repository.createChallenge(newChallenge)
            if (result.isSuccess) {
                val newId = result.getOrNull() ?: "unknown"
                Log.i(logTag, "Challenge created successfully with ID: $newId")
                _creationResult.postValue(CreateChallengeResult.Success(newId))
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to create challenge"
                Log.e(logTag, "Error creating challenge: $errorMsg")
                _creationResult.postValue(CreateChallengeResult.Error(errorMsg))
            }
        }
    }
}