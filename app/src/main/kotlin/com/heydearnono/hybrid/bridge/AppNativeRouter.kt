package com.heydearnono.hybrid.bridge

import com.heydearnono.hybrid.core.bridge.port.NativeRouter
import com.heydearnono.hybrid.core.common.DispatcherProvider
import kotlinx.coroutines.withContext

/**
 * `router.open` 的实现。只有 `:app` 认识导航图，所以它只能在这里。
 *
 * 白名单就是 [targets] 这张表：JS 传进来的名字必须在表里，否则一律 false。刻意**不做**
 * URL / scheme 匹配——那等于现在就定死一套统一路由协议，而这轮还没到该定的时候。
 *
 * 这个类没碰任何 Android API，所以它是可测的：路由名的匹配、未装载时的行为都有用例。
 */
internal class AppNativeRouter(
    private val targets: Map<String, String>,
    private val dispatchers: DispatcherProvider,
) : NativeRouter {
    /**
     * 由 [com.heydearnono.hybrid.navigation.BaseNavHost] 在组合时装上、离开时卸下。
     *
     * NavController 活在组合里，而这个 router 是单例——两者生命周期不同，只能靠一个可空引用
     * 对接。null 意味着当前没有导航宿主，此时跳转失败而不是排队等待：JS 拿到 false，
     * 比拿到一个永远不 resolve 的 Promise 好。
     */
    @Volatile
    var navigate: ((String) -> Unit)? = null

    override suspend fun open(route: String): Boolean {
        val target = targets[route] ?: return false
        val navigate = navigate ?: return false
        // NavController 是主线程的东西。
        withContext(dispatchers.main) { navigate(target) }
        return true
    }
}
