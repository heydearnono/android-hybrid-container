package com.heydearnono.hybrid.core.webview

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.heydearnono.hybrid.core.bridge.BridgeSecurityConfig

/**
 * 注入到 JS 全局的对象名。
 *
 * `bridge.js` 里写着同一个字面量。两处必须一致，而编译器管不到 assets 里的 js——
 * 改这里就要同时改那边，这是本轮里唯一一处靠人保证的对应关系。
 */
const val BRIDGE_JS_OBJECT_NAME: String = "__hybridNative"

/**
 * 装 bridge。
 *
 * @return false 表示系统 WebView 不支持 `WEB_MESSAGE_LISTENER`。调用方必须让页面进错误态，
 *   **不许静默降级**到 `addJavascriptInterface`：那条老路没有 origin 作用域，
 *   等于在「能力弱一点」的名义下把访问控制整个丢掉。
 */
internal fun WebView.installBridge(
    transport: BridgeTransport,
    config: BridgeSecurityConfig,
): Boolean {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false

    // 第一道闸门：白名单外的 origin 连注入对象都看不到。
    // 第二道在 BridgePolicy 里（dispatcher 侧）。两道读的是同一份 config，
    // BridgePolicyTest 里有一个用例专门锁这件事。
    WebViewCompat.addWebMessageListener(
        this,
        BRIDGE_JS_OBJECT_NAME,
        config.allowedOriginRules,
        transport.listener,
    )
    return true
}
