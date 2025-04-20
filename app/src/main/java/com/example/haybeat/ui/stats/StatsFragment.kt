package com.example.haybeat.ui.stats

// AndroidX Imports
import android.graphics.Color // For chart styling
import android.os.Bundle
import android.util.Log // For logging
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast // For showing messages
import androidx.appcompat.app.AlertDialog // For info dialog
import androidx.core.content.ContextCompat // For getting colors
import androidx.core.view.isVisible // For view visibility extension
import androidx.fragment.app.Fragment // Base class
import androidx.fragment.app.viewModels // KTX delegate for ViewModel
import androidx.lifecycle.Observer // Use Observer for LiveData
import androidx.navigation.fragment.findNavController // For navigation
import androidx.recyclerview.widget.LinearLayoutManager // For RecyclerView layout
import androidx.recyclerview.widget.RecyclerView // RecyclerView class
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout // SwipeRefreshLayout import

// Project specific imports
import com.example.haybeat.R // Resource IDs
import com.example.haybeat.data.model.Challenge // Data model
import com.example.haybeat.data.model.Habit // Data model
import com.example.haybeat.data.model.ParticipantProgress // Data model
import com.example.haybeat.databinding.FragmentStatsBinding // ViewBinding class

// Charting Library Imports
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet

/**
 * Fragment responsible for displaying various statistics, charts, challenges, and leaderboards.
 * Implements ChallengeInteractionListener to handle clicks on challenge items, join, and leave actions.
 */
class StatsFragment : Fragment(), ChallengeInteractionListener {

    // View Binding instance - nullable because view lifecycle is separate from fragment lifecycle
    private var _binding: FragmentStatsBinding? = null
    // Non-null accessor for binding (only use between onCreateView and onDestroyView)
    private val binding get() = _binding!!
    // Tag for logging specific to this fragment
    private val logTag = "StatsFragment_DEBUG"

    // ViewModel instance scoped to this fragment's lifecycle
    private val viewModel: StatsViewModel by viewModels()

    // --- Store SwipeRefreshLayout reference EXPLICITLY ---
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    // Adapters for the RecyclerViews displaying lists
    private lateinit var rankingAdapter: HabitRankingAdapter
    private lateinit var challengeAdapter: ChallengeAdapter
    private lateinit var leaderboardAdapter: LeaderboardAdapter

