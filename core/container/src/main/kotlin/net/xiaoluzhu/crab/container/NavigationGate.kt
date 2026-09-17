package net.xiaoluzhu.crab.container

/** [NavigationGate.decide] 的结果。 */
sealed interface NavigationDecision {
    /** 交给 WebView 自己加载。 */
    data object Allow : NavigationDecision

    /** 拒绝：留在当前页，并按 [slug] 打一行 `CRAB-NAV`。 */
    data class Deny(
        val slug: String,
    ) : NavigationDecision

    /** 交给系统应用（拨号盘、邮件），容器自己不加载。 */
    data class HandOffToSystem(
        val slug: String,
    ) : NavigationDecision
}

/**
 * 导航闸门：**默认拒绝**，只有承载 origin 上的地址能进容器。
 *
 * 判定放在纯 JVM 模块是刻意的——`shouldOverrideUrlLoading` 那一侧断言不了任何东西，而这里判错的表现是
 * 「容器把一个陌生页面装进来了」，看不出来。
 *
 * 每条拒绝都要 [NavigationDecision.Deny.slug]：静默拒绝与「页面写错了」分不开，探针页也就判不了。
 */
object NavigationGate {
    /** `tel:` / `mailto:` 交系统，不在容器里开。取值来自 pro 的越界目标表。 */
    private val SYSTEM_SCHEMES: Set<String> = setOf("tel", "mailto")

    /**
     * 子帧里放行的惰性 scheme。
     *
     * `data:` 是 `inject-scope` 那条断言的载体——探针页拿一个 `data:` iframe 问「注入漏没漏到子帧」，
     * 把它拦掉那条断言就永远超时。`about:` 是 WebView 自己会走的（`about:blank`），拦它会打断内部流程。
     * 主帧上这两个一律不放：容器只装承载 origin 的页面。
     */
    private val INERT_SUBFRAME_SCHEMES: Set<String> = setOf("data", "blob")

    fun decide(
        url: String,
        isMainFrame: Boolean,
    ): NavigationDecision {
        if (HostingOrigin.isHostingOrigin(url)) return NavigationDecision.Allow

        val scheme = schemeOf(url)
        // 连 scheme 都取不出来（相对地址、空串）也不放行：拿不准的一律当未知。
        if (scheme == null) return NavigationDecision.Deny(SLUG_UNKNOWN_SCHEME)

        if (scheme == "about") return NavigationDecision.Allow
        if (!isMainFrame && scheme in INERT_SUBFRAME_SCHEMES) return NavigationDecision.Allow

        // 系统 scheme 只在主帧交出去：子帧里一个 `tel:` iframe 就能拉起拨号盘，那不是页面该有的权力。
        if (scheme in SYSTEM_SCHEMES) {
            return if (isMainFrame) {
                NavigationDecision.HandOffToSystem(SLUG_SYSTEM_SCHEME)
            } else {
                NavigationDecision.Deny(SLUG_UNKNOWN_SCHEME)
            }
        }

        // http/https 但不是承载 origin：这是「跨 origin」，与「未知 scheme」分开报，两条断言判的不是一回事。
        return if (scheme == "http" || scheme == "https") {
            NavigationDecision.Deny(SLUG_CROSS_ORIGIN)
        } else {
            NavigationDecision.Deny(SLUG_UNKNOWN_SCHEME)
        }
    }

    /**
     * `_blank` / `window.open` 一律拒绝。**没有参数**：开新窗口这件事本身就不允许，
     * 目标是哪儿不影响结论。
     */
    fun decideWindowOpen(): NavigationDecision.Deny = NavigationDecision.Deny(SLUG_BLANK)

    /**
     * 取 scheme。只认 `scheme:` 这一种形状，且 scheme 必须是字母开头的合法字符——
     * 否则 `foo/bar:baz` 这类相对路径会被读成 scheme。
     */
    private fun schemeOf(url: String): String? {
        val colon = url.indexOf(':')
        if (colon <= 0) return null
        for (index in 0 until colon) {
            val char = url[index]
            val legal =
                char in 'a'..'z' || char in 'A'..'Z' ||
                    (index > 0 && (char in '0'..'9' || char == '+' || char == '-' || char == '.'))
            if (!legal) return null
        }
        return url.substring(0, colon).lowercase()
    }

    const val SLUG_CROSS_ORIGIN: String = "nav-cross-origin"
    const val SLUG_BLANK: String = "nav-blank"
    const val SLUG_SYSTEM_SCHEME: String = "nav-system-scheme"
    const val SLUG_UNKNOWN_SCHEME: String = "nav-unknown-scheme"
}
