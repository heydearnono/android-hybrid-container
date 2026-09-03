package com.heydearnono.hybrid.bridge

import com.heydearnono.hybrid.core.common.DispatcherProvider
import com.heydearnono.hybrid.navigation.NATIVE_ROUTE_TARGETS
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class TestDispatchers(
    dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

/**
 * `router.open` 的白名单查表逻辑。它是 `:app` 里少见的可测部分——因为刻意没碰 NavController，
 * 只把「跳哪儿」写成一个 lambda。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppNativeRouterTest {
    private val dispatchers = TestDispatchers(UnconfinedTestDispatcher())
    private val navigated = mutableListOf<String>()

    private fun router(targets: Map<String, String> = NATIVE_ROUTE_TARGETS) =
        AppNativeRouter(targets, dispatchers).apply { navigate = { navigated += it } }

    @Test
    fun `白名单里的每个路由名都能跳，且跳的是导航图里的路由`() =
        runTest {
            val router = router()

            // 用真表而不是造一个假表：这样表里写错一个 value，这个测试也会红。
            NATIVE_ROUTE_TARGETS.forEach { (jsName, route) ->
                assertTrue(router.open(jsName), "路由名 $jsName 应该可跳")
                assertEquals(route, navigated.last())
            }
            assertEquals(NATIVE_ROUTE_TARGETS.size, navigated.size)
        }

    @Test
    fun `白名单外的路由名返回 false 且不导航`() =
        runTest {
            assertFalse(router().open("settings"))

            assertEquals(emptyList(), navigated)
        }

    @Test
    fun `没有导航宿主时返回 false，而不是排队等待`() =
        runTest {
            val router = AppNativeRouter(NATIVE_ROUTE_TARGETS, dispatchers)

            // navigate 为 null 表示当前没有 NavController。JS 拿到 false，
            // 比拿到一个永远不 resolve 的 Promise 好。
            assertFalse(router.open(NATIVE_ROUTE_TARGETS.keys.first()))
        }
}
