package net.xiaoluzhu.crab.webview

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import net.xiaoluzhu.crab.container.DocumentStartScript
import net.xiaoluzhu.crab.container.HostingOrigin

/**
 * 容器本体：造 `WebView`、写配置、装拦截与注入、加载入口页。**取值与判定都不在这里**，
 * 全部来自 `:core:container`（那边能自测）。
 *
 * M3 的形状到「六项配置 + UA + 页面开口前注入」。导航闸门与降级（M4）往这上面加。
 *
 * @param versionName 由 `:app` 从 `BuildConfig.VERSION_NAME` 传进来——版本号只写在版本目录一处
 */
class CrabContainer(
    context: Context,
    isDebugBuild: Boolean,
    versionName: String,
) {
    /**
     * 注入走哪条路，**一个布尔决定，两条路不许叠加**。
     *
     * 支不支持由内核版本决定（不是系统版本），所以只能在运行期问一次，不能按 SDK level 推。
     * 两条都跑会让脚本里的 `injected` 变 2，`inject-order` 直接红。
     */
    private val documentStartScriptSupported: Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private val assetLoader: WebViewAssetLoader =
        WebViewAssetLoader
            .Builder()
            // 域名从 HostingOrigin 派生，不另写字面量；关掉 http 之后只有 https 能命中（配 setHttpAllowed(false)）。
            .setDomain(HostingOrigin.DOMAIN)
            .setHttpAllowed(false)
            // 只注册 `/`：承载 origin 上的任何路径都进 CrabAssetPathHandler，不给「未命中→打到网上」留口子。
            // PathMatcher 要求前缀首尾都是斜杠（javap 读过它的构造函数，不满足直接抛）。
            .addPathHandler(
                "/",
                CrabAssetPathHandler(
                    assets = context.assets,
                    inlineDocumentStartScript = !documentStartScriptSupported,
                ),
            ).build()

    val webView: WebView =
        WebView(context).apply {
            settings.applyCrabSpec(versionName)
            webViewClient = CrabWebViewClient(assetLoader)
            webChromeClient = CrabWebChromeClient(forwardConsoleToLogcat = isDebugBuild)
        }

    init {
        if (documentStartScriptSupported) {
            // 注入范围由 origin 规则限定；规则从 HostingOrigin 派生，与拦截、导航同一个来源。
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                DocumentStartScript.source,
                HostingOrigin.allowedOriginRules,
            )
        }
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
