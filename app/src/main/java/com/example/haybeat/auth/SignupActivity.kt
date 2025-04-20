package com.example.haybeat.auth // Ensure correct package

import android.content.Intent
import android.os.Bundle
import android.util.Patterns // For email validation
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.haybeat.MainActivity // To navigate after signup
import com.example.haybeat.R // To access resources
import com.example.haybeat.databinding.ActivitySignupBinding // Import generated ViewBinding class
import com.google.firebase.auth.FirebaseAuth // Firebase Authentication
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest // To set display name (optional)

class SignupActivity : AppCompatActivity() {

    // Declare ViewBinding and Firebase Auth variables
    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate layout and set content view using ViewBinding
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Set click listener for the signup button
        binding.signupButton.setOnClickListener {
            performSignup() // Call the signup logic function
        }

        // Set click listener for the "Already have an account?" TextView
        binding.goToLoginTextView.setOnClickListener {

            finish()

        }
    }

    // Function containing the logic to perform the signup attempt
    private fun performSignup() {
        // Get input values from the EditText fields and trim whitespace
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()

        // Validate the entered information
        if (!validateInput(email, password, confirmPassword)) {
            return // Stop if validation fails
        }

        // Show loading indicator and disable UI
        setLoading(true)

        // Use Firebase Auth to create a new user with email and password
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task -> // Add listener for completion
                // Hide loading indicator and re-enable UI
                setLoading(false)

                if (task.isSuccessful) {
                    // Signup was successful!
                    Toast.makeText(baseContext, "Signup Successful.", Toast.LENGTH_SHORT).show()
                    navigateToMain()

                } else {
                    // Signup failed. Determine the error.
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        // Specific error if password is not strong enough (Firebase requires >= 6 chars)
                        is FirebaseAuthWeakPasswordException -> getString(R.string.weak_password)
                        // Specific error if the email address is already registered
                        is FirebaseAuthUserCollisionException -> getString(R.string.email_in_use)
                        // Less common for signup, but possible if email format is severely wrong server-side
                        // is FirebaseAuthInvalidCredentialsException -> getString(R.string.invalid_email)
                        else -> exception?.localizedMessage ?: getString(R.string.auth_failed) // Generic error
                    }
                    // Show the error message
                    showError(errorMessage)
                }
            }
    }

    // Function to validate the signup input fields
    private fun validateInput(email: String, pass: String, confirmPass: String): Boolean {
        // Clear previous errors
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.confirmPasswordInputLayout.error = null

        var isValid = true

        // Validate Email
        if (email.isEmpty()) {
            binding.emailInputLayout.error = getString(R.string.enter_email)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = getString(R.string.invalid_email)
            isValid = false
        }

        // Validate Password
        if (pass.isEmpty()) {
            binding.passwordInputLayout.error = getString(R.string.enter_password)
            isValid = false
        } else if (pass.length < 6) { // Enforce minimum length
            binding.passwordInputLayout.error = getString(R.string.weak_password)
            isValid = false
        }

        // Validate Confirm Password
        if (confirmPass.isEmpty()) {
            binding.confirmPasswordInputLayout.error = "Please confirm password"
            isValid = false
        } else if (pass != confirmPass) { // Check if passwords match
            binding.confirmPasswordInputLayout.error = getString(R.string.password_mismatch)
            isValid = false
        }

        return isValid // Return true if all checks passed
    }

    // Optional Function: Update the newly created user's profile information
    private fun updateUserProfile(email: String) {
        val user = auth.currentUser // Get the newly signed-up user

        // Create a profile change request using a builder pattern
        val profileUpdates = UserProfileChangeRequest.Builder()
            // Set the display name (e.g., use the part of the email before the '@')
            .setDisplayName(email.substringBefore('@'))
            // You could also set a photo URI here if you had one
            // .setPhotoUri(Uri.parse("..."))
            .build() // Build the request object

        // Apply the profile updates to the user
        user?.updateProfile(profileUpdates)
            ?.addOnCompleteListener { task -> // Add a listener (optional)
                if (task.isSuccessful) {
                    // Log or confirm that the profile was updated (optional)
                    // Log.d("SignupActivity", "User profile updated.")
                } else {
                    // Handle error updating profile (less critical usually)
                    // Log.w("SignupActivity", "Error updating user profile.", task.exception)
                }
            }
    }

    // Function to navigate to MainActivity and clear the back stack
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Close SignupActivity
    }

    // Function to show/hide loading state and control UI element enabled state
    private fun setLoading(isLoading: Boolean) {
        binding.loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.signupButton.isEnabled = !isLoading
        binding.emailEditText.isEnabled = !isLoading
        binding.passwordEditText.isEnabled = !isLoading
        binding.confirmPasswordEditText.isEnabled = !isLoading
        binding.goToLoginTextView.isEnabled = !isLoading
    }

    // Function to show error messages using a Toast
    private fun showError(message: String) {
        Toast.makeText(baseContext, message, Toast.LENGTH_LONG).show()
        // Optional: Highlight the specific field related to the error
        if (message == getString(R.string.email_in_use)) {
            binding.emailInputLayout.error = " " // Use space to trigger error state visually
            binding.emailEditText.requestFocus()
        } else if (message == getString(R.string.weak_password)) {
            binding.passwordInputLayout.error = " "
            binding.passwordEditText.requestFocus()
        }
    }
}