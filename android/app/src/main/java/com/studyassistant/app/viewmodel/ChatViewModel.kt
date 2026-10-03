package com.studyassistant.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyassistant.app.data.model.ChatMessage
import com.studyassistant.app.data.model.ChatSession
import com.studyassistant.app.data.model.QueryRequest
import com.studyassistant.app.data.repository.ChatRepository
import com.studyassistant.app.StudyAssistantApp
import com.studyassistant.app.util.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _sessionsState = MutableStateFlow<UiState<List<ChatSession>>>(UiState.Loading)
    val sessionsState: StateFlow<UiState<List<ChatSession>>> = _sessionsState.asStateFlow()

    private val _messagesState = MutableStateFlow<UiState<List<ChatMessage>>>(UiState.Loading)
    val messagesState: StateFlow<UiState<List<ChatMessage>>> = _messagesState.asStateFlow()

    private val _sendState = MutableStateFlow<UiState<Unit>>(UiState.Empty)
    val sendState: StateFlow<UiState<Unit>> = _sendState.asStateFlow()

    fun fetchSessions() {
        viewModelScope.launch {
            _sessionsState.value = UiState.Loading
            val result = repository.getChatSessions()
            result.onSuccess { sessions ->
                if (sessions.isEmpty()) {
                    _sessionsState.value = UiState.Empty
                } else {
                    _sessionsState.value = UiState.Success(sessions)
                }
            }.onFailure { e ->
                _sessionsState.value = UiState.Error(e.message ?: "Failed to load sessions")
            }
        }
    }

    fun createSession(title: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.createChatSession(title)
            result.onSuccess { session ->
                onSuccess(session.id)
                fetchSessions()
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteChatSession(sessionId)
            fetchSessions()
        }
    }

    fun loadHistory(sessionId: String) {
        viewModelScope.launch {
            _messagesState.value = UiState.Loading
            val result = repository.getChatHistory(sessionId)
            result.onSuccess { messages ->
                if (messages.isEmpty()) {
                    _messagesState.value = UiState.Empty
                } else {
                    _messagesState.value = UiState.Success(messages)
                }
            }.onFailure { e ->
                _messagesState.value = UiState.Error(e.message ?: "Failed to load messages")
            }
        }
    }

    fun sendMessage(sessionId: String, query: String) {
        viewModelScope.launch {
            _sendState.value = UiState.Loading
            
            // Add optimistic user message
            val currentMessages = (_messagesState.value as? UiState.Success)?.data ?: emptyList()
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
            val userMsg = ChatMessage(
                id = "temp_user_${System.currentTimeMillis()}",
                session_id = sessionId,
                user_id = "",
                role = "user",
                content = query,
                sources = null,
                timestamp = timestamp
            )
            _messagesState.value = UiState.Success(currentMessages + userMsg)

            val result = repository.query(QueryRequest(query, sessionId))
            result.onSuccess { response ->
                _sendState.value = UiState.Success(Unit)
                val assistantMsg = ChatMessage(
                    id = "temp_assistant_${System.currentTimeMillis()}",
                    session_id = sessionId,
                    user_id = "",
                    role = "assistant",
                    content = response.response,
                    sources = response.sources,
                    timestamp = timestamp
                )
                val updatedMessages = (_messagesState.value as? UiState.Success)?.data ?: emptyList()
                _messagesState.value = UiState.Success(updatedMessages + assistantMsg)
            }.onFailure { e ->
                _sendState.value = UiState.Error(e.message ?: "Failed to send message")
                // Could remove optimistic user message on failure, or show error inline
            }
        }
    }

    fun resetSendState() {
        _sendState.value = UiState.Empty
    }

    companion object {
        fun provideFactory(app: StudyAssistantApp): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(ChatRepository(NetworkModule.api, app.sessionManager)) as T
                }
            }
        }
    }
}
