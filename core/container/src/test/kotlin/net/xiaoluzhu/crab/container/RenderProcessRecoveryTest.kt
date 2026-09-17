package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals

class RenderProcessRecoveryTest {
    @Test
    fun `第一次终止恢复一次`() {
        val recovery = RenderProcessRecovery()

        assertEquals(RenderProcessAction.RECOVER, recovery.onRenderProcessGone())
        assertEquals(1, recovery.recoveries)
    }

    @Test
    fun `同一次终止的重复回调不重复计数`() {
        val recovery = RenderProcessRecovery()
        recovery.onRenderProcessGone()

        assertEquals(RenderProcessAction.IGNORE, recovery.onRenderProcessGone())
        assertEquals(RenderProcessAction.IGNORE, recovery.onRenderProcessGone())
        assertEquals(1, recovery.recoveries, "重复事件把额度吃光的话，上限一次就形同虚设")
    }

    @Test
    fun `恢复完之后再终止就放弃——上限一次`() {
        val recovery = RenderProcessRecovery()
        recovery.onRenderProcessGone()
        recovery.onRecovered()

        assertEquals(RenderProcessAction.GIVE_UP, recovery.onRenderProcessGone())
        assertEquals(1, recovery.recoveries, "放弃不计数：额度没被用掉")
    }

    @Test
    fun `放弃之后继续来还是放弃，不会又恢复起来`() {
        val recovery = RenderProcessRecovery()
        recovery.onRenderProcessGone()
        recovery.onRecovered()
        recovery.onRenderProcessGone()

        assertEquals(RenderProcessAction.GIVE_UP, recovery.onRenderProcessGone())
    }

    @Test
    fun `人工重试把额度给满——那是人在场的决定`() {
        val recovery = RenderProcessRecovery()
        recovery.onRenderProcessGone()
        recovery.onRecovered()
        assertEquals(RenderProcessAction.GIVE_UP, recovery.onRenderProcessGone())

        recovery.onManualRetry()

        assertEquals(0, recovery.recoveries)
        assertEquals(RenderProcessAction.RECOVER, recovery.onRenderProcessGone())
    }

    @Test
    fun `上限可配，但默认是一次`() {
        assertEquals(1, RenderProcessRecovery.DEFAULT_LIMIT)

        val zero = RenderProcessRecovery(limit = 0)
        assertEquals(RenderProcessAction.GIVE_UP, zero.onRenderProcessGone())
    }
}
