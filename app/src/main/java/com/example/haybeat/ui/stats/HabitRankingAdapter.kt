package com.example.haybeat.ui.stats

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.haybeat.R
import com.example.haybeat.data.model.Habit // Import Habit model
import com.example.haybeat.databinding.ItemHabitRankingBinding // Import ViewBinding

// Adapter to display habits ranked, likely by streak.
class HabitRankingAdapter : ListAdapter<Habit, HabitRankingAdapter.RankingViewHolder>(HabitRankingDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val binding = ItemHabitRankingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RankingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RankingViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1) // Pass rank (1-based index)
    }

    // ViewHolder for a single item in the ranking list.
    inner class RankingViewHolder(private val binding: ItemHabitRankingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(habit: Habit, rank: Int) {
            val context = itemView.context
            binding.rankTextView.text = rank.toString()
            binding.habitNameTextView.text = habit.name
            binding.streakTextView.text = habit.streak.toString()

            // Set color indicator
            try {
                val color = Color.parseColor(habit.colorHex)
                (binding.colorIndicatorView.background as? GradientDrawable)?.setColor(color)
                    ?: binding.colorIndicatorView.setBackgroundColor(color)
            } catch (e: IllegalArgumentException) {
                // Fallback to default color on error
                binding.colorIndicatorView.setBackgroundColor(ContextCompat.getColor(context, R.color.teal_500))
            }
        }
    }
}

// DiffUtil Callback for the Habit model (used for ranking list).
class HabitRankingDiffCallback : DiffUtil.ItemCallback<Habit>() {
    override fun areItemsTheSame(oldItem: Habit, newItem: Habit): Boolean {
        // Items are the same if their IDs match
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Habit, newItem: Habit): Boolean {
        // Contents are the same if the relevant data for display hasn't changed.
        // Using the data class equals() is often sufficient if it includes displayed fields.
        return oldItem == newItem
        // Or compare specific fields:
        // return oldItem.name == newItem.name && oldItem.streak == newItem.streak && oldItem.colorHex == newItem.colorHex
    }
}