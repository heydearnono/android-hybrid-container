package net.xiaoluzhu.crab.webview

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import net.xiaoluzhu.crab.container.CrabLog

/**
 * M2 只干一件事：把页面的 `console.log` 转到 logcat，让 `scripts/probe.sh` 能回读探针结果。
 *
 * **这是调试管路，不是 JSBridge**：单向、只在 debug 构建里开，页面拿不到任何原生能力。release 构建下
 * 一律返回 true 把 console 消息吞掉——页面内容不该出现在用户设备的日志里。
 *
 * 对话框、权限、`onCreateWindow` 三类回调在 M4 接上。
 */
internal class CrabWebChromeClient(
    private val forwardConsoleToLogcat: Boolean,
) : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        if (forwardConsoleToLogcat) {
            Log.i(CrabLog.TAG, consoleMessage.message())
        }
        // 返回 true = 自己处理了，WebView 不再打默认那行（默认那行也会带上页面内容）。
        return true
    }
}
