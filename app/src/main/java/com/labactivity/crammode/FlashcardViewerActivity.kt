package com.labactivity.crammode

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class FlashcardViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrevious: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcard_viewer)

        val flashcards = intent.getSerializableExtra("flashcards") as? ArrayList<Flashcard>
        if (flashcards.isNullOrEmpty()) {
            Toast.makeText(this, "No flashcards received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewPager = findViewById(R.id.viewPager)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)

        val adapter = FlashcardAdapter(flashcards)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 1

        btnNext.setOnClickListener {
            if (viewPager.currentItem < adapter.itemCount - 1) {
                viewPager.currentItem += 1
            }
        }

        btnPrevious.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem -= 1
            }
        }

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // Disable/enable buttons based on position
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                btnPrevious.isEnabled = position > 0
                btnNext.isEnabled = position < adapter.itemCount - 1
            }
        })
    }
}
