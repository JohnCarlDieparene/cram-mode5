package com.labactivity.crammode

import java.io.Serializable

data class Flashcard(
    val question: String = "",           // ✅ default value
    val answer: String = "",             // ✅ default value
    var interval: Int = 1,               // For spaced repetition
    var lastSeenIndex: Int = -1          // Tracks when the card was last shown
) : Serializable
