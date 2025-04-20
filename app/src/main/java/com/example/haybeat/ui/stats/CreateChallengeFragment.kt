package com.example.haybeat.ui.stats

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.example.haybeat.R
import com.example.haybeat.databinding.FragmentCreateChallengeBinding

class CreateChallengeFragment : Fragment() {

    private var _binding: FragmentCreateChallengeBinding? = null
    private val binding get() = _binding!!
    private val logTag = "CreateChallengeFrag"

    private val viewModel: CreateChallengeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateChallengeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(logTag, "onViewCreated")

        setupToolbar()
        setupCreateButton()
        observeViewModel()
    }

    private fun setupToolbar() {
        // Connect Up button to NavController
        NavigationUI.setupWithNavController(binding.toolbar, findNavController())
        // No need to set title here, it's set in nav_graph label
    }

    private fun setupCreateButton() {
        binding.createButton.setOnClickListener {
            Log.d(logTag, "Create button clicked")
            // Get data from input fields
            val name = binding.challengeNameEditText.text.toString().trim()
            val goal = binding.challengeGoalEditText.text.toString().trim()
            val durationString = binding.challengeDurationEditText.text.toString().trim()
            val duration = if (durationString.isNotEmpty()) durationString.toIntOrNull() else null

            // Validate duration locally (optional, ViewModel also validates)
            if (durationString.isNotEmpty() && duration == null) {
                binding.challengeDurationLayout.error = "Enter a valid number of days"
                return@setOnClickListener
            } else {
                binding.challengeDurationLayout.error = null // Clear error
            }

            // Call ViewModel to create the challenge
            viewModel.createChallenge(name, goal, duration)
        }
    }

    private fun observeViewModel() {
        Log.d(logTag, "Setting up observers")
        viewModel.creationResult.observe(viewLifecycleOwner) { result ->
            // Show/hide loading indicator
            binding.loadingProgressBar.isVisible = result is CreateChallengeResult.Loading
            // Enable/disable create button while loading
            binding.createButton.isEnabled = result !is CreateChallengeResult.Loading

            when (result) {
                is CreateChallengeResult.Success -> {
                    Log.i(logTag, "Challenge creation successful (ID: ${result.challengeId}). Navigating back.")
                    Toast.makeText(context, "Challenge created!", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack() // Go back to Stats screen
                }
                is CreateChallengeResult.Error -> {
                    Log.e(logTag, "Challenge creation failed: ${result.message}")
                    // Show error message (e.g., in a Snackbar or Toast)
                    Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
                is CreateChallengeResult.Loading -> {
                    Log.d(logTag, "Challenge creation in progress...")
                    // Loading indicator handled by visibility toggle
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(logTag, "onDestroyView")
        _binding = null // Clean up binding reference
    }
}