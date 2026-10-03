package com.studyassistant.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyassistant.app.data.model.Document
import com.studyassistant.app.data.repository.DocumentRepository
import com.studyassistant.app.StudyAssistantApp
import com.studyassistant.app.util.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DocumentsViewModel(private val repository: DocumentRepository) : ViewModel() {

    private val _documentsState = MutableStateFlow<UiState<List<Document>>>(UiState.Loading)
    val documentsState: StateFlow<UiState<List<Document>>> = _documentsState.asStateFlow()

    private val _uploadState = MutableStateFlow<UiState<Document>>(UiState.Empty)
    val uploadState: StateFlow<UiState<Document>> = _uploadState.asStateFlow()

    fun fetchDocuments() {
        viewModelScope.launch {
            _documentsState.value = UiState.Loading
            val result = repository.getDocuments()
            result.onSuccess { docs ->
                if (docs.isEmpty()) {
                    _documentsState.value = UiState.Empty
                } else {
                    _documentsState.value = UiState.Success(docs)
                }
            }.onFailure { e ->
                _documentsState.value = UiState.Error(e.message ?: "Failed to load documents")
            }
        }
    }

    fun uploadDocument(uri: Uri) {
        viewModelScope.launch {
            _uploadState.value = UiState.Loading
            val result = repository.uploadDocument(uri)
            result.onSuccess { doc ->
                _uploadState.value = UiState.Success(doc)
                fetchDocuments()
            }.onFailure { e ->
                _uploadState.value = UiState.Error(e.message ?: "Upload failed")
            }
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            repository.deleteDocument(docId)
            fetchDocuments()
        }
    }

    fun resetUploadState() {
        _uploadState.value = UiState.Empty
    }

    companion object {
        fun provideFactory(app: StudyAssistantApp): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DocumentsViewModel(DocumentRepository(NetworkModule.api, app.sessionManager, app.applicationContext)) as T
                }
            }
        }
    }
}
