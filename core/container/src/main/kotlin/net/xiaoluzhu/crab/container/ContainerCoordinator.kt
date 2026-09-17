package net.xiaoluzhu.crab.container

/**
 * 容器要对外做的四件事。实现落在 `:core:webview` / `:app`（碰 Android API），**每个实现只允许是几行
 * 直线代码**——那边没有测试兜着。
 */
interface ContainerActions {
    /** 打一行 logcat。行的内容由 [CrabLog] 拼好，实现只管打出去。 */
    fun log(line: String)

    /** 把地址交给系统应用（拨号盘、邮件）。 */
    fun openInSystem(url: String)

    /** 重新加载入口页（人工按重试）。 */
    fun reloadEntry()

    /**
     * 换一个 WebView 再加载入口页。渲染进程终止之后旧的那个已经不能用了——
     * 在它上面 `reload()` 不会活过来。
     */
    fun recreateWebView()

    /** 状态变了。`:app` 据此在内容与错误界面之间切。 */
    fun onStateChanged(state: ContainerState)
}

/**
 * 容器的**全部判定**汇总处：导航、日志、错误态、渲染进程恢复。
 *
 * 之所以把这些从 `:core:webview` 抽到纯 JVM 模块：那边的回调顺序和返回值是最容易出错的地方
 * （`onReceivedError` 早于 `onPageFinished`、`onRenderProcessGone` 返回 false 会杀进程、
 * `JsResult` 必须回一次），而在那边写这些等于没有测试。`:core:webview` 只剩「把回调原样转进来、
 * 把返回值原样转回去」。
 */
class ContainerCoordinator(
    private val actions: ContainerActions,
    private val stateMachine: ContainerStateMachine = ContainerStateMachine(),
    private val recovery: RenderProcessRecovery = RenderProcessRecovery(),
) {
    val state: ContainerState get() = stateMachine.state

    /**
     * 导航闸门的落点。
     *
     * @return `shouldOverrideUrlLoading` 要返回的值：true = 容器拦下了这一跳
     */
    fun onNavigationRequest(
        url: String,
        isMainFrame: Boolean,
    ): Boolean =
        when (val decision = NavigationGate.decide(url, isMainFrame)) {
            NavigationDecision.Allow -> {
                false
            }

            is NavigationDecision.Deny -> {
                actions.log(CrabLog.navigation(decision.slug, url))
                true
            }

            is NavigationDecision.HandOffToSystem -> {
                actions.log(CrabLog.navigation(decision.slug, url))
                actions.openInSystem(url)
                true
            }
        }

    /**
     * `_blank` / `window.open` 一律拒绝。
     *
     * @param openerUrl 发起页的地址。`onCreateWindow` **拿不到目标地址**（它在 `resultMsg` 的 transport
     *   里，只有真的建了窗口才取得到），所以 `CRAB-NAV` 那行的 URL 位置填的是发起页
     */
    fun onWindowOpenRequest(openerUrl: String) {
        actions.log(CrabLog.navigation(NavigationGate.SLUG_BLANK, openerUrl))
    }

    fun onPageStarted() {
        transition { stateMachine.onLoadStarted() }
    }

    fun onPageFinished() {
        transition { stateMachine.onLoadFinished() }
        // 恢复后的这一次加载完成才算「恢复结束」；下一次终止才重新算额度。
        recovery.onRecovered()
    }

    /**
     * 主文档加载失败（`onReceivedError`）或 HTTP 错误（`onReceivedHttpError`）。
     *
     * 子资源失败由 [ContainerStateMachine] 过滤掉，**日志也跟着不打**——一张图挂了打一行 `CRAB-ERR`
     * 会让人以为整页失败了。
     */
    fun onLoadError(
        isMainFrame: Boolean,
        code: Int,
    ) {
        transition {
            if (stateMachine.onLoadError(isMainFrame, ErrorScene.LOAD, code)) {
                actions.log(CrabLog.error(ErrorScene.LOAD, code))
            }
        }
    }

    /**
     * SSL 错误。调用方那边**只许 `cancel()`**，一次也不许 `proceed()`。
     *
     * 主文档上的进错误态，子资源上的只留一行日志——一张图的证书问题让整页变错误界面是过度反应。
     * 承载 origin 落在 `.invalid` 下、解析就会失败，轮不到证书校验，所以**这条路没有观察面**：
     * 真收到一次说明有请求漏到了网上，那是 M2「承载 origin 上不发真实网络请求」被破的证据。
     *
     * @param isMainDocument 由调用方比 `SslError.getUrl()` 与当前页面地址得出。`onReceivedSslError`
     *   不带 `WebResourceRequest`，**拿不到主帧位**，所以这是个近似；判错的后果只是错误态多进或少进一次，
     *   而这条路本身造不出来。近似这件事记在 `TASKS.md` 里，不在这里悄悄糊掉
     */
    fun onSslError(
        primaryError: Int,
        isMainDocument: Boolean,
    ) {
        transition {
            actions.log(CrabLog.error(ErrorScene.SSL, primaryError))
            if (isMainDocument) stateMachine.onFatal(ErrorScene.SSL, primaryError)
        }
    }

    /**
     * 渲染进程终止。调用方**必须返回 true**（返回 false 系统会杀掉整个应用进程），
     * 所以这个函数没有返回值——没有「不接管」这个选项。
     *
     * @param didCrash 进 `CRAB-ERR` 那行的错误码位置：1 = 崩溃，0 = 被系统回收。
     *   这是这个回调唯一给出的数字
     */
    fun onRenderProcessGone(didCrash: Boolean) {
        val code = if (didCrash) 1 else 0
        when (recovery.onRenderProcessGone()) {
            // 同一次终止的重复回调：不计数，也不再打日志——重复的行会让人以为崩了两次。
            RenderProcessAction.IGNORE -> {
                Unit
            }

            RenderProcessAction.RECOVER -> {
                transition {
                    actions.log(CrabLog.error(ErrorScene.RENDER_GONE, code))
                    stateMachine.onLoadStarted()
                    actions.recreateWebView()
                }
            }

            RenderProcessAction.GIVE_UP -> {
                transition {
                    actions.log(CrabLog.error(ErrorScene.RENDER_GONE, code))
                    stateMachine.onFatal(ErrorScene.RENDER_GONE, code)
                }
            }
        }
    }

    /** 三种 JS 对话框各打一行。弹框与「在 `JsResult` 上回一次」在调用方。 */
    fun onDialog(type: String) {
        actions.log(CrabLog.dialog(type))
    }

    /**
     * 权限请求一律拒绝，但每一项都要留下「请求过」的证据。
     * 拒绝动作（`PermissionRequest.deny()` / `Callback.invoke(origin, false, false)`）在调用方。
     */
    fun onPermissionRequest(permissions: List<String>) {
        for (permission in permissions) {
            actions.log(CrabLog.permission(permission))
        }
    }

    /** 人工按了重试：额度给满、回 Loading、重新加载入口页。 */
    fun onRetry() {
        recovery.onManualRetry()
        transition { stateMachine.onRetry() }
        actions.reloadEntry()
    }

    /** 状态真变了才通知。重复通知会让 `:app` 白跑重组，也会掩盖「其实没变」这件事。 */
    private inline fun transition(block: () -> Unit) {
        val before = stateMachine.state
        block()
        if (stateMachine.state != before) actions.onStateChanged(stateMachine.state)
    }
}
