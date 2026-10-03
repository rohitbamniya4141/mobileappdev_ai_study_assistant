package com.studyassistant.app.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyassistant.app.StudyAssistantApp
import com.studyassistant.app.ui.components.AppTopBar
import com.studyassistant.app.ui.components.EmptyState
import com.studyassistant.app.ui.components.ErrorView
import com.studyassistant.app.ui.components.LoadingView
import com.studyassistant.app.ui.components.MessageBubble
import com.studyassistant.app.viewmodel.ChatViewModel
import com.studyassistant.app.viewmodel.UiState
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    sessionId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StudyAssistantApp
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.provideFactory(app))
    
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val sendState by viewModel.sendState.collectAsStateWithLifecycle()
    
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sessionId) {
        viewModel.loadHistory(sessionId)
    }

    LaunchedEffect(messagesState) {
        if (messagesState is UiState.Success) {
            val count = (messagesState as UiState.Success).data.size
            if (count > 0) {
                coroutineScope.launch {
                    listState.animateScrollToItem(count - 1)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Chat",
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (val state = messagesState) {
                    is UiState.Loading -> LoadingView()
                    is UiState.Error -> ErrorView(message = state.message, onRetry = { viewModel.loadHistory(sessionId) })
                    is UiState.Empty -> EmptyState("Ask a question about your documents...")
                    is UiState.Success -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(state.data) { message ->
                                MessageBubble(message = message)
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.ime.asPaddingValues())
                        .padding(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask a question...") },
                        maxLines = 4,
                        shape = MaterialTheme.shapes.large
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (query.isNotBlank()) {
                                viewModel.sendMessage(sessionId, query)
                                query = ""
                            }
                        },
                        enabled = query.isNotBlank() && sendState !is UiState.Loading
                    ) {
                        if (sendState is UiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
