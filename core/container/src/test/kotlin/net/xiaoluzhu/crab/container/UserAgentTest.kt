package net.xiaoluzhu.crab.container

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserAgentTest {
    private val systemUa =
        "Mozilla/5.0 (Linux; Android 16; sdk_gphone64_arm64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

    @Test
    fun `追加而不替换——系统那串一个字不动`() {
        val decorated = UserAgent.decorate(systemUa, "0.1.0")

        assertTrue(decorated.startsWith(systemUa), "系统 UA 必须原样在前；替换整串会改掉内核与设备字段")
        assertEquals("$systemUa Crab/0.1.0", decorated)
    }

    @Test
    fun `产品段的形状是 产品名斜杠版本号`() {
        assertEquals("Crab/9.9.9", UserAgent.decorate("", "9.9.9"))
    }

    @Test
    fun `重复装饰不重复追加`() {
        val once = UserAgent.decorate(systemUa, "0.1.0")

        assertEquals(once, UserAgent.decorate(once, "0.1.0"))
    }

    @Test
    fun `换了版本号就会追加——旧串里没有这个 token`() {
        val withOld = UserAgent.decorate(systemUa, "0.1.0")

        assertEquals("$withOld Crab/0.2.0", UserAgent.decorate(withOld, "0.2.0"))
    }
}
