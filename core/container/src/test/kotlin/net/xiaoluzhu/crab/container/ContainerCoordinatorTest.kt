package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 手写 fake，不引 mock 框架：接口变了它会编译不过，指向准确。 */
private class RecordingActions : ContainerActions {
    val lines = mutableListOf<String>()
    val systemUrls = mutableListOf<String>()
    val states = mutableListOf<ContainerState>()
    var reloads = 0
    var recreations = 0

    override fun log(line: String) {
        lines += line
    }

    override fun openInSystem(url: String) {
        systemUrls += url
    }

    override fun reloadEntry() {
        reloads++
    }

    override fun recreateWebView() {
        recreations++
    }

    override fun onStateChanged(state: ContainerState) {
        states += state
    }
}

class ContainerCoordinatorTest {
    private val actions = RecordingActions()
    private val coordinator = ContainerCoordinator(actions)

    @Test
    fun `同 origin 放行：不拦、不打日志、不交系统`() {
        assertFalse(coordinator.onNavigationRequest(HostingOrigin.SECOND_PAGE_URL, isMainFrame = true))

        assertEquals(emptyList(), actions.lines)
        assertEquals(emptyList(), actions.systemUrls)
    }

    @Test
    fun `跨 origin 拦下并打一行`() {
        assertTrue(coordinator.onNavigationRequest(ProbeContract.TARGET_CROSS_ORIGIN, isMainFrame = true))

        assertEquals(
            listOf("CRAB-NAV nav-cross-origin ${ProbeContract.TARGET_CROSS_ORIGIN}"),
            actions.lines,
        )
        assertEquals(emptyList(), actions.systemUrls, "跨 origin 不许交给系统浏览器——那等于把页面送出去")
    }

    @Test
    fun `未知 scheme 拦下并打一行，不交系统`() {
        assertTrue(coordinator.onNavigationRequest(ProbeContract.TARGET_UNKNOWN_SCHEME, isMainFrame = true))

        assertEquals(
            listOf("CRAB-NAV nav-unknown-scheme ${ProbeContract.TARGET_UNKNOWN_SCHEME}"),
            actions.lines,
        )
        assertEquals(emptyList(), actions.systemUrls)
    }

    @Test
    fun `tel 与 mailto 打一行并交系统`() {
        assertTrue(coordinator.onNavigationRequest(ProbeContract.TARGET_TEL, isMainFrame = true))
        assertTrue(coordinator.onNavigationRequest(ProbeContract.TARGET_MAILTO, isMainFrame = true))

        assertEquals(
            listOf(
                "CRAB-NAV nav-system-scheme ${ProbeContract.TARGET_TEL}",
                "CRAB-NAV nav-system-scheme ${ProbeContract.TARGET_MAILTO}",
            ),
            actions.lines,
        )
        assertEquals(listOf(ProbeContract.TARGET_TEL, ProbeContract.TARGET_MAILTO), actions.systemUrls)
    }

    @Test
    fun `开新窗口打一行，URL 位置填发起页`() {
        coordinator.onWindowOpenRequest(HostingOrigin.ENTRY_URL)

        assertEquals(listOf("CRAB-NAV nav-blank ${HostingOrigin.ENTRY_URL}"), actions.lines)
    }

    @Test
    fun `加载成功只通知一次 Content`() {
        coordinator.onPageStarted()
        coordinator.onPageFinished()

        assertEquals(listOf<ContainerState>(ContainerState.Content), actions.states, "起始就是 Loading，别再通知一次")
        assertEquals(ContainerState.Content, coordinator.state)
    }

    @Test
    fun `主文档失败进错误态并打 CRAB-ERR`() {
        coordinator.onPageStarted()
        coordinator.onLoadError(isMainFrame = true, code = -2)

        assertEquals(listOf("CRAB-ERR load -2"), actions.lines)
        assertEquals(listOf<ContainerState>(ContainerState.Error(ErrorScene.LOAD, -2)), actions.states)
    }

    @Test
    fun `子资源失败既不进错误态也不打日志`() {
        coordinator.onPageStarted()
        coordinator.onLoadError(isMainFrame = false, code = -1)
        coordinator.onPageFinished()

        assertEquals(emptyList(), actions.lines, "一张图挂了打一行 CRAB-ERR 会让人以为整页失败了")
        assertEquals(listOf<ContainerState>(ContainerState.Content), actions.states)
    }

