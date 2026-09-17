package net.xiaoluzhu.crab

import android.app.Application
import android.webkit.WebView
import net.xiaoluzhu.crab.container.RemoteDebugging

/**
 * 只做一件事：按构建类型开关远程调试。
 *
 * 放在 `Application.onCreate` 而不是容器里，因为 `setWebContentsDebuggingEnabled` 是**进程级**的静态开关，
 * 跟着某个 WebView 实例走会漏掉「换过一次 WebView」之后的那个（M4 的渲染进程恢复正好会换）。
 *
 * 判据在 [RemoteDebugging.enabledFor]（纯 JVM、有单测），这里只允许是这一句直线代码——
 * `AppDebugSwitchTest` 扫源码盯着传进去的是 `BuildConfig.DEBUG`。
 */
class CrabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebView.setWebContentsDebuggingEnabled(RemoteDebugging.enabledFor(BuildConfig.DEBUG))
    }
}
