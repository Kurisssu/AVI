package com.example.aviv1.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aviv1.ui.components.VoiceSelectionDialog
import com.example.aviv1.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: ChatViewModel = viewModel()
    val availableTtsVoices by viewModel.availableTtsVoices.collectAsState()
    val currentTtsVoice by viewModel.currentTtsVoice.collectAsState()
    
    var showVoiceSelectionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Card(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Text-to-Speech Settings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                
                HorizontalDivider()
                
                ListItem(
                    headlineContent = { Text("Text-to-speech voice") },
                    supportingContent = { 
                        val voiceName = currentTtsVoice?.name ?: "No voice selected"
                        Text(
                            text = voiceName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp).clickable { showVoiceSelectionDialog = true }
                )
            }
        }
        
        if (showVoiceSelectionDialog) {
            VoiceSelectionDialog(
                voices = availableTtsVoices,
                currentVoice = currentTtsVoice,
                onVoiceSelected = {
                    viewModel.setTtsVoice(it)
                    showVoiceSelectionDialog = false
                },
                onDismiss = { showVoiceSelectionDialog = false }
            )
        }
    }
} 