package com.example.aviv1.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Serviciu pentru transformarea textului în voce
 */
class TextToSpeechService(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TextToSpeechService"
        private const val PREFS_NAME = "tts_prefs"
        private const val KEY_VOICE_NAME = "selected_voice_name"
        private const val KEY_VOICE_LOCALE = "selected_voice_locale"
    }
    
    private var textToSpeech: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()
    
    private val _currentVoice = MutableStateFlow<Voice?>(null)
    val currentVoice: StateFlow<Voice?> = _currentVoice.asStateFlow()
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        initialize()
    }
    
    /**
     * Inițializarea serviciului TTS
     */
    fun initialize() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context, this)
            textToSpeech?.setOnUtteranceProgressListener(object : 
                android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.value = true
                    Log.d(TAG, "TTS playback started")
                }

                override fun onDone(utteranceId: String?) {
                    _isPlaying.value = false
                    Log.d(TAG, "TTS playback done")
                }

                override fun onError(utteranceId: String?) {
                    _isPlaying.value = false
                    Log.e(TAG, "TTS playback error")
                }
            })
        }
    }
    
    /**
     * Se apelează când TTS este inițializat
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TTS initialization successful")
            
            // Use English as default language
            val locEnglish = Locale.US
            val locDefault = Locale.getDefault()
            
            val result = if (textToSpeech?.isLanguageAvailable(locEnglish) == TextToSpeech.LANG_AVAILABLE) {
                textToSpeech?.setLanguage(locEnglish)
                Log.d(TAG, "TTS language set to English (US)")
                true
            } else if (textToSpeech?.isLanguageAvailable(locDefault) == TextToSpeech.LANG_AVAILABLE) {
                textToSpeech?.setLanguage(locDefault)
                Log.d(TAG, "TTS language set to device default: ${locDefault.displayLanguage}")
                true
            } else {
                textToSpeech?.setLanguage(Locale.US)
                Log.d(TAG, "TTS language set to English (US) as fallback")
                true
            }
            
            // Încărcăm vocile disponibile
            loadAvailableVoices()
            
            _isReady.value = result == true
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            _isReady.value = false
        }
    }
    
    /**
     * Încărcăm vocile disponibile pentru TTS
     */
    private fun loadAvailableVoices() {
        textToSpeech?.let { tts ->
            val voices = tts.voices?.toList() ?: emptyList()
            Log.d(TAG, "Loaded ${voices.size} available voices")
            _availableVoices.value = voices
            
            // Încercăm să restaurăm vocea salvată anterior
            val savedVoiceName = preferences.getString(KEY_VOICE_NAME, null)
            val savedVoiceLocale = preferences.getString(KEY_VOICE_LOCALE, null)
            
            // Căutăm vocea salvată în vocile disponibile
            if (savedVoiceName != null) {
                val savedVoice = voices.find { voice -> 
                    voice.name == savedVoiceName && 
                    (savedVoiceLocale == null || voice.locale.toString() == savedVoiceLocale)
                }
                
                if (savedVoice != null) {
                    // Am găsit vocea salvată, o setăm
                    setVoice(savedVoice)
                    Log.d(TAG, "Restored saved voice: $savedVoiceName")
                    return
                }
            }
            
            // Dacă nu am găsit vocea salvată sau nu există una salvată,
            // căutăm o voce în engleză ca opțiune implicită
            val englishVoice = voices.find { 
                it.locale.language == "en" || it.locale.language == Locale.ENGLISH.language 
            }
            
            if (englishVoice != null) {
                // Setăm o voce în engleză ca opțiune implicită
                setVoice(englishVoice)
                Log.d(TAG, "Set default English voice: ${englishVoice.name}")
            } else if (voices.isNotEmpty()) {
                // Dacă nu găsim o voce în engleză, folosim prima disponibilă
                setVoice(voices.first())
                Log.d(TAG, "Using first available voice: ${voices.first().name}")
            } else {
                // Nu există voci disponibile
                Log.e(TAG, "No TTS voices available")
            }
        }
    }
    
    /**
     * Setează vocea pentru TTS și o salvează pentru utilizări viitoare
     */
    fun setVoice(voice: Voice) {
        textToSpeech?.let { tts ->
            val result = tts.setVoice(voice)
            if (result == TextToSpeech.SUCCESS) {
                _currentVoice.value = voice
                
                // Salvăm vocea în SharedPreferences
                preferences.edit()
                    .putString(KEY_VOICE_NAME, voice.name)
                    .putString(KEY_VOICE_LOCALE, voice.locale.toString())
                    .apply()
                
                Log.d(TAG, "TTS voice set to: ${voice.name} and saved to preferences")
            } else {
                Log.e(TAG, "Failed to set TTS voice to: ${voice.name}")
            }
        }
    }
    
    /**
     * Redă textul prin TTS
     */
    fun speak(text: String) {
        if (_isReady.value) {
            // Oprim redarea curentă dacă există
            stop()
            
            // Parametri pentru TTS
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageId")
            
            // Redăm textul
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "messageId")
            Log.d(TAG, "TTS speaking: $text")
        } else {
            Log.e(TAG, "TTS not ready, cannot speak")
        }
    }
    
    /**
     * Oprește redarea TTS
     */
    fun stop() {
        textToSpeech?.stop()
        _isPlaying.value = false
        Log.d(TAG, "TTS playback stopped")
    }
    
    /**
     * Eliberează resursele
     */
    fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        _isReady.value = false
        Log.d(TAG, "TTS service shutdown")
    }
} 