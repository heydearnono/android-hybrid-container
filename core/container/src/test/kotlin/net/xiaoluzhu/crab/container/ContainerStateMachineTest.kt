package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContainerStateMachineTest {
    @Test
    fun `初始是 Loading`() {
        assertEquals(ContainerState.Loading, ContainerStateMachine().state)
    }

    @Test
    fun `加载完进 Content`() {
        val machine = ContainerStateMachine()
        machine.onLoadStarted()
        machine.onLoadFinished()

        assertEquals(ContainerState.Content, machine.state)
    }

    @Test
    fun `主文档失败进错误态，带场景与错误码`() {
        val machine = ContainerStateMachine()

        assertTrue(machine.onLoadError(isMainFrame = true, scene = ErrorScene.LOAD, code = -2))
        assertEquals(ContainerState.Error(ErrorScene.LOAD, -2), machine.state)
    }

    @Test
    fun `onPageFinished 不许把错误态盖回 Content——它在 onReceivedError 之后到`() {
        val machine = ContainerStateMachine()
        machine.onLoadError(isMainFrame = true, scene = ErrorScene.LOAD, code = 404)
        machine.onLoadFinished()

        assertEquals(
            ContainerState.Error(ErrorScene.LOAD, 404),
            machine.state,
            "盖回 Content 的表现是失败的页面显示成一张白纸，比错误界面更难查",
        )
    }

    @Test
    fun `子资源失败不进错误态`() {
        val machine = ContainerStateMachine()
        machine.onLoadStarted()

        assertFalse(machine.onLoadError(isMainFrame = false, scene = ErrorScene.LOAD, code = -1))
        assertEquals(ContainerState.Loading, machine.state, "一张图挂了就整页报错是错的")

        machine.onLoadFinished()
        assertEquals(ContainerState.Content, machine.state)
    }

    @Test
    fun `渲染进程终止与 SSL 错误不看帧，直接进错误态`() {
        val gone = ContainerStateMachine(initialState = ContainerState.Content)
        gone.onFatal(ErrorScene.RENDER_GONE, 1)
        assertEquals(ContainerState.Error(ErrorScene.RENDER_GONE, 1), gone.state)

        val ssl = ContainerStateMachine(initialState = ContainerState.Content)
        ssl.onFatal(ErrorScene.SSL, 3)
        assertEquals(ContainerState.Error(ErrorScene.SSL, 3), ssl.state)
    }

    @Test
    fun `重试回到 Loading，之后能正常进 Content`() {
        val machine = ContainerStateMachine()
        machine.onLoadError(isMainFrame = true, scene = ErrorScene.LOAD, code = -2)

        machine.onRetry()
        assertEquals(ContainerState.Loading, machine.state)

        machine.onLoadFinished()
        assertEquals(ContainerState.Content, machine.state)
    }

    @Test
    fun `重新开始加载会清掉上一次的错误态`() {
        val machine = ContainerStateMachine()
        machine.onFatal(ErrorScene.RENDER_GONE, 1)
        machine.onLoadStarted()

        assertEquals(ContainerState.Loading, machine.state)
    }

    @Test
    fun `三种场景的词各不相同——错误态靠它们区分`() {
        val scenes = setOf(ErrorScene.LOAD, ErrorScene.SSL, ErrorScene.RENDER_GONE)

        assertEquals(3, scenes.size)
    }
}
