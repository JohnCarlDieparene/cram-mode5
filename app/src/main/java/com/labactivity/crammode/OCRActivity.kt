package com.labactivity.crammode

import android.app.Activity
import android.content.*
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.*
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.labactivity.crammode.utils.FlashcardUtils
import retrofit2.*
import androidx.activity.result.ActivityResultLauncher
import android.text.method.ScrollingMovementMethod
import android.widget.Scroller
import java.io.File
import java.io.FileOutputStream
import com.labactivity.crammode.CohereClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.labactivity.crammode.model.StudyHistory
import com.google.android.material.button.MaterialButton
import com.labactivity.crammode.utils.QuizUtils










class OCRActivity : AppCompatActivity() {

    private lateinit var btnSelectImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnSummarize: Button
    private lateinit var btnClear: Button
    private lateinit var btnCopyOcr: Button
    private lateinit var btnCopySummary: Button

    private lateinit var ocrResult: EditText
    private lateinit var txtSummary: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var spinnerLength: Spinner
    private lateinit var spinnerFormat: Spinner
    private lateinit var spinnerFlashcardCount: Spinner
    private lateinit var modeToggleGroup: MaterialButtonToggleGroup
    private lateinit var spinnerQuizCount: Spinner
    private lateinit var spinnerTimePerQuestion: Spinner
    private lateinit var imagePreviewList: LinearLayout

    private lateinit var pdfPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var docxPickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var spinnerLanguage: Spinner

    private var selectedLanguage = "English"


    private var selectedImageUri: Uri? = null
    private var currentRotation = 0f
    private val imageCropQueue = ArrayDeque<Uri>()


    private val selectedImageUris = mutableListOf<Uri>()
    private val ocrResultsList = mutableListOf<String>()
    private lateinit var btnAddToOcr: Button


    private lateinit var cropLauncher: ActivityResultLauncher<CropImageContractOptions>
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    private var recognizedText: String = ""
    private var currentMode: Mode = Mode.SUMMARIZE

    enum class Mode { SUMMARIZE, FLASHCARDS, QUIZ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)


