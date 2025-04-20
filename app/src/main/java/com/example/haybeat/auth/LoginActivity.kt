package com.example.haybeat.auth // Make sure package name is correct

import android.content.Intent
import android.os.Bundle
import android.util.Patterns // For email validation
import android.view.View
import android.widget.Toast // For showing simple messages
import androidx.appcompat.app.AppCompatActivity // Base class for activities
import com.example.haybeat.MainActivity // To navigate after login
import com.example.haybeat.R // To access resources like strings
import com.example.haybeat.databinding.ActivityLoginBinding // Import generated ViewBinding class
import com.google.firebase.auth.FirebaseAuth // Firebase Authentication library
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

// LoginActivity inherits from AppCompatActivity to get standard Activity features
class LoginActivity : AppCompatActivity() {

    // Declare ViewBinding variable using 'lateinit' because it will be initialized in onCreate
    private lateinit var binding: ActivityLoginBinding
    // Declare Firebase Auth variable
    private lateinit var auth: FirebaseAuth

    // This function is called when the Activity is first created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Always call the superclass method first

        // Inflate the layout XML file (activity_login.xml) into usable View objects
        // ViewBinding creates a binding object for easy access to UI elements by their ID
        binding = ActivityLoginBinding.inflate(layoutInflater)

        // Set the user interface layout for this Activity
        setContentView(binding.root) // 'binding.root' refers to the root ConstraintLayout in activity_login.xml

        // Get an instance of the Firebase Authentication service
        auth = FirebaseAuth.getInstance()


        binding.loginButton.setOnClickListener {
            // Call the function to handle the login process
            performLogin()
        }

        // Set what happens when the "Don't have an account?" TextView is clicked
        binding.goToSignupTextView.setOnClickListener {
            // Create an Intent to start the SignupActivity
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
            // Optional: finish() // Call finish() if you don't want the user to press 'Back' to return here from Signup
        }
    }

    // Function containing the logic to perform the login attempt
    private fun performLogin() {
        // Get the text entered in the email field, convert to String, remove leading/trailing spaces
        val email = binding.emailEditText.text.toString().trim()
        // Get the text entered in the password field
        val password = binding.passwordEditText.text.toString().trim() // Trim password too

        // Check if the entered email and password are valid (not empty, correct format)
        if (!validateInput(email, password)) {
            return // Stop the function if input is invalid
        }

        // Show the loading indicator (ProgressBar) and disable buttons/fields
        setLoading(true)

        // Use Firebase Auth to sign in with the provided email and password
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task -> // Add a listener to know when the Firebase operation completes
                // Hide the loading indicator and re-enable buttons/fields
                setLoading(false)

                if (task.isSuccessful) {
                    // Login was successful!
                    // Show a brief success message
                    Toast.makeText(baseContext, "Login Successful.", Toast.LENGTH_SHORT).show()
                    // Navigate the user to the main screen of the app
                    navigateToMain()
                } else {
                    // Login failed. Get the error details.
                    val exception = task.exception // The error that occurred
                    // Determine a user-friendly error message based on the type of exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidUserException -> "No account found with this email." // Specific error for non-existent user
                        is FirebaseAuthInvalidCredentialsException -> "Incorrect password." // Specific error for wrong password
                        else -> exception?.localizedMessage ?: getString(R.string.auth_failed) // Use Firebase's message or a generic one
                    }
                    // Show the error message to the user
                    showError(errorMessage)
                }
            }
    }

    // Function to validate the email and password fields
    private fun validateInput(email: String, pass: String): Boolean {
        // Clear any previous error messages shown on the input fields
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null

        var isValid = true // Assume valid initially

        // Check if email is empty
        if (email.isEmpty()) {
            binding.emailInputLayout.error = getString(R.string.enter_email) // Show error message
            isValid = false // Mark as invalid
        }
        // Check if email format looks like a valid email address
        else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.invalid_email)
            isValid = false
        }

        // Check if password is empty
        if (pass.isEmpty()) {
            binding.passwordInputLayout.error = getString(R.string.enter_password)
            isValid = false
        }
        // Optional: Check password length (Firebase usually enforces minimum length on signup)
        // if (pass.isNotEmpty() && pass.length < 6) {
        //     binding.passwordInputLayout.error = "Password must be at least 6 characters"
        //     isValid = false
        // }

        return isValid // Return true if all checks passed, false otherwise
    }


    // Function to navigate to the MainActivity
    private fun navigateToMain() {
        // Create an Intent: an object that describes an operation to be performed (starting MainActivity)
        val intent = Intent(this, MainActivity::class.java)
        // Set flags to clear the activity history:
        // FLAG_ACTIVITY_NEW_TASK: Start MainActivity in a new task history.
        // FLAG_ACTIVITY_CLEAR_TASK: Clear any existing task associated with MainActivity before starting it.
        // Result: Pressing 'Back' from MainActivity will exit the app, not go back to LoginActivity.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent) // Start the MainActivity
        finish() // Close the LoginActivity so it's removed from the back stack
    }

    // Function to show/hide the loading progress bar and enable/disable UI controls
    private fun setLoading(isLoading: Boolean) {
        binding.loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE // Show/hide ProgressBar
        // Disable/Enable buttons and input fields while loading to prevent multiple clicks/edits
        binding.loginButton.isEnabled = !isLoading
        binding.emailEditText.isEnabled = !isLoading
        binding.passwordEditText.isEnabled = !isLoading
        binding.goToSignupTextView.isEnabled = !isLoading // Also disable the signup link
    }

    // Function to display an error message using a Toast (a small pop-up message)
    private fun showError(message: String) {
        Toast.makeText(baseContext, message, Toast.LENGTH_LONG).show() // Show message for longer duration

        // Optional: Highlight the password field if the error is specifically about the password
        if (message == "Incorrect password.") {
            binding.passwordInputLayout.error = " " // Set a non-empty error (even a space) to trigger the error state visually
            binding.passwordEditText.requestFocus() // Move cursor to password field
        }
    }
}