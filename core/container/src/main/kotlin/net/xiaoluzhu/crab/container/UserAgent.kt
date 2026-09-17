package net.xiaoluzhu.crab.container

/**
 * UA 只**追加**产品段，不替换整串。
 *
 * 替换掉系统 UA 会连带改掉内核版本、设备信息那些字段，页面的特性判断会跟着错，而错法不好复现。
 * 追加的形状取自 pro 的取值表：`<系统 UA> Crab/<versionName>`。
 */
object UserAgent {
    /** 产品名取自 pro 的取值表（与 `values/strings.xml` 里的显示名同一个词）。 */
    const val PRODUCT: String = "Crab"

    /**
     * @param systemUa `WebSettings.getUserAgentString()` 的原值
     * @param versionName 由 `:app` 从 `BuildConfig.VERSION_NAME` 传进来——版本号只在版本目录里写一处
     */
    fun decorate(
        systemUa: String,
        versionName: String,
    ): String {
        val token = "$PRODUCT/$versionName"
        // 已经带了就不再追加：容器只在启动时拼一次，但重复拼出来的 UA 很难在现场看出问题。
        if (systemUa.contains(token)) return systemUa
        return if (systemUa.isEmpty()) token else "$systemUa $token"
    }
}
