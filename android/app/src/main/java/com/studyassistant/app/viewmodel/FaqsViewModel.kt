package com.studyassistant.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studyassistant.app.data.model.FAQItem
import com.studyassistant.app.data.repository.StatsRepository
import com.studyassistant.app.StudyAssistantApp
import com.studyassistant.app.util.NetworkModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FaqsViewModel(private val repository: StatsRepository) : ViewModel() {

    private val _faqsState = MutableStateFlow<UiState<List<FAQItem>>>(UiState.Loading)
    val faqsState: StateFlow<UiState<List<FAQItem>>> = _faqsState.asStateFlow()

    init {
        fetchFaqs()
    }

    fun fetchFaqs() {
        viewModelScope.launch {
            _faqsState.value = UiState.Loading
            val result = repository.getFaqs()
            result.onSuccess { faqs ->
                if (faqs.isEmpty()) {
                    _faqsState.value = UiState.Empty
                } else {
                    _faqsState.value = UiState.Success(faqs)
                }
            }.onFailure { e ->
                _faqsState.value = UiState.Error(e.message ?: "Failed to load FAQs")
            }
        }
    }

    companion object {
        fun provideFactory(app: StudyAssistantApp): androidx.lifecycle.ViewModelProvider.Factory {
            return object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FaqsViewModel(StatsRepository(NetworkModule.api, app.sessionManager)) as T
                }
            }
        }
    }
}
