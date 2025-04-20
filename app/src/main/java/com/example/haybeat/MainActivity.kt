package com.example.haybeat

// --- Standard Android Imports ---
import android.content.Intent
import android.content.pm.PackageManager // Needed for permission check results (optional but good practice)
import android.os.Build // Needed for SDK version checks (e.g., for permissions)
import android.os.Bundle
import android.util.Log // **** IMPORT FOR LOGGING ****
import android.widget.Toast // **** IMPORT FOR TOAST MESSAGES ****
import androidx.appcompat.app.AlertDialog // **** IMPORT FOR ALERT DIALOG ****
import androidx.appcompat.app.AppCompatActivity

// --- ActivityResult Contracts (for Permissions) ---
import androidx.activity.result.contract.ActivityResultContracts // For requesting permissions
import androidx.core.content.ContextCompat // For checking permissions

// --- Navigation Component Imports ---
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController

// --- Firebase Auth ---
import com.google.firebase.auth.FirebaseAuth

// --- ViewBinding ---
import com.example.haybeat.databinding.ActivityMainBinding // Import your ViewBinding class

// --- App Specific Imports ---
import com.example.haybeat.auth.LoginActivity // Import your LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth
    private lateinit var appBarConfiguration: AppBarConfiguration

    // --- Permission Launcher ---
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "POST_NOTIFICATIONS permission granted.")
                // Permission is granted. You can now show notifications.
            } else {
                Log.w("MainActivity", "POST_NOTIFICATIONS permission denied.")
                // Explain to the user that reminders won't work without the permission.
                Toast.makeText(this, "Reminders require notification permission.", Toast.LENGTH_LONG).show()

            }
        }

    // --- onCreate ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser == null) {
            Log.w("MainActivity", "User not authenticated, redirecting to Login.")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return // Stop further execution in onCreate
        }


        Log.d("MainActivity", "User authenticated, setting up navigation.")
        setupNavigation()
        checkAndRequestNotificationPermission() // Check permission after login
    }

    // --- Setup Navigation ---
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_habits, R.id.navigation_stats, R.id.navigation_profile
            )
        )

    }

    // --- Handle Up Navigation (if using ActionBar) ---
    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp()
    }

    // --- Notification Permission Logic ---
    private fun checkAndRequestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                // Check if permission is already granted
                ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS // Use Manifest directly
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.i("MainActivity", "Notification permission already granted.")
                }
                // Check if we should show an explanation (user denied previously without "Don't ask again")
                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.i("MainActivity", "Showing notification permission rationale.")
                    showPermissionRationaleDialog() // Show explanation dialog
                }
                // Otherwise, request the permission directly
                else -> {
                    Log.i("MainActivity", "Requesting notification permission.")
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Notification permission not needed for older Android versions
            Log.d("MainActivity", "Notification permission not required for this Android version.")
        }
    }

    // Dialog to explain why the notification permission is needed
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this) // Use 'this' as Context in Activity
            .setTitle("Notification Permission Needed")
            .setMessage("Haybeat uses notifications to send you timely reminders for your habits. Please grant permission to enable this feature.")
            .setPositiveButton("Grant Permission") { _, _ ->
                // User agreed, launch the permission request again
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                // User cancelled, reminders won't work
                dialog.dismiss()
                Toast.makeText(this, "Reminders disabled without notification permission.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}