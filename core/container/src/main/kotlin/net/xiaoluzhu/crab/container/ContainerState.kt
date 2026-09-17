package net.xiaoluzhu.crab.container

/** 容器的三态。错误态带场景与错误码——三种失败共用一个界面，靠这两个字段才说得清是哪一种。 */
sealed interface ContainerState {
    data object Loading : ContainerState

    data object Content : ContainerState

    data class Error(
        val scene: String,
        val code: Int,
    ) : ContainerState
}

/**
 * 状态迁移的**全部规则**。放在纯 JVM 模块是刻意的：WebView 的回调顺序是这里最容易出错的地方，
 * 而在 `:core:webview` 里写这些规则等于没有测试。
 *
 * 两条讲究，都是踩过的形状：
 * 1. **`onReceivedError` 在 `onPageFinished` 之前到。** 所以 [onLoadFinished] 不许把错误态盖回 Content，
 *    否则失败的页面会显示成一张白纸，比错误界面更难查
 * 2. **子资源失败不进错误态。** `onReceivedError` 从 API 23 起对每个失败的子资源都回调一次，
 *    一张图挂了就整页报错是错的。过滤放在这里而不是回调点，为的是这条能被测到
 */
class ContainerStateMachine(
    initialState: ContainerState = ContainerState.Loading,
) {
    var state: ContainerState = initialState
        private set

    fun onLoadStarted() {
        state = ContainerState.Loading
    }

    /** 加载结束。已经进过错误态就保持错误态（见类注释第 1 条）。 */
    fun onLoadFinished() {
        if (state is ContainerState.Error) return
        state = ContainerState.Content
    }

    /**
     * @param isMainFrame 只有主文档失败才进错误态
     * @return 是否真的进了错误态（调用方据此决定要不要打 `CRAB-ERR`）
     */
    fun onLoadError(
        isMainFrame: Boolean,
        scene: String,
        code: Int,
    ): Boolean {
        if (!isMainFrame) return false
        state = ContainerState.Error(scene, code)
        return true
    }

    /** 渲染进程终止、SSL 错误这类没有「主帧」概念的失败：一律进错误态。 */
    fun onFatal(
        scene: String,
        code: Int,
    ) {
        state = ContainerState.Error(scene, code)
    }

    /** 人工按了重试。 */
    fun onRetry() {
        state = ContainerState.Loading
    }
}
