package net.xiaoluzhu.crab.probe

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 远程调试开关的接线检查。
 *
 * 这条只能扫源码：`WebView.setWebContentsDebuggingEnabled` 在 JVM 单测里是 `android.jar` 的 stub，
 * 调它只会抛「not mocked」（本仓刻意没开 `testOptions.unitTests.isReturnDefaultValues`），开了那个开关
 * 也只是让它静静返回默认值——两种情形都断言不了它的效果。而这条判定错了在设备上也看不出来：打开与关掉
 * 页面照跑，要等 release 包插上电脑才发现。所以判据是「源码里写的是 BuildConfig.DEBUG」，
 * 不是常量、不是运行期开关。
 */
class AppDebugSwitchTest {
    private val source = RepoFiles.text("app/src/main/kotlin/net/xiaoluzhu/crab/CrabApplication.kt")

    @Test
    fun `远程调试取的是 BuildConfig_DEBUG，经 RemoteDebugging 判`() {
        assertTrue(
            source.contains("RemoteDebugging.enabledFor(BuildConfig.DEBUG)"),
            "开关必须由 RemoteDebugging 判、参数必须是 BuildConfig.DEBUG：\n$source",
        )
        assertTrue(
            source.contains("WebView.setWebContentsDebuggingEnabled(RemoteDebugging.enabledFor("),
            "判出来的结果要直接交给 setWebContentsDebuggingEnabled，中间不许再有分支",
        )
    }

    @Test
    fun `开关不许写死成常量`() {
        for (literal in listOf("setWebContentsDebuggingEnabled(true)", "setWebContentsDebuggingEnabled(false)")) {
            assertTrue(
                !source.contains(literal),
                "写死成 $literal 等于把 release 包漏没漏调试押在人记不记得改回来上",
            )
        }
    }
}
