package com.example.haybeat.ui.stats

import android.util.Log
import androidx.lifecycle.*
import com.example.haybeat.data.model.Challenge
import com.example.haybeat.data.model.Habit
import com.example.haybeat.data.model.HabitCompletion
import com.example.haybeat.data.model.ParticipantProgress
import com.example.haybeat.data.repository.ChallengeRepository
import com.example.haybeat.data.repository.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt

// State data class for the entire Stats screen (Added challenge action loading state)
data class StatsScreenState(
    // Habit Stats
    val overallCompletionThisMonth: Int = 0,
    val completionTrend: List<Pair<Float, Float>> = emptyList(),
    val consistencyScore: Int = 0,
    val consistencyFactors: String = "",
    val completionByDay: List<Pair<String, Int>> = emptyList(),
    val mostEfficientDay: Pair<String, Int>? = null,
    val rankedHabits: List<Habit> = emptyList(),
    // Challenge Data
    val challenges: List<Challenge> = emptyList(),
    val selectedChallengeLeaderboard: List<ParticipantProgress>? = null,
    val selectedChallengeName: String? = null,
    // Loading/Error States
    val isLoadingHabits: Boolean = true,
    val isLoadingChallenges: Boolean = true,
    val isProcessingChallengeAction: Boolean = false, // For Join/Leave actions
    val error: String? = null
)

class StatsViewModel : ViewModel() {

    private val habitRepository = HabitRepository()
    private val challengeRepository = ChallengeRepository()
    private val logTag = "StatsViewModel_DEBUG"

    private val _statsScreenState = MutableLiveData<StatsScreenState>(StatsScreenState())
    val statsScreenState: LiveData<StatsScreenState> get() = _statsScreenState

    init {
        Log.d(logTag, "ViewModel initialized.")
        loadInitialData()
    }

    fun loadInitialData() {
        Log.d(logTag, "loadInitialData called.")
        _statsScreenState.value = StatsScreenState(isLoadingHabits = true, isLoadingChallenges = true) // Reset state
        viewModelScope.launch {
            launch { loadHabitStats() }
            launch { loadAllActiveChallengesOnce() }
        }
    }

    private suspend fun loadHabitStats() {
        Log.d(logTag, "loadHabitStats started.")
        val habitResult = habitRepository.fetchUserHabits()
        if (habitResult.isFailure) {
            handleError("Failed to load habits", habitResult.exceptionOrNull())
            _statsScreenState.postValue(_statsScreenState.value?.copy(isLoadingHabits = false))
            return
        }
        val habits = habitResult.getOrNull() ?: emptyList()
        if (habits.isEmpty()) {
            Log.i(logTag, "No habits found.")
            _statsScreenState.postValue(_statsScreenState.value?.copy(isLoadingHabits = false)) // Clear loading even if empty
            return
        }
        val completions = fetchCompletionsForStats(habits)
        if (completions == null) { // Error handled internally in fetchCompletionsForStats
            _statsScreenState.postValue(_statsScreenState.value?.copy(isLoadingHabits = false))
            return
        }
        try {
            val calculated = calculateAllStatsInternal(habits, completions)
            _statsScreenState.postValue(
                _statsScreenState.value?.copy(
                    isLoadingHabits = false, error = null, // Clear previous errors on success
                    overallCompletionThisMonth = calculated.overallCompletionThisMonth,
                    completionTrend = calculated.completionTrend,
                    consistencyScore = calculated.consistencyScore,
                    consistencyFactors = calculated.consistencyFactors,
                    completionByDay = calculated.completionByDay,
                    mostEfficientDay = calculated.mostEfficientDay,
                    rankedHabits = calculated.rankedHabits
                )
            )
            Log.d(logTag, "Habit stats calculation complete.")
        } catch (e: Exception) {
            handleError("Failed to calculate stats", e)
            _statsScreenState.postValue(_statsScreenState.value?.copy(isLoadingHabits = false))
        }
    }

