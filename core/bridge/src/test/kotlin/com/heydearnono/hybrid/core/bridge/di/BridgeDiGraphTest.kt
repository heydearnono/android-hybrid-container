package com.heydearnono.hybrid.core.bridge.di

import com.heydearnono.hybrid.core.bridge.BridgePolicy
import com.heydearnono.hybrid.core.bridge.BridgeSecurityConfig
import com.heydearnono.hybrid.core.bridge.FakeDeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.FakeNativeRouter
import com.heydearnono.hybrid.core.bridge.FakePageHost
import com.heydearnono.hybrid.core.bridge.FakeToaster
import com.heydearnono.hybrid.core.bridge.InMemoryKeyValueStore
import com.heydearnono.hybrid.core.bridge.OriginRule
import com.heydearnono.hybrid.core.bridge.port.DeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.port.KeyValueStore
import com.heydearnono.hybrid.core.bridge.port.NativeRouter
import com.heydearnono.hybrid.core.bridge.port.Toaster
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

/**
 * 只 `modules(...)` 不构造对象是测不出漏注册的，所以这里逐个 `get<T>()`。
 *
 * port 的真实现在 `:core:webview` / `:app`，这个模块看不到也不该看到，所以用 fake 顶上——
 * 这里验的是「bridge 自己声明的依赖能不能被解析」。
 */
class BridgeDiGraphTest {
    private val config = BridgeSecurityConfig(listOf(OriginRule("https://appassets.androidplatform.net", setOf())))

    private val portFakes =
        module {
            single<DeviceInfoProvider> { FakeDeviceInfoProvider() }
            single<Toaster> { FakeToaster() }
            single<NativeRouter> { FakeNativeRouter() }
            single<KeyValueStore> { InMemoryKeyValueStore() }
        }

    @Test
    fun `bridgeModule 声明的每个绑定都能解析`() {
        val koin = koinApplication { modules(bridgeModule(config), portFakes) }.koin
        try {
            assertNotNull(koin.get<BridgeSecurityConfig>())
            assertNotNull(koin.get<BridgePolicy>())
            assertNotNull(koin.get<BridgeDispatcherFactory>())
        } finally {
            koin.close()
        }
    }

    @Test
    fun `注册的能力集合等于预期`() {
        val koin = koinApplication { modules(bridgeModule(config), portFakes) }.koin
        try {
            // 漏接一个 handler 是最容易发生、又最难在编译期发现的错误：
            // 少一个能力，编译照过、测试照绿，只有 demo 页点下去才会 METHOD_NOT_FOUND。
            assertEquals(
                setOf(
                    "device.info",
                    "ui.toast",
                    "page.close",
                    "page.setTitle",
                    "router.open",
                    "storage.get",
                    "storage.set",
                    "storage.remove",
                ),
                koin.get<BridgeDispatcherFactory>().createRegistry(FakePageHost()).methods,
            )
        } finally {
            koin.close()
        }
    }

    @Test
    fun `每个页面拿到的是各自的 dispatcher`() {
        val factory =
            BridgeDispatcherFactory(
                policy = BridgePolicy(config),
                deviceInfo = FakeDeviceInfoProvider(),
                toaster = FakeToaster(),
                router = FakeNativeRouter(),
                store = InMemoryKeyValueStore(),
            )

        // page.close 必须打到发起调用的那个页面，所以 dispatcher 不能是单例。
        val first = factory.create(FakePageHost())
        val second = factory.create(FakePageHost())

        assertNotSame(first, second)
    }
}
