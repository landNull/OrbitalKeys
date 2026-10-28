package com.landnull.orbitalkeys

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.graphics.Color
import android.widget.Toast

class OrbitalKeysService : InputMethodService(), RecognitionListener {

    private lateinit var rootKeyboardContainer: FrameLayout
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(this)
        }
    }

    override fun onCreateInputView(): View {
        rootKeyboardContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#0b0f19"))
        }

        val orbitalView = OrbitalCanvasView(this, this)
        rootKeyboardContainer.addView(orbitalView)
        return rootKeyboardContainer
    }

    fun startVoiceRecognition() {
        if (speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
            Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Speech Engine Unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val transcribedText = matches[0] + " "
            currentInputConnection?.commitText(transcribedText, 1)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onError(error: Int) {
        Toast.makeText(this, "Dictation Error: $error", Toast.LENGTH_SHORT).show()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    fun sendRawChar(char: Char) {
        currentInputConnection?.commitText(char.toString(), 1)
    }

    fun sendRawAction(keyCode: Int) {
        currentInputConnection?.let {
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }
}
