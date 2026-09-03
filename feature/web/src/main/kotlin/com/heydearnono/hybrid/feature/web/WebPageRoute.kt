package com.heydearnono.hybrid.feature.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.heydearnono.hybrid.core.bridge.BridgeSecurityConfig
import com.heydearnono.hybrid.core.webview.HybridWebView
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/*
 * Native → JS 的事件名。`demo/index.html` 里订阅的是同样的字符串。
 */
internal const val EVENT_PAGE_RESUME = "page.resume"
internal const val EVENT_PAGE_PAUSE = "page.pause"

/**
 * 导航入口。
 *
 * `url` 只穿到 [HybridWebView]，不进 ViewModel——ViewModel 不需要知道自己装的是哪个页面，
 * 少一个字段就少一处「重进页面时 url 变了但状态没变」的坑。
 */
@Composable
fun WebPageRoute(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebPageViewModel = koinViewModel(),
    securityConfig: BridgeSecurityConfig = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // JS 侧 page.close。一次性事件，所以是 collect 而不是读状态。
    LaunchedEffect(viewModel, onClose) {
        viewModel.closeRequests.collectLatest { onClose() }
    }

    // 事件通道唯一的真实生产者。注意 native 只有在 JS 先 postMessage 过之后才拿到回包通道，
    // 所以页面开口之前的 page.resume 会被丢弃——这是 addWebMessageListener 的机制，不是 bug。
    LifecycleResumeEffect(viewModel) {
        viewModel.transport.emit(EVENT_PAGE_RESUME)
        onPauseOrDispose { viewModel.transport.emit(EVENT_PAGE_PAUSE) }
    }

    WebPageScreen(
        url = url,
        state = state,
        viewModel = viewModel,
        securityConfig = securityConfig,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebPageScreen(
    url: String,
    state: WebPageUiState,
    viewModel: WebPageViewModel,
    securityConfig: BridgeSecurityConfig,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = state.title.ifBlank { stringResource(R.string.web_title_fallback) })
                },
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            // WebView 始终挂着，不随 phase 增删：它一被移除就 destroy，重建等于重新加载。
            // 加载中和错误时用一层覆盖物盖住它，而不是把它换掉。
            HybridWebView(
                url = url,
                transport = viewModel.transport,
                securityConfig = securityConfig,
                listener = viewModel,
                modifier = Modifier.fillMaxSize(),
            )

            when (val phase = state.phase) {
                WebPagePhase.Loading -> CircularProgressIndicator()
                WebPagePhase.Content -> Unit
                is WebPagePhase.Error -> ErrorOverlay(phase.reason)
            }
        }
    }
}

@Composable
private fun ErrorOverlay(reason: WebPageErrorReason) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(reason.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun WebPageErrorReason.messageRes(): Int =
    when (this) {
        WebPageErrorReason.LOAD_FAILED -> R.string.web_error_load_failed
        WebPageErrorReason.BRIDGE_UNAVAILABLE -> R.string.web_error_bridge_unavailable
    }
