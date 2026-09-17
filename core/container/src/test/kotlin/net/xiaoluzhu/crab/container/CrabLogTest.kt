package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 日志格式一个字都不能改：`scripts/probe.sh` 用 `grep -qF` 按这些串回读，多一个空格就回读不到，
 * 而回读不到的表现是那条断言 FAIL——指不到原因。所以这里钉的是整行的字面形状。
 */
class CrabLogTest {
    @Test
    fun `导航行的形状是 前缀 slug URL`() {
        assertEquals(
            "CRAB-NAV nav-cross-origin https://out.crab.invalid/",
            CrabLog.navigation(NavigationGate.SLUG_CROSS_ORIGIN, ProbeContract.TARGET_CROSS_ORIGIN),
        )
    }

    @Test
    fun `probe_sh 回读用的前缀带尾空格，四种日志都对得上`() {
        // probe.sh grep 的是 `CRAB-NAV nav-blank `（带尾空格），所以 slug 之后必须还有内容。
        assertEquals(
            true,
            CrabLog.navigation(NavigationGate.SLUG_BLANK, "about:blank").startsWith("CRAB-NAV nav-blank "),
        )
        assertEquals(true, CrabLog.dialog(DialogType.ALERT).startsWith("CRAB-DLG "))
        assertEquals(true, CrabLog.permission("android.webkit.resource.VIDEO_CAPTURE").startsWith("CRAB-PERM "))
        assertEquals(true, CrabLog.error(ErrorScene.LOAD, 404).startsWith("CRAB-ERR "))
    }

    @Test
    fun `对话框三种类型`() {
        assertEquals("CRAB-DLG alert", CrabLog.dialog(DialogType.ALERT))
        assertEquals("CRAB-DLG confirm", CrabLog.dialog(DialogType.CONFIRM))
        assertEquals("CRAB-DLG prompt", CrabLog.dialog(DialogType.PROMPT))
    }

    @Test
    fun `权限行原样带上权限名`() {
        assertEquals("CRAB-PERM geolocation", CrabLog.permission("geolocation"))
    }

    @Test
    fun `错误行带场景与错误码，三种场景各一行`() {
        assertEquals("CRAB-ERR load -2", CrabLog.error(ErrorScene.LOAD, -2))
        assertEquals("CRAB-ERR ssl 3", CrabLog.error(ErrorScene.SSL, 3))
        assertEquals("CRAB-ERR render-gone 1", CrabLog.error(ErrorScene.RENDER_GONE, 1))
    }
}
