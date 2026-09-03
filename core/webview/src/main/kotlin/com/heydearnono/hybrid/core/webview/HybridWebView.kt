package com.heydearnono.hybrid.core.webview

import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.heydearnono.hybrid.core.bridge.BridgeSecurityConfig

/**
 * 容器把状态变化报给上层的唯一出口。
 *
 * 实现方是页面的 ViewModel——状态机放在那边，因为 ViewModel 能在 JVM 上测，而这一层不能。
 * 要求实现是**稳定实例**（ViewModel 天然满足）：它在 [HybridWebView] 的 factory 里被捕获，
 * 换一个实例不会被重新装上。
 */
interface HybridWebViewListener {
    fun onLoadStarted()

    fun onLoadFinished()

    /** 主框架加载失败。[url] 只用来排障，别直接显示给用户。 */
    fun onLoadFailed(url: String)

    fun onTitleChanged(title: String)

    /** 系统 WebView 不支持 `WEB_MESSAGE_LISTENER`，bridge 没装上。 */
    fun onBridgeUnavailable()
}

/**
 * 混合容器。
 *
 * **这个 Composable 在当前环境里一行都验证不了**：`android.webkit` 在 JVM 单测里是 stub，
 * `isReturnDefaultValues = true` 让每个调用静静返回默认值——写一个「测容器」的用例会绿，
 * 但那个绿是假的。所以这里只允许是直线代码，判断一律推给 [HybridWebViewListener] 的实现方。
 */
@Composable
fun HybridWebView(
    url: String,
    transport: BridgeTransport,
    securityConfig: BridgeSecurityConfig,
    listener: HybridWebViewListener,
    modifier: Modifier = Modifier,
) {
    // 记住「已经让它加载过哪个 url」。不记的话每次重组都会 loadUrl，页面永远加载不完。
    // 刻意不用 mutableStateOf：这不是 UI 状态，写它不该触发重组。
    val loaded = remember { LoadedUrl() }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                applyHybridDefaults()
                webViewClient = HybridWebViewClient(createAssetLoader(context), listener)
                webChromeClient =
                    object : WebChromeClient() {
                        override fun onReceivedTitle(
                            view: WebView?,
                            title: String?,
                        ) {
                            listener.onTitleChanged(title ?: return)
                        }
                    }
                if (!installBridge(transport, securityConfig)) listener.onBridgeUnavailable()
            }
        },
        update = { webView ->
            if (loaded.value != url) {
                loaded.value = url
                webView.loadUrl(url)
            }
        },
        // WebView 自己起线程、自己持有 native 资源，不 destroy 就是泄漏。
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}

private class LoadedUrl {
    var value: String? = null
}
