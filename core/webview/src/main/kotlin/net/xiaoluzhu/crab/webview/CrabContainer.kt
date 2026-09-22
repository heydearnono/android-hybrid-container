package net.xiaoluzhu.crab.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import net.xiaoluzhu.crab.container.ContainerActions
import net.xiaoluzhu.crab.container.ContainerCoordinator
import net.xiaoluzhu.crab.container.ContainerState
import net.xiaoluzhu.crab.container.CrabLog
import net.xiaoluzhu.crab.container.DocumentStartScript
import net.xiaoluzhu.crab.container.HostingOrigin

/**
 * 容器本体：造 `WebView`、写配置、装拦截与注入、加载入口页，并把 [ContainerActions] 的四件事落到
 * Android API 上。**取值与判定一条都不在这里**，全部来自 `:core:container`（那边能自测）。
 *
 * 这个类里每个函数都刻意只有几行直线代码，没有分支也没有状态——`:core:webview` 整个模块不带单测
 * （`android.webkit` 在这里是 stub，断言它只会假绿），所以「什么时候换 WebView、换几次、要不要打日志」
 * 这类判断一律在 [ContainerCoordinator] 那边。
 *
 * @param context **必须是 Activity 的 Context**：JS 对话框要往它上面弹窗，application context 弹不出来
 * @param versionName 由 `:app` 从 `BuildConfig.VERSION_NAME` 传进来——版本号只写在版本目录一处
 * @param onStateChanged 状态变了通知 `:app` 在内容与错误界面之间切
 * @param onWebViewReplaced 渲染进程终止后换了新的 `WebView`，`:app` 要把它重新挂进视图树。
 *   旧的那个已经 `destroy()` 了，继续挂着只会是一块白板
 */
class CrabContainer(
    private val context: Context,
    private val isDebugBuild: Boolean,
    private val versionName: String,
    private val onStateChanged: (ContainerState) -> Unit,
    private val onWebViewReplaced: (WebView) -> Unit,
) {
    /**
     * 注入走哪条路，**一个布尔决定，两条路不许叠加**。
     *
     * 支不支持由内核版本决定（不是系统版本），所以只能在运行期问一次，不能按 SDK level 推。
     * 两条都跑会让脚本里的 `injected` 变 2，`inject-order` 直接红。
     */
    private val documentStartScriptSupported: Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    // 一次性诊断：把上面那个布尔连内核版本一起打出来。
    //
    // 为什么非打不可：`inject-order` 绿了认不出走的是原生注入还是兜底——两条路都要求 `injected == 1`，
    // 那是设计意图。支持与否只能另打一次；而门控依赖的是 System WebView 的内核版本、不是系统版本，
    // 所以版本号也得一起打，否则换个镜像就不知道这个结果还算不算。
    //
    // 前缀刻意写成字面量，不从 CrabLog 或 ProbeContract 派生：那两处是 pro 取值表钉死的契约、有单测
    // 盯着、probe.sh 靠它们回读；这一行只是诊断，读到结果之后删掉它不该牵动契约。
    //
    // 用 // 而不是 KDoc，是因为 ktlint 的 standard:kdoc 不许 KDoc 出现在 class_initializer 上。
    init {
        val pkg = WebViewCompat.getCurrentWebViewPackage(context)
        Log.i(
            CrabLog.TAG,
            "CRAB-ENV DOCUMENT_START_SCRIPT=$documentStartScriptSupported " +
                "webview=${pkg?.packageName}/${pkg?.versionName}",
        )
    }

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

    /** 声明顺序有讲究：[webView] 的构造要把 `coordinator` 交给两个 client，它必须先存在。 */
    private val coordinator: ContainerCoordinator = ContainerCoordinator(AndroidActions())

    var webView: WebView = createWebView()
        private set

    val state: ContainerState get() = coordinator.state

    /** 加载承载目录里的入口页。地址来自 [HostingOrigin]，容器不接受外部传入的 URL。 */
    fun loadEntry() {
        webView.loadUrl(HostingOrigin.ENTRY_URL)
    }

    /** 返回键：能回就回，回不动交给宿主（`nav-back` 判的就是这两半）。 */
    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() {
        webView.goBack()
    }

    /** 错误界面上的「重试」。判定在 [ContainerCoordinator]，这里只是转一下。 */
    fun onRetry() {
        coordinator.onRetry()
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
        detachAndDestroy(webView)
    }

    /**
     * 造一个装好全套的 `WebView`。渲染进程终止后要再走一遍，所以配置、两个 client、注入**必须都在这里**
     * ——落在构造函数里的那份换过之后就没了，表现是换完的容器不注入、不拦截，看起来像页面自己坏了。
     */
    private fun createWebView(): WebView {
        val created =
            WebView(context).apply {
                settings.applyCrabSpec(versionName)
                webViewClient = CrabWebViewClient(assetLoader, coordinator)
                webChromeClient =
                    CrabWebChromeClient(
                        context = context,
                        coordinator = coordinator,
                        forwardConsoleToLogcat = isDebugBuild,
                    )
            }
        if (documentStartScriptSupported) {
            // 注入范围由 origin 规则限定；规则从 HostingOrigin 派生，与拦截、导航同一个来源。
            WebViewCompat.addDocumentStartJavaScript(
                created,
                DocumentStartScript.source,
                HostingOrigin.allowedOriginRules,
            )
        }
        return created
    }

    private fun detachAndDestroy(target: WebView) {
        (target.parent as? ViewGroup)?.removeView(target)
        target.destroy()
    }

    /** [ContainerActions] 的四件事各自只有几行。**判断一条都不在这里。** */
    private inner class AndroidActions : ContainerActions {
        override fun log(line: String) {
            Log.i(CrabLog.TAG, line)
        }

        /**
         * `tel:` / `mailto:` 交系统。模拟器上很可能没有拨号盘或邮件客户端，
         * 所以 [ActivityNotFoundException] 必须接住——不接就是容器自己崩在一次导航上。
         */
        override fun openInSystem(url: String) {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    // 从非 Activity 栈发起需要它；容器这边一律新任务，别把外部应用压进自己的栈里。
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            try {
                context.startActivity(intent)
            } catch (notFound: ActivityNotFoundException) {
                // 这行不是那四种日志之一，故意用 W 级别：它说的是「设备上没有这个应用」，
                // 不是「闸门判错了」。nav-system-scheme 那条断言本来就靠人工确认
                Log.w(CrabLog.TAG, "no activity for $url", notFound)
            }
        }

        override fun reloadEntry() {
            loadEntry()
        }

        /**
         * 渲染进程终止后旧的那个已经不能用了——在它上面 `reload()` 不会活过来，
         * 表现是永远一块白板。所以只能整个换掉，再让 `:app` 重新挂进视图树。
         */
        override fun recreateWebView() {
            val stale = webView
            webView = createWebView()
            detachAndDestroy(stale)
            onWebViewReplaced(webView)
            loadEntry()
        }

        override fun onStateChanged(state: ContainerState) {
            this@CrabContainer.onStateChanged(state)
        }
    }
}
