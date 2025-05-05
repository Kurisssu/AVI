package com.example.aviv1.ui.components

import android.speech.tts.Voice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun VoiceSelectionDialog(
    voices: List<Voice>,
    currentVoice: Voice?,
    onVoiceSelected: (Voice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a voice") },
        text = {
            Column {
                if (voices.isEmpty()) {
                    Text(
                        text = "No voices available.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(voices.sortedBy { it.name }) { voice ->
                            VoiceItem(
                                voice = voice,
                                isSelected = voice.name == currentVoice?.name,
                                onVoiceSelected = { onVoiceSelected(voice) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun VoiceItem(
    voice: Voice,
    isSelected: Boolean,
    onVoiceSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onVoiceSelected)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onVoiceSelected
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = getVoiceDisplayName(voice),
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = getVoiceDetails(voice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Voce selectată",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Obține un nume de afișare simplificat pentru voce
 */
private fun getVoiceDisplayName(voice: Voice): String {
    // Extragem numele țării din localizare
    val locale = voice.locale
    val countryName = locale.getDisplayCountry(Locale.getDefault())
    val languageName = locale.getDisplayLanguage(Locale.getDefault())
    
    // Simplificăm numele vocii (eliminăm prefixele comune)
    val simplifiedName = voice.name.replace(Regex("^(com\\.google\\.android\\.tts|en-us-x-|ro-ro-x-)"), "")
    
    return if (countryName.isNotEmpty() && languageName.isNotEmpty()) {
        "$simplifiedName ($languageName, $countryName)"
    } else {
        simplifiedName
    }
}

/**
 * Obține detalii despre voce (calitate, gen, etc.)
 */
private fun getVoiceDetails(voice: Voice): String {
    val features = mutableListOf<String>()
    
    // Gen vocal
    if (voice.name.contains("female", ignoreCase = true)) {
        features.add("Femeie")
    } else if (voice.name.contains("male", ignoreCase = true)) {
        features.add("Bărbat")
    }
    
    // Calitate
    if (voice.quality > Voice.QUALITY_NORMAL) {
        features.add("Calitate înaltă")
    }
    
    // Latență
    if (voice.latency == Voice.LATENCY_VERY_LOW || voice.latency == Voice.LATENCY_LOW) {
        features.add("Latență redusă")
    }
    
    return features.joinToString(", ")
} 