package com.example.base.feature.articles

import com.example.base.core.common.AppError
import com.example.base.core.domain.model.Article

/**
 * 列表页的三态 + 内容态。用 sealed interface 而不是「一个 data class 带 isLoading/error 字段」，
 * 是为了让「加载中却同时有错误」这种非法组合在类型层面就不存在。
 */
sealed interface ArticlesUiState {
    data object Loading : ArticlesUiState

    data object Empty : ArticlesUiState

    data class Error(
        val error: AppError,
    ) : ArticlesUiState

    data class Content(
        val articles: List<Article>,
    ) : ArticlesUiState
}
