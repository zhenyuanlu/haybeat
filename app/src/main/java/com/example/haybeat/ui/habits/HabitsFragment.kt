package com.example.haybeat.ui.habits

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer // Import Observer for LiveData
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout // Ensure import is present
import com.example.haybeat.R
import com.example.haybeat.databinding.FragmentHabitsBinding
import java.text.SimpleDateFormat
import java.util.*

// Fragment displaying the main habits list and progress.
class HabitsFragment : Fragment(), HabitInteractionListener {

    // Use property delegation for View Binding
    private var _binding: FragmentHabitsBinding? = null
    private val binding get() = _binding!! // Non-null assertion accessor
    private val logTag = "HabitsFragment_DEBUG" // Tag for filtering logs

    // Get instance of HabitsViewModel using KTX delegate
    private val viewModel: HabitsViewModel by viewModels()

    // Adapters for the RecyclerViews
    private lateinit var priorityHabitAdapter: HabitAdapter
    private lateinit var allHabitsAdapter: HabitAdapter

    // Inflate the layout for this fragment
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(logTag, "onCreateView called")
        _binding = FragmentHabitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Setup views, observers, and listeners after the view is created
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(logTag, "onViewCreated called")

        setupRecyclerViews()
        setupObservers() // Setup LiveData observers
        setupClickListeners()
        updateDateHeader() // Set today's date in the header

        // Setup swipe-to-refresh listener
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d(logTag, "Swipe to refresh triggered.")
            viewModel.refreshHabits() // Call ViewModel's refresh function
        }
    }

    // Configure the RecyclerViews and their adapters
    private fun setupRecyclerViews() {
        Log.d(logTag, "Setting up RecyclerViews")
        // Adapter for Priority Habits RecyclerView
        priorityHabitAdapter = HabitAdapter(this) // Pass listener interface
        binding.priorityHabitsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = priorityHabitAdapter
        }

        // Adapter for All Habits RecyclerView
        allHabitsAdapter = HabitAdapter(this)
        binding.allHabitsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = allHabitsAdapter
            isNestedScrollingEnabled = false // Disable scrolling since it's inside NestedScrollView
        }
    }

    // Observe LiveData from the ViewModel
    private fun setupObservers() {
        Log.d(logTag, "Setting up Observers for LiveData")

        // Observe the main screen state
        viewModel.screenState.observe(viewLifecycleOwner, Observer { state ->
            Log.d(logTag, "Observed screenState: isLoading=${state.isLoading}, error=${state.error}, items=${state.habitItems.size}")

            // Handle loading state (link to SwipeRefreshLayout)
            binding.swipeRefreshLayout.isRefreshing = state.isLoading

            // Handle errors
            if (!state.isLoading && state.error != null) {
                Toast.makeText(context, "Error: ${state.error}", Toast.LENGTH_LONG).show()
                // You might want to hide lists and show an error message view here
            }

            // Update progress UI elements
            binding.progressIndicator.progress = state.progressPercent
            binding.progressText.text = "${state.progressPercent}%"
            binding.progressSummaryText.text = state.progressSummary

            // Update overall empty state visibility
            binding.emptyStateView.isVisible = !state.isLoading && state.habitItems.isEmpty() && state.error == null
        })

        // Observe derived LiveData for priority habits
        viewModel.priorityHabits.observe(viewLifecycleOwner, Observer { priorityList ->
            Log.d(logTag, "Observed priorityHabits. Count: ${priorityList.size}")
            priorityHabitAdapter.submitList(priorityList) // Submit list to adapter
            val hasPriority = priorityList.isNotEmpty()
            binding.priorityGroup.isVisible = hasPriority
            // Check if priorityDivider exists before accessing it (if you added it)
            // binding.priorityDivider?.isVisible = hasPriority
        })

        // Observe derived LiveData for non-priority habits
        viewModel.nonPriorityHabits.observe(viewLifecycleOwner, Observer { nonPriorityList ->
            Log.d(logTag, "Observed nonPriorityHabits. Count: ${nonPriorityList.size}")
            allHabitsAdapter.submitList(nonPriorityList) // Submit list to adapter
            binding.allHabitsGroup.isVisible = nonPriorityList.isNotEmpty()
        })
    }

    // Setup listeners for UI elements like the FAB
    private fun setupClickListeners() {
        Log.d(logTag, "Setting up Click Listeners")
        binding.addHabitFab.setOnClickListener {
            Log.d(logTag, "Add Habit FAB clicked.")
            // Navigate to AddHabitFragment using Safe Args action
            val action = HabitsFragmentDirections.actionNavigationHabitsToAddHabitFragment(
                habitId = null, // Add mode
                title = getString(R.string.new_habit)
            )
            findNavController().navigate(action)
        }
    }

    // Update the date text view in the header
    private fun updateDateHeader() {
        val dateFormat = SimpleDateFormat(getString(R.string.date_format_today), Locale.getDefault())
        binding.headerDate.text = dateFormat.format(Date())
    }

    // --- HabitInteractionListener Implementation ---

    // Called when a habit row (not the checkbox) is clicked
    override fun onHabitClicked(habitUiItem: HabitUiItem) {
        Log.d(logTag, "onHabitClicked: Habit ID: ${habitUiItem.habit.id}")
        // Navigate to HabitDetailFragment using Safe Args action, passing the habit ID
        val action = HabitsFragmentDirections.actionNavigationHabitsToHabitDetailFragment(habitUiItem.habit.id)
        findNavController().navigate(action)
    }

    // Called when a habit checkbox is clicked
    override fun onHabitCheckClicked(habitUiItem: HabitUiItem) {
        Log.d(logTag, "onHabitCheckClicked: Habit ID: ${habitUiItem.habit.id}")
        // Delegate the toggle action to the ViewModel
        viewModel.toggleHabitCompletion(habitUiItem)
    }

    // --- Fragment Lifecycle ---

    // Called when the fragment is visible to the user and actively running.
    override fun onResume() {
        super.onResume()
        Log.d(logTag, "onResume called - Triggering background habit reload.")
        // Reload data when the fragment becomes visible again.
        // Use showLoading = false to avoid flashing the loading indicator on every resume.
        // The initial load in the ViewModel's init block handles the first loading state.
        viewModel.loadHabits(showLoading = false)
    }

    override fun onStart() {
        super.onStart()
        Log.d(logTag, "onStart called")
    }

    override fun onStop() {
        super.onStop()
        Log.d(logTag, "onStop called")
    }


    // Clean up View Binding reference when the view is destroyed
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(logTag, "onDestroyView called")
        // Important to clear adapter references to avoid leaks if views are complex
        binding.priorityHabitsRecyclerView.adapter = null
        binding.allHabitsRecyclerView.adapter = null
        _binding = null // Clean up View Binding reference
    }
}