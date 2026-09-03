package com.heydearnono.hybrid.feature.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heydearnono.hybrid.core.bridge.di.BridgeDispatcherFactory
import com.heydearnono.hybrid.core.bridge.port.PageHost
import com.heydearnono.hybrid.core.webview.BridgeTransport
import com.heydearnono.hybrid.core.webview.HybridWebViewListener
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * 一个 web 页的状态机，同时是 bridge 的两个对端：
 * 容器把加载事件报给它（[HybridWebViewListener]），JS 通过 `page.*` 也操作它（[PageHost]）。
 *
 * 这个类是本轮「把判断从不可测的地方搬走」的收货点——所有状态迁移的规则都在这里，
 * 而它跑在 JVM 上，能测。容器那边只剩转发。
 */
class WebPageViewModel(
    bridge: BridgeDispatcherFactory,
) : ViewModel(),
    HybridWebViewListener,
    PageHost {
    private val mutableState = MutableStateFlow(WebPageUiState())
    val state: StateFlow<WebPageUiState> = mutableState.asStateFlow()

    /**
     * JS 请求关闭页面。
     *
     * 用 Channel 而不是塞进 [state]：这是一次性事件，进过状态就会在旋转屏幕后被重放一次，
     * 变成「页面刚打开就自己关了」。
     */
    private val closeChannel = Channel<Unit>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val closeRequests: Flow<Unit> = closeChannel.receiveAsFlow()

    /**
     * 每个页面一个 dispatcher，因为 `page.close` 必须打到发起调用的那个页面。
     * scope 用 viewModelScope：页面销毁时正在跑的 handler 一起取消。
     */
    val transport: BridgeTransport = BridgeTransport(bridge.create(this), viewModelScope)

    override fun onLoadStarted() {
        setPhase(WebPagePhase.Loading)
    }

    override fun onLoadFinished() {
        // 加载失败时 onReceivedError 和 onPageFinished 都会来，且失败先到。
        // 不挡一下的话错误态会立刻被 Content 盖掉，用户看到的是一张白页而不是错误。
        mutableState.update { if (it.phase is WebPagePhase.Error) it else it.copy(phase = WebPagePhase.Content) }
    }

    override fun onLoadFailed(url: String) {
        setPhase(WebPagePhase.Error(WebPageErrorReason.LOAD_FAILED))
    }

    override fun onTitleChanged(title: String) {
        mutableState.update { it.copy(title = title) }
    }

    override fun onBridgeUnavailable() {
        setPhase(WebPagePhase.Error(WebPageErrorReason.BRIDGE_UNAVAILABLE))
    }

    /** JS 侧 `page.close`。 */
    override fun close() {
        closeChannel.trySend(Unit)
    }

    /** JS 侧 `page.setTitle`。允许空字符串——清掉标题是个合法请求。 */
    override fun setTitle(title: String) {
        mutableState.update { it.copy(title = title) }
    }

    private fun setPhase(phase: WebPagePhase) {
        mutableState.update { if (it.phase.isTerminal) it else it.copy(phase = phase) }
    }
}
