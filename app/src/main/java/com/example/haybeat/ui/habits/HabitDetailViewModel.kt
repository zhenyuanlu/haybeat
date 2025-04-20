package com.example.haybeat.ui.habits

import android.util.Log
import androidx.lifecycle.*
import com.example.haybeat.data.model.Habit
import com.example.haybeat.data.repository.HabitRepository
import kotlinx.coroutines.launch

// Represents the different states the Habit Detail screen can be in.
sealed class HabitDetailState {
    object Loading : HabitDetailState()
    data class Success(val habit: Habit) : HabitDetailState()
    data class Error(val message: String) : HabitDetailState()
    object Deleted : HabitDetailState() // State after successful deletion
}

class HabitDetailViewModel : ViewModel() {

    private val repository = HabitRepository()

    // LiveData holding the current state of the detail screen.
    private val _habitDetailState = MutableLiveData<HabitDetailState>()
    val habitDetailState: LiveData<HabitDetailState> get() = _habitDetailState

    // Store the currently loaded habit for actions like delete.
    private var currentHabitInternal: Habit? = null

    /** Loads the details for a specific habit ID. */
    fun loadHabitDetails(habitId: String) {
        _habitDetailState.value = HabitDetailState.Loading
        viewModelScope.launch {
            val habit = repository.getHabit(habitId)
            if (habit != null) {
                currentHabitInternal = habit // Store the loaded habit
                _habitDetailState.postValue(HabitDetailState.Success(habit))
            } else {
                val errorMsg = "Habit not found (ID: $habitId)"
                Log.e("HabitDetailViewModel", errorMsg)
                _habitDetailState.postValue(HabitDetailState.Error(errorMsg))
            }
        }
    }

    /** Deletes (archives) the currently loaded habit. */
    fun deleteCurrentHabit() {
        val habitToDelete = currentHabitInternal
        if (habitToDelete == null) {
            Log.e("HabitDetailViewModel", "Attempted to delete but no habit is loaded.")
            _habitDetailState.value = HabitDetailState.Error("Cannot delete, habit not loaded.")
            return
        }

        if (habitToDelete.id.isBlank()) {
            Log.e("HabitDetailViewModel", "Attempted to delete habit with blank ID.")
            _habitDetailState.value = HabitDetailState.Error("Cannot delete unsaved habit.")
            return
        }

        _habitDetailState.value = HabitDetailState.Loading // Indicate processing
        viewModelScope.launch {
            // Using archive (soft delete)
            val result = repository.archiveHabit(habitToDelete.id)

            if (result.isSuccess) {
                _habitDetailState.postValue(HabitDetailState.Deleted) // Signal success
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Error deleting habit"
                Log.e("HabitDetailViewModel", "Failed to delete habit ${habitToDelete.id}: $errorMsg")
                // Revert state back to Success to show the habit again, but with an error message
                _habitDetailState.postValue(HabitDetailState.Success(habitToDelete)) // Go back to showing the habit
                // TODO: Consider a separate LiveData/SharedFlow for transient errors like this delete failure
                // Toast.makeText(context, "Delete failed: $errorMsg", Toast.SHORT).show() // Cannot Toast from VM
            }
        }
    }
}