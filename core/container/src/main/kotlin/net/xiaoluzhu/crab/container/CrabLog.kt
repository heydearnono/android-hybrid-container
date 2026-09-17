package net.xiaoluzhu.crab.container

/**
 * 日志的唯一定义处。前缀与 slug 的拼写来自 pro 的取值表，`scripts/probe.sh` 靠它们回读，
 * 所以**一个字都不能改**。
 *
 * M2 只用得到 tag：探针页的结果经 `onConsoleMessage` 转到 logcat。导航、对话框、权限、错误四种日志的
 * 格式化函数在 M4 补上。
 */
object CrabLog {
    /** logcat tag。probe.sh 按消息前缀过滤，不依赖 tag，但统一一个 tag 便于人工看 */
    const val TAG: String = "Crab"
}
