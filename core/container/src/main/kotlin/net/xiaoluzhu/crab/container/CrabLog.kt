package net.xiaoluzhu.crab.container

/** 对话框类型，进 `CRAB-DLG` 那一行。三种取值与 `WebChromeClient` 的三个回调一一对应。 */
object DialogType {
    const val ALERT: String = "alert"
    const val CONFIRM: String = "confirm"
    const val PROMPT: String = "prompt"
}

/**
 * 错误场景，进 `CRAB-ERR` 那一行。
 *
 * 三种场景**共用一个错误态**（pro 定的），靠这个词区分是哪一种。pro 的取值表只钉了
 * `CRAB-ERR <场景> <错误码>` 的形状，没有钉场景词——这三个词是端侧自定，三端不必相同。
 */
object ErrorScene {
    /** 主文档加载失败：`onReceivedError` 或 `onReceivedHttpError`。 */
    const val LOAD: String = "load"

    /** 主文档 SSL 错误。承载 origin 是本地拦截的，正常情况下走不到这里。 */
    const val SSL: String = "ssl"

    /** 渲染进程终止。 */
    const val RENDER_GONE: String = "render-gone"
}

/**
 * 日志的唯一定义处。前缀与 slug 的拼写来自 pro 的取值表，`scripts/probe.sh` 靠它们回读，
 * 所以**一个字都不能改**。
 *
 * 格式化函数放在纯 JVM 模块、而不是在调用点直接 `Log.i("...")`，为的是这几行有单测盯着：
 * 少一个空格、多一个冒号，probe.sh 就回读不到，而表现是那条断言 FAIL——指不到原因。
 */
object CrabLog {
    /** logcat tag。probe.sh 按消息前缀过滤，不依赖 tag，但统一一个 tag 便于人工看 */
    const val TAG: String = "Crab"

    /** `CRAB-NAV <slug> <URL>`：闸门每做一次非放行判定就打一行。 */
    fun navigation(
        slug: String,
        url: String,
    ): String = "$PREFIX_NAV $slug $url"

    /** `CRAB-DLG <类型>`：三种 JS 对话框各打一行。 */
    fun dialog(type: String): String = "$PREFIX_DLG $type"

    /** `CRAB-PERM <权限>`：权限请求一律拒绝，但要留下「请求过」的证据。 */
    fun permission(permission: String): String = "$PREFIX_PERM $permission"

    /** `CRAB-ERR <场景> <错误码>`：三种场景共用一个错误态，靠场景词区分。 */
    fun error(
        scene: String,
        code: Int,
    ): String = "$PREFIX_ERR $scene $code"

    private const val PREFIX_NAV: String = "CRAB-NAV"
    private const val PREFIX_DLG: String = "CRAB-DLG"
    private const val PREFIX_PERM: String = "CRAB-PERM"
    private const val PREFIX_ERR: String = "CRAB-ERR"
}
