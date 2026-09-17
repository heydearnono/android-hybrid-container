package net.xiaoluzhu.crab.webview

import android.webkit.WebSettings
import net.xiaoluzhu.crab.container.MixedContentPolicy
import net.xiaoluzhu.crab.container.UserAgent
import net.xiaoluzhu.crab.container.WebSettingsSpec

/**
 * 把 [WebSettingsSpec] 的取值逐字段写进 `WebSettings`。**这里没有判断，只有搬运。**
 *
 * 之所以搬运和取值分两处：JVM 单测里 `WebSettings` 是 `android.jar` 的 stub，
 * `assertTrue(settings.javaScriptEnabled)` 会得到与代码无关的「绿」。取值放在能自测的
 * `:core:container`，这几行只能靠 M3 的「加载后生效差异表」在模拟器上看。
 *
 * 少写一行的表现是某项配置静默用系统默认值——所以顺序照着 [WebSettingsSpec] 的字段顺序写，便于逐行对。
 */
internal fun WebSettings.applyCrabSpec(versionName: String) {
    javaScriptEnabled = WebSettingsSpec.JAVA_SCRIPT_ENABLED
    domStorageEnabled = WebSettingsSpec.DOM_STORAGE_ENABLED
    mixedContentMode = mixedContentModeOf(WebSettingsSpec.MIXED_CONTENT_POLICY)

    allowFileAccess = WebSettingsSpec.ALLOW_FILE_ACCESS
    allowContentAccess = WebSettingsSpec.ALLOW_CONTENT_ACCESS
    allowFileAccessFromFileURLs = WebSettingsSpec.ALLOW_FILE_ACCESS_FROM_FILE_URLS
    allowUniversalAccessFromFileURLs = WebSettingsSpec.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS

    mediaPlaybackRequiresUserGesture = WebSettingsSpec.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE

    setSupportZoom(WebSettingsSpec.SUPPORT_ZOOM)
    builtInZoomControls = WebSettingsSpec.BUILT_IN_ZOOM_CONTROLS
    displayZoomControls = WebSettingsSpec.DISPLAY_ZOOM_CONTROLS
    textZoom = WebSettingsSpec.TEXT_ZOOM

    safeBrowsingEnabled = WebSettingsSpec.SAFE_BROWSING_ENABLED

    setSupportMultipleWindows(WebSettingsSpec.SUPPORT_MULTIPLE_WINDOWS)
    javaScriptCanOpenWindowsAutomatically = WebSettingsSpec.JAVA_SCRIPT_CAN_OPEN_WINDOWS_AUTOMATICALLY
    setGeolocationEnabled(WebSettingsSpec.GEOLOCATION_ENABLED)

    // UA 追加产品段，不替换整串：读系统给的原值，拼完写回去。
    userAgentString = UserAgent.decorate(userAgentString, versionName)
}

/**
 * 枚举到框架常量的对照表。三个常量的字面值（0 / 2 / 1）刻意不进 `:core:container`——
 * 那是框架的事实，不该在纯 JVM 模块里复制一份。
 */
private fun mixedContentModeOf(policy: MixedContentPolicy): Int =
    when (policy) {
        MixedContentPolicy.ALWAYS_ALLOW -> WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        MixedContentPolicy.COMPATIBILITY_MODE -> WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        MixedContentPolicy.NEVER_ALLOW -> WebSettings.MIXED_CONTENT_NEVER_ALLOW
    }
