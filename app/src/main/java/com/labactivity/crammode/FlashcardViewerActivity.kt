package com.labactivity.crammode

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

class FlashcardViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnReveal: Button
    private lateinit var btnCorrect: Button
    private lateinit var btnIncorrect: Button
    private lateinit var btnReviewMistakes: Button

    private val firestore = FirebaseFirestore.getInstance()

    private val flashcardAttempts = mutableListOf<FlashcardAttempt>()
    private var studyDeck = arrayListOf<Flashcard>()
    private var weakFlashcards = mutableListOf<Flashcard>()
    private var readOnly = false
    private var isReviewingWeakFlashcards = false
    private var sessionSaved = false
    private var currentIndex = 0
    private val weakFlashcardAttempts = mutableListOf<FlashcardAttempt>()


    private var allWeakFlashcardsCompleted = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcard_viewer)

        readOnly = intent.getBooleanExtra("readOnly", false)
        isReviewingWeakFlashcards = intent.getBooleanExtra("isReviewingWeakFlashcards", false)
        if (isReviewingWeakFlashcards) readOnly = false

        initUI()
        viewPager.isUserInputEnabled = !readOnly
        loadFlashcardsFromIntent()
    }

    private fun initUI() {
        viewPager = findViewById(R.id.viewPager)
        btnReveal = findViewById(R.id.btnReveal)
        btnCorrect = findViewById(R.id.btnCorrect)
        btnIncorrect = findViewById(R.id.btnIncorrect)
        btnReviewMistakes = findViewById(R.id.btnReviewMistakes)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnReveal.setOnClickListener { revealAnswerForCurrentCard() }
        btnCorrect.setOnClickListener { recordAttempt(true) }
        btnIncorrect.setOnClickListener { recordAttempt(false) }
        btnReviewMistakes.setOnClickListener { loadWeakFlashcards() }
    }

    private fun loadFlashcardsFromIntent() {
        val flashcards = intent.getSerializableExtra("flashcards") as? ArrayList<Flashcard>
        if (flashcards.isNullOrEmpty()) {
            Toast.makeText(this, "No flashcards received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        studyDeck = flashcards.map { it.copy(interval = 1, lastSeenIndex = -1) } as ArrayList
        flashcardAttempts.clear()
        currentIndex = 0

        if (readOnly) {
            val adapter = FlashcardAdapter(studyDeck) { _, _ -> updateButtonsForCurrentCard() }
            viewPager.adapter = adapter
            viewPager.isUserInputEnabled = true
            updateButtonsForCurrentCard()
        } else {
            showNextCard()
        }
    }

    private fun showNextCard() {
        if (studyDeck.isEmpty()) {
            Log.d("FlashcardDebug", "Study deck empty, ending session.")
            endSession()
            return
        }

        val currentCard = studyDeck.first()
        val adapter = FlashcardAdapter(arrayListOf(currentCard)) { _, _ ->
            updateButtonsForCurrentCard()
        }
        viewPager.adapter = adapter
        viewPager.currentItem = 0
        viewPager.isUserInputEnabled = !readOnly

        Log.d(
            "FlashcardDebug",
            "${if (isReviewingWeakFlashcards) "Weak" else "Normal"} flashcard: ${currentCard.question}"
        )
        updateButtonsForCurrentCard()

    }

    private fun revealAnswerForCurrentCard() {
        val holder = (viewPager.getChildAt(0) as? RecyclerView)
            ?.findViewHolderForAdapterPosition(0) as? FlashcardAdapter.ViewHolder
        holder?.revealAnswer()
        updateButtonsForCurrentCard()
    }

    private fun recordAttempt(isCorrect: Boolean) {
        if (studyDeck.isEmpty()) return

        val currentFlashcard = studyDeck.removeAt(0)

        if (isReviewingWeakFlashcards) {
            weakFlashcardAttempts.add(FlashcardAttempt(currentFlashcard, isCorrect))

            if (isCorrect) {
                // Remove from weakFlashcards so it decreases
                weakFlashcards.remove(currentFlashcard)
                // Mark in Firestore so it won't appear again in weak review
                markFlashcardReviewed(currentFlashcard)
            }

        } else {
            flashcardAttempts.add(FlashcardAttempt(currentFlashcard, isCorrect))
            updateFlashcardStats(currentFlashcard, isCorrect)
        }

        currentFlashcard.interval = if (isCorrect) currentFlashcard.interval * 2 else 1

        if (studyDeck.isEmpty()) {
            if (isReviewingWeakFlashcards) {
                Log.d("FlashcardDebug", "Weak review finished.")
                isReviewingWeakFlashcards = false
                endSession(isWeakReview = true)
            } else {
                endSession()
            }
        } else {
            showNextCard()
        }

        updateButtonsForCurrentCard()

    }

    // New helper function
    private fun markFlashcardReviewed(flashcard: Flashcard) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val safeQuestion = flashcard.question.replace("/", "_")
        val docRef = firestore.collection("user_flashcard_stats")
            .document("${user.uid}-$safeQuestion")

        docRef.update("wrongAttempts", 0)
            .addOnSuccessListener { Log.d("FlashcardDebug", "Marked '${flashcard.question}' as reviewed") }
            .addOnFailureListener { Log.e("FlashcardDebug", "Failed to update weak flashcard: ${it.message}") }

    }



    private fun updateButtonsForCurrentCard() {
        val adapter = viewPager.adapter as? FlashcardAdapter ?: return
        val isBack = adapter.isBackVisibleAt(0)

        when {
            readOnly -> {
                btnReveal.visibility = View.GONE
                btnCorrect.visibility = View.GONE
                btnIncorrect.visibility = View.GONE
                btnReviewMistakes.visibility = View.GONE
            }
            else -> {
                btnReveal.visibility = if (!isBack) View.VISIBLE else View.GONE
                btnCorrect.visibility = if (isBack) View.VISIBLE else View.GONE
                btnIncorrect.visibility = if (isBack) View.VISIBLE else View.GONE
                btnReviewMistakes.visibility =
                    if (!isReviewingWeakFlashcards && weakFlashcards.isNotEmpty() && !allWeakFlashcardsCompleted) View.VISIBLE else View.GONE
            }
        }

    }

    private fun endSession(isWeakReview: Boolean = false) {
        val total = if (isWeakReview) weakFlashcardAttempts.size else flashcardAttempts.size
        val correct = if (isWeakReview) weakFlashcardAttempts.count { it.isCorrect }
        else flashcardAttempts.count { it.isCorrect }
        val incorrect = total - correct

        val weakFlashcardsList = if (isWeakReview) {
            weakFlashcardAttempts.map { it.flashcard } as ArrayList<Flashcard>
        } else {
            flashcardAttempts.filter { !it.isCorrect }.map { it.flashcard } as ArrayList<Flashcard>
        }

        // Save session only if normal review
        if (!sessionSaved && !isWeakReview) {
            saveFlashcardSessionToHistory()
            sessionSaved = true
        }

        val intent = Intent(this, FlashcardSummaryActivity::class.java).apply {
            putExtra("total", total)
            putExtra("correct", correct)
            putExtra("incorrect", incorrect)
            putExtra("weakFlashcards", weakFlashcardsList)
        }
        startActivity(intent)
        finish()
    }


    private fun updateFlashcardStats(flashcard: Flashcard, isCorrect: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val safeQuestion = flashcard.question.replace("/", "_")
        val docRef =
            firestore.collection("user_flashcard_stats").document("${user.uid}-$safeQuestion")

        firestore.runTransaction { txn ->
            val snap = txn.get(docRef)
            val total = (snap.getLong("totalAttempts") ?: 0) + 1
            val wrong = (snap.getLong("wrongAttempts") ?: 0) + if (!isCorrect) 1 else 0
            txn.set(
                docRef, mapOf(
                    "uid" to user.uid,
                    "question" to flashcard.question,
                    "answer" to flashcard.answer,
                    "totalAttempts" to total,
                    "wrongAttempts" to wrong,
                    "accuracy" to if (total > 0) 1f - (wrong.toFloat() / total) else 1f,
                    "lastReviewed" to System.currentTimeMillis()
                )
            )
        }
    }

    private fun saveFlashcardSessionToHistory() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (readOnly || isReviewingWeakFlashcards) return

        val timestamp = System.currentTimeMillis()
        val historyData = hashMapOf(
            "uid" to user.uid,
            "timestamp" to timestamp,
            "type" to "flashcards",
            "total" to flashcardAttempts.size,
            "correct" to flashcardAttempts.count { it.isCorrect },
            "incorrect" to flashcardAttempts.count { !it.isCorrect },
            "flashcards" to flashcardAttempts.map { att ->
                mapOf(
                    "question" to att.flashcard.question,
                    "answer" to att.flashcard.answer,
                    "isCorrect" to att.isCorrect
                )
            }
        )
        firestore.collection("study_history").add(historyData)
    }

    private fun loadWeakFlashcards() {
        if (allWeakFlashcardsCompleted) {
            Toast.makeText(this, "All weak flashcards have already been reviewed!", Toast.LENGTH_SHORT).show()
            return
        }

        val user = FirebaseAuth.getInstance().currentUser ?: return

        firestore.collection("user_flashcard_stats")
            .whereEqualTo("uid", user.uid)
            .get()
            .addOnSuccessListener { snap ->
                val loadedWeakFlashcards = snap.documents.mapNotNull { doc ->
                    val wrong = doc.getLong("wrongAttempts") ?: 0
                    val question = doc.getString("question") ?: return@mapNotNull null
                    val answer = doc.getString("answer") ?: return@mapNotNull null
                    if (wrong > 0) { // Only include flashcards still marked wrong in Firestore
                        Flashcard(
                            question = question,
                            answer = answer,
                            interval = 1,
                            lastSeenIndex = -1
                        )
                    } else null
                }

                if (loadedWeakFlashcards.isEmpty()) {
                    Toast.makeText(this, "No new weak flashcards to review.", Toast.LENGTH_SHORT).show()
                    allWeakFlashcardsCompleted = true
                    updateButtonsForCurrentCard()
                    return@addOnSuccessListener
                }

                weakFlashcards = loadedWeakFlashcards.toMutableList()
                studyDeck = ArrayList(weakFlashcards)
                currentIndex = 0
                readOnly = false
                isReviewingWeakFlashcards = true

                Toast.makeText(
                    this,
                    "You have ${weakFlashcards.size} weak flashcards to review!",
                    Toast.LENGTH_SHORT
                ).show()

                showNextCard()
            }
            .addOnFailureListener {
                Log.d("FlashcardDebug", "Failed to load weak flashcards: ${it.message}")
                Toast.makeText(this, "Failed to load weak flashcards.", Toast.LENGTH_SHORT).show()
            }
    }



    data class FlashcardAttempt(
        val flashcard: Flashcard,
        val isCorrect: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )
}