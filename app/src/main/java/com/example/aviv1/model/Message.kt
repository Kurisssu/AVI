package com.example.aviv1.model

import java.util.Date

enum class MessageType {
    USER, ASSISTANT
}

data class Message(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val type: MessageType,
    val timestamp: Date = Date(),
    val isProcessing: Boolean = false
) 