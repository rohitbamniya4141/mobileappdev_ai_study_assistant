package com.studyassistant.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import com.studyassistant.app.ui.components.PrimaryButton
import com.studyassistant.app.viewmodel.HomeViewModel
import com.studyassistant.app.viewmodel.UiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Composable
fun HomeScreen(
    onNavigateToFaqs: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToDocs: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as StudyAssistantApp
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.provideFactory(app))
    
    val statsState by viewModel.statsState.collectAsStateWithLifecycle()
    
    // Get user name synchronously for initial display
    val userName = remember {
        runBlocking { app.sessionManager.userNameFlow.first() ?: "User" }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "Home",
            actions = {
                IconButton(
                    onClick = {
                        runBlocking { app.sessionManager.clearSession() }
                        onLogout()
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                }
            }
        )

        when (val state = statsState) {
            is UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(message = state.message, onRetry = { viewModel.fetchStats() })
            is UiState.Empty -> EmptyState("No stats available.")
            is UiState.Success -> {
                val stats = state.data
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Hello, $userName!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Welcome to your AI Study Assistant.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatCard("Docs", stats.documents.toString(), Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        StatCard("Chunks", stats.chunks.toString(), Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        StatCard("FAQs", stats.faqs.toString(), Modifier.weight(1f))
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    PrimaryButton(text = "Ask AI", onClick = onNavigateToChat)
                    Spacer(modifier = Modifier.height(16.dp))
                    PrimaryButton(text = "Upload Document", onClick = onNavigateToDocs)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onNavigateToFaqs,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Browse FAQs")
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
