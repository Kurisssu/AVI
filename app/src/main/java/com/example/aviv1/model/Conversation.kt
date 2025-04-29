package com.example.aviv1.model

import java.util.Date
import java.util.UUID

/**
 * Model care reprezintă o conversație completă
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<Message> = emptyList(),
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    /**
     * Returnează un titlu prescurtat pentru afișare în liste
     */
    fun getDisplayTitle(): String {
        return if (title.length > 30) {
            "${title.take(27)}..."
        } else {
            title
        }
    }

    /**
     * Generează un titlu automat din primul mesaj al utilizatorului
     */
    companion object {
        fun generateTitleFromMessage(message: String): String {
            return if (message.length > 40) {
                "${message.take(37)}..."
            } else {
                message
            }
        }
    }
} 