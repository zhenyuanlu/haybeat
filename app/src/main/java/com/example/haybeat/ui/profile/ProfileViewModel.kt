package com.example.haybeat.ui.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.haybeat.data.model.User // Optional User model for Firestore profile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore // Needed if fetching profile from Firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Data class representing the user profile information shown in the UI.
data class UserProfileUiState(
    val name: String? = null,
    val email: String? = null,
    val photoUrl: String? = null, // URL as String
    val membershipStatus: String? = "Free Member", // Example default
    val isLoading: Boolean = true
)

class ProfileViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance() // If fetching from Firestore

    // LiveData holding the current state of the user profile UI.
    private val _userProfileState = MutableLiveData<UserProfileUiState>(UserProfileUiState(isLoading = true))
    val userProfileState: LiveData<UserProfileUiState> get() = _userProfileState

    init {
        loadUserProfile() // Load profile when ViewModel is created
    }

    /** Loads user profile data from Firebase Auth and optionally Firestore. */
    fun loadUserProfile() {
        _userProfileState.value = UserProfileUiState(isLoading = true) // Set loading state
        val firebaseUser: FirebaseUser? = auth.currentUser

        if (firebaseUser == null) {
            Log.w("ProfileViewModel", "No authenticated user found.")
            _userProfileState.value = UserProfileUiState(isLoading = false) // Not loading, but no user
            return
        }

        val nameFromAuth = firebaseUser.displayName?.takeIf { it.isNotBlank() }
            ?: firebaseUser.email?.substringBefore('@') // Fallback to email prefix
        val emailFromAuth = firebaseUser.email
        val photoUrlFromAuth = firebaseUser.photoUrl?.toString()


        viewModelScope.launch {
            var firestoreProfile: User? = null
            try {
                // Assumes you have a 'users' collection with document ID = user UID
                val docRef = firestore.collection("users").document(firebaseUser.uid)
                firestoreProfile = docRef.get().await().toObject(User::class.java)
                Log.d("ProfileViewModel", "Firestore profile fetched: $firestoreProfile")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error fetching Firestore user profile", e)
                // Handle error - proceed with Auth data only
            }

            _userProfileState.postValue(
                UserProfileUiState(
                    // Prioritize Firestore data if available
                    name = firestoreProfile?.displayName ?: nameFromAuth,
                    email = firestoreProfile?.email ?: emailFromAuth,
                    photoUrl = firestoreProfile?.photoUrl ?: photoUrlFromAuth,
                    membershipStatus = firestoreProfile?.membershipStatus ?: "Free Member", // Default if not in Firestore
                    isLoading = false // Loading finished
                )
            )
        }
    }

    fun signOut() {
        Log.i("ProfileViewModel", "Signing out user...")
        auth.signOut()
        // Navigation is handled by the Fragment observing the AuthStateListener
    }
}