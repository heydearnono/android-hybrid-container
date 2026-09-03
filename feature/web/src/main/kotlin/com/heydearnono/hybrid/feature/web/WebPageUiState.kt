package com.heydearnono.hybrid.feature.web

/**
 * web 页的状态。
 *
 * 标题和加载阶段是两件独立的事——标题可以来自 `<title>`，也可以来自 JS 调 `page.setTitle`，
 * 两者都和「页面加载到哪一步」无关。所以不是一个大 sealed interface，而是 data class 套一个。
 */
data class WebPageUiState(
    val phase: WebPagePhase = WebPagePhase.Loading,
    val title: String = "",
)

sealed interface WebPagePhase {
    data object Loading : WebPagePhase

    data object Content : WebPagePhase

    data class Error(
        val reason: WebPageErrorReason,
    ) : WebPagePhase
}

enum class WebPageErrorReason {
    /** 主框架没加载起来。子资源失败不算。 */
    LOAD_FAILED,

    /**
     * 系统 WebView 不支持 `WEB_MESSAGE_LISTENER`，bridge 没装上。
     *
     * 这是**终局**：页面本身也许能显示，但它调不了任何原生能力，装它的容器就没有意义了。
     * 显式报错而不是让页面以为自己在一个正常容器里——静默降级会变成线上「偶发不生效」。
     */
    BRIDGE_UNAVAILABLE,
}

/** [BRIDGE_UNAVAILABLE][WebPageErrorReason.BRIDGE_UNAVAILABLE] 之后不再接受任何状态变化。 */
internal val WebPagePhase.isTerminal: Boolean
    get() = this is WebPagePhase.Error && reason == WebPageErrorReason.BRIDGE_UNAVAILABLE
