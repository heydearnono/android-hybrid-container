package com.heydearnono.hybrid.core.webview

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.heydearnono.hybrid.core.bridge.BridgeDispatcher
import com.heydearnono.hybrid.core.bridge.BridgeEventEmitter
import com.heydearnono.hybrid.core.bridge.BridgeReply
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * JS 报文的入口与出口，一个页面一个。
 *
 * 这一层只做三件事：丢掉不该处理的报文、把 origin 原样交给 [BridgeDispatcher]、
 * 把回包切回主线程。任何「怎么处理这条报文」的判断都在 `:core:bridge` 里，那边能测。
 *
 * webkit 的 `WebMessageListener` 刻意是内部实现（[listener]）而不是超类型：
 * 上层（`:feature:web`）拿着这个对象只是为了发事件，不该被迫把 androidx.webkit 拉进自己的
 * 编译类路径。
 *
 * @param scope 页面的作用域（实际是 `viewModelScope`）。页面销毁时它取消，
 *   在跑的 handler 一起取消——这也是 [BridgeDispatcher] 刻意不自己持有 scope 的原因。
 */
class BridgeTransport(
    private val dispatcher: BridgeDispatcher,
    private val scope: CoroutineScope,
) {
    /**
     * 通往当前 JS 侧的回包通道。
     *
     * 只有 JS 先 `postMessage` 过一次，native 才拿到 replyProxy——这是
     * `addWebMessageListener` 的机制，不是本类的限制。所以事件在页面「开口」之前发不出去。
     */
    @Volatile
    private var channel: BridgeReply? = null

    /** 复用一个 emitter，转发到「当下的」通道；没有通道时静默丢弃。 */
    private val emitter = BridgeEventEmitter(BridgeReply { payload -> channel?.send(payload) })

    internal val listener =
        WebViewCompat.WebMessageListener { view, message, sourceOrigin, isMainFrame, replyProxy ->
            onPostMessage(view, message, sourceOrigin, isMainFrame, replyProxy)
        }

    private fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        // iframe 不给 bridge。origin 白名单挡不住这一条：一个被授权的 origin 完全可以
        // 用 iframe 套一个同源页面，而那个页面未必是我们放进去的那份。
        if (!isMainFrame) return

        // getData() 在类型是 ArrayBuffer 时会抛（webkit 1.17.0 的 checkType）。
        // 先看类型，别让 JS 发一个 ArrayBuffer 就把 app 干掉。
        if (message.type != WebMessageCompat.TYPE_STRING) return
        val raw = message.data ?: return

        // postMessage 要求主线程（JavaScriptReplyProxy 上标了 @UiThread）。
        // View.post 从任何线程调都安全，用它换掉「靠调度器恰好回到主线程」的隐式假设。
        val reply = BridgeReply { payload -> view.post { replyProxy.postMessage(payload) } }
        channel = reply

        scope.launch { dispatcher.dispatch(origin = sourceOrigin.toString(), raw = raw, reply = reply) }
    }

    /** Native → JS 的单向事件。通道还没建立时是 no-op。 */
    fun emit(
        event: String,
        data: JsonElement? = null,
    ) {
        emitter.emit(event, data)
    }
}
