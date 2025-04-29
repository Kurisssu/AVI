package com.example.aviv1.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChatInput(
    recognizedText: String,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bara de text
        ElevatedCard(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (recognizedText.isNotEmpty()) recognizedText else "Apasă pe microfon pentru a începe înregistrarea",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (recognizedText.isNotEmpty()) 
                        MaterialTheme.colorScheme.onSurface 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Butonul de microfon
        RecordButton(
            isRecording = isRecording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording
        )
        
        // Spațiu între butoane
        Spacer(modifier = Modifier.width(8.dp))
        
        // Butonul de trimitere
        SendButton(
            isEnabled = recognizedText.isNotEmpty(),
            onSendMessage = onSendMessage
        )
    }
}

@Composable
fun RecordButton(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1.2f else 1.0f,
        label = "scale"
    )
    
    FloatingActionButton(
        onClick = { 
            if (isRecording) onStopRecording() else onStartRecording()
        },
        modifier = modifier.scale(scale),
        containerColor = if (isRecording) 
            MaterialTheme.colorScheme.error 
        else 
            MaterialTheme.colorScheme.primary
    ) {
        if (isRecording) {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = "Oprește înregistrarea"
            )
        } else {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Începe înregistrarea"
            )
        }
    }
}

@Composable
fun SendButton(
    isEnabled: Boolean,
    onSendMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = { if (isEnabled) onSendMessage() },
        modifier = modifier,
        containerColor = if (isEnabled)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Trimite mesajul",
            tint = if (isEnabled) 
                MaterialTheme.colorScheme.onPrimaryContainer 
            else 
                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun RecordingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.Red)
                .padding(end = 8.dp)
        )
        
        Text(
            text = "Înregistrare în curs...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
} 