package com.example.haybeat.ui.habits

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.haybeat.R
import com.example.haybeat.databinding.ItemHabitBinding // Import ViewBinding

// Interface for handling user interactions on a habit item.
interface HabitInteractionListener {
    fun onHabitClicked(habitUiItem: HabitUiItem) // When the whole row is clicked
    fun onHabitCheckClicked(habitUiItem: HabitUiItem) // When the checkbox is clicked
}

// Adapter for displaying the list of habits in a RecyclerView.
class HabitAdapter(private val listener: HabitInteractionListener) :
    ListAdapter<HabitUiItem, HabitAdapter.HabitViewHolder>(HabitDiffCallback()) {

    // Creates a new ViewHolder when the RecyclerView needs one.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HabitViewHolder {
        // Inflate the item layout using ViewBinding
        val binding = ItemHabitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HabitViewHolder(binding, listener)
    }

    // Binds the data from a HabitUiItem to the views within a ViewHolder.
    override fun onBindViewHolder(holder: HabitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ViewHolder class holds references to the views for a single habit item.
    inner class HabitViewHolder(
        private val binding: ItemHabitBinding, // ViewBinding instance for item_habit.xml
        private val listener: HabitInteractionListener
    ) : RecyclerView.ViewHolder(binding.root) {

        // Binds the data to the views.
        fun bind(habitUiItem: HabitUiItem) {
            val habit = habitUiItem.habit
            val context = itemView.context

            // Set text views
            binding.habitNameTextView.text = habit.name
            binding.habitCategoryTextView.text = habit.category // Show category
            binding.habitStreakTextView.text = habit.streak.toString() // Show streak number

            // Set Checkbox state (important: set listener to null first to prevent triggering during bind)
            binding.habitCheckBox.setOnCheckedChangeListener(null)
            binding.habitCheckBox.isChecked = habitUiItem.isCompletedToday

            // Apply visual changes based on completion status
            if (habitUiItem.isCompletedToday) {
                // Checked state: Teal tint, strike-through text, reduced alpha
                binding.habitCheckBox.buttonTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.teal_500))
                binding.habitNameTextView.paintFlags = binding.habitNameTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.habitCategoryTextView.paintFlags = binding.habitCategoryTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.root.alpha = 0.7f // Make item look slightly faded/disabled
            } else {
                // Unchecked state: Default tint, normal text, full alpha
                binding.habitCheckBox.buttonTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.grey_400)) // Default grey
                binding.habitNameTextView.paintFlags = binding.habitNameTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.habitCategoryTextView.paintFlags = binding.habitCategoryTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.root.alpha = 1.0f
            }

            // Set color indicator based on habit's colorHex
            try {
                val color = Color.parseColor(habit.colorHex)
                // Try to get the GradientDrawable background and set its color
                val background = binding.colorIndicatorView.background as? GradientDrawable
                background?.setColor(color)
                    ?: binding.colorIndicatorView.setBackgroundColor(color) // Fallback if background is not GradientDrawable
            } catch (e: IllegalArgumentException) {
                // Handle invalid hex color string, set a default color
                val defaultColor = ContextCompat.getColor(context, R.color.teal_500)
                binding.colorIndicatorView.setBackgroundColor(defaultColor)
            }

            // Re-attach the listener for checkbox clicks
            binding.habitCheckBox.setOnClickListener {
                // Use setOnClickListener for checkbox to avoid issues with rapid clicks
                // The isChecked state might not be updated immediately with setOnCheckedChangeListener sometimes
                listener.onHabitCheckClicked(habitUiItem)
            }
            // binding.habitCheckBox.setOnCheckedChangeListener { _, _ ->
            //     listener.onHabitCheckClicked(habitUiItem)
            // }


            // Set listener for clicking the entire habit row
            binding.root.setOnClickListener {
                listener.onHabitClicked(habitUiItem)
            }
        }
    }
}

// DiffUtil helps RecyclerView efficiently update only the items that have changed.
class HabitDiffCallback : DiffUtil.ItemCallback<HabitUiItem>() {
    // Checks if two items represent the same habit object (based on ID).
    override fun areItemsTheSame(oldItem: HabitUiItem, newItem: HabitUiItem): Boolean {
        return oldItem.habit.id == newItem.habit.id
    }

    // Checks if the contents of the habit item have changed (affects UI display).
    override fun areContentsTheSame(oldItem: HabitUiItem, newItem: HabitUiItem): Boolean {
        // Rely on the data class's generated equals() method for comparison
        return oldItem == newItem
    }
}