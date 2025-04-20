package com.example.haybeat.ui.habits

import android.util.Log
import androidx.lifecycle.*
import com.example.haybeat.data.model.Habit
import com.example.haybeat.data.repository.HabitRepository
import kotlinx.coroutines.launch
import java.util.*

// Represents the possible outcomes of saving or deleting a habit.
sealed class HabitModifyResult {
    object Success : HabitModifyResult()
    data class Error(val message: String) : HabitModifyResult()
    object Loading : HabitModifyResult()
}

class AddHabitViewModel : ViewModel() {

    private val repository = HabitRepository()

    // LiveData to communicate the result of save/delete operations back to the Fragment.
    private val _modifyResult = MutableLiveData<HabitModifyResult>()
    val modifyResult: LiveData<HabitModifyResult> get() = _modifyResult

    // LiveData to hold the habit being edited (null if adding a new habit).
    private val _habitToEdit = MutableLiveData<Habit?>()
    val habitToEdit: LiveData<Habit?> get() = _habitToEdit

    /**
     * Loads habit data from the repository if a habitId is provided (for editing).
     * If habitId is null or blank, it sets habitToEdit to null for Add mode.
     */
    fun loadHabit(habitId: String?) {
        if (habitId == null || habitId.isBlank()) {
            _habitToEdit.value = null // Ensure it's null for Add mode
            return
        }
        _modifyResult.value = HabitModifyResult.Loading // Show loading while fetching
        viewModelScope.launch {
            val habit = repository.getHabit(habitId)
            _habitToEdit.postValue(habit) // Post value as we are in coroutine
            _modifyResult.postValue(null) // Clear loading state after fetch (success or fail)
            if (habit == null) {
                Log.e("AddHabitViewModel", "Failed to load habit $habitId for editing.")
                // Optionally post an error result if habit not found
                // _modifyResult.postValue(HabitModifyResult.Error("Habit not found"))
            }
        }
    }

    /**
     * Saves the provided Habit object.
     * If the habit has a blank ID, it adds a new habit.
     * If the habit has an ID, it updates the existing habit.
     */
    fun saveHabit(habit: Habit) {
        _modifyResult.value = HabitModifyResult.Loading
        viewModelScope.launch {
            val result = if (habit.id.isBlank()) {
                // Add new habit
                repository.addHabit(habit) // Repository handles setting userId
            } else {
                // Update existing habit
                repository.updateHabit(habit)
            }

            // Post result to LiveData based on success/failure
            if (result.isSuccess) {
                _modifyResult.postValue(HabitModifyResult.Success)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error saving habit"
                Log.e("AddHabitViewModel", "Error saving habit: $errorMsg")
                _modifyResult.postValue(HabitModifyResult.Error(errorMsg))
            }
        }
    }

    /**
     * Deletes (archives) the given habit.
     */
    fun deleteHabit(habit: Habit) {
        if (habit.id.isBlank()) {
            Log.w("AddHabitViewModel", "Attempted to delete unsaved habit.")
            return // Cannot delete unsaved habit
        }

        _modifyResult.value = HabitModifyResult.Loading // Show loading
        viewModelScope.launch {
            // Using archive (soft delete) method from repository
            val result = repository.archiveHabit(habit.id)

            // Alternative: Hard delete (use with caution)
            // val result = repository.deleteHabitPermanently(habit.id)

            if (result.isSuccess) {
                _modifyResult.postValue(HabitModifyResult.Success) // Indicate success (triggers nav back)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Error deleting habit"
                Log.e("AddHabitViewModel", "Error deleting habit ${habit.id}: $errorMsg")
                _modifyResult.postValue(HabitModifyResult.Error(errorMsg))
            }
        }
    }
}