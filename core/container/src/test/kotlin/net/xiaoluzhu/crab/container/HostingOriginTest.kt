package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostingOriginTest {
    @Test
    fun `入口地址由 origin 与承载目录拼出来`() {
        assertEquals("${HostingOrigin.ORIGIN}/${HostingOrigin.HOSTING_DIR}/index.html", HostingOrigin.ENTRY_URL)
        assertEquals("${HostingOrigin.ORIGIN}/${HostingOrigin.HOSTING_DIR}/second.html", HostingOrigin.SECOND_PAGE_URL)
        assertTrue(HostingOrigin.isHostingOrigin(HostingOrigin.ENTRY_URL))
        assertTrue(HostingOrigin.isHostingOrigin(HostingOrigin.SECOND_PAGE_URL))
    }

    @Test
    fun `注入规则只有承载 origin 一条`() {
        assertEquals(setOf(HostingOrigin.ORIGIN), HostingOrigin.allowedOriginRules)
    }

    @Test
    fun `origin 本身、带路径、带 query、带 fragment 都算同 origin`() {
        assertTrue(HostingOrigin.isHostingOrigin(HostingOrigin.ORIGIN))
        assertTrue(HostingOrigin.isHostingOrigin("${HostingOrigin.ORIGIN}/"))
        assertTrue(HostingOrigin.isHostingOrigin("${HostingOrigin.ORIGIN}/probe/x?a=1"))
        assertTrue(HostingOrigin.isHostingOrigin("${HostingOrigin.ORIGIN}#f"))
    }

    @Test
    fun `scheme 与域名大小写不敏感`() {
        assertTrue(HostingOrigin.isHostingOrigin(HostingOrigin.ENTRY_URL.uppercase()))
    }

    @Test
    fun `看起来像子域的地址必须判成跨 origin`() {
        assertFalse(HostingOrigin.isHostingOrigin("https://${HostingOrigin.DOMAIN}.evil.com/"))
        assertFalse(HostingOrigin.isHostingOrigin("https://evil.com/${HostingOrigin.DOMAIN}"))
        assertFalse(HostingOrigin.isHostingOrigin("https://evil.${HostingOrigin.DOMAIN.substringAfter('.')}/"))
    }

    @Test
    fun `带端口或用户信息一律判否——宁可把自己判成越界`() {
        assertFalse(HostingOrigin.isHostingOrigin("https://${HostingOrigin.DOMAIN}:443/"))
        assertFalse(HostingOrigin.isHostingOrigin("https://evil.com@${HostingOrigin.DOMAIN}/"))
    }

    @Test
    fun `换 scheme 就不是同 origin`() {
        assertFalse(HostingOrigin.isHostingOrigin("http://${HostingOrigin.DOMAIN}/probe/index.html"))
        assertFalse(HostingOrigin.isHostingOrigin("file:///android_asset/probe/index.html"))
        assertFalse(HostingOrigin.isHostingOrigin("crabx://probe"))
        assertFalse(HostingOrigin.isHostingOrigin("mailto:probe@crab.invalid"))
    }

    @Test
    fun `畸形地址判否而不是抛`() {
        assertFalse(HostingOrigin.isHostingOrigin(""))
        assertFalse(HostingOrigin.isHostingOrigin("://x"))
        assertFalse(HostingOrigin.isHostingOrigin("https:/${HostingOrigin.DOMAIN}"))
        assertFalse(HostingOrigin.isHostingOrigin("https:///probe"))
        assertFalse(HostingOrigin.isHostingOrigin("https://${HostingOrigin.DOMAIN.dropLast(2)}/"))
    }
}
