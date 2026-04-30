package com.mirchevsky.lifearchitect2.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.mirchevsky.lifearchitect2.R
import com.mirchevsky.lifearchitect2.data.AppLanguage
import com.mirchevsky.lifearchitect2.data.db.AppDatabase
import com.mirchevsky.lifearchitect2.data.db.entity.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WidgetVoiceActivity : Activity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var statusLabel: TextView
    private lateinit var inputField: EditText
    private lateinit var micButton: ImageButton
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_voice)

        window.setBackgroundDrawableResource(R.drawable.widget_background)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.also { it.dimAmount = 0.6f }

        statusLabel = findViewById(R.id.widget_voice_status)
        inputField = findViewById(R.id.widget_voice_input)
        micButton = findViewById(R.id.widget_voice_mic_btn)
        cancelButton = findViewById(R.id.widget_voice_cancel)
        saveButton = findViewById(R.id.widget_voice_save)

        cancelButton.setOnClickListener { finish() }

        saveButton.setOnClickListener {
            val title = inputField.text.toString().trim()
            if (title.isBlank()) {
                inputField.error = getString(R.string.task_title_required)
                return@setOnClickListener
            }
            saveTask(title)
        }

        micButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        startListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, getString(R.string.speech_not_available), Toast.LENGTH_SHORT).show()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    statusLabel.text = getString(R.string.listening)
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    statusLabel.text = getString(R.string.processing)
                }

                override fun onError(error: Int) {
                    isListening = false
                    statusLabel.text = getString(R.string.tap_mic_retry)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""

                    if (text.isNotBlank()) {
                        inputField.setText(text)
                        inputField.setSelection(text.length)
                        statusLabel.text = getString(R.string.edit_then_save)
                    } else {
                        statusLabel.text = getString(R.string.nothing_heard_retry)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            val selectedLanguage = AppLanguage.fromId(
                getSharedPreferences("life_architect_prefs", MODE_PRIVATE)
                    .getString("app_language", AppLanguage.SYSTEM.id)
            )

            selectedLanguage.speechTag?.let {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, it)
            }
        }

        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        statusLabel.text = getString(R.string.tap_mic_retry)
    }

    private fun saveTask(title: String) {
        val task = TaskEntity(
            title = title,
            difficulty = "medium",
            userId = "local_user",
            status = "pending",
            isCompleted = false
        )

        activityScope.launch {
            AppDatabase.getDatabase(applicationContext)
                .taskDao()
                .upsertTask(task)

            runOnUiThread {
                val manager = AppWidgetManager.getInstance(applicationContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(applicationContext, TaskWidgetProvider::class.java)
                )

                @Suppress("DEPRECATION")
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_task_list)

                Toast.makeText(
                    this@WidgetVoiceActivity,
                    getString(R.string.task_added),
                    Toast.LENGTH_SHORT
                ).show()

                finish()
            }
        }
    }
}