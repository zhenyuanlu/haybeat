package com.example.haybeat.ui.profile

import android.content.Intent
import android.content.SharedPreferences // Import for saving theme preference
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.haybeat.R
import com.example.haybeat.auth.LoginActivity
import com.example.haybeat.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
// Import Glide/Coil if using for image loading:
// import com.bumptech.glide.Glide
import androidx.preference.PreferenceManager // **** ADD THIS IMPORT ****
import androidx.core.view.isVisible          // **** ADD THIS IMPORT ****
import androidx.core.content.ContextCompat

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var auth: FirebaseAuth // Keep direct auth reference for listener
    private lateinit var authStateListener: FirebaseAuth.AuthStateListener
    private lateinit var sharedPreferences: SharedPreferences

    // Key for saving theme preference
    private val PREF_KEY_DARK_MODE = "pref_dark_mode_enabled"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        observeViewModel()
        setupAuthStateListener()
        setupDarkModeSwitch()

        // Reload profile data when the fragment is shown
        viewModel.loadUserProfile()
    }

    // Observe profile data from ViewModel
    private fun observeViewModel() {
        viewModel.userProfileState.observe(viewLifecycleOwner) { state ->
            // TODO: Handle loading state (show spinner?)
            // binding.profileLoadingSpinner.isVisible = state.isLoading
            // binding.profileContentGroup.isVisible = !state.isLoading

            if (!state.isLoading) {
                binding.profileName.text = state.name ?: "User"
                binding.profileEmail.text = state.email ?: "No email"
                binding.membershipChip.text = state.membershipStatus
                binding.membershipChip.isVisible = !state.membershipStatus.isNullOrBlank()

                // TODO: Load profile image using Glide or Coil if photoUrl exists
                if (state.photoUrl != null) {
                    // Glide.with(this).load(state.photoUrl).circleCrop().placeholder(R.drawable.ic_user).into(binding.profileImage)
                } else {
                    // Set default placeholder
                    binding.profileImage.setImageResource(R.drawable.ic_user)
                    binding.profileImage.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.grey_500) // Adjust tint
                }
            }
        }
    }

    // Setup listeners for buttons and settings items
    private fun setupClickListeners() {
        binding.signOutButton.setOnClickListener {
            showSignOutConfirmation()
        }
        // TODO: Implement navigation/actions for these settings items
        binding.settingsNotificationsItem.setOnClickListener {
            Toast.makeText(context, "Notification Settings (Not Implemented)", Toast.LENGTH_SHORT).show()
        }
        binding.settingsAppSettingsItem.setOnClickListener {
            Toast.makeText(context, "App Settings (Not Implemented)", Toast.LENGTH_SHORT).show()
        }
        binding.settingsExportItem.setOnClickListener {
            Toast.makeText(context, "Export Data (Not Implemented)", Toast.LENGTH_SHORT).show()
        }
        binding.settingsHelpItem.setOnClickListener {
            Toast.makeText(context, "Help & Support (Not Implemented)", Toast.LENGTH_SHORT).show()
        }
    }

    // Setup the Dark Mode switch and its listener
    private fun setupDarkModeSwitch() {
        // Set initial switch state based on current theme/preference
        val isDarkModeCurrentlyEnabled = sharedPreferences.getBoolean(PREF_KEY_DARK_MODE, isSystemInDarkMode())
        binding.darkModeSwitch.isChecked = isDarkModeCurrentlyEnabled
        // Immediately apply based on preference on first load
        // applyTheme(isDarkModeCurrentlyEnabled) // Optional: apply immediately

        // Listener for switch changes
        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveThemePreference(isChecked)
            applyTheme(isChecked)
        }
    }


    private fun isSystemInDarkMode(): Boolean {
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }


    private fun saveThemePreference(isDarkMode: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_KEY_DARK_MODE, isDarkMode).apply()
    }


    private fun applyTheme(isDarkMode: Boolean) {
        val mode = if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        // Apply the theme change
        AppCompatDelegate.setDefaultNightMode(mode)
        // Note: This typically requires the Activity to be recreated to fully apply.
        // A common approach is to show a small message asking the user to restart,
        // or just let it apply fully on the next app launch.
        // requireActivity().recreate() // <-- This restarts the activity, can be jarring.
        Log.d("ProfileFragment", "Dark mode set to: $isDarkMode. Activity restart might be needed.")
    }


    // Shows the confirmation dialog before signing out
    private fun showSignOutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sign_out)
            .setMessage(R.string.sign_out_confirm) // Use specific confirmation string
            .setPositiveButton(R.string.sign_out) { _, _ ->
                viewModel.signOut() // Delegate sign out to ViewModel
                // The AuthStateListener below will handle navigation
            }
            .setNegativeButton(R.string.cancel, null) // Do nothing on cancel
            .show()
    }

    // Listen for Firebase Authentication state changes (specifically sign out)
    private fun setupAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            // Check if the user is null AND the fragment is still attached to its context
            if (firebaseAuth.currentUser == null && context != null) {
                Log.i("ProfileFragment", "Auth state changed: User signed out. Navigating to Login.")
                // User signed out, navigate to LoginActivity and clear the back stack
                navigateToLogin()
            }
        }
    }

    // Navigates to LoginActivity, clearing the task stack
    private fun navigateToLogin() {
        // Ensure context is available before creating Intent
        context?.let { ctx ->
            val intent = Intent(ctx, LoginActivity::class.java)
            // Clear the activity stack so user cannot press back to MainActivity
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            // Finish the hosting MainActivity
            activity?.finish()
        } ?: run {
            Log.e("ProfileFragment", "Context was null when trying to navigate to Login.")
        }
    }

    // Register the listener when the fragment starts
    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }

    // Unregister the listener when the fragment stops
    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
    }

    // Clean up View Binding reference
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}