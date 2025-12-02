package com.labactivity.crammode

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class EditProfileActivity : AppCompatActivity() {

    private lateinit var imgProfilePhoto: ImageView
    private lateinit var edtDisplayName: EditText
    private lateinit var btnSave: Button
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        imgProfilePhoto = findViewById(R.id.imgProfilePhoto)
        edtDisplayName = findViewById(R.id.edtDisplayName)
        btnSave = findViewById(R.id.btnSave)

        loadCurrentUser()

        // Disable photo change
        imgProfilePhoto.setOnClickListener {
            Toast.makeText(this, "Changing profile photo is disabled in free version.", Toast.LENGTH_SHORT).show()
        }

        btnSave.setOnClickListener {
            saveProfileChanges()
        }
    }

    private fun loadCurrentUser() {
        auth.currentUser?.let { user ->
            edtDisplayName.setText(user.displayName)
            Glide.with(this)
                .load(user.photoUrl)
                .placeholder(R.drawable.ic_user)
                .circleCrop()
                .into(imgProfilePhoto)
        }
    }

    private fun saveProfileChanges() {
        val displayName = edtDisplayName.text.toString().trim()
        val user = auth.currentUser

        if (user == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                    finish() // return to UserProfileActivity
                } else {
                    Toast.makeText(this, "Update failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
