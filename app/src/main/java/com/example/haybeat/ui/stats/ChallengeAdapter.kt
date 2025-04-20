package com.example.haybeat.ui.stats

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible // For visibility extension
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.haybeat.R
import com.example.haybeat.data.model.Challenge
import com.example.haybeat.databinding.ItemChallengeBinding
import com.google.firebase.auth.FirebaseAuth // Needed to check current user
import java.text.SimpleDateFormat
import java.util.*

// --- MODIFIED Listener Interface ---
interface ChallengeInteractionListener {
    fun onChallengeClicked(challenge: Challenge)
    fun onJoinChallengeClicked(challenge: Challenge) // New action for Join button
    fun onLeaveChallengeClicked(challenge: Challenge) // New action for Leave button
}

class ChallengeAdapter(private val listener: ChallengeInteractionListener) :
    ListAdapter<Challenge, ChallengeAdapter.ChallengeViewHolder>(ChallengeDiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val logTag = "ChallengeAdapter"
    // Get current user ID when adapter is created
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChallengeViewHolder {
        Log.d(logTag, "onCreateViewHolder called")
        val binding = ItemChallengeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChallengeViewHolder(binding, listener, currentUserId) // Pass currentUserId
    }

    override fun onBindViewHolder(holder: ChallengeViewHolder, position: Int) {
        Log.d(logTag, "onBindViewHolder called for position $position")
        holder.bind(getItem(position))
    }

    // --- ViewHolder with Modifications ---
    inner class ChallengeViewHolder(
        private val binding: ItemChallengeBinding,
        private val listener: ChallengeInteractionListener,
        private val currentUserId: String? // Store current user ID
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(challenge: Challenge) {
            Log.d(logTag, "Binding challenge: ${challenge.name}")
            binding.challengeNameText.text = challenge.name
            val goalText = challenge.goalDescription.takeIf { !it.isNullOrBlank() } ?: "Achieve your best!"
            binding.challengeGoalText.text = itemView.context.getString(R.string.challenge_goal_display_format, goalText)
            binding.challengeEndDateText.text = challenge.endDate?.let { date ->
                try { itemView.context.getString(R.string.challenge_end_date_format, dateFormat.format(date)) }
                catch (e: Exception) { "Ends: Date Error" }
            } ?: itemView.context.getString(R.string.challenge_ongoing)

            // --- Join/Leave Button Logic ---
            val isParticipant = currentUserId != null && challenge.participantIds.contains(currentUserId)
            Log.d(logTag, "User $currentUserId is participant in ${challenge.id}: $isParticipant")

            binding.joinButton.isVisible = !isParticipant // Show Join if NOT participant
            binding.leaveButton.isVisible = isParticipant // Show Leave if IS participant

            // Disable buttons if user is the owner (simplification: owner cannot leave/re-join via button)
            if (currentUserId == challenge.ownerId) {
                binding.joinButton.isVisible = false
                binding.leaveButton.isEnabled = false // Visually indicate owner cannot leave
                binding.leaveButton.alpha = 0.5f
            } else {
                binding.leaveButton.isEnabled = true // Ensure Leave is enabled for non-owners
                binding.leaveButton.alpha = 1.0f
            }


            // --- Click Listeners ---
            binding.root.setOnClickListener {
                Log.d(logTag, "Challenge item clicked: ${challenge.name}")
                listener.onChallengeClicked(challenge)
            }
            binding.joinButton.setOnClickListener {
                Log.d(logTag, "Join button clicked for: ${challenge.name}")
                listener.onJoinChallengeClicked(challenge)
            }
            binding.leaveButton.setOnClickListener {
                Log.d(logTag, "Leave button clicked for: ${challenge.name}")
                listener.onLeaveChallengeClicked(challenge)
            }
        }
    }
}

// ChallengeDiffCallback remains the same
class ChallengeDiffCallback : DiffUtil.ItemCallback<Challenge>() { /* ... same as before ... */
    override fun areItemsTheSame(oldItem: Challenge, newItem: Challenge): Boolean {
        return oldItem.id == newItem.id
    }
    override fun areContentsTheSame(oldItem: Challenge, newItem: Challenge): Boolean {
        // Compare fields relevant to display, including participantIds for button visibility
        return oldItem.name == newItem.name &&
                oldItem.goalDescription == newItem.goalDescription &&
                oldItem.endDate == newItem.endDate &&
                oldItem.participantIds == newItem.participantIds && // Important for join/leave buttons
                oldItem.ownerId == newItem.ownerId // Important for disabling leave button
    }
}