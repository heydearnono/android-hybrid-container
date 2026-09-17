package net.xiaoluzhu.crab.webview

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import net.xiaoluzhu.crab.container.HostingOrigin

/**
 * 容器本体：造 `WebView`、装拦截、加载入口页。**取值与判定都不在这里**，
 * 全部来自 `:core:container`（那边能自测）。
 *
 * M2 的形状只到「页面能起来、拦截点接上」。六项配置与 UA（M3）、导航闸门与降级（M4）往这上面加。
 * 只借了 DOM storage 一项配置提前打开，因为 `storage` 断言要它。
 */
class CrabContainer(
    context: Context,
    isDebugBuild: Boolean,
) {
    private val assetLoader: WebViewAssetLoader =
        WebViewAssetLoader
            .Builder()
            // 域名从 HostingOrigin 派生，不另写字面量；关掉 http 之后只有 https 能命中（配 setHttpAllowed(false)）。
            .setDomain(HostingOrigin.DOMAIN)
            .setHttpAllowed(false)
            // 只注册 `/`：承载 origin 上的任何路径都进 CrabAssetPathHandler，不给「未命中→打到网上」留口子。
            // PathMatcher 要求前缀首尾都是斜杠（javap 读过它的构造函数，不满足直接抛）。
            .addPathHandler("/", CrabAssetPathHandler(context.assets))
            .build()

    val webView: WebView =
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = CrabWebViewClient(assetLoader)
            webChromeClient = CrabWebChromeClient(forwardConsoleToLogcat = isDebugBuild)
        }

    /** 加载承载目录里的入口页。地址来自 [HostingOrigin]，容器不接受外部传入的 URL。 */
    fun loadEntry() {
        webView.loadUrl(HostingOrigin.ENTRY_URL)
    }

    fun onPause() {
        webView.onPause()
        webView.pauseTimers()
    }

    fun onResume() {
        webView.resumeTimers()
        webView.onResume()
    }

    /** 先从视图树摘除再 `destroy()`：还挂在父容器上就 destroy 会崩。 */
    fun destroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }
}
