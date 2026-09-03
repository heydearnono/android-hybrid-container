package com.heydearnono.hybrid.core.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 这组是整轮改动里最值钱的测试：容器本身在本环境验证不了，但「谁能调什么」
 * 完全是纯 JVM 的判断，可以彻底测死。
 */
class BridgePolicyTest {
    private val config =
        BridgeSecurityConfig(
            rules =
                listOf(
                    OriginRule("https://appassets.androidplatform.net", setOf("storage.get", "storage.set")),
                    OriginRule("https://a.example.com", setOf("storage.get")),
                ),
        )
    private val policy = BridgePolicy(config)

    @Test
    fun `登记过的 origin 调白名单内的能力放行`() {
        assertTrue(policy.isAllowed("https://appassets.androidplatform.net", "storage.set"))
    }

    @Test
    fun `能力白名单是逐条 origin 的`() {
        assertTrue(policy.isAllowed("https://a.example.com", "storage.get"))
        // 同一个 origin，没登记的能力照样拒绝——授权粒度到能力，不是到站点。
        assertFalse(policy.isAllowed("https://a.example.com", "storage.set"))
    }

    @Test
    fun `后缀伪造的域名不匹配`() {
        assertFalse(policy.isAllowed("https://a.example.com.evil.com", "storage.get"))
        assertFalse(policy.isAllowed("https://evil.com/?x=https://a.example.com", "storage.get"))
    }

    @Test
    fun `子域不继承父域的授权`() {
        assertFalse(policy.isAllowed("https://b.a.example.com", "storage.get"))
    }

    @Test
    fun `scheme 不同不互通`() {
        assertFalse(policy.isAllowed("http://a.example.com", "storage.get"))
    }

    @Test
    fun `端口不同不互通`() {
        assertFalse(policy.isAllowed("https://a.example.com:8443", "storage.get"))
    }

    @Test
    fun `未登记的 origin 一律拒绝`() {
        assertFalse(policy.isAllowed("https://unknown.example.com", "storage.get"))
        assertFalse(policy.isAllowed("", "storage.get"))
        assertFalse(policy.isAllowed("null", "storage.get"))
    }

    @Test
    fun `尾斜杠和大小写不影响匹配`() {
        assertTrue(policy.isAllowed("https://A.Example.com/", "storage.get"))
    }

    @Test
    fun `allowAnyOrigin 打开后全放开`() {
        val debug = BridgePolicy(config.copy(allowAnyOrigin = true))

        assertTrue(debug.isAllowed("https://evil.com", "storage.set"))
        assertTrue(debug.isAllowed("https://evil.com", "method.that.does.not.exist"))
    }

    @Test
    fun `两道闸门读的是同一份配置`() {
        // 注入层（addWebMessageListener 的 allowedOriginRules）和分发层（BridgePolicy）
        // 必须同源，否则会出现「注入进来了但分发拒绝」这种极难查的不一致。
        assertEquals(
            setOf("https://appassets.androidplatform.net", "https://a.example.com"),
            config.allowedOriginRules,
        )
        config.allowedOriginRules.forEach { origin ->
            assertTrue(
                config.rules
                    .first { it.origin == origin }
                    .methods
                    .any { policy.isAllowed(origin, it) },
            )
        }
    }

    @Test
    fun `allowAnyOrigin 打开后注入层也退化成通配`() {
        assertEquals(
            setOf(BridgeSecurityConfig.WILDCARD_ORIGIN_RULE),
            config.copy(allowAnyOrigin = true).allowedOriginRules,
        )
    }
}
