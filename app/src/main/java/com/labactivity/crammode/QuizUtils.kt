package com.labactivity.crammode.utils

import com.labactivity.crammode.model.QuizQuestion

object QuizUtils {

    fun parseQuizQuestions(raw: String): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()

        // Clean text
        val cleanedRaw = raw.replace("**", "")
            .replace("#", "")
            .replace(Regex("\\r\\n?"), "\n")
            .replace(Regex("\\n{2,}"), "\n") // remove extra blank lines

        // Split by Question/Tanong keyword (with optional numbers)
        val blocks = cleanedRaw.split(
            Regex("(?i)(?=Question\\s*\\d*[:\\-]?|Tanong\\s*\\d*[:\\-]?)")
        )

        for (block in blocks) {
            if (block.isBlank()) continue

            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            // --- Topic ---
            val topic = Regex("""(?:Topic|Paksa)\s*[:\-]?\s*(.+)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.trim().orEmpty()

            // --- Question text ---
            val questionText = lines.firstOrNull { line ->
                !line.matches(Regex("^[A-Da-d][\\.:\\-\\)]\\s*.+")) &&
                        !line.startsWith("Answer", ignoreCase = true) &&
                        !line.startsWith("Sagot", ignoreCase = true)
            }?.let { line ->
                // Remove leading 'Question:' or 'Tanong:' if present
                line.replace(Regex("(?i)^(Question|Tanong)[:\\-]?\\s*"), "").trim()
            } ?: continue

            // --- Choices ---
            val choiceMap = mutableMapOf<String, String>()
            lines.forEach { line ->
                val match = Regex("^([A-Da-d])[\\.:\\-\\)]\\s*(.+)$").find(line)
                if (match != null) {
                    val letter = match.groupValues[1].uppercase()
                    val text = match.groupValues[2].trim()
                    choiceMap[letter] = text
                }
            }

            if (choiceMap.isEmpty()) continue

            // --- Answer ---
            val answerLetter = Regex("""(?:Answer|Sagot|Correct\s*Answer)\s*[:\-]?\s*([A-Da-d])""",
                RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.uppercase()

            val correctAnswer = if (answerLetter != null && choiceMap.containsKey(answerLetter)) {
                choiceMap[answerLetter]!!
            } else {
                choiceMap["A"] ?: choiceMap.values.first()
            }

            // --- Build options list (A-D) ---
            val options = listOf("A", "B", "C", "D").map { choiceMap[it] ?: "Option $it" }

            // Add question
            questions.add(
                QuizQuestion(
                    question = questionText,
                    options = options,
                    correctAnswer = correctAnswer,
                    userAnswer = null,
                    isCorrect = false,
                    topic = topic
                )
            )
        }

        return questions
    }
}
