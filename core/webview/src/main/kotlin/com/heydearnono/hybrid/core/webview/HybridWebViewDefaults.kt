package com.heydearnono.hybrid.core.webview

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView

/*
 * 容器的安全默认值。
 *
 * 这里的每一项都显式写出来，包括那些「反正默认就是 false」的——因为默认值随 targetSdk 变，
 * 而 targetSdk 是会被升的。写死了，升级时至少不会静默变松。
 *
 * 这个文件是本轮改动里最不可自测的部分：`android.webkit` 在 JVM 单测里是 stub，
 * `isReturnDefaultValues = true` 会让下面每一行都静静地什么都不做。所以它只允许是直线代码。
 */

@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.applyHybridDefaults() {
    settings.apply {
        // 容器的前提。关掉它 bridge 就没有对端了，所以这不是一个可配置项。
        javaScriptEnabled = true

        // 页面来自 https origin，不该有任何理由去读本地文件或 content:// provider。
        // 这两项默认值在 API 30 才变成 false，minSdk 26 必须自己写。
        allowFileAccess = false
        allowContentAccess = false

        // https 页面里的 http 子资源直接拒绝，不做 COMPATIBILITY_MODE 的降级。
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // 不给 window.open 开第二个 WebView：多窗口要配 WebChromeClient 的 onCreateWindow，
        // 那是一整套不可自测的生命周期，首批不要。
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false

        // localStorage / sessionStorage。宿主页面基本都依赖它，关掉等于容器装不了正常的页面。
        // 注意它和 bridge 的 storage.* 是两套东西：这个按 origin 隔离、随清数据消失，
        // storage.* 落在 app 私有目录里。
        domStorageEnabled = true

        // 定位要走权限申请和用户可见的授权流程，bridge 里没有这个能力，就别给 JS 留半个入口。
        setGeolocationEnabled(false)
    }
}
