package com.example.aviv1.viewmodel

import android.app.Application
import android.speech.tts.Voice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aviv1.model.Conversation
import com.example.aviv1.model.Message
import com.example.aviv1.model.MessageType
import com.example.aviv1.repository.ConversationRepository
import com.example.aviv1.service.AIApiService
import com.example.aviv1.service.AIMessage
import com.example.aviv1.service.SpeechRecognitionService
import com.example.aviv1.service.TextToSpeechService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    
    private val speechRecognitionService = SpeechRecognitionService(application.applicationContext)
    private val aiApiService = AIApiService()
    private val conversationRepository = ConversationRepository(application.applicationContext)
    private val textToSpeechService = TextToSpeechService(application.applicationContext)
    
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
    
    init {
        // Inițializăm serviciul de recunoaștere vocală
        speechRecognitionService.initialize()
        
        // Monitorizăm textul recunoscut
        viewModelScope.launch {
            speechRecognitionService.recognizedText.collectLatest { text ->
                _currentRecognizedText.value = text
            }
        }
        
        // Monitorizăm starea de înregistrare
        viewModelScope.launch {
            speechRecognitionService.isRecording.collectLatest { isRecording ->
                _isRecording.value = isRecording
            }
        }
        
        // Monitorizăm conversația activă și actualizăm mesajele
        viewModelScope.launch {
            conversationRepository.activeConversationFlow.collectLatest { conversation ->
                _messages.value = conversation?.messages ?: emptyList()
            }
        }
        
        // Creăm o conversație implicită dacă nu există niciuna
        viewModelScope.launch {
            conversationRepository.createDefaultConversationIfNeeded()
        }
    }
    
    // === FUNCȚII PENTRU TTS (TEXT-TO-SPEECH) ===
    
    /**
     * Redă un mesaj prin TTS
     */
    fun speakMessage(message: Message) {
        _selectedMessageForTts.value = message
        textToSpeechService.speak(message.content)
    }
    
    /**
     * Oprește redarea TTS
     */
    fun stopSpeaking() {
        textToSpeechService.stop()
        _selectedMessageForTts.value = null
    }
    
    /**
     * Schimbă vocea TTS
     */
    fun setTtsVoice(voice: Voice) {
        textToSpeechService.setVoice(voice)
    }
    
    // === FUNCȚII PENTRU CONVERSAȚII ===
    
    /**
     * Creează o nouă conversație
     */
    fun createNewConversation(title: String = "Conversație nouă") {
        viewModelScope.launch {
            conversationRepository.createNewConversation(title)
        }
    }
    
    /**
     * Șterge o conversație
     */
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
        speechRecognitionService.startListening()
    }
    
    // Funcție pentru a opri ascultarea
    fun stopListening() {
        speechRecognitionService.stopListening()
    }
    
    // Funcție pentru a trimite textul recunoscut ca mesaj
    fun sendRecognizedText() {
        val recognizedText = _currentRecognizedText.value
        
        if (recognizedText.isNotBlank()) {
            sendMessage(recognizedText)
            _currentRecognizedText.value = ""
            speechRecognitionService.clearRecognizedText()
        }
    }
    
    // Funcția veche care făcea ambele operații, acum împărțită în două funcții separate
    // Păstrată pentru compatibilitate
    fun stopListeningAndSendMessage() {
        stopListening()
        sendRecognizedText()
    }
    
    // Funcție pentru a trimite un mesaj text
    fun sendMessage(content: String) {
        val userMessage = Message(
            content = content,
            type = MessageType.USER
        )
        
        // Adăugăm mesajul în conversația activă
        viewModelScope.launch {
            // Dacă este primul mesaj, folosim-l pentru a genera automat un titlu dacă titlul este generic
            activeConversation.value?.let { conversation ->
                if (conversation.messages.isEmpty() && conversation.title == "Conversație nouă") {
                    val newTitle = Conversation.generateTitleFromMessage(content)
                    renameConversation(conversation.id, newTitle)
                }
            }
            
            // Adăugăm mesajul utilizatorului
            conversationRepository.addMessageToActiveConversation(userMessage)
            
            // Adăugăm un mesaj temporar pentru asistent
            val tempAssistantMessage = Message(
                content = "...",
                type = MessageType.ASSISTANT,
                isProcessing = true
            )
            
            conversationRepository.addMessageToActiveConversation(tempAssistantMessage)
            
            // Cerem răspunsul de la API
            requestAIResponse()
        }
    }
    
    // Funcție pentru a cere răspunsul de la API
    private suspend fun requestAIResponse() {
        _isLoading.value = true
        try {
            // Obținem conversația activă actualizată cu mesajele noi
            val currentConversation = activeConversation.value ?: return
            
            // Convertim mesajele pentru API
            val aiMessages = convertToAIMessages(currentConversation.messages)
            
            // Obținem răspunsul
            val response = aiApiService.getAIResponse(aiMessages)
            
            // Înlocuim mesajul temporar cu răspunsul real
            val messages = currentConversation.messages.toMutableList()
            // Verificăm dacă ultimul mesaj este mesajul de procesare
            if (messages.lastOrNull()?.isProcessing == true) {
                // Înlocuim mesajul temporar cu răspunsul real
                messages.removeAt(messages.size - 1)
                val assistantMessage = Message(
                    content = response,
                    type = MessageType.ASSISTANT
                )
                messages.add(assistantMessage)
                
                // Actualizăm conversația
                val updatedConversation = currentConversation.copy(messages = messages)
                conversationRepository.updateConversation(updatedConversation)
            }
        } catch (e: Exception) {
            // În caz de eroare, actualizăm mesajul temporar cu un mesaj de eroare
            activeConversation.value?.let { conversation ->
                val messages = conversation.messages.toMutableList()
                if (messages.lastOrNull()?.isProcessing == true) {
                    messages[messages.size - 1] = Message(
                        content = "S-a produs o eroare. Te rog să încerci din nou.",
                        type = MessageType.ASSISTANT
                    )
                    
                    // Actualizăm conversația
                    val updatedConversation = conversation.copy(messages = messages)
                    conversationRepository.updateConversation(updatedConversation)
                }
            }
        } finally {
            _isLoading.value = false
        }
    }
    
    // Funcție pentru a converti mesajele noastre în formatul pentru API
    private fun convertToAIMessages(messages: List<Message>): List<AIMessage> {
        val aiMessages = mutableListOf<AIMessage>()
        
        // Adăugăm un sistem prompt pentru a instrui modelul
        aiMessages.add(
            AIMessage(
                role = "system",
                content = "Ești un asistent vocal inteligent. Extrage întrebările din conversația utilizatorului și răspunde-le cu informații utile și precise. Dacă nu există întrebări explicite, interpretează intenția utilizatorului și oferă un răspuns util."
            )
        )
        
        // Luăm ultimele 10 mesaje din conversație pentru context
        val recentMessages = messages.takeLast(10).filterNot { it.isProcessing }
        
        for (message in recentMessages) {
            val role = when (message.type) {
                MessageType.USER -> "user"
                MessageType.ASSISTANT -> "assistant"
            }
            
            aiMessages.add(AIMessage(role = role, content = message.content))
        }
        
        return aiMessages
    }
    
    // Curățăm resursele la final
    override fun onCleared() {
        super.onCleared()
        speechRecognitionService.release()
        textToSpeechService.shutdown()
    }
} 