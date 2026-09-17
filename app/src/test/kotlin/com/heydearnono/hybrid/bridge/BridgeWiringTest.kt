package com.heydearnono.hybrid.bridge

import com.heydearnono.hybrid.core.bridge.BridgePolicy
import com.heydearnono.hybrid.core.bridge.CAPABILITIES_METHOD
import com.heydearnono.hybrid.core.bridge.di.BridgeDispatcherFactory
import com.heydearnono.hybrid.core.bridge.port.DeviceInfo
import com.heydearnono.hybrid.core.bridge.port.DeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.port.KeyValueStore
import com.heydearnono.hybrid.core.bridge.port.NativeRouter
import com.heydearnono.hybrid.core.bridge.port.PageHost
import com.heydearnono.hybrid.core.bridge.port.Toaster
import com.heydearnono.hybrid.core.webview.APP_ASSETS_ORIGIN
import com.heydearnono.hybrid.navigation.ACCEPTANCE_PAGE_URL
import com.heydearnono.hybrid.navigation.DEMO_PAGE_URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private object NoopPageHost : PageHost {
    override fun close() = Unit

    override fun setTitle(title: String) = Unit
}

/** 这里不关心 port 做什么，只关心「注册表里有哪些能力」，所以全给空实现。 */
private fun registryMethods(): Set<String> =
    BridgeDispatcherFactory(
        policy = BridgePolicy(appBridgeSecurityConfig()),
        deviceInfo =
            DeviceInfoProvider {
                DeviceInfo(
                    osVersion = "16",
                    sdkInt = 37,
                    manufacturer = "Google",
                    model = "Pixel 9",
                    appVersionName = "0.1.0",
                    appVersionCode = 1L,
                    locale = "zh-CN",
                )
            },
        toaster =
            object : Toaster {
                override suspend fun show(
                    text: String,
                    long: Boolean,
                ) = Unit
            },
        router = NativeRouter { false },
        store =
            object : KeyValueStore {
                override suspend fun get(key: String): String? = null

                override suspend fun set(
                    key: String,
                    value: String,
                ) = Unit

                override suspend fun remove(key: String) = Unit
            },
    ).createRegistry(NoopPageHost).methods

/**
 * 锁住白名单和实际接线之间的对应关系。
 *
 * 白名单是手写的（默认拒绝，见 [DEMO_PAGE_METHODS] 的注释），手写就会写错：
 * 名字打错一个字母，运行时表现是「这个按钮点了没反应」，而那要真机才看得出来。
 * 这个测试把「打错字」提前到 JVM 上。
 */
class BridgeWiringTest {
    @Test
    fun `白名单里的方法名都真的注册了，且没有注册了却没授权的能力`() {
        // 用 assertEquals 而不是 containsAll：两个方向都要锁。多出来的说明白名单漏加了
        // 一行——那个能力任何页面都调不到，同样是个 bug，只是表现成「功能没生效」。
        //
        // bridge.capabilities 不在 registry 里（它是 BridgeDispatcher 里的特殊方法，
        // 见 BridgeDispatcher.execute 的说明），所以从「注册表」这边排除，只在下面
        // 单独锁它自己在白名单里的那一条。
        assertEquals(registryMethods(), DEMO_PAGE_METHODS)
    }

    @Test
    fun `demo 页的 URL 落在被授权的 origin 上`() {
        // 起始页要是加载自另一个 origin，bridge 会一条不通，而错误信息只有 PERMISSION_DENIED。
        assertTrue(DEMO_PAGE_URL.startsWith("$APP_ASSETS_ORIGIN/"), "demo 页 URL: $DEMO_PAGE_URL")
    }

    @Test
    fun `验收页的 URL 落在被授权的 origin 上`() {
        assertTrue(ACCEPTANCE_PAGE_URL.startsWith("$APP_ASSETS_ORIGIN/"), "验收页 URL: $ACCEPTANCE_PAGE_URL")
    }

    @Test
    fun `分发闸门和注入闸门读的是同一份配置`() {
        val config = appBridgeSecurityConfig()
        val policy = BridgePolicy(config)

        assertEquals(setOf(APP_ASSETS_ORIGIN), config.allowedOriginRules)
        DEMO_PAGE_METHODS.forEach { method ->
            assertTrue(policy.isAllowed(APP_ASSETS_ORIGIN, method), "$method 应被授权")
        }
    }

    @Test
    fun `demo 页的 origin 已被授权调用握手能力`() {
        // bridge.capabilities 不在 DEMO_PAGE_METHODS/registry 里，容易在加白名单时漏掉，
        // 漏掉的后果是握手请求本身先被 PERMISSION_DENIED 挡掉，JS 侧连"能力清单"都拿不到。
        val policy = BridgePolicy(appBridgeSecurityConfig())

        assertTrue(policy.isAllowed(APP_ASSETS_ORIGIN, CAPABILITIES_METHOD))
    }

    @Test
    fun `别的 origin 一个能力都调不到`() {
        val policy = BridgePolicy(appBridgeSecurityConfig())

        // 后缀伪造在 :core:bridge 的 policy 测试里已覆盖，这里只确认「默认拒绝」在真配置上成立。
        assertFalse(policy.isAllowed("https://evil.example.com", "device.info"))
        assertFalse(policy.isAllowed("http://appassets.androidplatform.net", "device.info"))
    }

    @Test
    fun `release 里不许放开 origin 校验`() {
        assertFalse(appBridgeSecurityConfig().allowAnyOrigin)
    }
}

