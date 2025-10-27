package com.labactivity.crammode

import android.graphics.Color
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.labactivity.crammode.model.QuizQuestion

class QuizViewerActivity : AppCompatActivity() {

    private lateinit var txtQuestion: TextView
    private lateinit var txtScore: TextView
    private lateinit var txtFeedback: TextView
    private lateinit var txtTimer: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var radioGroup: RadioGroup
    private lateinit var optionA: RadioButton
    private lateinit var optionB: RadioButton
    private lateinit var optionC: RadioButton
    private lateinit var optionD: RadioButton

    private lateinit var btnSubmit: Button
    private lateinit var btnNext: Button

    private lateinit var btnPrevious: Button


    private var quizList: MutableList<QuizQuestion> = mutableListOf()
    private var currentIndex = 0
    private var score = 0
    private var answered = false
    private var readOnly = false
    private var quizTimestamp: Long = 0L

    private var countDownTimer: CountDownTimer? = null
    private var questionTimeMillis: Long = 15000L // default 15s

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_viewer)

        // --- UI Bindings ---
        txtQuestion = findViewById(R.id.txtQuestion)
        txtScore = findViewById(R.id.txtScore)
        txtFeedback = findViewById(R.id.txtFeedback)
        txtTimer = findViewById(R.id.txtTimer)
        progressBar = findViewById(R.id.progressBar)

        radioGroup = findViewById(R.id.radioGroup)
        optionA = findViewById(R.id.optionA)
        optionB = findViewById(R.id.optionB)
        optionC = findViewById(R.id.optionC)
        optionD = findViewById(R.id.optionD)

        btnSubmit = findViewById(R.id.btnSubmit)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }


        // --- Get intent data ---
        intent.getParcelableArrayListExtra<QuizQuestion>("quizQuestions")?.let {
            quizList = ArrayList(it)
        }
        readOnly = intent.getBooleanExtra("readOnly", false)
        quizTimestamp = intent.getLongExtra("timestamp", 0L)

        val selectedTimeOption = intent.getStringExtra("timePerQuestion") ?: "medium"
        questionTimeMillis = when (selectedTimeOption) {
            "easy" -> 30000L
            "medium" -> 15000L
            "hard" -> 10000L
            else -> 15000L
        }

        // --- Initialize quiz ---
        if (quizList.isNotEmpty()) {
            updateProgress()
            showQuestion()
        } else {
            txtQuestion.text = "No quiz data found."
            btnSubmit.isEnabled = false
            btnNext.isEnabled = false
        }

        // --- Button listeners ---
        btnSubmit.setOnClickListener {
            if (!answered && !readOnly) checkAnswer()
        }

        btnNext.setOnClickListener {
            if (currentIndex < quizList.size - 1) {
                currentIndex++
                updateProgress()
                showQuestion()
            } else {
                val correctCount = if (readOnly) {
                    quizList.count { it.userAnswer == it.correctAnswer }
                } else {
                    score
                }
                val total = quizList.size
                val percent = (correctCount.toFloat() / total * 100).toInt()

                val builder = androidx.appcompat.app.AlertDialog.Builder(this)
                builder.setTitle("Quiz Completed")
                builder.setMessage("You scored $correctCount out of $total\nPercentage: $percent%")
                builder.setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    if (!readOnly) saveResultsToFirestore()
                    finish()
                }
                builder.setCancelable(false)
                builder.show()
            }
        }

        btnPrevious.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                updateProgress()
                showQuestion()
            }
        }

    }

    // --- Progress update ---
    private fun updateProgress() {
        val percent = if (quizList.isNotEmpty()) {
            ((currentIndex + 1).toFloat() / quizList.size * 100).toInt()
        } else 0
        progressBar.progress = percent

        txtScore.text = if (readOnly) {
            val correctCount = quizList.count { it.userAnswer == it.correctAnswer }
            "Score: $correctCount / ${quizList.size}"
        } else {
            "Score: $score / ${quizList.size}"
        }
    }

    // --- Show question ---
    private fun showQuestion() {
        val q = quizList[currentIndex]
        txtQuestion.text = "Q${currentIndex + 1}: ${q.question}"
        optionA.text = q.options.getOrElse(0) { "" }
        optionB.text = q.options.getOrElse(1) { "" }
        optionC.text = q.options.getOrElse(2) { "" }
        optionD.text = q.options.getOrElse(3) { "" }

        radioGroup.clearCheck()
        resetOptionColors()
        txtFeedback.text = ""

        if (readOnly) {
            // --- Read-only mode (history) ---
            setOptionsEnabled(false)
            btnSubmit.visibility = View.GONE
            txtTimer.visibility = View.GONE

            // Pre-select user's answer
            when (q.userAnswer) {
                optionA.text.toString() -> optionA.isChecked = true
                optionB.text.toString() -> optionB.isChecked = true
                optionC.text.toString() -> optionC.isChecked = true
                optionD.text.toString() -> optionD.isChecked = true
            }

            // Feedback coloring
            when {
                q.userAnswer == q.correctAnswer -> {
                    txtFeedback.text = "✅ Your Answer: ${q.userAnswer}"
                    txtFeedback.setTextColor(Color.GREEN)
                }
                q.userAnswer.isNullOrEmpty() -> {
                    txtFeedback.text = "⚪ No Answer\n✅ Correct Answer: ${q.correctAnswer}"
                    txtFeedback.setTextColor(Color.GRAY)
                }
                else -> {
                    txtFeedback.text = "❌ Your Answer: ${q.userAnswer}\n✅ Correct Answer: ${q.correctAnswer}"
                    txtFeedback.setTextColor(Color.RED)
                }
            }

            btnNext.visibility = if (currentIndex < quizList.size - 1) View.VISIBLE else View.GONE
            answered = true
            btnPrevious.visibility = if (currentIndex > 0) View.VISIBLE else View.GONE

        } else {
            // --- Interactive mode (taking quiz) ---
            setOptionsEnabled(true)
            answered = false
            btnSubmit.visibility = View.VISIBLE
            btnNext.visibility = View.GONE
            txtTimer.visibility = View.VISIBLE
            startQuestionTimer()
        }
    }

    // --- Check answer (interactive mode) ---
    private fun checkAnswer() {
        val selectedId = radioGroup.checkedRadioButtonId
        if (selectedId != -1) {
            val selectedRadio = findViewById<RadioButton>(selectedId)
            val selectedAnswer = selectedRadio.text.toString()
            val currentQuestion = quizList[currentIndex]

            // Save user's answer
            currentQuestion.userAnswer = selectedAnswer

            countDownTimer?.cancel()
            setOptionsEnabled(false)

            // Feedback (simple in interactive mode)
            if (selectedAnswer == currentQuestion.correctAnswer) {
                txtFeedback.text = "✅ Correct!"
                txtFeedback.setTextColor(Color.GREEN)
                score++
            } else {
                txtFeedback.text = "❌ Incorrect!"
                txtFeedback.setTextColor(Color.RED)
            }

            btnSubmit.visibility = View.GONE
            btnNext.visibility = View.VISIBLE
            answered = true
            updateProgress()
        } else {
            Toast.makeText(this, "Please select an answer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoSubmit() {
        val currentQuestion = quizList[currentIndex]
        currentQuestion.userAnswer = null
        txtFeedback.text = "⏰ Time's up! Correct answer: ${currentQuestion.correctAnswer}"
        txtFeedback.setTextColor(Color.RED)
        setOptionsEnabled(false)
        btnSubmit.visibility = View.GONE
        btnNext.visibility = View.VISIBLE
        answered = true
        updateProgress()
    }

    private fun saveResultsToFirestore() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && quizTimestamp != 0L) {
            val db = Firebase.firestore
            db.collection("study_history")
                .whereEqualTo("uid", user.uid)
                .whereEqualTo("timestamp", quizTimestamp)
                .get()
                .addOnSuccessListener { snapshot ->
                    for (doc in snapshot) {
                        db.collection("study_history")
                            .document(doc.id)
                            .update("quiz", quizList)
                    }
                }
        }
    }

    private fun startQuestionTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(questionTimeMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                txtTimer.text = "Time left: ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                txtTimer.text = "⏰ Time's up!"
                autoSubmit()
            }
        }.start()
    }

    private fun setOptionsEnabled(enabled: Boolean) {
        for (i in 0 until radioGroup.childCount) {
            radioGroup.getChildAt(i).isEnabled = enabled
        }
    }

    private fun resetOptionColors() {
        val defaultColor = Color.BLACK
        optionA.setTextColor(defaultColor)
        optionB.setTextColor(defaultColor)
        optionC.setTextColor(defaultColor)
        optionD.setTextColor(defaultColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
