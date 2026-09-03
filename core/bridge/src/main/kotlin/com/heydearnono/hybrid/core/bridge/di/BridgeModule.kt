package com.heydearnono.hybrid.core.bridge.di

import com.heydearnono.hybrid.core.bridge.BridgeDispatcher
import com.heydearnono.hybrid.core.bridge.BridgeHandler
import com.heydearnono.hybrid.core.bridge.BridgePolicy
import com.heydearnono.hybrid.core.bridge.BridgeRegistry
import com.heydearnono.hybrid.core.bridge.BridgeSecurityConfig
import com.heydearnono.hybrid.core.bridge.defaultBridgeJson
import com.heydearnono.hybrid.core.bridge.handler.DeviceInfoHandler
import com.heydearnono.hybrid.core.bridge.handler.PageCloseHandler
import com.heydearnono.hybrid.core.bridge.handler.PageSetTitleHandler
import com.heydearnono.hybrid.core.bridge.handler.RouterHandler
import com.heydearnono.hybrid.core.bridge.handler.StorageGetHandler
import com.heydearnono.hybrid.core.bridge.handler.StorageRemoveHandler
import com.heydearnono.hybrid.core.bridge.handler.StorageSetHandler
import com.heydearnono.hybrid.core.bridge.handler.ToastHandler
import com.heydearnono.hybrid.core.bridge.port.DeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.port.KeyValueStore
import com.heydearnono.hybrid.core.bridge.port.NativeRouter
import com.heydearnono.hybrid.core.bridge.port.PageHost
import com.heydearnono.hybrid.core.bridge.port.Toaster
import org.koin.dsl.module

/**
 * 组装能力表和 dispatcher。
 *
 * 为什么是工厂而不是单例：`page.close` / `page.setTitle` 要打到**具体某个页面**，
 * 而 [PageHost] 的实现就是那个页面的 ViewModel。做成单例就必须引入一个「当前页面」的
 * 全局可变引用，那在多个 web 页共存时必然出错。
 *
 * 其余四个 port 是进程级的，所以它们在构造参数里、只解析一次。
 */
class BridgeDispatcherFactory(
    private val policy: BridgePolicy,
    private val deviceInfo: DeviceInfoProvider,
    private val toaster: Toaster,
    private val router: NativeRouter,
    private val store: KeyValueStore,
) {
    /**
     * 单独暴露出来，好让 DI 图测试能断言「注册了哪些能力」——
     * 漏接一个 handler 是最容易发生、又最难在编译期发现的错误。
     */
    fun createRegistry(pageHost: PageHost): BridgeRegistry = BridgeRegistry(handlers(pageHost))

    fun create(pageHost: PageHost): BridgeDispatcher =
        BridgeDispatcher(
            registry = createRegistry(pageHost),
            policy = policy,
            json = defaultBridgeJson(),
        )

    private fun handlers(pageHost: PageHost): List<BridgeHandler> =
        listOf(
            DeviceInfoHandler(deviceInfo),
            ToastHandler(toaster),
            PageCloseHandler(pageHost),
            PageSetTitleHandler(pageHost),
            RouterHandler(router),
            StorageGetHandler(store),
            StorageSetHandler(store),
            StorageRemoveHandler(store),
        )
}

/**
 * bridge 自己能装配的部分。port 的实现由 `:core:webview` / `:feature:web` / `:app` 提供——
 * 这个模块不知道它们是谁，只声明依赖。
 *
 * Json 刻意不进 DI 图：`:core:network` 已经注册过一个 `Json`，两个配置不同的同类型绑定
 * 会互相覆盖。bridge 的 Json 配置和网络层没有关系，直接构造。
 */
fun bridgeModule(securityConfig: BridgeSecurityConfig) =
    module {
        single { securityConfig }
        single { BridgePolicy(get()) }
        single { BridgeDispatcherFactory(get(), get(), get(), get(), get()) }
    }
