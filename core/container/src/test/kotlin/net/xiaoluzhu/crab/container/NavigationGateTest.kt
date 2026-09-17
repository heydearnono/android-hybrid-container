package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationGateTest {
    @Test
    fun `承载 origin 上的地址放行`() {
        assertEquals(NavigationDecision.Allow, decide(HostingOrigin.ENTRY_URL))
        assertEquals(NavigationDecision.Allow, decide(HostingOrigin.SECOND_PAGE_URL))
        assertEquals(NavigationDecision.Allow, decide("${HostingOrigin.ORIGIN}/probe/index.html?x=1#y"))
    }

    @Test
    fun `看起来像子域的地址必须判成跨 origin`() {
        // 只比前缀或只比后缀的实现都会在这里放行，而放行的表现是容器把一个陌生页面装了进来。
        assertEquals(deny(NavigationGate.SLUG_CROSS_ORIGIN), decide("https://and.crab.invalid.evil.com/"))
        assertEquals(deny(NavigationGate.SLUG_CROSS_ORIGIN), decide("https://evil.and.crab.invalid/"))
        assertEquals(deny(NavigationGate.SLUG_CROSS_ORIGIN), decide("https://and.crab.invalid:8443/probe/"))
        assertEquals(deny(NavigationGate.SLUG_CROSS_ORIGIN), decide("https://user@and.crab.invalid/probe/"))
    }

    @Test
    fun `换 scheme 也不是同 origin`() {
        assertEquals(deny(NavigationGate.SLUG_CROSS_ORIGIN), decide("http://and.crab.invalid/probe/index.html"))
    }

    @Test
    fun `pro 定的跨 origin 目标判拒绝`() {
        assertEquals(deny(NavigationGate.SLUG_CROSS_ORIGIN), decide(ProbeContract.TARGET_CROSS_ORIGIN))
    }

    @Test
    fun `未知 scheme 判拒绝，不与跨 origin 混为一条`() {
        assertEquals(deny(NavigationGate.SLUG_UNKNOWN_SCHEME), decide(ProbeContract.TARGET_UNKNOWN_SCHEME))
        assertEquals(deny(NavigationGate.SLUG_UNKNOWN_SCHEME), decide("intent://scan#Intent;end"))
        assertEquals(deny(NavigationGate.SLUG_UNKNOWN_SCHEME), decide("javascript:alert(1)"))
    }

    @Test
    fun `tel 与 mailto 交系统`() {
        assertEquals(
            NavigationDecision.HandOffToSystem(NavigationGate.SLUG_SYSTEM_SCHEME),
            decide(ProbeContract.TARGET_TEL),
        )
        assertEquals(
            NavigationDecision.HandOffToSystem(NavigationGate.SLUG_SYSTEM_SCHEME),
            decide(ProbeContract.TARGET_MAILTO),
        )
    }

    @Test
    fun `子帧里的 tel 不交系统——一个 iframe 不该有拉起拨号盘的权力`() {
        assertEquals(
            deny(NavigationGate.SLUG_UNKNOWN_SCHEME),
            NavigationGate.decide(ProbeContract.TARGET_TEL, isMainFrame = false),
        )
    }

    @Test
    fun `子帧里的 data 放行——inject-scope 那条断言靠它`() {
        assertEquals(
            NavigationDecision.Allow,
            NavigationGate.decide("data:text/html,%3Cscript%3E1%3C/script%3E", isMainFrame = false),
        )
    }

    @Test
    fun `主帧上的 data 不放行`() {
        assertEquals(deny(NavigationGate.SLUG_UNKNOWN_SCHEME), decide("data:text/html,hi"))
    }

    @Test
    fun `about 两种帧都放行——WebView 自己会走`() {
        assertEquals(NavigationDecision.Allow, decide("about:blank"))
        assertEquals(NavigationDecision.Allow, NavigationGate.decide("about:blank", isMainFrame = false))
    }

    @Test
    fun `取不出 scheme 的一律当未知，不放行`() {
        assertEquals(deny(NavigationGate.SLUG_UNKNOWN_SCHEME), decide(""))
        assertEquals(deny(NavigationGate.SLUG_UNKNOWN_SCHEME), decide("/probe/index.html"))
        assertEquals(deny(NavigationGate.SLUG_UNKNOWN_SCHEME), decide("://and.crab.invalid/"))
        // scheme 位置上有非法字符：`foo/bar:baz` 不是 `foo/bar` 这个 scheme。
        assertEquals(deny(NavigationGate.SLUG_UNKNOWN_SCHEME), decide("foo/bar:baz"))
    }

    @Test
    fun `scheme 大小写不敏感`() {
        assertEquals(deny(NavigationGate.SLUG_CROSS_ORIGIN), decide("HTTPS://out.crab.invalid/"))
        assertEquals(
            NavigationDecision.HandOffToSystem(NavigationGate.SLUG_SYSTEM_SCHEME),
            decide("MAILTO:probe@crab.invalid"),
        )
    }

    @Test
    fun `开新窗口一律拒绝，slug 固定`() {
        assertEquals(deny(NavigationGate.SLUG_BLANK), NavigationGate.decideWindowOpen())
    }

    @Test
    fun `四个 slug 都在探针清单里`() {
        val slugs =
            listOf(
                NavigationGate.SLUG_CROSS_ORIGIN,
                NavigationGate.SLUG_BLANK,
                NavigationGate.SLUG_SYSTEM_SCHEME,
                NavigationGate.SLUG_UNKNOWN_SCHEME,
            )
        for (slug in slugs) {
            assertEquals(true, slug in ProbeContract.SLUGS, "闸门的 slug `$slug` 不在 ProbeContract.SLUGS 里")
        }
    }

    private fun decide(url: String): NavigationDecision = NavigationGate.decide(url, isMainFrame = true)

    private fun deny(slug: String): NavigationDecision = NavigationDecision.Deny(slug)
}
