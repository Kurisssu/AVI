package com.example.aviv1.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aviv1.model.DialogSegment
import com.example.aviv1.model.Message
import com.example.aviv1.ui.components.ChatInput
import com.example.aviv1.ui.components.DiarizationRecordingIndicator
import com.example.aviv1.ui.components.MessageItem
import com.example.aviv1.ui.components.RecordingIndicator
import com.example.aviv1.util.PermissionUtils
import com.example.aviv1.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Versiunea actualizată a ecranului de chat
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToConversations: () -> Unit = {}
) {
    PermissionUtils.RequestPermission(
        onPermissionResult = { isGranted ->
            // Putem face ceva când se acordă permisiunea
        }
    ) {
        // Conținutul ecranului, care se afișează doar când permisiunea este acordată
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = "Intelligent Voice Assistant",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to conversations")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToConversations) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { paddingValues ->
            ChatScreenContent(paddingValues = paddingValues)
        }
    }
}

/**
 * Conținutul ecranului de chat, care poate fi reutilizat
 */
@Composable
fun ChatScreenContent(paddingValues: PaddingValues) {
    val viewModel: ChatViewModel = viewModel()
    val messages by viewModel.messages.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recognizedText by viewModel.currentRecognizedText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedMessageForTts by viewModel.selectedMessageForTts.collectAsState()
    val isTtsPlaying by viewModel.isTtsPlaying.collectAsState()
    
    // State pentru diarizare
    val isDiarizationRecording by viewModel.isDiarizationRecording.collectAsState()
    val isDiarizationProcessing by viewModel.isDiarizationProcessing.collectAsState()
    val diarizationSegments by viewModel.diarizationSegments.collectAsState()
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Rulăm o operație când se adaugă un nou mesaj pentru a derula la sfârșitul listei
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    // Trimitem automat conversația transcrisă către API când e gata
    LaunchedEffect(diarizationSegments) {
        if (diarizationSegments.isNotEmpty() && diarizationSegments.any { it.text.isNotEmpty() } && !isDiarizationRecording && !isDiarizationProcessing) {
            viewModel.sendDiarizationResultToChat()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Spațiul principal cu mesajele
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                items(messages) { message ->
                    MessageItem(
                        message = message,
                        isPlaying = isTtsPlaying && selectedMessageForTts == message,
                        onPlayClick = { viewModel.setSelectedMessageForTts(message) },
                        onStopClick = { viewModel.stopTts() }
                    )
                }
            }
            
            // Separator
            HorizontalDivider()
            
            // Indicator de înregistrare voce
            if (isRecording) {
                RecordingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
            
            // Indicator de înregistrare conversație (diarizare)
            if (isDiarizationRecording || isDiarizationProcessing) {
                DiarizationRecordingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
            
            // Input de chat
            ChatInput(
                recognizedText = recognizedText,
                isRecording = isRecording,
                onStartRecording = { viewModel.startListening() },
                onStopRecording = { viewModel.stopListening() },
                onSendMessage = { viewModel.sendRecognizedText() },
                isDiarizationRecording = isDiarizationRecording,
                onStartDiarizationRecording = { viewModel.startDiarizationRecording() },
                onStopDiarizationRecording = { viewModel.stopDiarizationRecording() },
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            )
        }
        
        // Indicator de încărcare pentru răspunsul AI
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
} 