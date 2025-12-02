package com.labactivity.crammode

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class FlashcardAdapter(
    private val flashcards: List<Flashcard>,
    private val onSideChange: ((position: Int, isBack: Boolean) -> Unit)? = null
) : RecyclerView.Adapter<FlashcardAdapter.ViewHolder>() {

    // Track each card's front/back state
    private val isBackVisible = BooleanArray(flashcards.size) { false }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cardFront: CardView = view.findViewById(R.id.cardFront)
        private val cardBack: CardView = view.findViewById(R.id.cardBack)
        private val questionText: TextView = view.findViewById(R.id.textQuestion)
        private val answerText: TextView = view.findViewById(R.id.textAnswer)
        private var isFront = true

        init {
            val flipListener = View.OnClickListener { flipCard(adapterPosition) }
            cardFront.setOnClickListener(flipListener)
            cardBack.setOnClickListener(flipListener)
            questionText.setOnClickListener(flipListener)
            answerText.setOnClickListener(flipListener)
        }

        fun bind(flashcard: Flashcard) {
            questionText.text = "Q: ${flashcard.question}"
            answerText.text = "A: ${flashcard.answer}"

            // Restore correct side for recycled ViewHolder
            isFront = !isBackVisible[adapterPosition]
            cardFront.visibility = if (isFront) View.VISIBLE else View.GONE
            cardBack.visibility = if (isFront) View.GONE else View.VISIBLE
            cardFront.rotationY = 0f
            cardBack.rotationY = 0f

            // Notify Activity about current side
            onSideChange?.invoke(adapterPosition, !isFront)
        }

        fun revealAnswer() {
            if (isFront) flipCard(adapterPosition)
        }

        private fun flipCard(position: Int) {
            val scale = cardFront.context.resources.displayMetrics.density
            cardFront.cameraDistance = 8000 * scale
            cardBack.cameraDistance = 8000 * scale

            val visibleCard = if (isFront) cardFront else cardBack
            val hiddenCard = if (isFront) cardBack else cardFront

            visibleCard.animate()
                .rotationY(90f)
                .setDuration(200)
                .withEndAction {
                    visibleCard.visibility = View.GONE
                    hiddenCard.visibility = View.VISIBLE
                    hiddenCard.rotationY = -90f
                    hiddenCard.animate()
                        .rotationY(0f)
                        .setDuration(200)
                        .withEndAction {
                            isFront = !isFront
                            isBackVisible[position] = !isFront
                            onSideChange?.invoke(position, !isFront)
                        }.start()
                }.start()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_flashcard, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(flashcards[position])

    override fun getItemCount() = flashcards.size

    fun getFlashcard(position: Int) = flashcards[position]

    fun isBackVisibleAt(position: Int): Boolean {
        return isBackVisible.getOrNull(position) ?: false
    }

}