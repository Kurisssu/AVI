package com.example.aviv1.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aviv1.model.DialogSegment
import com.example.aviv1.service.AudioTranscriptionService
import com.example.aviv1.service.SpeakerDiarizationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class SpeakerDiarizationViewModel(application: Application) : AndroidViewModel(application) {

    // Service-ul pentru diarizare
    private val diarizationService = SpeakerDiarizationService(application.applicationContext)
    
    // Service-ul pentru transcriere
    private val transcriptionService = AudioTranscriptionService(application.applicationContext)
    
    // Cheia de acces Picovoice - ar trebui să fie stocată în mod sigur
    // Într-o aplicație reală, ar trebui obținută din BuildConfig sau de pe un server securizat
    private val accessKey = "DOQH65gztbLrJVoj5U5uA4xWCsc39Ovyw+QACrOygB/QoNEnVkc/xg=="
    
    // Datele audio capturate (pentru transcriere ulterioară)
    private var capturedPcmData: ShortArray? = null

    // Stare UI
    private val _uiState = MutableStateFlow(SpeakerDiarizationUiState())
    val uiState: StateFlow<SpeakerDiarizationUiState> = _uiState.asStateFlow()

    init {
        // Inițializare servicii
        initializeServices()
        
        // Observare rezultate diarizare
        viewModelScope.launch {
            diarizationService.diarizationResult.collect { falconSegments ->
                val dialogSegments = DialogSegment.fromFalconSegments(falconSegments)
                _uiState.update { it.copy(
                    dialogSegments = dialogSegments,
                    hasResults = dialogSegments.isNotEmpty()
                )}
                
                // Dacă avem rezultate și datele audio sunt disponibile, începem transcrierea
                if (dialogSegments.isNotEmpty() && capturedPcmData != null) {
                    transcribeDialogSegments(dialogSegments, capturedPcmData!!)
                }
            }
        }
        
        // Observare stare procesare diarizare
        viewModelScope.launch {
            diarizationService.isProcessing.collect { isProcessing ->
                _uiState.update { it.copy(isProcessing = isProcessing) }
            }
        }
        
        // Observare stare procesare transcriere
        viewModelScope.launch {
            transcriptionService.isProcessing.collect { isProcessing ->
                _uiState.update { it.copy(
                    isTranscribing = isProcessing,
                    // Consideram ca procesam fie daca diarizam sau transcrim
                    isProcessing = isProcessing || it.isProcessing
                )}
            }
        }
        
        // Observare erori diarizare
        viewModelScope.launch {
            diarizationService.error.collect { error ->
                if (error != null) {
                    _uiState.update { it.copy(
                        error = error,
                        hasError = true
                    )}
                }
            }
        }
        
        // Observare erori transcriere
        viewModelScope.launch {
            transcriptionService.error.collect { error ->
                if (error != null) {
                    _uiState.update { it.copy(
                        error = error,
                        hasError = true
                    )}
                }
            }
        }
    }

    private fun initializeServices() {
        viewModelScope.launch {
            try {
                // Inițializare serviciu diarizare
                diarizationService.initialize(accessKey)
                
                // Inițializare serviciu transcriere
                try {
                    transcriptionService.initialize(accessKey)
                    _uiState.update { it.copy(
                        isInitialized = true, 
                        isTranscriptionEnabled = true
                    )}
                } catch (e: Exception) {
                    _uiState.update { it.copy(
                        isInitialized = true,
                        isTranscriptionEnabled = false,
                        error = "Transcrierea nu este disponibilă: ${e.message}",
                        hasError = true
                    )}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Eroare la inițializarea serviciilor: ${e.message}",
                    hasError = true
                )}
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            try {
                // Curățăm datele anterioare
                capturedPcmData = null
                
                // Actualizăm starea înainte de a porni înregistrarea efectivă
                _uiState.update { it.copy(isRecording = true) }
                diarizationService.startRecording()
            } catch (e: Exception) {
                // În caz de eroare, resetăm starea
                _uiState.update { it.copy(
                    isRecording = false,
                    error = "Eroare la pornirea înregistrării: ${e.message}",
                    hasError = true
                )}
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            try {
                // Actualizăm starea imediat, nu așteptăm să se termine procesarea
                _uiState.update { it.copy(isRecording = false) }
                
                // Salvăm o referință la datele PCM înainte de oprirea înregistrării
                val pcmData = (diarizationService as? SpeakerDiarizationService)?.getCapturedPcmData()
                capturedPcmData = pcmData
                
                // Oprim înregistrarea și procesăm datele
                diarizationService.stopRecording()
                
                // Ne asigurăm că starea isProcessing se resetează după ce se finalizează procesarea
                // Adăugăm un delay scurt pentru a ne asigura că toate operațiile de procesare s-au finalizat
                kotlinx.coroutines.delay(500)
                _uiState.update { it.copy(isProcessing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Eroare la oprirea înregistrării: ${e.message}",
                    hasError = true,
                    isProcessing = false
                )}
            }
        }
    }

    private fun transcribeDialogSegments(segments: List<DialogSegment>, pcmData: ShortArray) {
        if (!_uiState.value.isTranscriptionEnabled) {
            return
        }
        
        _uiState.update { it.copy(isTranscribing = true) }
        
        viewModelScope.launch {
            try {
                // Transcrierea fiecărui segment identificat
                val updatedSegments = segments.mapIndexed { index, segment ->
                    // Actualizăm starea pentru a arăta progresul
                    _uiState.update { it.copy(
                        transcriptionProgress = (index + 1) / segments.size.toFloat(),
                        currentTranscribingSegment = index
                    )}
                    
                    // Transcriem segmentul
                    val result = transcriptionService.transcribeSegment(
                        pcmData, 
                        segment.startTime, 
                        segment.endTime
                    )
                    
                    if (result.isSuccessful) {
                        // Actualizăm segmentul cu textul transcris
                        // În versiunea nouă a API-ului, nu mai avem informații despre încredere
                        // așa că setăm o valoare implicită
                        segment.copy(
                            text = result.text,
                            confidence = 0.8f  // Valoare implicită, deoarece API-ul nu mai oferă această informație
                        )
                    } else {
                        // Păstrăm segmentul neschimbat în caz de eroare
                        segment
                    }
                }
                
                // Actualizăm UI-ul cu segmentele transcrise
                _uiState.update { it.copy(
                    dialogSegments = updatedSegments,
                    hasTranscribedResults = true,
                    isTranscribing = false,
                    transcriptionProgress = 1f,
                    currentTranscribingSegment = -1
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Eroare la transcrierea segmentelor: ${e.message}",
                    hasError = true,
                    isTranscribing = false
                )}
            }
        }
    }
    
    /**
     * Forțează transcrierea unui segment specific
     */
    fun transcribeSegment(segmentIndex: Int) {
        val segments = _uiState.value.dialogSegments
        val pcmData = capturedPcmData
        
        if (segmentIndex < 0 || segmentIndex >= segments.size || pcmData == null || 
            !_uiState.value.isTranscriptionEnabled) {
            return
        }
        
        val segment = segments[segmentIndex]
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    isTranscribing = true,
                    currentTranscribingSegment = segmentIndex
                )}
                
                // Transcriem segmentul
                val result = transcriptionService.transcribeSegment(
                    pcmData, 
                    segment.startTime, 
                    segment.endTime
                )
                
                if (result.isSuccessful) {
                    // Actualizăm doar segmentul specific în lista
                    val updatedSegments = segments.toMutableList()
                    updatedSegments[segmentIndex] = segment.copy(
                        text = result.text,
                        confidence = 0.8f  // Valoare implicită, deoarece API-ul nu mai oferă această informație
                    )
                    
                    _uiState.update { it.copy(
                        dialogSegments = updatedSegments,
                        isTranscribing = false,
                        currentTranscribingSegment = -1
                    )}
                } else {
                    _uiState.update { it.copy(
                        error = "Nu s-a putut transcrie segmentul: ${result.errorMessage}",
                        hasError = true,
                        isTranscribing = false,
                        currentTranscribingSegment = -1
                    )}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Eroare la transcrierea segmentului: ${e.message}",
                    hasError = true,
                    isTranscribing = false,
                    currentTranscribingSegment = -1
                )}
            }
        }
    }

    fun processAudioFile(filePath: String) {
        viewModelScope.launch {
            try {
                // Resetăm datele
                capturedPcmData = null
                diarizationService.processAudioFile(filePath)
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Eroare la procesarea fișierului audio: ${e.message}",
                    hasError = true
                )}
            }
        }
    }

    fun clearResults() {
        viewModelScope.launch {
            diarizationService.clearResults()
            _uiState.update { it.copy(
                dialogSegments = emptyList(),
                hasResults = false,
                hasTranscribedResults = false,
                error = null,
                hasError = false,
                transcriptionProgress = 0f,
                currentTranscribingSegment = -1
            )}
            
            // Eliberăm memoria
            capturedPcmData = null
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(
            error = null,
            hasError = false
        )}
    }

    override fun onCleared() {
        super.onCleared()
        diarizationService.release()
        transcriptionService.release()
        capturedPcmData = null
    }
}

/**
 * Model pentru starea UI a diarizării
 */
data class SpeakerDiarizationUiState(
    val isInitialized: Boolean = false,
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val isTranscriptionEnabled: Boolean = false,
    val isTranscribing: Boolean = false,
    val dialogSegments: List<DialogSegment> = emptyList(),
    val hasResults: Boolean = false,
    val hasTranscribedResults: Boolean = false,
    val error: String? = null,
    val hasError: Boolean = false,
    val transcriptionProgress: Float = 0f,
    val currentTranscribingSegment: Int = -1
) 