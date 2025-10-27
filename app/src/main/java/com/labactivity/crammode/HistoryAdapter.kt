package com.labactivity.crammode

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.labactivity.crammode.model.StudyHistory
import com.labactivity.crammode.utils.FlashcardUtils
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    val items: MutableList<StudyHistory>,
    private val onDeleteClick: (StudyHistory) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card = itemView.findViewById<View>(R.id.cardHistory)
        val icon: ImageView = itemView.findViewById(R.id.imgType)
        val txtType: TextView = itemView.findViewById(R.id.txtType)
        val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        val txtPreview: TextView = itemView.findViewById(R.id.txtPreview)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_study_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = items[position]
        val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())

        holder.txtDate.text = sdf.format(Date(item.timestamp))

        when (item.type) {
            "summary" -> {
                holder.txtType.text = "Summary"
                holder.icon.setImageResource(R.drawable.ic_summary) // ✅ summary icon
                holder.txtPreview.text =
                    item.resultText.take(120) + if (item.resultText.length > 120) "..." else ""
            }

            "flashcards" -> {
                holder.txtType.text = "Flashcards"
                holder.icon.setImageResource(R.drawable.ic_flashcards) // ✅ flashcards icon
                val flashcards = FlashcardUtils.parseFlashcards(item.resultText)
                holder.txtPreview.text = "Flashcards: ${flashcards.size} cards"
            }

            "quiz" -> {
                holder.txtType.text = "Quiz"
                holder.icon.setImageResource(R.drawable.ic_quiz) // ✅ quiz icon
                val correct = item.quiz.count { it.userAnswer == it.correctAnswer }
                holder.txtPreview.text = "Quiz: ${item.quiz.size} questions • Score: $correct"
            }
        }

        // Item click → open the content
        holder.card.setOnClickListener {
            val context = holder.itemView.context
            when (item.type) {
                "summary" -> {
                    androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle("Summary")
                        .setMessage(item.resultText)
                        .setPositiveButton("Close", null)
                        .show()
                }

                "flashcards" -> {
                    val flashcards = FlashcardUtils.parseFlashcards(item.resultText)
                    val intent = Intent(context, FlashcardViewerActivity::class.java)
                    intent.putExtra("flashcards", ArrayList(flashcards))
                    context.startActivity(intent)
                }

                "quiz" -> {
                    val intent = Intent(context, QuizViewerActivity::class.java)
                    intent.putParcelableArrayListExtra("quizQuestions", ArrayList(item.quiz))
                    intent.putExtra("timestamp", item.timestamp)
                    intent.putExtra("readOnly", true)
                    context.startActivity(intent)
                }
            }
        }

        // Delete click
        holder.btnDelete.setOnClickListener {
            onDeleteClick(item)
        }
    }


    override fun getItemCount(): Int = items.size
}
