package com.example.aviv1.viewmodel

import android.app.Application
import android.speech.tts.Voice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aviv1.model.Conversation
import com.example.aviv1.model.DialogSegment
import com.example.aviv1.model.Message
import com.example.aviv1.model.MessageType
import com.example.aviv1.repository.ConversationRepository
import com.example.aviv1.service.AIApiService
import com.example.aviv1.service.AIMessage
import com.example.aviv1.service.ApiKeyManager
import com.example.aviv1.service.AudioTranscriptionService
import com.example.aviv1.service.SpeakerDiarizationService
import com.example.aviv1.service.SpeechRecognitionService
import com.example.aviv1.service.TextToSpeechService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val speechRecognitionService = SpeechRecognitionService(application.applicationContext)
    private val aiApiService = AIApiService()
    private val conversationRepository = ConversationRepository(application.applicationContext)
    private val textToSpeechService = TextToSpeechService(application.applicationContext)
    
    // Servicii pentru diarizare și transcriere
    private val diarizationService = SpeakerDiarizationService(application.applicationContext)
    private val transcriptionService = AudioTranscriptionService(application.applicationContext)
    
    // Manager pentru cheile API
    private val apiKeyManager = ApiKeyManager.getInstance(application.applicationContext)
    
    // Starea mesajelor din conversație - acum obținute din conversația activă
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    // Starea conversațiilor
    val conversations = conversationRepository.conversationsFlow
    val activeConversation = conversationRepository.activeConversationFlow
    
    // Starea textului curent recunoscut
    private val _currentRecognizedText = MutableStateFlow("")
    val currentRecognizedText: StateFlow<String> = _currentRecognizedText.asStateFlow()
    
    // Starea de înregistrare audio
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    // Starea pentru încărcare răspuns
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Starea TTS
    val isTtsReady = textToSpeechService.isReady
    val isTtsPlaying = textToSpeechService.isPlaying
    val availableTtsVoices = textToSpeechService.availableVoices
    val currentTtsVoice = textToSpeechService.currentVoice
    
    // Mesajul curent selectat pentru TTS
    private val _selectedMessageForTts = MutableStateFlow<Message?>(null)
    val selectedMessageForTts: StateFlow<Message?> = _selectedMessageForTts.asStateFlow()
    
    // Stare pentru diarizare și transcriere
    private val _isDiarizationRecording = MutableStateFlow(false)
    val isDiarizationRecording: StateFlow<Boolean> = _isDiarizationRecording.asStateFlow()
    
    private val _isDiarizationProcessing = MutableStateFlow(false)
    val isDiarizationProcessing: StateFlow<Boolean> = _isDiarizationProcessing.asStateFlow()
    
    private val _diarizationSegments = MutableStateFlow<List<DialogSegment>>(emptyList())
    val diarizationSegments: StateFlow<List<DialogSegment>> = _diarizationSegments.asStateFlow()
    
    // Stocare temporară pentru datele audio capturate
    private var capturedPcmData: ShortArray? = null
    
    init {
        // Inițializare AIApiService
        aiApiService.initialize(application.applicationContext)
        
        // Inițializare SpeechRecognitionService
        speechRecognitionService.initialize()
        
        // Observare mesaje din conversația activă
        viewModelScope.launch {
            activeConversation.collectLatest { conversation ->
                conversation?.let {
                    _messages.value = it.messages
                }
            }
        }
        
        // Observare text recunoscut
        viewModelScope.launch {
            speechRecognitionService.recognizedText.collect { text ->
                Log.d("ChatViewModel", "Received recognized text: '$text'")
                _currentRecognizedText.value = text
            }
        }
        
        // Observare stare înregistrare
        viewModelScope.launch {
            speechRecognitionService.isRecording.collect { recording ->
                _isRecording.value = recording
            }
        }
        
        // Observare rezultate diarizare
        viewModelScope.launch {
            diarizationService.diarizationResult.collect { falconSegments ->
                val dialogSegments = DialogSegment.fromFalconSegments(falconSegments)
                _diarizationSegments.value = dialogSegments
                
                // Dacă avem rezultate și datele audio sunt disponibile, începem transcrierea
                if (dialogSegments.isNotEmpty() && capturedPcmData != null) {
                    transcribeDialogSegments(dialogSegments, capturedPcmData!!)
                }
            }
        }
        
        // Observare stare procesare diarizare
        viewModelScope.launch {
            diarizationService.isProcessing.collect { isProcessing ->
                _isDiarizationProcessing.value = isProcessing
            }
        }
        
        // Inițializarea serviciilor de diarizare și transcriere
        initializeDiarizationServices()
    }
    
    private fun initializeDiarizationServices() {
        viewModelScope.launch {
            try {
                // Obținem cheia de acces securizată
                val accessKey = apiKeyManager.getPicovoiceAccessKey()
                
                // Inițializare serviciu diarizare
                diarizationService.initialize(accessKey)
                
                // Inițializare serviciu transcriere
                transcriptionService.initialize(accessKey)
            } catch (e: Exception) {
                // Tratare eroare inițializare
                addSystemMessage("Error initializing speech recognition services: ${e.message}")
            }
        }
    }
    
    // === FUNCȚII PENTRU DIARIZARE ȘI TRANSCRIERE ===
    
    // Funcție pentru a porni înregistrarea pentru diarizare
    fun startDiarizationRecording() {
        viewModelScope.launch {
            try {
                // Curățăm datele anterioare
                capturedPcmData = null
                _diarizationSegments.value = emptyList()
                
                // Actualizăm starea înainte de a porni înregistrarea efectivă
                _isDiarizationRecording.value = true
                diarizationService.startRecording()
                
                // Anunțăm utilizatorul că înregistrăm
                addSystemMessage("Recording conversation... Speak to detect dialog.")
            } catch (e: Exception) {
                // În caz de eroare, resetăm starea
                _isDiarizationRecording.value = false
                addSystemMessage("Error starting conversation recording: ${e.message}")
            }
        }
    }
    
    // Funcție pentru a opri înregistrarea și procesa conversația
    fun stopDiarizationRecording() {
        viewModelScope.launch {
            try {
                // Actualizăm starea imediat
                _isDiarizationRecording.value = false
                addSystemMessage("Processing recorded conversation...")
                
                // Salvăm o referință la datele PCM înainte de oprirea înregistrării
                val pcmData = diarizationService.getCapturedPcmData()
                capturedPcmData = pcmData
                
                // Oprim înregistrarea și procesăm datele
                diarizationService.stopRecording()
                
                // Ne asigurăm că starea isProcessing se resetează după finalizarea procesării
                delay(500)
                _isDiarizationProcessing.value = false
            } catch (e: Exception) {
                _isDiarizationProcessing.value = false
                addSystemMessage("Error processing conversation: ${e.message}")
            }
        }
    }
    
    // Funcție pentru transcrierea segmentelor de dialog
    private fun transcribeDialogSegments(segments: List<DialogSegment>, pcmData: ShortArray) {
        viewModelScope.launch {
            try {
                // Actualizăm segmentele de dialog cu textul transcris
                val updatedSegments = segments.mapIndexed { index, segment ->
                    // Transcriem segmentul
                    val result = transcriptionService.transcribeSegment(
                        pcmData, 
                        segment.startTime, 
                        segment.endTime
                    )
                    
                    if (result.isSuccessful) {
                        // Actualizăm segmentul cu textul transcris
                        segment.copy(
                            text = result.text,
                            confidence = 0.8f  // Valoare implicită
                        )
                    } else {
                        segment
                    }
                }
                
                // Actualizăm lista de segmente
                _diarizationSegments.value = updatedSegments
                
                // Verificăm doar dacă avem text, nu mai întrebăm utilizatorul
                if (updatedSegments.any { it.text.isNotEmpty() }) {
                    addSystemMessage("Conversation detected and transcribed. Analyzing...")
                } else {
                    addSystemMessage("Could not transcribe the conversation. Please try again.")
                }
            } catch (e: Exception) {
                addSystemMessage("Error transcribing conversation: ${e.message}")
            }
        }
    }
    
    // Funcție pentru a genera un prompt din segmentele de dialog și a-l trimite la ChatGPT
    fun sendDiarizationResultToChat() {
        viewModelScope.launch {
            try {
                val segments = _diarizationSegments.value
                if (segments.isEmpty() || segments.all { it.text.isEmpty() }) {
                    addSystemMessage("No transcribed conversation to analyze.")
                    return@launch
                }
                
                // Construim mesajul vizibil pentru utilizator - doar textul transcris
                val visibleUserContent = buildString {
                    segments.forEach { segment ->
                        if (segment.text.isNotEmpty()) {
                            append("Speaker ${segment.speakerTag}: ${segment.text}\n")
                        }
                    }
                }
                
                // Construim promptul complet pentru API (invizibil pentru utilizator)
                val apiPrompt = buildString {
                    segments.forEach { segment ->
                        if (segment.text.isNotEmpty()) {
                            append("Speaker ${segment.speakerTag}: ${segment.text}\n")
                        }
                    }
                    append("\nYou are an intelligent voice assistant. " +
                        "Extract the questions from the user's conversation and answer only to them, concisely and clearly with helpful and accurate information. " +
                            "If there are no explicit questions, interpret the user's intent and provide a useful response. Always respond in speakers language." +
                            "IMPORTANT: only structured and clear answers to questions.")
                }
                
                // Adăugăm mesajul vizibil la conversație pentru utilizator
                val userMessage = Message(
                    content = visibleUserContent,
                    type = MessageType.USER
                )
                
                addMessageToConversation(userMessage)
                
                // Folosim promptul complet pentru API, nu mesajul vizibil
                getAIResponseWithCustomPrompt(apiPrompt)
            } catch (e: Exception) {
                addSystemMessage("Error sending conversation for analysis: ${e.message}")
            }
        }
    }
    
    // Funcție specializată pentru a trimite un prompt personalizat către API,
    // fără a modifica mesajul afișat utilizatorului
    private fun getAIResponseWithCustomPrompt(apiPrompt: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Adăugăm un mesaj temporar care indică încărcarea
            val loadingMessage = Message(
                content = "Generate the response...",
                type = MessageType.ASSISTANT,
                isProcessing = true
            )
            addMessageToConversation(loadingMessage)
            
            try {
                // Pregătim mesajele pentru API
                val messageHistory = mutableListOf<AIMessage>()
                
                // Adăugăm un sistem prompt specific pentru analiza conversației
                messageHistory.add(AIMessage(
                    role = "system",
                    content = "You are an intelligent voice assistant. " +
                            "Extract the questions from the user's conversation and answer only to them, concisely and clearly with helpful and accurate information. " +
                            "If there are no explicit questions, interpret the user's intent and provide a useful response. Always respond in speakers language." +
                            "IMPORTANT: only structured and clear answers to questions."
                ))
                
                // Adăugăm promptul personalizat
                messageHistory.add(AIMessage(
                    role = "user",
                    content = apiPrompt
                ))
                
                // Obținem răspunsul de la API
                val response = aiApiService.getAIResponse(messageHistory)
                
                // Înlocuim mesajul de loading cu răspunsul primit
                val assistantMessage = Message(
                    content = response,
                    type = MessageType.ASSISTANT
                )
                
                // Actualizăm conversația pentru a înlocui mesajul de loading cu răspunsul final
                activeConversation.value?.let { conversation ->
                    val updatedMessages = conversation.messages.toMutableList()
                    
                    // Găsim și înlocuim mesajul de loading
                    val loadingIndex = updatedMessages.indexOfLast { it.isProcessing }
                    if (loadingIndex != -1) {
                        updatedMessages[loadingIndex] = assistantMessage
                    } else {
                        // În caz că nu găsim mesajul de loading, adăugăm răspunsul la final
                        updatedMessages.add(assistantMessage)
                    }
                    
                    val updatedConversation = conversation.copy(
                        messages = updatedMessages,
                        updatedAt = java.util.Date()
                    )
                    
                    conversationRepository.updateConversation(updatedConversation)
                    
                    // Citim automat răspunsul asistentului
                    setSelectedMessageForTts(assistantMessage)
                }
            } catch (e: Exception) {
                // În caz de eroare, înlocuim mesajul de loading cu mesajul de eroare
                val errorMessage = Message(
                    content = "Error: ${e.message}",
                    type = MessageType.ASSISTANT
                )
                
                // Actualizăm conversația pentru a înlocui mesajul de loading cu mesajul de eroare
                activeConversation.value?.let { conversation ->
                    val updatedMessages = conversation.messages.toMutableList()
                    
                    // Găsim și înlocuim mesajul de loading
                    val loadingIndex = updatedMessages.indexOfLast { it.isProcessing }
                    if (loadingIndex != -1) {
                        updatedMessages[loadingIndex] = errorMessage
                    } else {
                        // În caz că nu găsim mesajul de loading, adăugăm mesajul de eroare la final
                        updatedMessages.add(errorMessage)
                    }
                    
                    val updatedConversation = conversation.copy(
                        messages = updatedMessages,
                        updatedAt = java.util.Date()
                    )
                    
                    conversationRepository.updateConversation(updatedConversation)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // Funcție pentru a adăuga un mesaj de sistem informativ
    private fun addSystemMessage(text: String) {
        // Adăugăm mesajul ca un răspuns de la asistent, dar marcat special
        val message = Message(
            content = text,
            type = MessageType.ASSISTANT
        )
        
        addMessageToConversation(message)
    }
    
    // === FUNCȚII PENTRU GESTIONAREA CONVERSAȚIILOR ===
    
    // Funcție pentru a crea o nouă conversație
    fun createNewConversation(title: String = "New conversation") {
        viewModelScope.launch {
            // Folosim metoda repository-ului pentru a crea o nouă conversație
            // care o și setează ca fiind activă
            conversationRepository.createNewConversation(title)
        }
    }
    
    // Funcție pentru a adăuga un mesaj la conversația activă
    private fun addMessageToConversation(message: Message) {
        viewModelScope.launch {
            val activeConv = activeConversation.value
            
            if (activeConv == null) {
                // Dacă nu există o conversație activă, creăm una nouă
                val newTitle = if (message.type == MessageType.USER) {
                    Conversation.generateTitleFromMessage(message.content)
                } else {
                    "New conversation"
                }
                
                val newConversation = conversationRepository.createNewConversation(newTitle)
                // Adăugăm mesajul la conversația nou creată
                val updatedConversation = newConversation.copy(
                    messages = listOf(message)
                )
                conversationRepository.updateConversation(updatedConversation)
            } else {
                // Adăugăm mesajul la conversația existentă
                val updatedMessages = activeConv.messages.toMutableList()
                updatedMessages.add(message)
                
                val updatedConversation = activeConv.copy(
                    messages = updatedMessages,
                    updatedAt = java.util.Date()
                )
                
                conversationRepository.updateConversation(updatedConversation)
            }
        }
    }
    
    // Funcție pentru a șterge o conversație
    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
        }
    }
    
    /**
     * Setează conversația activă
     */
    fun setActiveConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.setActiveConversation(conversationId)
        }
    }
    
    /**
     * Redenumește o conversație
     */
    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            val conversation = conversations.value.find { it.id == conversationId }
            conversation?.let {
                val updatedConversation = it.copy(title = newTitle)
                conversationRepository.updateConversation(updatedConversation)
            }
        }
    }
    
    // === FUNCȚII PENTRU RECUNOAȘTERE VOCALĂ ȘI TRIMITERE MESAJE ===
    
    // Funcție pentru a începe ascultarea
    fun startListening() {
        Log.d("ChatViewModel", "Starting simple voice recording")
        speechRecognitionService.startListening()
    }
    
    // Funcție pentru a opri ascultarea
    fun stopListening() {
        Log.d("ChatViewModel", "Stop simple voice recording")
        speechRecognitionService.stopListening()
    }
    
    // Funcție pentru a trimite textul recunoscut ca mesaj
    fun sendRecognizedText() {
        val recognizedText = _currentRecognizedText.value
        
        Log.d("ChatViewModel", "Sending recognized text: '$recognizedText'")
        
        if (recognizedText.isNotBlank()) {
            sendMessage(recognizedText)
            _currentRecognizedText.value = ""
            speechRecognitionService.clearRecognizedText()
            Log.d("ChatViewModel", "Text has been sent and cleared")
        } else {
            Log.d("ChatViewModel", "Nothing sent, recognized text is empty")
        }
    }
    
    // Funcția veche care făcea ambele operații, acum împărțită în două funcții separate
    // Păstrată pentru compatibilitate
    fun stopListeningAndSendMessage() {
        stopListening()
        sendRecognizedText()
    }
    
    // Funcție pentru a trimite un mesaj text
    fun sendMessage(messageText: String) {
        if (messageText.isBlank()) return
        
        val userMessage = Message(
            content = messageText,
            type = MessageType.USER
        )
        
        // Adăugăm mesajul utilizatorului la conversație
        addMessageToConversation(userMessage)
        
        // Generăm răspunsul de la API
        getAIResponse(messageText)
    }
    
    // Funcție pentru a obține răspunsul de la API
    private fun getAIResponse(userQuestion: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            // Adăugăm un mesaj temporar care indică încărcarea
            val loadingMessage = Message(
                content = "Generating the response...",
                type = MessageType.ASSISTANT,
                isProcessing = true
            )
            addMessageToConversation(loadingMessage)
            
            try {
                // Pregătim istoricul conversației pentru a păstra contextul
                val messageHistory = mutableListOf<AIMessage>()
                
                // Adăugăm un sistem prompt specific pentru analiza conversației
                messageHistory.add(AIMessage(
                    role = "system",
                    content = "You are an intelligent voice assistant. " +
                            "Extract the questions from the user's conversation and answer only to them, concisely and clearly with helpful and accurate information. " +
                            "If there are no explicit questions, interpret the user's intent and provide a useful response. Always respond in speakers language." +
                            "IMPORTANT: only structured and clear answers to questions."
                ))
                
                // Adăugăm mesajul curent al utilizatorului (care conține conversația transcrisă)
                messageHistory.add(AIMessage(
                    role = "user",
                    content = userQuestion
                ))
                
                // Obținem răspunsul de la API
                val response = aiApiService.getAIResponse(messageHistory)
                
                // Înlocuim mesajul de loading cu răspunsul primit
                val assistantMessage = Message(
                    content = response,
                    type = MessageType.ASSISTANT
                )
                
                // Actualizăm conversația pentru a înlocui mesajul de loading cu răspunsul final
                activeConversation.value?.let { conversation ->
                    val updatedMessages = conversation.messages.toMutableList()
                    
                    // Găsim și înlocuim mesajul de loading
                    val loadingIndex = updatedMessages.indexOfLast { it.isProcessing }
                    if (loadingIndex != -1) {
                        updatedMessages[loadingIndex] = assistantMessage
                    } else {
                        // În caz că nu găsim mesajul de loading, adăugăm răspunsul la final
                        updatedMessages.add(assistantMessage)
                    }
                    
                    val updatedConversation = conversation.copy(
                        messages = updatedMessages,
                        updatedAt = java.util.Date()
                    )
                    
                    conversationRepository.updateConversation(updatedConversation)
                    
                    // Citim automat răspunsul asistentului
                    setSelectedMessageForTts(assistantMessage)
                }
            } catch (e: Exception) {
                // În caz de eroare, înlocuim mesajul de loading cu mesajul de eroare
                val errorMessage = Message(
                    content = "Error: ${e.message}",
                    type = MessageType.ASSISTANT
                )
                
                // Actualizăm conversația pentru a înlocui mesajul de loading cu mesajul de eroare
                activeConversation.value?.let { conversation ->
                    val updatedMessages = conversation.messages.toMutableList()
                    
                    // Găsim și înlocuim mesajul de loading
                    val loadingIndex = updatedMessages.indexOfLast { it.isProcessing }
                    if (loadingIndex != -1) {
                        updatedMessages[loadingIndex] = errorMessage
                    } else {
                        // În caz că nu găsim mesajul de loading, adăugăm mesajul de eroare la final
                        updatedMessages.add(errorMessage)
                    }
                    
                    val updatedConversation = conversation.copy(
                        messages = updatedMessages,
                        updatedAt = java.util.Date()
                    )
                    
                    conversationRepository.updateConversation(updatedConversation)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // === FUNCȚII TTS ===
    
    /**
     * Setează mesajul selectat pentru TTS și pornește audio
     */
    fun setSelectedMessageForTts(message: Message?) {
        _selectedMessageForTts.value = message
        
        message?.let {
            textToSpeechService.speak(it.content)
        }
    }
    
    /**
     * Oprește playback-ul TTS
     */
    fun stopTts() {
        textToSpeechService.stop()
        _selectedMessageForTts.value = null
    }
    
    /**
     * Schimbă vocea TTS
     */
    fun setTtsVoice(voice: Voice) {
        textToSpeechService.setVoice(voice)
    }
    
    override fun onCleared() {
        super.onCleared()
        textToSpeechService.shutdown()
        speechRecognitionService.release()
        diarizationService.release()
        transcriptionService.release()
    }
} 