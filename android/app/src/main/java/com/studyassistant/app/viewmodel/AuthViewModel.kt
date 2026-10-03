package com.studyassistant.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyassistant.app.data.model.LoginRequest
import com.studyassistant.app.data.model.RegisterRequest
import com.studyassistant.app.data.model.UserResponse
import com.studyassistant.app.data.repository.AuthRepository
import com.studyassistant.app.util.NetworkModule
import com.studyassistant.app.StudyAssistantApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    
    private val _loginState = MutableStateFlow<UiState<UserResponse>>(UiState.Empty)
    val loginState: StateFlow<UiState<UserResponse>> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<UiState<UserResponse>>(UiState.Empty)
    val registerState: StateFlow<UiState<UserResponse>> = _registerState.asStateFlow()

    fun login(request: LoginRequest) {
        viewModelScope.launch {
            _loginState.value = UiState.Loading
            val result = repository.login(request)
            result.onSuccess { user ->
                _loginState.value = UiState.Success(user)
            }.onFailure { e ->
                _loginState.value = UiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun register(request: RegisterRequest) {
        viewModelScope.launch {
            _registerState.value = UiState.Loading
            val result = repository.register(request)
            result.onSuccess { user ->
                _registerState.value = UiState.Success(user)
            }.onFailure { e ->
                _registerState.value = UiState.Error(e.message ?: "Registration failed")
            }
        }
    }
    
    fun resetStates() {
        _loginState.value = UiState.Empty
        _registerState.value = UiState.Empty
    }

    companion object {
        fun provideFactory(app: StudyAssistantApp): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(AuthRepository(NetworkModule.api, app.sessionManager)) as T
                }
            }
        }
    }
}
