package com.example.aviv1.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aviv1.model.Conversation
import com.example.aviv1.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateBack: () -> Unit,
    onSelectConversation: (String) -> Unit
) {
    val viewModel: ChatViewModel = viewModel()
    val conversations by viewModel.conversations.collectAsState()
    val activeConversation by viewModel.activeConversation.collectAsState()
    
    var showNewConversationDialog by remember { mutableStateOf(false) }
    var newConversationTitle by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversații") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Înapoi")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewConversationDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Conversație nouă")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (conversations.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "Nu există conversații.\nApasă pe + pentru a crea o conversație nouă.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(conversations) { conversation ->
                        val isActive = activeConversation?.id == conversation.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable {
                                    viewModel.setActiveConversation(conversation.id)
                                    onSelectConversation(conversation.id)
                                }
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = conversation.getDisplayTitle(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = if (isActive) 
                                            MaterialTheme.typography.titleMedium.copy(
                                                color = MaterialTheme.colorScheme.primary
                                            ) 
                                        else 
                                            MaterialTheme.typography.titleMedium
                                    )
                                },
                                supportingContent = {
                                    val lastMessage = conversation.messages.lastOrNull()?.content ?: ""
                                    Text(
                                        text = lastMessage,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = {
                                        viewModel.deleteConversation(conversation.id)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Șterge conversația"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        
        if (showNewConversationDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showNewConversationDialog = false 
                    newConversationTitle = ""
                },
                title = { Text("Conversație nouă") },
                text = { 
                    TextField(
                        value = newConversationTitle,
                        onValueChange = { newConversationTitle = it },
                        label = { Text("Titlu conversație") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val title = if (newConversationTitle.isBlank()) "Conversație nouă" else newConversationTitle
                            viewModel.createNewConversation(title)
                            showNewConversationDialog = false
                            newConversationTitle = ""
                        }
                    ) {
                        Text("Creează")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showNewConversationDialog = false 
                            newConversationTitle = ""
                        }
                    ) {
                        Text("Anulează")
                    }
                }
            )
        }
    }
}

// Extension function pentru afișarea titlului conversației
fun Conversation.getDisplayTitle(): String {
    return if (this.title.isBlank()) "Conversație fără titlu" else this.title
} 