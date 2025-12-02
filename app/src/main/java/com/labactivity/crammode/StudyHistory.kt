package com.labactivity.crammode.model

import com.labactivity.crammode.Flashcard

data class StudyHistory(
    var id: String = "",
    val uid: String = "",
    val type: String = "",

    val inputText: String = "",
    val timestamp: Long = 0L,
    val resultText: String = "",
    val quiz: List<QuizQuestion> = emptyList(),
    val flashcards: List<Flashcard> = emptyList()   // ✅ Add this!!
)
