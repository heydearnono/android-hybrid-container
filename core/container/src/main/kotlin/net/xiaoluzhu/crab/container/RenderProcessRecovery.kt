package net.xiaoluzhu.crab.container

/** [RenderProcessRecovery.onRenderProcessGone] 的结论。 */
enum class RenderProcessAction {
    /** 换一个 WebView 重新加载入口页。 */
    RECOVER,

    /** 同一次终止的重复回调：什么都不做，**也不计数**。 */
    IGNORE,

    /** 已经用掉了恢复次数：进错误态，等人按重试。 */
    GIVE_UP,
}

/**
 * 渲染进程终止后恢复几次。**上限一次**：反复自动恢复会把「页面在崩」这件事藏起来，
 * 表现是应用看着能用但一直在闪。
 *
 * 计数必须**幂等**——同一次终止可能收到多次 `onRenderProcessGone`（同一个 WebView 上的重复事件），
 * 每次都计数会让上限形同虚设。这里用「恢复完成」作为分界：还没恢复完就再来的一律 [RenderProcessAction.IGNORE]。
 *
 * 这个类不认识 WebView：它只是一个计数器，所以能自测。真正要返回 true 的那行在 `:core:webview`
 * （`onRenderProcessGone` 返回 false 会让系统杀掉整个应用进程）。
 */
class RenderProcessRecovery(
    private val limit: Int = DEFAULT_LIMIT,
) {
    private var used: Int = 0
    private var recovering: Boolean = false

    /** 已经用掉的恢复次数。 */
    val recoveries: Int get() = used

    fun onRenderProcessGone(): RenderProcessAction {
        if (recovering) return RenderProcessAction.IGNORE
        if (used >= limit) return RenderProcessAction.GIVE_UP
        used += 1
        recovering = true
        return RenderProcessAction.RECOVER
    }

    /** 新 WebView 已经把入口页重新加载起来。在这之后的终止才算下一次。 */
    fun onRecovered() {
        recovering = false
    }

    /** 人工按了重试：额度重新给满——那是人在场的决定，不是容器自己在循环。 */
    fun onManualRetry() {
        used = 0
        recovering = false
    }

    companion object {
        const val DEFAULT_LIMIT: Int = 1
    }
}
