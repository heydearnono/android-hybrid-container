package com.heydearnono.hybrid.core.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

/**
 * 容器的 WebViewClient。
 *
 * 只做两件事：把 [APP_ASSETS_ORIGIN] 的请求交给 asset loader、把主框架的加载结果报给上层。
 * 子资源的失败一律不报——一张图片 404 不该让整页进错误态。
 */
internal class HybridWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val listener: HybridWebViewListener,
) : WebViewClientCompat() {
    /**
     * 离线包将来挂的就是这个钩子。现在只有 assets，返回 null 表示「照常走网络」。
     */
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: android.graphics.Bitmap?,
    ) {
        listener.onLoadStarted()
    }

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        listener.onLoadFinished()
    }

    /**
     * 刻意不读 [WebResourceErrorCompat] 的 code/description：那两个 getter 各自要求一个
     * WebViewFeature，读它们就得加两层 `isFeatureSupported` 判断，而这是不可自测的代码。
     * 上层只需要知道「主框架挂了」，文案由它自己给。
     */
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceErrorCompat,
    ) {
        if (!request.isForMainFrame) return
        listener.onLoadFailed(request.url.toString())
    }

    /**
     * 走 asset loader 时最可能出现的失败就是这条：文件名写错，loader 返回 404。
     * 不报的话页面只会白屏，排障全靠猜。
     */
    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (!request.isForMainFrame) return
        listener.onLoadFailed(request.url.toString())
    }
}
