package com.labactivity.crammode

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth

class UserProfileActivity : AppCompatActivity() {

    private lateinit var imgProfilePhoto: ImageView
    private lateinit var txtUserName: TextView
    private lateinit var txtUserEmail: TextView
    private lateinit var btnViewHistory: Button
    private lateinit var btnLogout: Button
    private lateinit var btnEditProfile: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        // Initialize views
        imgProfilePhoto = findViewById(R.id.imgProfilePhoto)
        txtUserName = findViewById(R.id.txtUserName)
        txtUserEmail = findViewById(R.id.txtUserEmail)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        btnLogout = findViewById(R.id.btnLogout)
        btnEditProfile = findViewById(R.id.btnEditProfile)

        // Load user data and setup buttons
        loadUserData()
        setupButtons()
    }

    private fun loadUserData() {
        val user = FirebaseAuth.getInstance().currentUser

        Log.d("PROFILE_PHOTO_URL", user?.photoUrl.toString()) // debug photo URL

        if (user != null) {
            val displayName = user.displayName ?: "Guest User"
            val email = user.email ?: "No email available"

            txtUserName.text = displayName
            txtUserEmail.text = email

            val photoUrl = user.photoUrl
            if (photoUrl != null) {
                Glide.with(this)
                    .load(photoUrl)
                    .circleCrop() // ensures the photo is circular
                    .placeholder(R.drawable.ic_user) // default icon while loading
                    .error(R.drawable.ic_user)       // fallback if loading fails
                    .into(imgProfilePhoto)
            } else {
                imgProfilePhoto.setImageResource(R.drawable.ic_user)
            }

        } else {
            txtUserName.text = "Guest User"
            txtUserEmail.text = "No account logged in"
            imgProfilePhoto.setImageResource(R.drawable.ic_user)
        }
    }

    private fun setupButtons() {
        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        btnEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()  // refresh profile info when returning from EditProfileActivity
    }
}
