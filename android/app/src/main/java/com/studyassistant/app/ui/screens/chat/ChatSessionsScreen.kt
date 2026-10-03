package com.studyassistant.app.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyassistant.app.StudyAssistantApp
import com.studyassistant.app.ui.components.AppTopBar
import com.studyassistant.app.ui.components.ChatSessionCard
import com.studyassistant.app.ui.components.EmptyState
import com.studyassistant.app.ui.components.ErrorView
import com.studyassistant.app.ui.components.LoadingView
import com.studyassistant.app.viewmodel.ChatViewModel
import com.studyassistant.app.viewmodel.UiState

@Composable
fun ChatSessionsScreen(
    onSessionClick: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StudyAssistantApp
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.provideFactory(app))
    
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchSessions()
    }

    Scaffold(
        topBar = { AppTopBar(title = "Conversations") },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.createSession("New Conversation", onSessionClick) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Session")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = sessionsState) {
                is UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(message = state.message, onRetry = { viewModel.fetchSessions() })
                is UiState.Empty -> EmptyState("No conversations yet. Start one!")
                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(state.data) { session ->
                            ChatSessionCard(
                                session = session,
                                onClick = { onSessionClick(it.id) },
                                onDelete = { showDeleteDialog = it.id }
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete Conversation") },
                text = { Text("Are you sure you want to delete this conversation?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteSession(showDeleteDialog!!)
                        showDeleteDialog = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
