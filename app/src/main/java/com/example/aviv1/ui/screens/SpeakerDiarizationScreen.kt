package com.example.aviv1.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aviv1.model.DialogSegment
import com.example.aviv1.viewmodel.SpeakerDiarizationUiState
import com.example.aviv1.viewmodel.SpeakerDiarizationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerDiarizationScreen(
    viewModel: SpeakerDiarizationViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dialog Detection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        SpeakerDiarizationContent(
            uiState = uiState,
            onStartRecording = { viewModel.startRecording() },
            onStopRecording = { viewModel.stopRecording() },
            onClearResults = { viewModel.clearResults() },
            onTranscribeSegment = { viewModel.transcribeSegment(it) },
            onDismissError = { viewModel.dismissError() },
            paddingValues = paddingValues
        )
    }
}

@Composable
fun SpeakerDiarizationContent(
    uiState: SpeakerDiarizationUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onClearResults: () -> Unit,
    onTranscribeSegment: (Int) -> Unit,
    onDismissError: () -> Unit,
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Card cu acțiuni
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Voice Identification in Conversations",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Butoane control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Buton înregistrare
                    Button(
                        onClick = {
                            if (uiState.isRecording) onStopRecording() else onStartRecording() 
                        },
                        // Nu mai dezactivăm butonul în timpul înregistrării, doar în timpul procesării finale
                        enabled = uiState.isInitialized && (!uiState.isProcessing || uiState.isRecording)
                    ) {
                        Icon(
                            imageVector = if (uiState.isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (uiState.isRecording) "Stop recording" else "Start recording",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(if (uiState.isRecording) "Stop" else "Record")
                    }
                    
                    // Buton curățare rezultate
                    Button(
                        onClick = onClearResults,
                        enabled = uiState.hasResults && !uiState.isProcessing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear results",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Clear")
                    }
                }
                
                // Indicator de transcriere
                if (uiState.isTranscriptionEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Automatic transcription is enabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Indicator procesare - afișăm în mod distinct înregistrarea și procesarea
        AnimatedVisibility(
            visible = uiState.isRecording || uiState.isProcessing || uiState.isTranscribing
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = when {
                        uiState.isRecording -> "Recording in progress..."
                        uiState.isTranscribing -> "Transcribing audio..."
                        else -> "Processing audio data..."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Afișăm progresul transcrierii dacă e cazul
                if (uiState.isTranscribing && uiState.transcriptionProgress > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LinearProgressIndicator(
                        progress = { uiState.transcriptionProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(4.dp),
                        strokeCap = StrokeCap.Round
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Segment ${uiState.currentTranscribingSegment + 1}/${uiState.dialogSegments.size}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        
        // Erori
        AnimatedVisibility(visible = uiState.hasError) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Eroare",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    
                    Text(
                        text = uiState.error ?: "Eroare necunoscută",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = onDismissError) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Închide",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Informații despre starea curentă
        AnimatedVisibility(visible = uiState.isRecording) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Active recording... Speak to detect conversation between people.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Lista de segmente de dialog
        if (uiState.hasResults) {
            Text(
                text = "Dialog Results (${uiState.dialogSegments.size} segments)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(uiState.dialogSegments) { index, segment ->
                    DialogSegmentItem(
                        segment = segment,
                        index = index,
                        isTranscribing = uiState.isTranscribing && uiState.currentTranscribingSegment == index,
                        isTranscriptionEnabled = uiState.isTranscriptionEnabled,
                        onTranscribeClick = { onTranscribeSegment(index) }
                    )
                }
            }
        } else if (!uiState.isProcessing && !uiState.isRecording && !uiState.hasError) {
            // Stare goală
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Press the Record button to detect conversation between people",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DialogSegmentItem(
    segment: DialogSegment,
    index: Int,
    isTranscribing: Boolean = false,
    isTranscriptionEnabled: Boolean = false,
    onTranscribeClick: () -> Unit = {}
) {
    // Culori diferite pentru diferiți vorbitori
    val speakerColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.surfaceTint
    )
    
    // Selectare culoare bazată pe speakerTag
    val speakerColor = speakerColors[segment.speakerTag % speakerColors.size]
    
    // Progress pentru durată
    val progressFraction by animateFloatAsState(
        targetValue = 1f,
        label = "progress"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header cu informații despre vorbitor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar vorbitor
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(speakerColor.copy(alpha = 0.2f))
                        .border(2.dp, speakerColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Speaker ${segment.speakerTag}",
                        tint = speakerColor
                    )
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = "Speaker ${segment.speakerTag}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = speakerColor
                    )
                    
                    Row {
                        Text(
                            text = "Interval: ",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = String.format("%.2f - %.2f sec", segment.startTime, segment.endTime),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Row {
                        Text(
                            text = "Duration: ",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = String.format("%.2f sec", segment.duration),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Indicator de încredere transcriere dacă există
                    if (segment.confidence > 0) {
                        Row {
                            Text(
                                text = "Confidence: ",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = String.format("%.0f%%", segment.confidence * 100),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Buton transcriere manuală
                if (isTranscriptionEnabled && !isTranscribing && segment.text.isEmpty()) {
                    IconButton(
                        onClick = onTranscribeClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Transcribe segment",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Indicator proces de transcriere
                if (isTranscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                
                // Buton de re-transcriere dacă există text deja
                if (isTranscriptionEnabled && !isTranscribing && segment.text.isNotEmpty()) {
                    IconButton(
                        onClick = onTranscribeClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-transcribe segment",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress bar pentru durata segmentului
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = speakerColor
            )
            
            // Text transcris dacă există
            if (segment.text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Stilizăm textul transcris pentru a-l evidenția
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    //colors = CardDefaults.cardColors(containerColor = speakerColor.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Transcription:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = speakerColor
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = segment.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (isTranscriptionEnabled && !isTranscribing) {
                // Mesaj pentru segmente care nu au fost încă transcrise
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Press the",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "button to transcribe this segment",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
} 