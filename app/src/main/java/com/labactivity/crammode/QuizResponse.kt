    package com.labactivity.crammode

    data class QuizResponse(
        val generations: List<QuizGeneration>
    )

    data class QuizGeneration(
        val text: String
    )
