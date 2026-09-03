package com.heydearnono.hybrid.feature.web

import com.heydearnono.hybrid.core.bridge.BridgePolicy
import com.heydearnono.hybrid.core.bridge.BridgeSecurityConfig
import com.heydearnono.hybrid.core.bridge.di.BridgeDispatcherFactory
import com.heydearnono.hybrid.core.bridge.port.DeviceInfo
import com.heydearnono.hybrid.core.bridge.port.DeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.port.KeyValueStore
import com.heydearnono.hybrid.core.bridge.port.NativeRouter
import com.heydearnono.hybrid.core.bridge.port.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * dispatcher 本身在 `:core:bridge` 里测过了，这里只是要一个能构造出来的实例，
 * 所以五个 port 全给空实现。
 */
private fun bridgeFactory(): BridgeDispatcherFactory =
    BridgeDispatcherFactory(
        policy = BridgePolicy(BridgeSecurityConfig(rules = emptyList())),
        deviceInfo =
            DeviceInfoProvider {
                DeviceInfo(
                    osVersion = "14",
                    sdkInt = 34,
                    manufacturer = "m",
                    model = "d",
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
    )

/**
 * WebView 在 JVM 上是 stub，所以「加载状态怎么迁移」这条规则刻意整个放在 ViewModel 里——
 * 这是本轮容器相关逻辑里唯一真能测到的部分，容器自己只负责把回调转过来。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebPageViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = WebPageViewModel(bridgeFactory())

    @Test
    fun `初始状态是 Loading`() {
        assertEquals(WebPagePhase.Loading, viewModel().state.value.phase)
    }

    @Test
    fun `加载完成后进入 Content`() {
        val viewModel = viewModel()

        viewModel.onLoadStarted()
        viewModel.onLoadFinished()

        assertEquals(WebPagePhase.Content, viewModel.state.value.phase)
    }

    @Test
    fun `主框架失败后进入 Error`() {
        val viewModel = viewModel()

        viewModel.onLoadFailed("https://appassets.androidplatform.net/assets/demo/index.html")

        assertEquals(WebPagePhase.Error(WebPageErrorReason.LOAD_FAILED), viewModel.state.value.phase)
    }

    @Test
    fun `失败之后到来的 onLoadFinished 不能把错误盖成 Content`() {
        val viewModel = viewModel()

        // 真实顺序就是这样：加载失败时 onReceivedError 先到，紧接着还会来一次 onPageFinished。
        viewModel.onLoadFailed("https://example.com")
        viewModel.onLoadFinished()

        assertEquals(WebPagePhase.Error(WebPageErrorReason.LOAD_FAILED), viewModel.state.value.phase)
    }

    @Test
    fun `bridge 不可用是终局状态，后续加载事件都覆盖不了`() {
        val viewModel = viewModel()

        viewModel.onBridgeUnavailable()
        viewModel.onLoadStarted()
        viewModel.onLoadFinished()

        assertEquals(
            WebPagePhase.Error(WebPageErrorReason.BRIDGE_UNAVAILABLE),
            viewModel.state.value.phase,
        )
    }

    @Test
    fun `WebView 报上来的标题进入 state`() {
        val viewModel = viewModel()

        viewModel.onTitleChanged("网页自己的标题")

        assertEquals("网页自己的标题", viewModel.state.value.title)
    }

    @Test
    fun `page setTitle 允许空串，用来清掉标题`() {
        val viewModel = viewModel()
        viewModel.setTitle("先设一个")

        viewModel.setTitle("")

        assertEquals("", viewModel.state.value.title)
    }

    @Test
    fun `page close 发出一次关闭请求`() =
        runTest {
            val viewModel = viewModel()

            viewModel.close()

            // 收到即通过；收不到会挂在这里由 runTest 超时报错。
            assertEquals(Unit, viewModel.closeRequests.first())
        }
}
