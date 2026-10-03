package com.studyassistant.app.ui.screens.documents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyassistant.app.StudyAssistantApp
import com.studyassistant.app.ui.components.AppTopBar
import com.studyassistant.app.ui.components.DocumentCard
import com.studyassistant.app.ui.components.EmptyState
import com.studyassistant.app.ui.components.ErrorView
import com.studyassistant.app.ui.components.LoadingView
import com.studyassistant.app.viewmodel.DocumentsViewModel
import com.studyassistant.app.viewmodel.UiState

@Composable
fun DocumentsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as StudyAssistantApp
    val viewModel: DocumentsViewModel = viewModel(factory = DocumentsViewModel.provideFactory(app))
    
    val documentsState by viewModel.documentsState.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.uploadDocument(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.fetchDocuments()
    }

    LaunchedEffect(uploadState) {
        if (uploadState is UiState.Success) {
            snackbarHostState.showSnackbar("Document uploaded successfully")
            viewModel.resetUploadState()
        } else if (uploadState is UiState.Error) {
            snackbarHostState.showSnackbar((uploadState as UiState.Error).message)
            viewModel.resetUploadState()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "My Documents") },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            // Some Android providers use octet-stream or x-pdf for PDFs —
                            // including these ensures those files appear in the picker too.
                            "application/x-pdf",
                            "application/octet-stream"
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Upload, contentDescription = "Upload Document")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (uploadState is UiState.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val state = documentsState) {
                    is UiState.Loading -> LoadingView()
                    is UiState.Error -> ErrorView(message = state.message, onRetry = { viewModel.fetchDocuments() })
                    is UiState.Empty -> EmptyState("No documents yet. Upload a PDF or DOCX to get started.")
                    is UiState.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(state.data) { document ->
                                DocumentCard(
                                    document = document,
                                    onDelete = { showDeleteDialog = it.id }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete Document") },
                text = { Text("Are you sure you want to delete this document? This will also remove associated chunks.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteDocument(showDeleteDialog!!)
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
