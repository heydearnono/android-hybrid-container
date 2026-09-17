package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteDebuggingTest {
    @Test
    fun `debug 构建打开`() {
        assertTrue(RemoteDebugging.enabledFor(isDebugBuild = true))
    }

    @Test
    fun `release 构建关掉`() {
        assertFalse(
            RemoteDebugging.enabledFor(isDebugBuild = false),
            "release 包能被 chrome://inspect 连上，在设备上看不出来——这条错了要等包出去才发现",
        )
    }
}
