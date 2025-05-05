package com.example.aviv1.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SpeechRecognitionService(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldAppendResults = false

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    companion object {
        private const val TAG = "SpeechRecognitionService"
    }

    fun initialize() {
        Log.d(TAG, "Initializing speech recognition service")
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.d(TAG, "Speech recognition is available")
            
            // Verifică dacă deja există un SpeechRecognizer
            if (speechRecognizer != null) {
                Log.d(TAG, "SpeechRecognizer deja existent, îl eliberăm mai întâi")
                release()
            }
            
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(recognitionListener)
                Log.d(TAG, "Speech recognizer created and listener set successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating speech recognizer: ${e.message}", e)
            }
        } else {
            Log.e(TAG, "Speech recognition is not available on this device")
        }
    }

    fun startListening() {
        Log.d(TAG, "Attempting to start listening")
        if (isListening) {
            Log.d(TAG, "Already listening, returning")
            return
        }
        
        // Verificăm dacă SpeechRecognizer este inițializat
        if (speechRecognizer == null) {
            Log.e(TAG, "SpeechRecognizer nu este inițializat, reinițializăm")
            initialize()
            
            // Verificăm din nou după inițializare
            if (speechRecognizer == null) {
                Log.e(TAG, "Nu s-a putut inițializa SpeechRecognizer, ieșim")
                return
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO") // Setăm limba română
        }

        try {
            Log.d(TAG, "Starting speech recognition with intent")
            _isRecording.value = true
            shouldAppendResults = _recognizedText.value.isNotEmpty()
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(TAG, "Speech recognition started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}", e)
            _isRecording.value = false
            isListening = false
        }
    }

    fun stopListening() {
        Log.d(TAG, "Attempting to stop listening")
        if (!isListening) {
            Log.d(TAG, "Not listening, returning")
            return
        }

        speechRecognizer?.stopListening()
        _isRecording.value = false
        isListening = false
        Log.d(TAG, "Speech recognition stopped")
    }

    fun clearRecognizedText() {
        Log.d(TAG, "Clearing recognized text")
        _recognizedText.value = ""
        shouldAppendResults = false
    }

    fun release() {
        Log.d(TAG, "Releasing speech recognition resources")
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Log.d(TAG, "RMS changed: $rmsdB")
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            Log.d(TAG, "Buffer received")
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech detected")
            _isRecording.value = false
            isListening = false
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error"
            }
            Log.e(TAG, "Recognition error: $errorMessage ($error)")
            
            _isRecording.value = false
            isListening = false
            
            // Restart listening if error is not fatal
            if (error != SpeechRecognizer.ERROR_NO_MATCH && 
                error != SpeechRecognizer.ERROR_CLIENT) {
                Log.d(TAG, "Attempting to restart listening after error")
                startListening()
            }
        }

        override fun onResults(results: Bundle?) {
            Log.d(TAG, "Results received")
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.d(TAG, "Recognized text: $text")
                
                // Nu mai concatenăm rezultatele, ci înlocuim textul
                // de fiecare dată când primim un rezultat final
                _recognizedText.value = text
            } else {
                Log.d(TAG, "No matches found in results")
            }
            
            // Nu mai repornește automat ascultarea după un rezultat final
            // Utilizatorul va folosi butonul pentru a reîncepe ascultarea
            // și butonul separat pentru a trimite mesajul
        }

        override fun onPartialResults(partialResults: Bundle?) {
            Log.d(TAG, "Partial results received")
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.d(TAG, "Partial recognized text: $text")
                _recognizedText.value = text
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d(TAG, "Event received: $eventType")
        }
    }
} 