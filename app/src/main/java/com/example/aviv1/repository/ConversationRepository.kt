package com.example.aviv1.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aviv1.model.Conversation
import com.example.aviv1.model.Message
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Date

class ConversationRepository(private val context: Context) {

    private val TAG = "ConversationRepository"
    private val PREFS_NAME = "aviv1_conversations"
    private val CONVERSATIONS_KEY = "conversations_list"
    private val ACTIVE_CONVERSATION_KEY = "active_conversation_id"

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val gson: Gson by lazy {
        GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").create()
    }

    // Flow pentru a observa conversațiile
    private val _conversationsFlow = MutableStateFlow<List<Conversation>>(emptyList())
    val conversationsFlow: StateFlow<List<Conversation>> = _conversationsFlow.asStateFlow()

    // Flow pentru a observa conversația activă
    private val _activeConversationFlow = MutableStateFlow<Conversation?>(null)
    val activeConversationFlow: StateFlow<Conversation?> = _activeConversationFlow.asStateFlow()

    init {
        // Încarcă conversațiile la inițializare
        loadConversations()
        loadActiveConversation()
    }

    /**
     * Încarcă toate conversațiile din SharedPreferences
     */
    private fun loadConversations() {
        try {
            val conversationsJson = prefs.getString(CONVERSATIONS_KEY, null)
            if (conversationsJson != null) {
                val type = object : TypeToken<List<Conversation>>() {}.type
                val conversations: List<Conversation> = gson.fromJson(conversationsJson, type)
                _conversationsFlow.value = conversations.sortedByDescending { it.updatedAt }
                Log.d(TAG, "Loaded ${conversations.size} conversations")
            } else {
                _conversationsFlow.value = emptyList()
                Log.d(TAG, "No conversations found in SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading conversations", e)
            _conversationsFlow.value = emptyList()
        }
    }

    /**
     * Încarcă conversația activă
     */
    private fun loadActiveConversation() {
        val activeConversationId = prefs.getString(ACTIVE_CONVERSATION_KEY, null)
        if (activeConversationId != null) {
            _activeConversationFlow.value = _conversationsFlow.value.find { it.id == activeConversationId }
            Log.d(TAG, "Loaded active conversation: ${_activeConversationFlow.value?.title}")
        }
    }

    /**
     * Salvează toate conversațiile în SharedPreferences
     */
    private suspend fun saveConversations() = withContext(Dispatchers.IO) {
        try {
            val conversationsJson = gson.toJson(_conversationsFlow.value)
            prefs.edit().putString(CONVERSATIONS_KEY, conversationsJson).apply()
            Log.d(TAG, "Saved ${_conversationsFlow.value.size} conversations")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving conversations", e)
        }
    }

    /**
     * Salvează ID-ul conversației active
     */
    private suspend fun saveActiveConversationId() = withContext(Dispatchers.IO) {
        _activeConversationFlow.value?.let { conversation ->
            prefs.edit().putString(ACTIVE_CONVERSATION_KEY, conversation.id).apply()
            Log.d(TAG, "Saved active conversation ID: ${conversation.id}")
        } ?: run {
            prefs.edit().remove(ACTIVE_CONVERSATION_KEY).apply()
            Log.d(TAG, "Removed active conversation ID")
        }
    }

    /**
     * Creează o nouă conversație
     */
    suspend fun createNewConversation(title: String): Conversation = withContext(Dispatchers.IO) {
        val newConversation = Conversation(
            title = title,
            messages = emptyList()
        )

        val updatedList = _conversationsFlow.value.toMutableList().apply {
            add(0, newConversation)
        }

        _conversationsFlow.value = updatedList
        _activeConversationFlow.value = newConversation

        saveConversations()
        saveActiveConversationId()

        Log.d(TAG, "Created new conversation: ${newConversation.title}")
        newConversation
    }

    /**
     * Adaugă un mesaj la conversația activă
     */
    suspend fun addMessageToActiveConversation(message: Message) = withContext(Dispatchers.IO) {
        _activeConversationFlow.value?.let { currentConversation ->
            val updatedMessages = currentConversation.messages.toMutableList().apply {
                add(message)
            }

            val updatedConversation = currentConversation.copy(
                messages = updatedMessages,
                updatedAt = Date()
            )

            updateConversation(updatedConversation)
            
            Log.d(TAG, "Added message to conversation: ${currentConversation.title}")
        }
    }

    /**
     * Actualizează o conversație existentă
     */
    suspend fun updateConversation(updatedConversation: Conversation) = withContext(Dispatchers.IO) {
        val updatedList = _conversationsFlow.value.toMutableList().apply {
            val index = indexOfFirst { it.id == updatedConversation.id }
            if (index != -1) {
                this[index] = updatedConversation
            }
        }

        _conversationsFlow.value = updatedList.sortedByDescending { it.updatedAt }
        
        if (_activeConversationFlow.value?.id == updatedConversation.id) {
            _activeConversationFlow.value = updatedConversation
        }

        saveConversations()
        
        Log.d(TAG, "Updated conversation: ${updatedConversation.title}")
    }

    /**
     * Șterge o conversație
     */
    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        val updatedList = _conversationsFlow.value.toMutableList().apply {
            removeAll { it.id == conversationId }
        }

        _conversationsFlow.value = updatedList

        // Dacă am șters conversația activă, setăm prima conversație disponibilă ca activă
        // sau null dacă nu mai există nicio conversație
        if (_activeConversationFlow.value?.id == conversationId) {
            _activeConversationFlow.value = updatedList.firstOrNull()
            saveActiveConversationId()
        }

        saveConversations()
        
        Log.d(TAG, "Deleted conversation with ID: $conversationId")
    }

    /**
     * Setează conversația activă
     */
    suspend fun setActiveConversation(conversationId: String) = withContext(Dispatchers.IO) {
        val conversation = _conversationsFlow.value.find { it.id == conversationId }
        if (conversation != null) {
            _activeConversationFlow.value = conversation
            saveActiveConversationId()
            Log.d(TAG, "Set active conversation: ${conversation.title}")
        } else {
            Log.e(TAG, "Conversation with ID $conversationId not found")
        }
    }

    /**
     * Creează o conversație nouă și o setează ca activă dacă nu există nicio conversație
     */
    suspend fun createDefaultConversationIfNeeded(): Conversation? = withContext(Dispatchers.IO) {
        if (_conversationsFlow.value.isEmpty()) {
            val newConversation = createNewConversation("Conversație nouă")
            Log.d(TAG, "Created default conversation")
            return@withContext newConversation
        }
        
        if (_activeConversationFlow.value == null && _conversationsFlow.value.isNotEmpty()) {
            val firstConversation = _conversationsFlow.value.first()
            _activeConversationFlow.value = firstConversation
            saveActiveConversationId()
            Log.d(TAG, "Set first conversation as active: ${firstConversation.title}")
            return@withContext firstConversation
        }
        
        return@withContext _activeConversationFlow.value
    }
} 