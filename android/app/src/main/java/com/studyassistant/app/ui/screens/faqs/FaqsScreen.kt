package com.studyassistant.app.ui.screens.faqs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyassistant.app.StudyAssistantApp
import com.studyassistant.app.data.model.FAQItem
import com.studyassistant.app.ui.components.AppTopBar
import com.studyassistant.app.ui.components.EmptyState
import com.studyassistant.app.ui.components.ErrorView
import com.studyassistant.app.ui.components.LoadingView
import com.studyassistant.app.viewmodel.FaqsViewModel
import com.studyassistant.app.viewmodel.UiState

@Composable
fun FaqsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StudyAssistantApp
    val viewModel: FaqsViewModel = viewModel(factory = FaqsViewModel.provideFactory(app))
    
    val faqsState by viewModel.faqsState.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf<String?>("All") }

    Scaffold(
        topBar = { AppTopBar(title = "Frequently Asked Questions", onBackClick = onBackClick) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = faqsState) {
                is UiState.Loading -> LoadingView()
                is UiState.Error -> ErrorView(message = state.message, onRetry = { viewModel.fetchFaqs() })
                is UiState.Empty -> EmptyState("No FAQs available.")
                is UiState.Success -> {
                    val allFaqs = state.data
                    val categories = listOf("All") + allFaqs.map { it.category }.distinct()
                    val filteredFaqs = if (selectedCategory == "All") allFaqs else allFaqs.filter { it.category == selectedCategory }

                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyRow(
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { category ->
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(category) }
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(filteredFaqs) { faq ->
                                FaqCard(faq = faq)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FaqCard(faq: FAQItem) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = faq.question,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = faq.answer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text(faq.category)
                    }
                }
            }
        }
    }
}
