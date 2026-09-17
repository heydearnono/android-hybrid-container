package net.xiaoluzhu.crab.container

/**
 * 远程调试（`chrome://inspect`）的开关判定。
 *
 * 一个函数值得单独一个文件，是因为**这条判定错了看不出来**：
 * 打开与关掉在设备上表现完全一样（页面照跑），只有把 release 包插上电脑、在 `chrome://inspect` 里看见
 * 它才发现漏了，而那时包已经出去了。所以判据必须写在能被单测盯住的地方，`:app` 那边只允许是一句
 * 「把 [enabledFor] 的结果交给 `WebView.setWebContentsDebuggingEnabled`」。
 *
 * 取的是**构建类型**，不是常量、也不是运行期配置：
 * - 写成常量 `true` / `false`：改一次就忘一次，release 包漏开的风险全押在人记不记得上
 * - 做成运行期开关（远端配置、隐藏入口）：等于给 release 包留了一条能打开调试的路，
 *   而这条路本身就是要防的东西
 *
 * `:app` 传进来的必须是 `BuildConfig.DEBUG`（`AppDebugSwitchTest` 扫源码盯着这一句）。
 */
object RemoteDebugging {
    /**
     * @param isDebugBuild 调用方的 `BuildConfig.DEBUG`
     * @return 是否打开远程调试
     */
    fun enabledFor(isDebugBuild: Boolean): Boolean = isDebugBuild
}