    // Helper to fetch completions needed for stats
    private suspend fun fetchCompletionsForStats(habits: List<Habit>): List<HabitCompletion>? {
        val calendar = Calendar.getInstance(); val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -30); val startDate = calendar.time // Look back 30 days
        val allCompletions = mutableListOf<HabitCompletion>()
        return try {
            habits.forEach { habit ->
                allCompletions.addAll(habitRepository.getCompletionsForHabit(habit.id, startDate, endDate))
            }
            Log.d(logTag, "Fetched ${allCompletions.size} completions for stats.")
            allCompletions
        } catch (e: Exception) {
            handleError("Could not load completion history", e)
            null // Indicate error
        }
    }


    private suspend fun loadAllActiveChallengesOnce() {
        Log.d(logTag, "loadAllActiveChallengesOnce started.")
        val challengeResult = challengeRepository.fetchAllActiveChallenges()
        if (challengeResult.isSuccess) {
            val challenges = challengeResult.getOrNull() ?: emptyList()
            Log.i(logTag, "Fetched ${challenges.size} active challenges.")
            _statsScreenState.postValue(
                _statsScreenState.value?.copy(isLoadingChallenges = false, challenges = challenges, error = null)
            )
        } else {
            handleError("Failed to load challenges", challengeResult.exceptionOrNull())
            _statsScreenState.postValue(_statsScreenState.value?.copy(isLoadingChallenges = false))
        }
    }

    /** Selects a challenge to display its leaderboard */
    fun selectChallenge(challenge: Challenge) {
        Log.d(logTag, "Challenge selected: ${challenge.name}")
        val sortedLeaderboard = challenge.participantsProgress.sortedByDescending { it.progress }
        _statsScreenState.value = _statsScreenState.value?.copy(
            selectedChallengeLeaderboard = sortedLeaderboard,
            selectedChallengeName = challenge.name
        )
    }

    /** Clears the selected challenge */
    fun clearSelectedChallenge() {
        Log.d(logTag, "Clearing selected challenge.")
        if (_statsScreenState.value?.selectedChallengeLeaderboard != null) {
            _statsScreenState.value = _statsScreenState.value?.copy(selectedChallengeLeaderboard = null, selectedChallengeName = null)
        }
    }

    /** Handles Join Challenge action */
    fun joinChallenge(challengeId: String) {
        if (_statsScreenState.value?.isProcessingChallengeAction == true) return // Prevent double clicks
        Log.d(logTag, "joinChallenge called for ID: $challengeId")
        _statsScreenState.value = _statsScreenState.value?.copy(isProcessingChallengeAction = true, error = null)
        viewModelScope.launch {
            val result = challengeRepository.joinChallenge(challengeId)
            if (result.isSuccess) {
                Log.i(logTag, "Successfully joined challenge $challengeId. Reloading challenges.")
                loadAllActiveChallengesOnce() // Reload list after joining
            } else {
                handleError("Failed to join challenge", result.exceptionOrNull())
            }
            // Always reset processing state, even on error
            _statsScreenState.postValue(_statsScreenState.value?.copy(isProcessingChallengeAction = false))
        }
    }

    /** Handles Leave Challenge action */
    fun leaveChallenge(challengeId: String) {
        if (_statsScreenState.value?.isProcessingChallengeAction == true) return
        Log.d(logTag, "leaveChallenge called for ID: $challengeId")
        _statsScreenState.value = _statsScreenState.value?.copy(isProcessingChallengeAction = true, error = null)
        viewModelScope.launch {
            val result = challengeRepository.leaveChallenge(challengeId)
            if (result.isSuccess) {
                Log.i(logTag, "Successfully left challenge $challengeId. Reloading challenges.")
                // If this challenge was selected, clear the selection
                if (_statsScreenState.value?.selectedChallengeName == _statsScreenState.value?.challenges?.find { it.id == challengeId }?.name) {
                    _statsScreenState.postValue(_statsScreenState.value?.copy(selectedChallengeName = null, selectedChallengeLeaderboard = null))
                }
                loadAllActiveChallengesOnce() // Reload list after leaving
            } else {
                handleError("Failed to leave challenge", result.exceptionOrNull())
            }
            _statsScreenState.postValue(_statsScreenState.value?.copy(isProcessingChallengeAction = false))
        }
    }


    /** Internal calculation function */
    private fun calculateAllStatsInternal(habits: List<Habit>, completions: List<HabitCompletion>): StatsScreenState {
        Log.d(logTag, "Starting internal stats calculation.")
        val completionsByDate = completions.groupBy { it.dateString }
        val completionsByHabit = completions.groupBy { it.habitId }

        val consistency = calculateConsistencyScore(habits, completionsByHabit)
        val completionDay = calculateCompletionByDay(habits, completionsByDate)
        val ranked = habits.sortedByDescending { it.streak }
        val overallCompletion = calculateOverallCompletion(habits, completionsByDate, 30)
        val trend = calculateCompletionTrend(habits, completionsByDate, 4)

        Log.d(logTag, "Internal calculation finished. Consistency=${consistency.first}, Overall=${overallCompletion}")
        // Only return habit-related stats, caller merges state
        return StatsScreenState(
            overallCompletionThisMonth = overallCompletion,
            completionTrend = trend,
            consistencyScore = consistency.first,
            consistencyFactors = consistency.second,
            completionByDay = completionDay.first,
            mostEfficientDay = completionDay.second,
            rankedHabits = ranked
        )
    }

    // --- Calculation Helpers (Implementations from previous response) ---
    private fun calculateConsistencyScore(habits: List<Habit>, completionsByHabit: Map<String, List<HabitCompletion>>): Pair<Int, String> { /* ... same logic ... */ if(habits.isEmpty()) return Pair(0,"");val totalStreaks=habits.sumOf { it.streak };val maxStreakPoints=10;val streakFactor=min((totalStreaks.toFloat()/(habits.size*5).coerceAtLeast(1)),1f)*maxStreakPoints;val cal=Calendar.getInstance();var totalPossibleCompletions7d=0;var actualCompletions7d=0;val dateFormat=SimpleDateFormat("yyyy-MM-dd",Locale.US);for(i in 0 until 7){val date=cal.time;val dateStr=dateFormat.format(date);habits.forEach { habit->if(isHabitScheduledOn(habit,date)){totalPossibleCompletions7d++;if(completionsByHabit[habit.id]?.any { it.dateString==dateStr }==true) actualCompletions7d++}};cal.add(Calendar.DAY_OF_YEAR,-1)};val completionPercent7d=if(totalPossibleCompletions7d>0)(actualCompletions7d*100.0/totalPossibleCompletions7d).roundToInt() else 0;val score=(completionPercent7d*0.7+streakFactor*3).roundToInt().coerceIn(0,100);val factors="Streaks +${streakFactor.roundToInt()} pts, Last 7d +${completionPercent7d}%";return Pair(score,factors) }
    private fun calculateCompletionByDay(habits: List<Habit>, completionsByDate: Map<String, List<HabitCompletion>>): Pair<List<Pair<String, Int>>, Pair<String, Int>?> { /* ... same logic ... */ val dayCounts=IntArray(7){0};val dayTotals=IntArray(7){0};val calendar=Calendar.getInstance();val dateFormat=SimpleDateFormat("yyyy-MM-dd",Locale.US);val relevantDates=completionsByDate.keys.sortedDescending().take(28);relevantDates.forEach { dateStr->try{val date=dateFormat.parse(dateStr)?:return@forEach;calendar.time=date;val dayOfWeekIndex=(calendar.get(Calendar.DAY_OF_WEEK)+5)%7;val completionsOnThisDay=completionsByDate[dateStr]?.map { it.habitId }?.toSet()?:emptySet();habits.forEach { habit->if(isHabitScheduledOn(habit,date)){dayTotals[dayOfWeekIndex]++;if(completionsOnThisDay.contains(habit.id)) dayCounts[dayOfWeekIndex]++}}}catch(e:Exception){Log.w(logTag,"Date parse error: $dateStr",e)}};val dayNames=listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun");val percentages=dayTotals.mapIndexed { index,total->if(total>0)(dayCounts[index]*100.0/total).roundToInt() else 0};val completionByDay=dayNames.zip(percentages);val mostEfficient=completionByDay.filter { it.second>0 }.maxByOrNull { it.second };return Pair(completionByDay,mostEfficient) }
    private fun calculateOverallCompletion(habits: List<Habit>, completionsByDate: Map<String, List<HabitCompletion>>, daysLookback: Int): Int { /* ... same logic ... */ val calendar=Calendar.getInstance();val dateFormat=SimpleDateFormat("yyyy-MM-dd",Locale.US);var totalPossible=0;var actualCompletions=0;for(i in 0 until daysLookback){val date=calendar.time;val dateStr=dateFormat.format(date);val completionsOnThisDay=completionsByDate[dateStr]?.map { it.habitId }?.toSet()?:emptySet();habits.forEach { habit->if(isHabitScheduledOn(habit,date)){totalPossible++;if(completionsOnThisDay.contains(habit.id)) actualCompletions++}};calendar.add(Calendar.DAY_OF_YEAR,-1)};return if(totalPossible>0)(actualCompletions*100.0/totalPossible).roundToInt() else 0 }
    private fun calculateCompletionTrend(habits: List<Habit>, completionsByDate: Map<String, List<HabitCompletion>>, weeksLookback: Int): List<Pair<Float, Float>> { /* ... same logic ... */ val weeklyPercentages=mutableListOf<Pair<Float,Float>>();val calendar=Calendar.getInstance();val dateFormat=SimpleDateFormat("yyyy-MM-dd",Locale.US);calendar.set(Calendar.DAY_OF_WEEK,calendar.firstDayOfWeek);calendar.add(Calendar.DAY_OF_YEAR,-((weeksLookback-1)*7));for(week in 1..weeksLookback){var weeklyPossible=0;var weeklyActual=0;for(day in 0 until 7){val date=calendar.time;val dateStr=dateFormat.format(date);val completionsOnThisDay=completionsByDate[dateStr]?.map { it.habitId }?.toSet()?:emptySet();habits.forEach { habit->if(isHabitScheduledOn(habit,date)){weeklyPossible++;if(completionsOnThisDay.contains(habit.id)) weeklyActual++}};calendar.add(Calendar.DAY_OF_YEAR,1)};val weeklyPercent=if(weeklyPossible>0)(weeklyActual*100.0f/weeklyPossible)else 0f;weeklyPercentages.add(week.toFloat() to weeklyPercent)};return weeklyPercentages }
    private fun isHabitScheduledOn(habit: Habit, date: Date): Boolean { /* ... same logic ... */ val calendar=Calendar.getInstance().apply { time=date };val dayOfWeek=(calendar.get(Calendar.DAY_OF_WEEK)-Calendar.SUNDAY+7)%7+1;return when(habit.frequencyType){"daily"->true;"weekly"->true /* Assumes weekly = always scheduled */;"specific_days"->habit.specificDays?.contains(dayOfWeek)?:false;else ->false} }

    /** Helper to append error messages, avoiding nulls and duplicates */
    private fun appendError(existingError: String?, newError: String?): String? {
        if (newError == null) return existingError
        val baseError = existingError ?: ""
        return if (baseError.contains(newError)) baseError else "$baseError\n$newError".trim()
    }

    private fun handleError(message: String, throwable: Throwable?) {
        Log.e(logTag, "$message: ${throwable?.message}", throwable)
        val currentError = _statsScreenState.value?.error
        val displayMessage = throwable?.localizedMessage ?: message // Prefer specific exception message
        _statsScreenState.postValue( // Use postValue as this might be called from background
            _statsScreenState.value?.copy(error = appendError(currentError, displayMessage))
        )
    }
}