    // Called to inflate the layout for this fragment
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(logTag, "onCreateView called")
        // Inflate the layout using ViewBinding
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        // Return the root view of the inflated layout
        return binding.root
    }

    // Called after the view has been created - setup UI and observers here
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(logTag, "onViewCreated called")

        // --- Find SwipeRefreshLayout EXPLICITLY using ID ---
        // Use the root view from the binding to find the child view
        swipeRefreshLayout = binding.root.findViewById(R.id.stats_swipe_refresh_layout)
        if (swipeRefreshLayout == null) {
            Log.e(logTag, "CRITICAL: SwipeRefreshLayout with ID 'stats_swipe_refresh_layout' not found in the view hierarchy! Refresh will not work.")
            // Optionally disable refresh features or show an error to the user
        } else {
            Log.d(logTag, "SwipeRefreshLayout found successfully.")
        }

        setupRecyclerViews()    // Configure RecyclerViews
        setupClickListeners()   // Set up button clicks (uses swipeRefreshLayout variable)
        observeViewModel()      // Start observing data changes (uses swipeRefreshLayout variable)
        setupCharts()           // Apply initial styling to charts
    }

    // Called when the fragment becomes visible to the user
    override fun onResume() {
        super.onResume()
        Log.d(logTag, "onResume called - Calling loadInitialData in ViewModel")
        // Refresh data every time the screen becomes visible to ensure freshness
        // The loading indicator state will be handled by the observer
        viewModel.loadInitialData()
    }

    // Called when the view hierarchy associated with the fragment is being removed
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(logTag, "onDestroyView called - Cleaning up adapters and binding")
        // Prevent memory leaks by nullifying adapter references when view is destroyed
        if(::rankingAdapter.isInitialized) binding.habitRankingRecyclerView.adapter = null
        if(::challengeAdapter.isInitialized) binding.challengesRecyclerView.adapter = null
        if(::leaderboardAdapter.isInitialized) binding.leaderboardRecyclerView.adapter = null
        swipeRefreshLayout = null // Clear reference to SwipeRefreshLayout
        // Nullify the binding object itself
        _binding = null
    }

    // --- Setup Functions ---

    /** Configures all RecyclerViews with their LayoutManagers and Adapters */
    private fun setupRecyclerViews() {
        Log.d(logTag, "Setting up RecyclerViews")
        // Habit Ranking List
        rankingAdapter = HabitRankingAdapter()
        binding.habitRankingRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = rankingAdapter
            isNestedScrollingEnabled = false // Disable nested scrolling inside NestedScrollView
        }
        // Challenge List
        challengeAdapter = ChallengeAdapter(this) // Pass 'this' fragment as the listener
        binding.challengesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = challengeAdapter
            isNestedScrollingEnabled = false
        }
        // Leaderboard List
        leaderboardAdapter = LeaderboardAdapter()
        binding.leaderboardRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = leaderboardAdapter
            isNestedScrollingEnabled = false
        }
    }

    /** Sets up onClickListeners for interactive UI elements */
    private fun setupClickListeners() {
        Log.d(logTag, "Setting up Click Listeners")

        // Setup SwipeRefreshLayout Listener (using the explicitly found reference)
        swipeRefreshLayout?.setOnRefreshListener { // Use safe call ?.
            Log.d(logTag, "Swipe to refresh triggered.")
            viewModel.loadInitialData() // Call ViewModel to reload all data
        }
        // Optionally set swipe refresh indicator colors
        swipeRefreshLayout?.setColorSchemeResources( // Use safe call ?.
            R.color.teal_500, R.color.amber_500, R.color.blue_500
        )

        // Setup Manual Refresh Button Listener
        binding.refreshButton.setOnClickListener {
            Log.d(logTag, "Manual refresh button clicked.")
            // Show the swipe indicator visually and trigger the refresh
            swipeRefreshLayout?.isRefreshing = true // Use safe call ?.
            viewModel.loadInitialData()
        }

        // Consistency Score Info Button
        binding.consistencyInfoButton.setOnClickListener { showConsistencyInfoDialog() }

        // Create Challenge Button Navigation
        binding.createChallengeButton.setOnClickListener {
            Log.d(logTag, "Create Challenge button clicked - Navigating.")
            // Use the Safe Args generated action ID to navigate
            try {
                findNavController().navigate(R.id.action_navigation_stats_to_createChallengeFragment)
            } catch (e: Exception) {
                Log.e(logTag, "Navigation to CreateChallengeFragment failed", e)
                Toast.makeText(context, "Error navigating to create challenge.", Toast.LENGTH_SHORT).show()
            }
        }

        // Clear selected challenge when clicking on the main background (NestedScrollView)
        binding.root.findViewById<androidx.core.widget.NestedScrollView>(R.id.stats_scroll_view)?.setOnClickListener { // Find NestedScrollView if it has ID
            viewModel.clearSelectedChallenge()
        }
        // Prevent clicks on the leaderboard section itself from closing it
        binding.leaderboardSection.setOnClickListener { /* Consume click, do nothing */ }
    }

    /** Sets up observers to listen for data changes from the ViewModel's LiveData */
    private fun observeViewModel() {
        Log.d(logTag, "Setting up ViewModel Observers with LiveData")
        viewModel.statsScreenState.observe(viewLifecycleOwner, Observer { state ->
            // The 'state' parameter here IS the StatsScreenState object
            if (state == null) {
                Log.w(logTag, "Observed null state, returning.")
                return@Observer // Avoid processing if state is null
            }

            Log.d(logTag, "Observed statsScreenState update: isLoadingHabits=${state.isLoadingHabits}, isLoadingChal=${state.isLoadingChallenges}, isProcessingAction=${state.isProcessingChallengeAction}, error=${state.error}")

            // --- Control SwipeRefreshLayout Indicator ---
            val isOverallLoading = state.isLoadingHabits || state.isLoadingChallenges
            // Only update isRefreshing if the state actually changed and layout exists
            if (swipeRefreshLayout?.isRefreshing != isOverallLoading) {
                swipeRefreshLayout?.isRefreshing = isOverallLoading
            }
            // Disable manual refresh button while loading
            binding.refreshButton.isEnabled = !isOverallLoading


            // --- Handle Errors ---
            if (state.error != null) {
                // Show error message (consider showing only the *latest* error)
                Toast.makeText(context, "Error: ${state.error}", Toast.LENGTH_LONG).show()
                Log.e(logTag, "State Error: ${state.error}")
                // viewModel.clearError() // Implement in ViewModel if desired
            }

            // --- Update UI Sections based on non-loading state ---
            if (!state.isLoadingHabits) {
                updateOverallCompletion(state.overallCompletionThisMonth)
                updateConsistencyScore(state.consistencyScore, state.consistencyFactors)
                updateHabitRanking(state.rankedHabits)
                updateCompletionByDayChart(state.completionByDay)
                updateMostEfficientDay(state.mostEfficientDay)
                updateCompletionTrendChart(state.completionTrend)
            } else {
                // Optionally show habit-specific loading indicators
            }

            if (!state.isLoadingChallenges) {
                updateChallengesList(state.challenges, state.isProcessingChallengeAction)
            } else {
                // Show loading text for challenges
                binding.emptyChallengesText.isVisible = true
                binding.emptyChallengesText.text = getString(R.string.loading)
                binding.challengesRecyclerView.isVisible = false
                binding.leaderboardSection.isVisible = false // Hide leaderboard while challenges load
            }

            updateLeaderboard(state.selectedChallengeName, state.selectedChallengeLeaderboard)

        }) // End Observer
    }

    /** Initializes the visual style and basic configuration of the charts */
    private fun setupCharts() {
        Log.d(logTag, "Setting up Chart Styles")
        setupLineChartStyle(binding.completionTrendChart)
        setupBarChartStyle(binding.completionByDayChart)
    }


    // --- UI Update Helper Functions ---

    private fun updateOverallCompletion(percentage: Int) {
        // Use try-catch for safety when accessing context/resources
        try {
            binding.overallCompletionPercentText.text = getString(R.string.completion_percent_format, percentage.toFloat())
            binding.overallCompletionDescText.text = getString(R.string.this_month)
        } catch (e: IllegalStateException) {
            Log.w(logTag, "Context/resources not available in updateOverallCompletion", e)
            binding.overallCompletionPercentText.text = "$percentage%" // Fallback
        }
    }

    private fun updateConsistencyScore(score: Int, factors: String) {
        binding.consistencyProgressIndicator.progress = score
        binding.consistencyScoreText.text = score.toString()
        binding.consistencyFactorsText.text = factors

        val rating: String; val colorRes: Int
        when {
            score >= 80 -> { rating = "Excellent"; colorRes = R.color.teal_500 }
            score >= 60 -> { rating = "Good"; colorRes = R.color.amber_500 }
            else -> { rating = "Needs Improvement"; colorRes = R.color.red_500 }
        }
        binding.consistencyRatingText.text = rating
        context?.let { ctx ->
            val color = ContextCompat.getColor(ctx, colorRes)
            binding.consistencyProgressIndicator.setIndicatorColor(color)
            binding.consistencyScoreText.setTextColor(color)
        }
    }

    private fun updateHabitRanking(rankedHabits: List<Habit>) {
        rankingAdapter.submitList(rankedHabits)
        val hasHabits = rankedHabits.isNotEmpty()
        binding.emptyRankingText.isVisible = !hasHabits
        binding.habitRankingRecyclerView.isVisible = hasHabits
    }


    private fun updateMostEfficientDay(mostEfficient: Pair<String, Int>?) {
        if (mostEfficient != null && mostEfficient.second > 0) {
            binding.mostEfficientDayText.text = "${mostEfficient.first} (${mostEfficient.second}%)"
            binding.mostEfficientDayText.isVisible = true
        } else {
            binding.mostEfficientDayText.isVisible = false
        }
    }

    private fun updateChallengesList(challenges: List<Challenge>, isProcessing: Boolean) {
        challengeAdapter.submitList(challenges)
        val hasChallenges = challenges.isNotEmpty()
        // Show empty text only if NOT loading and NOT processing and list IS empty
        binding.emptyChallengesText.isVisible = !hasChallenges && !isProcessing && swipeRefreshLayout?.isRefreshing == false
        if (binding.emptyChallengesText.isVisible) binding.emptyChallengesText.text = getString(R.string.no_challenges) // Reset text if shown
        binding.challengesRecyclerView.isVisible = hasChallenges
        // Dim list while processing join/leave
        binding.challengesRecyclerView.alpha = if (isProcessing) 0.5f else 1.0f
        // Disable touch interaction on list while processing
        binding.challengesRecyclerView.suppressLayout(isProcessing) // Requires androidx.recyclerview:recyclerview:1.3.0+
    }

    /** Shows/Hides and populates the leaderboard section */
    private fun updateLeaderboard(challengeName: String?, leaderboardData: List<ParticipantProgress>?) {
        val showLeaderboard = leaderboardData != null && challengeName != null
        binding.leaderboardSection.isVisible = showLeaderboard
        if (showLeaderboard) {
            Log.d(logTag, "Updating leaderboard UI for: $challengeName")
            try {
                binding.leaderboardTitleText.text = getString(R.string.leaderboard_title_format, challengeName)
                // Ensure data is sorted before submitting
                leaderboardAdapter.submitList(leaderboardData?.sortedByDescending { it.progress })
            } catch (e: IllegalStateException) {
                Log.w(logTag, "Context/resources not available in updateLeaderboard", e)
            }
        } else {
            Log.d(logTag, "Hiding leaderboard section.")
            if (::leaderboardAdapter.isInitialized && leaderboardAdapter.itemCount > 0) { // Clear adapter only if needed
                leaderboardAdapter.submitList(emptyList())
            }
        }
    }

    // --- Chart Update & Style Functions ---

    private fun updateCompletionTrendChart(trendData: List<Pair<Float, Float>>) {
        context ?: return // Exit if fragment context is lost
        Log.d(logTag, "Updating trend chart with ${trendData.size} data points.")

        if (trendData.isEmpty()) {
            binding.completionTrendChart.clear()
            binding.completionTrendChart.data = null
            binding.completionTrendChart.invalidate()
            return
        }

        val entries = trendData.map { Entry(it.first, it.second) }
        val dataSet = LineDataSet(entries, "Completion Trend")

        // Dataset Styling
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.teal_500)
        dataSet.color = primaryColor
        dataSet.valueTextColor = ContextCompat.getColor(requireContext(), R.color.grey_600)
        dataSet.lineWidth = 2f
        dataSet.setDrawCircles(true); dataSet.setCircleColor(primaryColor); dataSet.circleRadius = 3f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawFilled(true)
        dataSet.fillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.chart_fill_teal)

        val lineData = LineData(dataSet as ILineDataSet)
        binding.completionTrendChart.data = lineData

        // X-axis Configuration
        binding.completionTrendChart.xAxis.apply {
            axisMinimum = trendData.firstOrNull()?.first ?: 1f
            axisMaximum = trendData.lastOrNull()?.first ?: (trendData.size.toFloat() + 0.5f)
            labelCount = trendData.size.coerceAtMost(5)
            valueFormatter = object : ValueFormatter() { override fun getFormattedValue(value: Float): String { return "Wk ${value.toInt()}" } }
        }
        binding.completionTrendChart.invalidate()
    }


    private fun updateCompletionByDayChart(completionByDay: List<Pair<String, Int>>) {
        context ?: return
        Log.d(logTag, "Updating day chart with ${completionByDay.size} data points.")

        if (completionByDay.isEmpty() || completionByDay.all { it.second == 0 }) {
            binding.completionByDayChart.clear()
            binding.completionByDayChart.data = null
            binding.completionByDayChart.invalidate()
            updateMostEfficientDay(null)
            return
        }

        val entries = completionByDay.mapIndexed { index, pair -> BarEntry(index.toFloat(), pair.second.toFloat()) }
        val dataSet = BarDataSet(entries, "Completion by Day")

        // Dataset Styling
        dataSet.color = ContextCompat.getColor(requireContext(), R.color.teal_400)
        dataSet.valueTextColor = ContextCompat.getColor(requireContext(), R.color.grey_700)
        dataSet.valueTextSize = 10f
        dataSet.valueFormatter = object : ValueFormatter() { override fun getFormattedValue(value: Float): String { return "${value.toInt()}%" } }
        dataSet.setDrawValues(true)

        val barData = BarData(dataSet); barData.barWidth = 0.6f
        binding.completionByDayChart.data = barData

        // X-axis Configuration
        val dayLabels = completionByDay.map { it.first }
        binding.completionByDayChart.xAxis.valueFormatter = IndexAxisValueFormatter(dayLabels)
        binding.completionByDayChart.xAxis.labelCount = dayLabels.size

        binding.completionByDayChart.invalidate()
    }


    private fun setupLineChartStyle(chart: LineChart) {
        context ?: return
        chart.description.isEnabled = false; chart.legend.isEnabled = false
        chart.setTouchEnabled(true); chart.setPinchZoom(true); chart.setScaleEnabled(true)
        chart.setDrawGridBackground(false); chart.setNoDataText(getString(R.string.loading))

        val axisTextColor = ContextCompat.getColor(requireContext(), R.color.grey_500)
        val xAxis = chart.xAxis; xAxis.position = XAxis.XAxisPosition.BOTTOM; xAxis.setDrawGridLines(false); xAxis.setDrawAxisLine(true)
        xAxis.textColor = axisTextColor; xAxis.granularity = 1f; xAxis.setAvoidFirstLastClipping(true)
        val leftAxis = chart.axisLeft; leftAxis.setDrawGridLines(true); leftAxis.setDrawAxisLine(false)
        leftAxis.textColor = axisTextColor; leftAxis.axisMinimum = 0f; leftAxis.axisMaximum = 105f; leftAxis.labelCount = 6
        chart.axisRight.isEnabled = false
    }

    private fun setupBarChartStyle(chart: BarChart) {
        context ?: return
        chart.description.isEnabled = false; chart.legend.isEnabled = false
        chart.setTouchEnabled(true); chart.setPinchZoom(false); chart.setScaleEnabled(false)
        chart.setDrawGridBackground(false); chart.setDrawBarShadow(false); chart.setFitBars(true)
        chart.setNoDataText(getString(R.string.loading))

        val axisTextColor = ContextCompat.getColor(requireContext(), R.color.grey_500)
        val xAxis = chart.xAxis; xAxis.position = XAxis.XAxisPosition.BOTTOM; xAxis.setDrawGridLines(false); xAxis.setDrawAxisLine(true)
        xAxis.textColor = axisTextColor; xAxis.granularity = 1f; xAxis.setCenterAxisLabels(true)
        val leftAxis = chart.axisLeft; leftAxis.setDrawGridLines(true); leftAxis.setDrawAxisLine(false)
        leftAxis.textColor = axisTextColor; leftAxis.axisMinimum = 0f; leftAxis.axisMaximum = 105f; leftAxis.labelCount = 6
        chart.axisRight.isEnabled = false
    }

    // --- Dialogs ---

    /** Shows an explanation dialog for the consistency score */
    private fun showConsistencyInfoDialog() {
        context?.let {
            AlertDialog.Builder(it)
                .setTitle(R.string.consistency_score)
                .setMessage(R.string.consistency_desc)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    // --- ChallengeInteractionListener Implementation ---

    override fun onChallengeClicked(challenge: Challenge) {
        Log.d(logTag, "ChallengeInteractionListener: onChallengeClicked: ${challenge.name}")
        viewModel.selectChallenge(challenge) // Tell ViewModel to update selected challenge/leaderboard
    }

    override fun onJoinChallengeClicked(challenge: Challenge) {
        Log.d(logTag, "ChallengeInteractionListener: onJoinChallengeClicked: ${challenge.name}")
        Toast.makeText(context, "Joining ${challenge.name}...", Toast.LENGTH_SHORT).show()
        viewModel.joinChallenge(challenge.id) // Tell ViewModel to handle joining
    }

    override fun onLeaveChallengeClicked(challenge: Challenge) {
        Log.d(logTag, "ChallengeInteractionListener: onLeaveChallengeClicked: ${challenge.name}")
        // Optional: Add confirmation dialog before leaving
        context?.let {
            AlertDialog.Builder(it)
                .setTitle("Leave Challenge?")
                .setMessage("Are you sure you want to leave the challenge '${challenge.name}'?")
                .setPositiveButton("Leave") { _, _ ->
                    Toast.makeText(context, "Leaving ${challenge.name}...", Toast.LENGTH_SHORT).show()
                    viewModel.leaveChallenge(challenge.id) // Tell ViewModel to handle leaving
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

} // End StatsFragment