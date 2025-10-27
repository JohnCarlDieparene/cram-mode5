package com.labactivity.crammode

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.labactivity.crammode.databinding.ActivityHistoryBinding
import com.labactivity.crammode.model.StudyHistory

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var historyAdapter: HistoryAdapter
    private var historyList = mutableListOf<StudyHistory>()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish() // Close activity and return to previous screen
        }

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)


        val currentUser = auth.currentUser
        if (currentUser == null) {
            binding.txtEmptyMessage.text = "Please log in to view history."
            binding.txtEmptyMessage.visibility = View.VISIBLE
            binding.recyclerHistory.visibility = View.GONE
            return
        }

        val userId = currentUser.uid
        firestore.collection("study_history")
            .whereEqualTo("uid", userId)
            .get()
            .addOnSuccessListener { result ->
                historyList = result.documents.map { doc ->
                    val history = doc.toObject(StudyHistory::class.java)!!
                    history.id = doc.id // ✅ store document ID
                    history
                }.sortedByDescending { it.timestamp }
                    .toMutableList()

                if (historyList.isEmpty()) {
                    binding.txtEmptyMessage.text = "No history found."
                    binding.txtEmptyMessage.visibility = View.VISIBLE
                    binding.recyclerHistory.visibility = View.GONE
                } else {
                    binding.txtEmptyMessage.visibility = View.GONE
                    binding.recyclerHistory.visibility = View.VISIBLE
                    historyAdapter = HistoryAdapter(historyList) { item ->
                        // Confirm deletion
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Delete")
                            .setMessage("Are you sure you want to delete this item?")
                            .setPositiveButton("Yes") { dialog, _ ->
                                deleteHistoryItem(item)
                                dialog.dismiss()
                            }
                            .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                            .show()
                    }
                    binding.recyclerHistory.adapter = historyAdapter
                }
            }
            .addOnFailureListener { e ->
                Log.e("HistoryActivity", "Firestore fetch failed: ${e.message}", e)
                binding.txtEmptyMessage.text = "Failed to load history: ${e.message}"
                binding.txtEmptyMessage.visibility = View.VISIBLE
                binding.recyclerHistory.visibility = View.GONE
            }
    }

    private fun deleteHistoryItem(item: StudyHistory) {
        val user = auth.currentUser ?: return
        firestore.collection("study_history")
            .document(item.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show()
                // Remove from local list and notify adapter
                historyList.remove(item)
                historyAdapter.notifyDataSetChanged()

                // Show "No history" if list empty
                if (historyList.isEmpty()) {
                    binding.txtEmptyMessage.text = "No history found."
                    binding.txtEmptyMessage.visibility = View.VISIBLE
                    binding.recyclerHistory.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

}
