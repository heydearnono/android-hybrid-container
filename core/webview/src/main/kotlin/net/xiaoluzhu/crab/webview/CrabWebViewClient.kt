package net.xiaoluzhu.crab.webview

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * 只做转发，不做判断。M2 只接拦截点，M4 再接导航闸门与三种错误回调。
 *
 * `:core:webview` 整个模块刻意不带单测（`android.webkit` 在这里是 stub，断言它只会假绿），所以这个类里
 * 不许出现分支与状态。
 */
internal class CrabWebViewClient(
    private val assetLoader: WebViewAssetLoader,
) : WebViewClient() {
    /**
     * 承载 origin 上的一切都由 [CrabAssetPathHandler] 接住，因此这里返回 null 只会发生在别的 origin 上。
     * 而别的 origin 根本进不来——导航闸门（M4）拦在前面。
     */
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
}