    @Test
    fun `失败之后到达的 onPageFinished 不许把错误态盖回 Content`() {
        coordinator.onLoadError(isMainFrame = true, code = 404)
        coordinator.onPageFinished()

        assertEquals(ContainerState.Error(ErrorScene.LOAD, 404), coordinator.state)
        assertEquals(listOf<ContainerState>(ContainerState.Error(ErrorScene.LOAD, 404)), actions.states)
    }

    @Test
    fun `主文档 SSL 错误进错误态并打 CRAB-ERR`() {
        coordinator.onSslError(primaryError = 3, isMainDocument = true)

        assertEquals(listOf("CRAB-ERR ssl 3"), actions.lines)
        assertEquals(ContainerState.Error(ErrorScene.SSL, 3), coordinator.state)
    }

    @Test
    fun `子资源 SSL 错误只留一行，不把整页变错误界面`() {
        coordinator.onPageStarted()
        coordinator.onPageFinished()

        coordinator.onSslError(primaryError = 3, isMainDocument = false)

        assertEquals(listOf("CRAB-ERR ssl 3"), actions.lines, "证据要留：这一行说明有请求漏到了网上")
        assertEquals(ContainerState.Content, coordinator.state, "一张图的证书问题不该让整页变错误界面")
    }

    @Test
    fun `渲染进程终止第一次换 WebView，回到 Loading`() {
        coordinator.onPageStarted()
        coordinator.onPageFinished()

        coordinator.onRenderProcessGone(didCrash = true)

        assertEquals(listOf("CRAB-ERR render-gone 1"), actions.lines)
        assertEquals(1, actions.recreations)
        assertEquals(ContainerState.Loading, coordinator.state)
    }

    @Test
    fun `同一次终止的重复回调不再换、不再打`() {
        coordinator.onRenderProcessGone(didCrash = true)
        coordinator.onRenderProcessGone(didCrash = true)
        coordinator.onRenderProcessGone(didCrash = true)

        assertEquals(1, actions.recreations)
        assertEquals(listOf("CRAB-ERR render-gone 1"), actions.lines, "重复的行会让人以为崩了两次")
    }

    @Test
    fun `恢复完再终止就进错误态，不再换 WebView`() {
        coordinator.onRenderProcessGone(didCrash = true)
        coordinator.onPageFinished() // 新 WebView 把页面装起来了 = 恢复结束
        actions.lines.clear()

        coordinator.onRenderProcessGone(didCrash = false)

        assertEquals(listOf("CRAB-ERR render-gone 0"), actions.lines)
        assertEquals(1, actions.recreations, "上限一次")
        assertEquals(ContainerState.Error(ErrorScene.RENDER_GONE, 0), coordinator.state)
    }

    @Test
    fun `重试回 Loading、重新加载、额度给满`() {
        coordinator.onRenderProcessGone(didCrash = true)
        coordinator.onPageFinished()
        coordinator.onRenderProcessGone(didCrash = true)
        assertTrue(coordinator.state is ContainerState.Error)

        coordinator.onRetry()

        assertEquals(ContainerState.Loading, coordinator.state)
        assertEquals(1, actions.reloads)

        // 额度给满之后又能恢复一次：人按了重试，那是人在场的决定，不是容器自己在循环。
        coordinator.onRenderProcessGone(didCrash = true)
        assertEquals(2, actions.recreations)
    }

    @Test
    fun `三种对话框各打一行`() {
        coordinator.onDialog(DialogType.ALERT)
        coordinator.onDialog(DialogType.CONFIRM)
        coordinator.onDialog(DialogType.PROMPT)

        assertEquals(listOf("CRAB-DLG alert", "CRAB-DLG confirm", "CRAB-DLG prompt"), actions.lines)
    }

    @Test
    fun `权限每一项都留一行`() {
        coordinator.onPermissionRequest(listOf("android.webkit.resource.VIDEO_CAPTURE", "geolocation"))

        assertEquals(
            listOf(
                "CRAB-PERM android.webkit.resource.VIDEO_CAPTURE",
                "CRAB-PERM geolocation",
            ),
            actions.lines,
        )
    }
}
