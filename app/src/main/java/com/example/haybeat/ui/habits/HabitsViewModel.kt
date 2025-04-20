package com.example.haybeat.ui.habits

import android.util.Log
import androidx.lifecycle.* // General import
import com.example.haybeat.data.model.Habit
import com.example.haybeat.data.repository.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.roundToInt

// Data class combining Habit with its completion status for today, used by the UI adapter.
data class HabitUiItem(
    val habit: Habit,
    val isCompletedToday: Boolean
)

// Represents the overall state for the Habits screen
data class HabitsScreenState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val habitItems: List<HabitUiItem> = emptyList(),
    val progressPercent: Int = 0,
    val progressSummary: String = "Loading habits..."
)

class HabitsViewModel : ViewModel() {

    private val repository = HabitRepository()
    private val todayDate = Date() // Capture today's date once
    private val logTag = "HabitsViewModel_DEBUG"

    // --- State Management using LiveData ---
    private val _screenState = MutableLiveData<HabitsScreenState>(HabitsScreenState()) // Initial state is loading
    val screenState: LiveData<HabitsScreenState> get() = _screenState

    // --- Derived LiveData using MediatorLiveData ---

    val priorityHabits: LiveData<List<HabitUiItem>> = MediatorLiveData<List<HabitUiItem>>().apply {
        addSource(screenState) { state -> // Observe the source LiveData
            val filteredList = state.habitItems.filter {
                it.habit.priority.equals("high", ignoreCase = true) && !it.isCompletedToday
            }
            if (value != filteredList) { // Only update if changed
                Log.d(logTag, "[MediatorLiveData] Calculating priorityHabits. Output count: ${filteredList.size}")
                value = filteredList
            }
        }
    }

    val nonPriorityHabits: LiveData<List<HabitUiItem>> = MediatorLiveData<List<HabitUiItem>>().apply {
        addSource(screenState) { state -> // Observe the source LiveData
            val filteredList = state.habitItems.filter {
                !it.habit.priority.equals("high", ignoreCase = true) || it.isCompletedToday
            }
            if (value != filteredList) { // Only update if changed
                Log.d(logTag, "[MediatorLiveData] Calculating nonPriorityHabits. Output count: ${filteredList.size}")
                value = filteredList
            }
        }
    }

    // --- Data Loading ---
    init {
        loadHabits(showLoading = true) // Ensure initial load shows loading
    }

    /** Fetches habits and their completion status, updates LiveData */
    fun loadHabits(showLoading: Boolean = true) {
        Log.d(logTag, "loadHabits called. showLoading=$showLoading")
        // Update state to show loading only if requested and not already loading
        if (showLoading && _screenState.value?.isLoading == false) {
            _screenState.value = _screenState.value?.copy(isLoading = true, error = null)
        } else if (!showLoading && _screenState.value?.isLoading == true) {
            // If called without showLoading but was already loading, keep loading state
            // Or set loading false if needed: _screenState.value = _screenState.value?.copy(isLoading = false)
        }


        viewModelScope.launch {
            val habitResult = repository.fetchUserHabits() // One-time fetch

            if (habitResult.isSuccess) {
                val habits = habitResult.getOrNull() ?: emptyList()
                Log.d(logTag, "Successfully fetched ${habits.size} habits.")

                // Fetch completion status for the retrieved habits
                val completionsMap = fetchTodayCompletionsMap(habits.map { it.id })
                Log.d(logTag, "Fetched completions map with size: ${completionsMap.size}")

                // Combine data and update state
                val uiList = habits.map { habit ->
                    HabitUiItem(
                        habit = habit,
                        isCompletedToday = completionsMap[habit.id] ?: false
                    )
                }
                updateStateWithData(uiList)

            } else {
                val errorMsg = habitResult.exceptionOrNull()?.message ?: "Failed to load habits"
                Log.e(logTag, "Error loading habits: $errorMsg")
                _screenState.postValue(HabitsScreenState(isLoading = false, error = errorMsg)) // Use postValue from background
            }
        }
    }

    /** Internal helper to fetch completions */
    private suspend fun fetchTodayCompletionsMap(habitIds: List<String>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (habitIds.isEmpty()) return@withContext emptyMap()
        val completionsMap = mutableMapOf<String, Boolean>()
        try {
            habitIds.forEach { habitId ->
                val completion = repository.getCompletionStatus(habitId, todayDate)
                completionsMap[habitId] = completion?.completed ?: false
            }
        } catch (e: Exception) {
            Log.e(logTag, "[fetchCompletionsMap] Error during fetch", e)
        }
        return@withContext completionsMap
    }

    /** Updates the _screenState LiveData with the processed UI list */
    private fun updateStateWithData(uiList: List<HabitUiItem>) {
        val completedCount = uiList.count { it.isCompletedToday }
        val totalCount = uiList.size
        val progressPercent = if (totalCount > 0) (completedCount * 100.0 / totalCount).roundToInt() else 0
        val summary = if (totalCount == 0) "No habits tracked today." else "$completedCount of $totalCount habits completed"

        Log.d(logTag, "Updating screen state. Count: $totalCount, Progress: $progressPercent%")
        // Use postValue if called from background coroutine, use setValue if sure on main thread
        // Since loadHabits calls this from viewModelScope (potentially background), use postValue
        _screenState.postValue(
            HabitsScreenState(
                isLoading = false,
                error = null,
                habitItems = uiList,
                progressPercent = progressPercent,
                progressSummary = summary
            )
        )
    }

    // --- Actions ---

    /** Toggles completion and reloads data */
    fun toggleHabitCompletion(habitUiItem: HabitUiItem) {
        Log.d(logTag, "toggleHabitCompletion called for Habit ID: ${habitUiItem.habit.id}, Current State: ${habitUiItem.isCompletedToday}")

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.toggleHabitCompletion(
                habitUiItem.habit.id,
                todayDate,
                habitUiItem.isCompletedToday
            )
            if (result.isSuccess) {
                Log.i(logTag, "Successfully toggled habit ${habitUiItem.habit.id}. Reloading data...")
                // Reload data on the main thread after successful toggle
                withContext(Dispatchers.Main) {
                    loadHabits(showLoading = false) // Reload without intense loading indicator
                }
            } else {
                Log.e(logTag, "Failed to toggle habit ${habitUiItem.habit.id}", result.exceptionOrNull())
                // Post error state back to UI
                withContext(Dispatchers.Main) {
                    // Maybe show previous state + error temporarily
                    val currentState = _screenState.value ?: HabitsScreenState()
                    _screenState.value = currentState.copy(error = "Failed to update habit")
                    // Could add a mechanism to clear the error after a few seconds
                }
            }
        }
    }

    /** Function to be called for manual refresh (e.g., SwipeRefreshLayout) */
    fun refreshHabits() {
        Log.d(logTag, "refreshHabits called.")
        loadHabits(showLoading = true) // Show loading indicator on manual refresh
    }
}