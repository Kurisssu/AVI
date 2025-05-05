package com.example.aviv1.service

import ai.picovoice.android.voiceprocessor.VoiceProcessor
import ai.picovoice.android.voiceprocessor.VoiceProcessorException
import ai.picovoice.falcon.Falcon
import ai.picovoice.falcon.FalconException
import ai.picovoice.falcon.FalconSegment
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SpeakerDiarizationService(private val context: Context) {

    private var falcon: Falcon? = null
    private var voiceProcessor: VoiceProcessor? = null
    private val pcmData = ArrayList<Short>()
    private var isRecording = false

    private val _diarizationResult = MutableStateFlow<List<FalconSegment>>(emptyList())
    val diarizationResult: StateFlow<List<FalconSegment>> = _diarizationResult.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    companion object {
        private const val TAG = "SpeakerDiarizationSvc"
        private const val FRAME_LENGTH = 512
    }

    fun initialize(accessKey: String) {
        try {
            Log.d(TAG, "Inițializare serviciu de diarizare")
            
            // Inițializare Falcon pentru diarizare
            falcon = Falcon.Builder()
                .setAccessKey(accessKey)
                .build(context)
            
            // Inițializare VoiceProcessor pentru capturarea audio
            voiceProcessor = VoiceProcessor.getInstance()
            
            Log.d(TAG, "Serviciu de diarizare inițializat cu succes")
        } catch (e: FalconException) {
            Log.e(TAG, "Eroare la inițializarea Falcon: ${e.message}", e)
            _error.value = "Eroare la inițializarea diarizării: ${e.message}"
        } catch (e: VoiceProcessorException) {
            Log.e(TAG, "Eroare la inițializarea VoiceProcessor: ${e.message}", e)
            _error.value = "Eroare la inițializarea procesării vocii: ${e.message}"
        } catch (e: Exception) {
            Log.e(TAG, "Eroare neașteptată: ${e.message}", e)
            _error.value = "Eroare neașteptată: ${e.message}"
        }
    }

    fun startRecording() {
        if (isRecording) {
            Log.d(TAG, "Înregistrarea este deja pornită")
            return
        }

        try {
            Log.d(TAG, "Pornirea înregistrării pentru diarizare")
            pcmData.clear()
            _diarizationResult.value = emptyList()
            _error.value = null
            
            // Setăm starea de înregistrare înainte de a configura VoiceProcessor
            _isProcessing.value = true
            
            // Configurare listener pentru cadre audio
            voiceProcessor?.addFrameListener { frame ->
                synchronized(pcmData) {
                    for (sample in frame) {
                        pcmData.add(sample)
                    }
                }
                // Log doar ocazional pentru a nu încărca consola
                if (pcmData.size % 10000 == 0) {
                    Log.d(TAG, "Date audio capturate: ${pcmData.size} eșantioane")
                }
            }
            
            // Pornire înregistrare
            falcon?.let {
                voiceProcessor?.start(FRAME_LENGTH, it.sampleRate)
                isRecording = true
                Log.d(TAG, "Înregistrare pornită cu succes, sampleRate: ${it.sampleRate}, frameLength: $FRAME_LENGTH")
            } ?: run {
                Log.e(TAG, "Falcon nu este inițializat")
                _error.value = "Serviciul de diarizare nu este inițializat"
                _isProcessing.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la pornirea înregistrării: ${e.message}", e)
            _error.value = "Eroare la pornirea înregistrării: ${e.message}"
            _isProcessing.value = false
            isRecording = false
        }
    }

    fun stopRecording() {
        if (!isRecording) {
            Log.d(TAG, "Înregistrarea nu este pornită")
            return
        }

        try {
            Log.d(TAG, "Oprirea înregistrării și procesarea datelor")
            
            // Salvăm o copie a datelor audio înainte de a opri înregistrarea
            val capturedData: List<Short>
            synchronized(pcmData) {
                capturedData = pcmData.toList()
            }
            
            // Oprim procesorul vocal
            voiceProcessor?.stop()
            voiceProcessor?.clearFrameListeners()
            
            // Actualizăm starea
            isRecording = false
            
            Log.d(TAG, "Înregistrare oprită, procesăm ${capturedData.size} eșantioane audio")
            
            // Procesare date audio capturate
            if (capturedData.isNotEmpty()) {
                processCapturedAudio(capturedData)
            } else {
                Log.w(TAG, "Nu s-au capturat date audio")
                _error.value = "Nu s-au capturat date audio"
                _isProcessing.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la oprirea înregistrării: ${e.message}", e)
            _error.value = "Eroare la oprirea înregistrării: ${e.message}"
            _isProcessing.value = false
            isRecording = false
        }
    }

    private fun processCapturedAudio() {
        processCapturedAudio(pcmData.toList())
    }

    private fun processCapturedAudio(audioData: List<Short>) {
        try {
            if (audioData.isEmpty()) {
                Log.w(TAG, "Nu s-au capturat date audio")
                _error.value = "Nu s-au capturat date audio"
                _isProcessing.value = false
                return
            }

            Log.d(TAG, "Procesare date audio: ${audioData.size} eșantioane")
            
            // Conversie la array de short pentru procesare
            val pcmArray = audioData.toShortArray()
            
            // Efectuăm diarizarea
            falcon?.let {
                Log.d(TAG, "Începere diarizare cu Falcon")
                val segments = it.process(pcmArray)
                _diarizationResult.value = segments.toList()
                Log.d(TAG, "Diarizare finalizată: ${segments.size} segmente")
                
                // Afișăm informații despre segmente pentru debug
                segments.forEach { segment ->
                    Log.d(TAG, "Segment: Speaker ${segment.speakerTag}, " +
                              "Start: ${segment.startSec}s, End: ${segment.endSec}s")
                }
            } ?: run {
                Log.e(TAG, "Falcon nu este inițializat pentru procesare")
                _error.value = "Serviciul de diarizare nu este inițializat"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la procesarea datelor audio: ${e.message}", e)
            _error.value = "Eroare la procesarea datelor audio: ${e.message}"
        } finally {
            // Resetăm starea de procesare
            _isProcessing.value = false
            Log.d(TAG, "Procesare finalizată, starea isProcessing resetată")
        }
    }

    /**
     * Returnează datele audio capturate ca ShortArray.
     * Această metodă este utilă pentru transcrierea segmentelor audio identificate.
     */
    fun getCapturedPcmData(): ShortArray? {
        synchronized(pcmData) {
            if (pcmData.isEmpty()) {
                return null
            }
            return pcmData.toShortArray()
        }
    }

    fun processAudioFile(filePath: String) {
        try {
            _isProcessing.value = true
            _error.value = null
            _diarizationResult.value = emptyList()
            
            Log.d(TAG, "Procesare fișier audio: $filePath")
            
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "Fișierul audio nu există: $filePath")
                _error.value = "Fișierul audio nu există"
                _isProcessing.value = false
                return
            }
            
            // Efectuăm diarizarea pe fișier
            falcon?.let {
                val segments = it.processFile(filePath)
                _diarizationResult.value = segments.toList()
                Log.d(TAG, "Diarizare fișier finalizată: ${segments.size} segmente")
                
                // Afișăm informații despre segmente pentru debug
                segments.forEach { segment ->
                    Log.d(TAG, "Segment: Speaker ${segment.speakerTag}, " +
                              "Start: ${segment.startSec}s, End: ${segment.endSec}s")
                }
            } ?: run {
                Log.e(TAG, "Falcon nu este inițializat pentru procesare fișier")
                _error.value = "Serviciul de diarizare nu este inițializat"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la procesarea fișierului audio: ${e.message}", e)
            _error.value = "Eroare la procesarea fișierului audio: ${e.message}"
        } finally {
            _isProcessing.value = false
        }
    }

    fun clearResults() {
        _diarizationResult.value = emptyList()
        _error.value = null
    }

    fun release() {
        try {
            Log.d(TAG, "Eliberare resurse")
            if (isRecording) {
                stopRecording()
            }
            
            falcon?.delete()
            falcon = null
            
            pcmData.clear()
            _diarizationResult.value = emptyList()
            _error.value = null
            _isProcessing.value = false
            isRecording = false
            
            Log.d(TAG, "Resurse eliberate cu succes")
        } catch (e: Exception) {
            Log.e(TAG, "Eroare la eliberarea resurselor: ${e.message}", e)
        }
    }
} 