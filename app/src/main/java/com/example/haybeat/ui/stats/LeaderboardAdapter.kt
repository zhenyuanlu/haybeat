package com.example.haybeat.ui.stats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.haybeat.R
import com.example.haybeat.data.model.ParticipantProgress
import com.example.haybeat.databinding.ItemLeaderboardEntryBinding
import com.google.firebase.auth.FirebaseAuth

class LeaderboardAdapter :
    ListAdapter<ParticipantProgress, LeaderboardAdapter.LeaderboardViewHolder>(LeaderboardDiffCallback()) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val binding = ItemLeaderboardEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class LeaderboardViewHolder(private val binding: ItemLeaderboardEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(participant: ParticipantProgress, rank: Int) {
            binding.leaderboardRankText.text = rank.toString()
            binding.leaderboardProgressText.text = participant.progress.toString() // Main score
            binding.leaderboardStreakText.text = participant.currentStreak.toString() // Display streak

            val displayName = participant.userName?.takeIf { it.isNotBlank() } ?: "Anonymous"

            if (participant.userId == currentUserId) {
                binding.leaderboardNameText.text = itemView.context.getString(R.string.leaderboard_you_format, displayName)
                binding.leaderboardNameText.setTextAppearance(R.style.TextAppearance_Material3_BodyLarge_Bold)
            } else {
                binding.leaderboardNameText.text = displayName
                binding.leaderboardNameText.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            }
        }
    }
}

// DiffUtil Callback needs to compare streak fields now
class LeaderboardDiffCallback : DiffUtil.ItemCallback<ParticipantProgress>() {
    override fun areItemsTheSame(oldItem: ParticipantProgress, newItem: ParticipantProgress): Boolean {
        return oldItem.userId == newItem.userId
    }

    override fun areContentsTheSame(oldItem: ParticipantProgress, newItem: ParticipantProgress): Boolean {
        return oldItem.userName == newItem.userName &&
                oldItem.progress == newItem.progress &&
                oldItem.currentStreak == newItem.currentStreak // Compare streak
    }
}