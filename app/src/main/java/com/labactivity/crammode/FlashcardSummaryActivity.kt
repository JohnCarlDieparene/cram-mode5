package com.labactivity.crammode

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FlashcardSummaryActivity : AppCompatActivity() {

    private lateinit var btnReviewMistakes: Button
    private lateinit var btnFinish: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcard_summary)

        val total = intent.getIntExtra("total", 0)
        val correct = intent.getIntExtra("correct", 0)
        val incorrect = intent.getIntExtra("incorrect", 0)

        findViewById<TextView>(R.id.textTotal).text = "Total reviewed: $total"
        findViewById<TextView>(R.id.textCorrect).text = "Correct: $correct"
        findViewById<TextView>(R.id.textIncorrect).text = "Incorrect: $incorrect"
        findViewById<TextView>(R.id.textAccuracy).text = "Accuracy: ${if (total > 0) (correct * 100 / total) else 0}%"

        btnReviewMistakes = findViewById(R.id.btnReviewMistakesSummary)
        btnFinish = findViewById(R.id.btnFinishSummary)

        btnFinish.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()

        val weakFlashcards = intent.getSerializableExtra("weakFlashcards") as? ArrayList<Flashcard>

        if (weakFlashcards.isNullOrEmpty()) {
            btnReviewMistakes.visibility = View.GONE
        } else {
            btnReviewMistakes.visibility = View.VISIBLE
            btnReviewMistakes.setOnClickListener {
                val intent = Intent(this, FlashcardViewerActivity::class.java)
                intent.putExtra("flashcards", weakFlashcards)
                intent.putExtra("readOnly", false)
                intent.putExtra("isReviewingWeakFlashcards", true)
                startActivity(intent)
                finish()
            }
        }
    }

}