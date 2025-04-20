package com.example.haybeat.ui.habits

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout // Import FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.haybeat.R
import com.example.haybeat.data.model.Habit
import com.example.haybeat.databinding.FragmentHabitDetailBinding
import com.example.haybeat.service.AlarmScheduler
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.*
import android.text.format.DateFormat


// Fragment to display the details of a specific habit, shown as a Bottom Sheet.
class HabitDetailFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentHabitDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HabitDetailViewModel by viewModels()
    private val args: HabitDetailFragmentArgs by navArgs() // Safe Args to get habitId
    private var currentHabit: Habit? = null // Store the currently displayed habit

    // --- BottomSheet Dialog Customization ---
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            // Get the FrameLayout holding the bottom sheet content
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED // Start fully expanded
                behavior.skipCollapsed = true // Don't allow it to collapse partially
                // Optional: Set peek height if you wanted a collapsed state
                // behavior.peekHeight = resources.displayMetrics.heightPixels / 2
            }
        }
        return dialog
    }

    // --- Fragment Lifecycle ---
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHabitDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        observeViewModel()

        // Load habit details using the ID passed from navigation arguments
        Log.d("HabitDetailFragment", "Loading details for habit ID: ${args.habitId}")
        viewModel.loadHabitDetails(args.habitId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clean up binding
    }

    // --- UI Setup ---

    private fun setupToolbar() {
        // Handle Up/Back button click
        binding.toolbar.setNavigationOnClickListener {
            dismiss() // Close the bottom sheet dialog
        }
        // Handle menu item clicks (Edit, Delete)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    currentHabit?.let { habitToEdit ->
                        // Navigate to AddHabitFragment, passing the current habit's ID and title
                        val action = HabitDetailFragmentDirections.actionHabitDetailFragmentToAddHabitFragment(
                            habitId = habitToEdit.id,
                            title = getString(R.string.edit_habit) // Set title for edit mode
                        )
                        findNavController().navigate(action)
                        dismiss() // Close detail view after navigating to edit
                    } ?: run {
                        Toast.makeText(context, "Cannot edit, habit data not loaded.", Toast.LENGTH_SHORT).show()
                    }
                    true // Consume event
                }
                R.id.action_delete -> {
                    showDeleteConfirmation()
                    true // Consume event
                }
                else -> false // Event not handled
            }
        }
    }

    // Observe changes in the ViewModel state
    private fun observeViewModel() {
        viewModel.habitDetailState.observe(viewLifecycleOwner) { state ->
            // Show/hide loading indicator
            binding.loadingProgressBar.isVisible = state is HabitDetailState.Loading
            // Show/hide main content area
            binding.contentScrollView.isVisible = state is HabitDetailState.Success

            when (state) {
                is HabitDetailState.Success -> {
                    currentHabit = state.habit // Store the loaded habit
                    populateUi(state.habit) // Update UI with habit data
                }
                is HabitDetailState.Error -> {
                    Log.e("HabitDetailFragment", "Error loading habit: ${state.message}")
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                    // Dismiss the dialog if the habit couldn't be found
                    if (state.message.contains("not found")) {
                        dismissAllowingStateLoss() // Use allowingStateLoss if state might be saved
                    }
                }
                is HabitDetailState.Deleted -> {
                    Toast.makeText(context, R.string.habit_deleted, Toast.LENGTH_SHORT).show()
                    // Cancel any alarms associated with the deleted habit
                    currentHabit?.let { AlarmScheduler.cancelHabitReminder(requireContext(), it.id) }
                    dismissAllowingStateLoss() // Close dialog after successful deletion
                }
                HabitDetailState.Loading -> {
                    // Handled by progress bar visibility toggle above
                }
            }
        }
    }

    /** Populates the UI elements with data from the loaded Habit object */
    private fun populateUi(habit: Habit) {
        binding.habitNameTextView.text = habit.name
        binding.currentStreakText.text = habit.streak.toString()
        binding.totalCompletionsText.text = habit.totalCompletions.toString()
        binding.bestStreakText.text = habit.longestStreak.toString()

        binding.categoryText.text = habit.category
        binding.priorityText.text = habit.priority.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        } // Capitalize

        // Format Frequency Text
        binding.frequencyText.text = when (habit.frequencyType) {
            "daily" -> getString(R.string.daily)
            "weekly" -> "${habit.weeklyGoal} ${getString(R.string.times_per_week)}"
            "specific_days" -> habit.specificDays
                ?.mapNotNull { dayIndexToString(it) } // Convert indices to day names
                ?.joinToString(", ") // Join with commas
                ?: getString(R.string.specific_days) // Fallback
            else -> habit.frequencyType // Show raw value if unknown type
        }

        // Format Reminder Text
        binding.reminderText.text = if (habit.reminderTime != null) {
            try {
                val parts = habit.reminderTime!!.split(":")
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                    set(Calendar.MINUTE, parts[1].toInt())
                }
                DateFormat.getTimeFormat(context).format(calendar.time) // Use system format
            } catch (e: Exception) {
                Log.w("HabitDetailFragment", "Failed to format reminder time: ${habit.reminderTime}", e)
                getString(R.string.no_reminder)
            }
        } else {
            getString(R.string.no_reminder)
        }

        // Format Start Date Text
        binding.startDateText.text = habit.createdAt?.let {
            SimpleDateFormat(getString(R.string.date_format_short), Locale.getDefault()).format(it)
        } ?: "N/A"

        // Set Color Indicator
        try {
            val color = Color.parseColor(habit.colorHex)
            (binding.colorIndicatorView.background as? GradientDrawable)?.setColor(color)
                ?: binding.colorIndicatorView.setBackgroundColor(color)
        } catch (e: IllegalArgumentException) {
            Log.w("HabitDetailFragment", "Invalid color hex: ${habit.colorHex}")
            // Set default color if parse fails
            binding.colorIndicatorView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.teal_500))
        }
    }

    // --- Helpers ---

    /** Helper to convert day index (1=Mon, 7=Sun) to short string (M, T, W...) */
    private fun dayIndexToString(index: Int): String? {
        return when(index) {
            1 -> getString(R.string.monday_short)
            2 -> getString(R.string.tuesday_short)
            3 -> getString(R.string.wednesday_short)
            4 -> getString(R.string.thursday_short)
            5 -> getString(R.string.friday_short)
            6 -> getString(R.string.saturday_short)
            7 -> getString(R.string.sunday_short)
            else -> null // Invalid index
        }
    }

    /** Shows the confirmation dialog before deleting a habit */
    private fun showDeleteConfirmation() {
        currentHabit?.let { habitToDelete ->
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_delete_habit)
                .setMessage(getString(R.string.delete_habit_message, habitToDelete.name))
                .setIcon(R.drawable.ic_delete) // Use the delete icon
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewModel.deleteCurrentHabit() // Call ViewModel to handle deletion
                }
                .setNegativeButton(R.string.cancel, null) // Do nothing on cancel
                .show()
        } ?: run {
            Toast.makeText(context, "Cannot delete, habit data not loaded.", Toast.LENGTH_SHORT).show()
        }
    }
}