        // Bind views
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnSummarize = findViewById(R.id.btnSummarize)
        btnClear = findViewById(R.id.btnClear)
        btnCopyOcr = findViewById(R.id.btnCopyOcr)
        btnCopySummary = findViewById(R.id.btnCopySummary)
        ocrResult = findViewById(R.id.ocrResult)
        txtSummary = findViewById(R.id.txtSummary)
        progressBar = findViewById(R.id.progressBar)
        spinnerLength = findViewById(R.id.spinnerLength)
        spinnerFormat = findViewById(R.id.spinnerFormat)
        spinnerFlashcardCount = findViewById(R.id.spinnerFlashcardCount)
        modeToggleGroup = findViewById(R.id.modeToggleGroup)
        spinnerQuizCount = findViewById(R.id.spinnerQuizCount)
        spinnerTimePerQuestion = findViewById(R.id.spinnerTimePerQuestion)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)





        ocrResult.setScroller(Scroller(this))
        ocrResult.movementMethod = ScrollingMovementMethod()
        ocrResult.isVerticalScrollBarEnabled = true
        ocrResult.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }


        imagePreviewList = findViewById(R.id.imagePreviewList)











        findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }






        spinnerQuizCount.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("3", "5", "10")
        )


        spinnerLength.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Short", "Medium", "Long")
        )
        spinnerFormat.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Paragraph", "Bullets")
        )
        spinnerFlashcardCount.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("3", "5", "10")
        )


        val timeOptions = listOf("Easy (30s)", "Medium (20s)", "Hard (10s)")
        val adapterTime = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeOptions)
        adapterTime.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimePerQuestion.adapter = adapterTime


        val ocrResult = findViewById<EditText>(R.id.ocrResult)
        val btnSelectFile = findViewById<Button>(R.id.btnSelectFile)

        filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    uri?.let {
                        val mimeType = contentResolver.getType(it) ?: ""

                        when {
                            mimeType.contains("pdf") -> {
                                extractTextFromPdf(it)
                            }

                            mimeType.contains("officedocument.wordprocessingml") || uri.toString()
                                .endsWith(".docx") -> {
                                try {
                                    val file = uriToFile(it)
                                    val text = DocxTextExtractor.extractText(file)
                                    ocrResult.setText(text)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(
                                        this,
                                        "Failed to extract DOCX text.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }

                            else -> {
                                Toast.makeText(this, "Unsupported file type.", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            }



        btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES, arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    )
                )
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(Intent.createChooser(intent, "Select PDF or DOCX file"))
        }


        spinnerLanguage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedLanguage = parent.getItemAtPosition(position).toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }


        // Toggle group selection
        modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentMode = when (checkedId) {
                    R.id.btnModeSummarize -> Mode.SUMMARIZE
                    R.id.btnModeFlashcards -> Mode.FLASHCARDS
                    R.id.btnModeQuiz -> Mode.QUIZ
                    else -> Mode.SUMMARIZE
                }
                updateModeUI()
            }
        }

        updateModeUI()

        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data

                    data?.clipData?.let { clipData ->
                        for (i in 0 until clipData.itemCount) {
                            imageCropQueue.add(clipData.getItemAt(i).uri)
                        }
                        processNextCrop()
                    }

                    data?.data?.let { uri ->
                        imageCropQueue.add(uri)
                        processNextCrop()
                    }
                }
            }



        cropLauncher = registerForActivityResult(CropImageContract()) { result ->
            if (result.isSuccessful) {
                result.uriContent?.let { uri ->
                    selectedImageUri = uri
                    selectedImageUris.add(uri)
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    addImageToPreviewList(bitmap)
                    runTextRecognition(bitmap)
                }
            } else {
                Toast.makeText(this, "Crop failed: ${result.error?.message}", Toast.LENGTH_SHORT)
                    .show()
            }

            // ✅ Move to next image in the queue
            processNextCrop()
        }



        btnTakePhoto.setOnClickListener {
            val cropOptions = CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    guidelines = CropImageView.Guidelines.ON,
                    imageSourceIncludeCamera = true,
                    imageSourceIncludeGallery = false
                )
            )
            cropLauncher.launch(cropOptions)
        }

        btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickImageLauncher.launch(Intent.createChooser(intent, "Select Pictures"))

        }

        btnSummarize.setOnClickListener {
            recognizedText = ocrResult.text.toString()
            if (recognizedText.isNotBlank()) {
                when (currentMode) {
                    Mode.SUMMARIZE -> summarizeText(recognizedText)
                    Mode.FLASHCARDS -> generateFlashcards(recognizedText)
                    Mode.QUIZ -> generateQuiz(recognizedText)
                }
            } else {
                txtSummary.text = "No text found to process."
            }
        }

        btnClear.setOnClickListener {
            ocrResult.setText("")
            txtSummary.text = ""

            imagePreviewList.removeAllViews()
            selectedImageUris.clear()
            ocrResultsList.clear()

        }

        // Copy OCR Text
        btnCopyOcr.setOnClickListener {
            val ocrText = ocrResult.text.toString()
            if (ocrText.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OCR Text", ocrText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "OCR text copied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No OCR text to copy", Toast.LENGTH_SHORT).show()
            }
        }

