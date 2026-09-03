package com.heydearnono.hybrid.feature.articles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heydearnono.hybrid.core.common.AppError
import com.heydearnono.hybrid.core.domain.model.Article
import org.koin.androidx.compose.koinViewModel

/** 导航入口。ViewModel 只在这里获取，下面的 Composable 全部只吃状态，方便脱离 DI 预览与测试。 */
@Composable
fun ArticlesRoute(
    modifier: Modifier = Modifier,
    viewModel: ArticlesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArticlesScreen(
        state = state,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArticlesScreen(
    state: ArticlesUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.articles_title)) }) },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                ArticlesUiState.Loading -> CircularProgressIndicator()
                ArticlesUiState.Empty -> Text(stringResource(R.string.articles_empty))
                is ArticlesUiState.Error -> ErrorContent(error = state.error, onRetry = onRetry)
                is ArticlesUiState.Content -> ArticleList(articles = state.articles)
            }
        }
    }
}

@Composable
private fun ArticleList(articles: List<Article>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = articles, key = { it.id }) { article ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(text = article.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Text(text = article.author, style = MaterialTheme.typography.labelSmall)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ErrorContent(
    error: AppError,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        Text(text = error.displayMessage())
        Button(onClick = onRetry) { Text(stringResource(R.string.articles_retry)) }
    }
}

@Composable
private fun AppError.displayMessage(): String =
    when (this) {
        AppError.Network -> stringResource(R.string.articles_error_network)

        AppError.Serialization -> stringResource(R.string.articles_error_serialization)

        is AppError.Http -> stringResource(R.string.articles_error_http, code)

        // 这个页面产生不了 Rejected（它只是拉列表），但 when 必须穷举。
        // 给一句通用文案，而不是把 bridge 的错误码泄露到用户界面上。
        is AppError.Rejected -> stringResource(R.string.articles_error_unexpected)
    }
