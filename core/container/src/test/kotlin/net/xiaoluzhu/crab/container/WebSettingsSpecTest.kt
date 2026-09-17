package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 只钉取值。**写进 `WebSettings` 的那几行这里断言不了**——JVM 单测里它是 stub，断言只会得到假绿。
 * 「写没写进去」由 M3 的加载后生效差异表在模拟器上看。
 */
class WebSettingsSpecTest {
    @Test
    fun `脚本与 DOM storage 开`() {
        assertTrue(WebSettingsSpec.JAVA_SCRIPT_ENABLED)
        assertTrue(WebSettingsSpec.DOM_STORAGE_ENABLED)
    }

    @Test
    fun `混合内容取 NEVER_ALLOW`() {
        assertEquals(MixedContentPolicy.NEVER_ALLOW, WebSettingsSpec.MIXED_CONTENT_POLICY)
    }

    @Test
    fun `四项文件与内容访问全关`() {
        assertFalse(WebSettingsSpec.ALLOW_FILE_ACCESS)
        assertFalse(WebSettingsSpec.ALLOW_CONTENT_ACCESS)
        assertFalse(WebSettingsSpec.ALLOW_FILE_ACCESS_FROM_FILE_URLS)
        assertFalse(WebSettingsSpec.ALLOW_UNIVERSAL_ACCESS_FROM_FILE_URLS)
    }

    @Test
    fun `有声媒体要手势`() {
        assertTrue(WebSettingsSpec.MEDIA_PLAYBACK_REQUIRES_USER_GESTURE)
    }

    @Test
    fun `缩放三项关，文字缩放钉在 100`() {
        assertFalse(WebSettingsSpec.SUPPORT_ZOOM)
        assertFalse(WebSettingsSpec.BUILT_IN_ZOOM_CONTROLS)
        assertFalse(WebSettingsSpec.DISPLAY_ZOOM_CONTROLS)
        assertEquals(100, WebSettingsSpec.TEXT_ZOOM)
    }

    @Test
    fun `Safe Browsing 关`() {
        assertFalse(WebSettingsSpec.SAFE_BROWSING_ENABLED)
    }

    @Test
    fun `多窗口、脚本开窗、定位三项显式开——为的是回调能被调用后当场拒绝`() {
        assertTrue(WebSettingsSpec.SUPPORT_MULTIPLE_WINDOWS)
        assertTrue(WebSettingsSpec.JAVA_SCRIPT_CAN_OPEN_WINDOWS_AUTOMATICALLY)
        assertTrue(WebSettingsSpec.GEOLOCATION_ENABLED)
    }
}