// Copy Summary Text
        btnCopySummary.setOnClickListener {
            val summaryText = txtSummary.text.toString()
            if (summaryText.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Summary Text", summaryText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Summary copied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No summary to copy", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)!!
        val file = File.createTempFile("temp", ".docx", cacheDir)
        inputStream.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }


    private fun extractTextFromPdf(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(cacheDir, "temp.pdf")
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            val fileDescriptor =
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val textResults = mutableListOf<String>()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)

                val bitmap = Bitmap.createBitmap(
                    page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        textResults.add(visionText.text)
                        ocrResult.setText(textResults.joinToString("\n\n"))
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "OCR failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }

            renderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    private fun addImageToPreviewList(bitmap: Bitmap) {
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400 // You can adjust height here
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }

        imageView.setOnClickListener {
            val tempUri = getImageUriFromBitmap(bitmap)
            val intent = Intent(this@OCRActivity, FullscreenImageActivity::class.java)
            intent.putExtra("imageUri", tempUri.toString())
            startActivity(intent)
        }

        imagePreviewList.addView(imageView)
    }

    private fun getImageUriFromBitmap(bitmap: Bitmap): Uri {
        val path = MediaStore.Images.Media.insertImage(contentResolver, bitmap, "TempImage", null)
        return Uri.parse(path)
    }


    private fun processNextCrop() {
        if (imageCropQueue.isNotEmpty()) {
            val nextUri = imageCropQueue.removeFirst()
            val cropOptions = CropImageContractOptions(
                nextUri,
                CropImageOptions(guidelines = CropImageView.Guidelines.ON)
            )
            cropLauncher.launch(cropOptions)
        }
    }


    private fun updateModeUI() {
        val layoutSummaryOptions = findViewById<LinearLayout>(R.id.layoutSummaryOptions)
        val layoutFlashcardCount = findViewById<LinearLayout>(R.id.layoutFlashcardCount)
        val labelResult = findViewById<TextView>(R.id.labelResult)
        val resultContainer = findViewById<ScrollView>(R.id.resultContainer)
        val layoutQuizCount = findViewById<LinearLayout>(R.id.layoutQuizCount)
        val layoutQuizTime = findViewById<LinearLayout>(R.id.layoutQuizTime)

        when (currentMode) {
            Mode.SUMMARIZE -> {
                btnSummarize.text = "Summarize Text"
                layoutSummaryOptions.visibility = View.VISIBLE
                layoutFlashcardCount.visibility = View.GONE
                labelResult.visibility = View.VISIBLE
                resultContainer.visibility = View.VISIBLE
                btnClear.visibility = View.VISIBLE
                btnCopyOcr.visibility = View.VISIBLE
                btnCopySummary.visibility = View.VISIBLE
                layoutQuizCount.visibility = View.GONE
                layoutQuizTime.visibility = View.GONE

            }

            Mode.FLASHCARDS -> {
                btnSummarize.text = "Generate Flashcards"
                layoutSummaryOptions.visibility = View.GONE
                layoutFlashcardCount.visibility = View.VISIBLE
                labelResult.visibility = View.GONE
                resultContainer.visibility = View.GONE
                btnClear.visibility = View.VISIBLE
                btnCopyOcr.visibility = View.VISIBLE
                btnCopySummary.visibility = View.VISIBLE
                layoutQuizCount.visibility = View.GONE
                layoutQuizTime.visibility = View.GONE
            }

            Mode.QUIZ -> {
                btnSummarize.text = "Generate Quiz"
                layoutSummaryOptions.visibility = View.GONE
                layoutFlashcardCount.visibility = View.GONE
                labelResult.visibility = View.GONE
                resultContainer.visibility = View.GONE
                btnClear.visibility = View.VISIBLE
                btnCopyOcr.visibility = View.VISIBLE
                btnCopySummary.visibility = View.VISIBLE
                layoutQuizCount.visibility = View.VISIBLE
                layoutQuizTime.visibility = View.VISIBLE
            }
        }
    }

    private fun runTextRecognition(bitmap: Bitmap) {
        progressBar.visibility = View.VISIBLE
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                recognizedText = visionText.text
                ocrResultsList.add(recognizedText)
                ocrResult.setText(ocrResultsList.joinToString("\n\n"))
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                ocrResult.setText("Failed to recognize text: ${e.message}")
                progressBar.visibility = View.GONE
            }
    }


    private var lastRequestTime = 0L
    private val COOLDOWN_MS = 6000L // 6 seconds cooldown between requests

    private fun canMakeRequest(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRequestTime < COOLDOWN_MS) {
            Toast.makeText(this, "Please wait before making another request.", Toast.LENGTH_SHORT)
                .show()
            return false
        }
        lastRequestTime = now
        return true
    }

    // ---------------- SUMMARIZATION ----------------
    private fun summarizeText(text: String) {
        val length = spinnerLength.selectedItem.toString().lowercase()
        val format = spinnerFormat.selectedItem.toString().lowercase()

        progressBar.visibility = View.VISIBLE
        btnSummarize.isEnabled = false

        val systemPrompt = if (selectedLanguage == "Filipino") {
            """
        Ikaw ay isang AI study assistant. Buodin ang ibinigay na teksto sa malinaw at maikling paraan.
        Haba: $length
        Format: $format
        """.trimIndent()
        } else {
            """
        You are an AI study assistant. Summarize the given text clearly and concisely.
        Length: $length
        Format: $format
        """.trimIndent()
        }

        val userPrompt = if (selectedLanguage == "Filipino") {
            "Buodin ang tekstong ito:\n\n$text"
        } else {
            "Summarize this text:\n\n$text"
        }

        val request = ChatRequest(
            messages = listOf(
                ChatMessage("system", listOf(MessageContent(text = systemPrompt))),
                ChatMessage("user", listOf(MessageContent(text = userPrompt)))
            )
        )

        sendChatRequest(request) { reply ->
            val summary = if (!reply.isNullOrBlank()) reply else "No summary generated."
            txtSummary.text = summary

            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val history = StudyHistory(
                    uid = user.uid,
                    type = "summary",
                    inputText = text,
                    resultText = summary,
                    timestamp = System.currentTimeMillis()
                )
                Firebase.firestore.collection("study_history")
                    .add(history)
                    .addOnSuccessListener { Log.d("SaveHistory", "Summary saved") }
                    .addOnFailureListener { Log.e("SaveHistory", "Failed to save summary", it) }
            }
        }
    }


    // ---------------- FLASHCARDS ----------------
    private fun generateFlashcards(text: String) {
        val count = spinnerFlashcardCount.selectedItem.toString().toInt()
        val shortenedText = text.take(3000)

        progressBar.visibility = View.VISIBLE
        btnSummarize.isEnabled = false

        val systemPrompt = if (selectedLanguage == "Filipino") {
            "Ikaw ay isang AI tutor na lumilikha ng flashcards sa anyong Tanong at Sagot."
        } else {
            "You are an AI tutor generating study flashcards in Q&A format."
        }

        val userPrompt = if (selectedLanguage == "Filipino") {
            """
        Gumawa ng eksaktong $count flashcards mula sa sumusunod na teksto.

        ⚠️ Format strictly (walang numbering o bullet points):
        Q: [Isulat ang tanong dito]
        A: [Isulat ang sagot dito]

        Teksto:
        $shortenedText
        """.trimIndent()
        } else {
            """
        Create exactly $count flashcards from the following text.

        ⚠️ Format strictly (no numbering, no bullet points, no extra explanations):
        Q: [Write the question here]
        A: [Write the answer here]

        Text:
        $shortenedText
        """.trimIndent()
        }

        val request = ChatRequest(
            model = "command-a-03-2025",
            messages = listOf(
                ChatMessage("system", listOf(MessageContent(text = systemPrompt))),
                ChatMessage("user", listOf(MessageContent(text = userPrompt)))
            )
        )

        sendChatRequest(request) { reply ->
            val flashcards = FlashcardUtils.parseFlashcards(reply).shuffled()
            if (flashcards.isNotEmpty()) {
                // Save to Firestore
                val flashcardText = flashcards.joinToString("\n\n") { "Q: ${it.question}\nA: ${it.answer}" }
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    val history = StudyHistory(
                        uid = user.uid,
                        type = "flashcards",
                        inputText = text,
                        resultText = flashcardText,
                        timestamp = System.currentTimeMillis()
                    )
                    Firebase.firestore.collection("study_history")
                        .add(history)
                        .addOnSuccessListener { Log.d("SaveHistory", "Flashcards saved") }
                        .addOnFailureListener { Log.e("SaveHistory", "Failed to save flashcards", it) }
                }

                // Open viewer
                val intent = Intent(this, FlashcardViewerActivity::class.java)
                intent.putExtra("flashcards", ArrayList(flashcards))
                startActivity(intent)
            } else {
                txtSummary.text = reply
                Toast.makeText(this, "No flashcards generated.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------------- QUIZ ----------------
    private fun generateQuiz(text: String) {
        val count = spinnerQuizCount.selectedItem.toString().toInt()
        val shortenedText = text.take(3000)

        progressBar.visibility = View.VISIBLE
        btnSummarize.isEnabled = false

        val systemPrompt = if (selectedLanguage == "Filipino") {
            """
        Ikaw ay isang AI quiz generator. Gumawa ng eksaktong $count multiple-choice na tanong mula sa ibinigay na teksto.
        Gamitin ang eksaktong format na ito:
        
        Tanong: <question text>
        A. <choice1>
        B. <choice2>
        C. <choice3>
        D. <choice4>
        Sagot: <tamang letra A-D>
        """.trimIndent()
        } else {
            """
        You are an AI quiz generator. Generate exactly $count multiple-choice questions from the given text.
        Use this exact format:
        
        Question: <question text>
        A. <choice1>
        B. <choice2>
        C. <choice3>
        D. <choice4>
        Answer: <correct letter A-D>
        """.trimIndent()
        }

        val userPrompt = if (selectedLanguage == "Filipino") {
            "Gumawa ng $count tanong mula sa tekstong ito:\n\n$shortenedText\n\nSundin ang format."
        } else {
            "Generate $count questions from this text:\n\n$shortenedText\n\nFollow the format exactly."
        }

        val request = ChatRequest(
            messages = listOf(
                ChatMessage("system", listOf(MessageContent(text = systemPrompt))),
                ChatMessage("user", listOf(MessageContent(text = userPrompt)))
            )
        )

        sendChatRequest(request) { reply ->
            val questions = QuizUtils.parseQuizQuestions(reply) // ✅ returns List<QuizQuestion>
            if (questions.isNotEmpty()) {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    val history = StudyHistory(
                        uid = user.uid,
                        type = "quiz",
                        inputText = text,
                        resultText = reply, // keep raw text if you want
                        timestamp = System.currentTimeMillis(),
                        quiz = questions // ✅ save parsed quiz with correctAnswer + empty userAnswer
                    )

                    Firebase.firestore.collection("study_history")
                        .add(history)
                        .addOnSuccessListener { Log.d("SaveHistory", "Quiz saved with full data") }
                        .addOnFailureListener { Log.e("SaveHistory", "Failed to save quiz", it) }

                    // ✅ Start viewer in "take quiz" mode
                    val intent = Intent(this, QuizViewerActivity::class.java)
                    intent.putParcelableArrayListExtra("quizQuestions", ArrayList(questions))
                    intent.putExtra("timestamp", history.timestamp)
                    intent.putExtra("readOnly", false)
                    startActivity(intent)
                }
            } else {
                txtSummary.text = reply
                Toast.makeText(this, "No quiz questions generated.", Toast.LENGTH_LONG).show()
            }
        }
    }



    // ---------------- COMMON FUNCTION ----------------
    private fun sendChatRequest(request: ChatRequest, onResult: (String) -> Unit) {
        CohereClient.api.chat("Bearer ${BuildConfig.COHERE_API_KEY}", request)
            .enqueue(object : Callback<ChatResponse> {
                override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true

                    if (response.isSuccessful) {
                        val reply = response.body()
                            ?.message
                            ?.content
                            ?.joinToString("\n") { it.text.trim() }
                            ?.takeIf { it.isNotBlank() }
                            ?: ""

                        onResult(reply)
                    } else {
                        val error = response.errorBody()?.string()
                        Log.e("CohereDebug", "Error ${response.code()} $error")
                        Toast.makeText(this@OCRActivity, "API Error ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    btnSummarize.isEnabled = true
                    Log.e("CohereDebug", "API call failed", t)
                    Toast.makeText(this@OCRActivity, "API call failed: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }
}