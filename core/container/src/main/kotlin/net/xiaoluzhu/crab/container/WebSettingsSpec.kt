package net.xiaoluzhu.crab.container

/** 混合内容策略，三个取值与 `WebSettings` 的三个常量一一对应；容器取 [NEVER_ALLOW]。 */
enum class MixedContentPolicy { ALWAYS_ALLOW, COMPATIBILITY_MODE, NEVER_ALLOW }

/**
 * 六项配置 + Android 独有开关的**取值**。取值在这里（能自测），写入 `WebSettings` 那几行在
 * `:core:webview`（只能靠模拟器观察）。
 *
 * 这个切法不是洁癖：JVM 单测里 `WebSettings` 是 `android.jar` 的 stub，断言
 * `settings.javaScriptEnabled == true` 会得到「绿」，而那个绿与代码写没写无关。所以取值必须与写入分开，
 * 单测只钉取值，写入靠 M3 的「加载后生效差异表」在真机上看。
 *
 * 每一项都写清为什么取这个值——将来有人想翻，先得回答这里的理由。
 */
object WebSettingsSpec {
    /** 探针页全靠脚本判断，且承载内容是自己打进 APK 的。 */
    const val JAVA_SCRIPT_ENABLED: Boolean = true

    /** `storage` 断言要它；容器自己不存任何东西。 */
    const val DOM_STORAGE_ENABLED: Boolean = true

    /** https 承载页里不许混进 http 子资源：混进去就意味着有请求裸奔到网上。 */
    val MIXED_CONTENT_POLICY: MixedContentPolicy = MixedContentPolicy.NEVER_ALLOW

    /** 页面一律走 `WebViewAssetLoader`，不需要 `file://`；开着等于给穿越留一条不经路由的路。 */
    const val ALLOW_FILE_ACCESS: Boolean = false

    /** `content://` 同理：容器不承载 ContentProvider 里的东西。 */
    const val ALLOW_CONTENT_ACCESS: Boolean = false

    /** 两项 file URL 的跨源访问：历史上最容易出本地文件读取漏洞的开关，一律关。 */
    const val ALLOW_FILE_ACCESS_FROM_FILE_URLS: Boolean = false

    const val ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS: Boolean = false

    /** 有声媒体必须由用户手势触发。 */
    const val MEDIA_PLAYBACK_REQUIRES_USER_GESTURE: Boolean = true

    /** 缩放三项全关：容器承载的是自己的页面，交互靠页面自己做。 */
    const val SUPPORT_ZOOM: Boolean = false

    const val BUILT_IN_ZOOM_CONTROLS: Boolean = false

    const val DISPLAY_ZOOM_CONTROLS: Boolean = false

    /** 文字缩放钉在 100：跟随系统字号会让同一份探针页在不同设备上排版不同，判据也跟着飘。 */
    const val TEXT_ZOOM: Int = 100

    /** Safe Browsing 关：它会把 URL 交给远端服务判，而承载 origin 落在 `.invalid`，判不出结果还多一次外发。 */
    const val SAFE_BROWSING_ENABLED: Boolean = false

    /**
     * 下面三项**显式打开**，为的是让回调有机会被调用，然后在回调里拒绝并留下日志。
     * 关掉它们同样「打不开新窗口/拿不到定位」，但那是静默的——与「页面写错了」分不开。
     */
    const val SUPPORT_MULTIPLE_WINDOWS: Boolean = true

    const val JAVA_SCRIPT_CAN_OPEN_WINDOWS_AUTOMATICALLY: Boolean = true

    const val GEOLOCATION_ENABLED: Boolean = true
}
