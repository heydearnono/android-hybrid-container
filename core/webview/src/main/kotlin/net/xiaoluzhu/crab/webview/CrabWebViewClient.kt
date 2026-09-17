package net.xiaoluzhu.crab.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import net.xiaoluzhu.crab.container.ContainerCoordinator

/**
 * 只做转发，不做判断。判定全在 [ContainerCoordinator]（纯 JVM，有单测）。
 *
 * `:core:webview` 整个模块刻意不带单测（`android.webkit` 在这里是 stub，断言它只会假绿），所以这个类里
 * 不许出现分支与状态。三个返回值上的讲究写在各自的注释里——它们是这个文件里唯一「写错也不报错」的地方。
 */
internal class CrabWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val coordinator: ContainerCoordinator,
) : WebViewClient() {
    /**
     * 承载 origin 上的一切都由 [CrabAssetPathHandler] 接住，因此这里返回 null 只会发生在别的 origin 上。
     * 而别的 origin 根本进不来——导航闸门拦在前面。
     */
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

    /** 返回 true = 容器接管（这一跳不加载）。 */
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = coordinator.onNavigationRequest(request.url.toString(), request.isForMainFrame)

    override fun onPageStarted(
        view: WebView,
        url: String,
        favicon: Bitmap?,
    ) {
        coordinator.onPageStarted()
    }

    override fun onPageFinished(
        view: WebView,
        url: String,
    ) {
        coordinator.onPageFinished()
    }

    /** 从 API 23 起每个失败的子资源都会回调一次，所以主帧与否必须一路带过去。 */
    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        coordinator.onLoadError(request.isForMainFrame, error.errorCode)
    }

    /** 主文档 404（故意删掉入口文件那一遍）走的是这条，不是 [onReceivedError]。 */
    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        coordinator.onLoadError(request.isForMainFrame, errorResponse.statusCode)
    }

    /**
     * **只 `cancel()`，一次也不许 `proceed()`。** 放过去等于容器自己给了一个不可信证书通行证，
     * 而那种放行在现场看不出来。
     *
     * 这个回调**不带 `WebResourceRequest`**，拿不到主帧位，所以「是不是主文档」只能拿出错的地址与
     * 当前页面地址比一下。判据在 [ContainerCoordinator]，这里只负责比出那个布尔。
     */
    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        coordinator.onSslError(error.primaryError, isMainDocument = error.url == view.url)
        handler.cancel()
    }

    /**
     * **必须返回 true。** 返回 false 系统会杀掉整个应用进程——表现是应用无声消失，
     * 与「崩了」分不开。
     */
    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        coordinator.onRenderProcessGone(detail.didCrash())
        return true
    }
}
