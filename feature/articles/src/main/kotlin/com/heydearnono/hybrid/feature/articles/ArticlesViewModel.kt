package com.heydearnono.hybrid.feature.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heydearnono.hybrid.core.common.Outcome
import com.heydearnono.hybrid.core.domain.usecase.GetArticlesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArticlesViewModel(
    private val getArticles: GetArticlesUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ArticlesUiState>(ArticlesUiState.Loading)
    val state: StateFlow<ArticlesUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = ArticlesUiState.Loading
            mutableState.value =
                when (val outcome = getArticles()) {
                    is Outcome.Success -> {
                        if (outcome.value.isEmpty()) {
                            ArticlesUiState.Empty
                        } else {
                            ArticlesUiState.Content(outcome.value)
                        }
                    }

                    is Outcome.Failure -> {
                        ArticlesUiState.Error(outcome.error)
                    }
                }
        }
    }
}
