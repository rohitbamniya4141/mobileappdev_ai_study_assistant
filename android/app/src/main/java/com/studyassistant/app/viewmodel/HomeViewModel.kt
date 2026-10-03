package com.studyassistant.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyassistant.app.data.model.StatsResponse
import com.studyassistant.app.data.repository.StatsRepository
import com.studyassistant.app.StudyAssistantApp
import com.studyassistant.app.util.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: StatsRepository) : ViewModel() {

    private val _statsState = MutableStateFlow<UiState<StatsResponse>>(UiState.Loading)
    val statsState: StateFlow<UiState<StatsResponse>> = _statsState.asStateFlow()

    init {
        fetchStats()
    }

    fun fetchStats() {
        viewModelScope.launch {
            _statsState.value = UiState.Loading
            val result = repository.getStats()
            result.onSuccess { stats ->
                _statsState.value = UiState.Success(stats)
            }.onFailure { e ->
                _statsState.value = UiState.Error(e.message ?: "Failed to load stats")
            }
        }
    }

    companion object {
        fun provideFactory(app: StudyAssistantApp): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(StatsRepository(NetworkModule.api, app.sessionManager)) as T
                }
            }
        }
    }
}
