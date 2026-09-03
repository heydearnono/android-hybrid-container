package com.heydearnono.hybrid.core.bridge.handler

import com.heydearnono.hybrid.core.bridge.FakeDeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.FakeNativeRouter
import com.heydearnono.hybrid.core.bridge.FakePageHost
import com.heydearnono.hybrid.core.bridge.FakeToaster
import com.heydearnono.hybrid.core.bridge.json
import com.heydearnono.hybrid.core.bridge.rejectedCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun params(raw: String): JsonObject = Json.parseToJsonElement(raw) as JsonObject

/**
 * handler 层的职责就两件：校验参数、把结果编成报文。两件都是纯 JVM 的，所以能测死。
 *
 * 已知的弱点，写在这里而不是散在各处：这些用例里的报文是**手抄的常量**。
 * 改了 `bridge.js` 却忘了改协议，这组测试不会红——它只锁 native 这一个方向。
 */
class HandlerTest {
    @Test
    fun `device_info 输出固定的字段集合`() =
        runTest {
            val result = DeviceInfoHandler(FakeDeviceInfoProvider()).handle(null)

            assertEquals(
                """{"platform":"android","osVersion":"16","sdkInt":37,"manufacturer":"Google",""" +
                    """"model":"Pixel 9","appVersionName":"1.0.0","appVersionCode":1,"locale":"zh-CN"}""",
                result.json(),
            )
        }

    @Test
    fun `ui_toast 把 text 和 long 传给 port`() =
        runTest {
            val toaster = FakeToaster()

            ToastHandler(toaster).handle(params("""{"text":"存好了","long":true}"""))

            assertEquals(listOf("存好了" to true), toaster.shown)
        }

    @Test
    fun `ui_toast 的 long 缺省为 false`() =
        runTest {
            val toaster = FakeToaster()

            ToastHandler(toaster).handle(params("""{"text":"hi"}"""))

            assertEquals(listOf("hi" to false), toaster.shown)
        }

    @Test
    fun `ui_toast 缺 text 或 text 为空白时拒绝且不碰 port`() =
        runTest {
            val toaster = FakeToaster()
            val handler = ToastHandler(toaster)

            assertEquals("INVALID_PARAMS", handler.handle(null).rejectedCode())
            assertEquals("INVALID_PARAMS", handler.handle(params("""{}""")).rejectedCode())
            assertEquals("INVALID_PARAMS", handler.handle(params("""{"text":"  "}""")).rejectedCode())
            // 数字不是字符串，不做隐式转换——JS 侧传错类型应该立刻报错，而不是弹出 "42"。
            assertEquals("INVALID_PARAMS", handler.handle(params("""{"text":42}""")).rejectedCode())
            assertTrue(toaster.shown.isEmpty())
        }

    @Test
    fun `page_close 调用 port`() =
        runTest {
            val host = FakePageHost()

            PageCloseHandler(host).handle(null)

            assertTrue(host.closed)
        }

    @Test
    fun `page_setTitle 允许空串因为清空标题是正当请求`() =
        runTest {
            val host = FakePageHost()

            PageSetTitleHandler(host).handle(params("""{"title":""}"""))

            assertEquals(listOf(""), host.titles)
        }

    @Test
    fun `page_setTitle 缺 title 时拒绝`() =
        runTest {
            val host = FakePageHost()

            assertEquals("INVALID_PARAMS", PageSetTitleHandler(host).handle(params("""{}""")).rejectedCode())
            assertTrue(host.titles.isEmpty())
        }

    @Test
    fun `router_open 白名单内的路由成功`() =
        runTest {
            val router = FakeNativeRouter()

            val result = RouterHandler(router).handle(params("""{"route":"articles"}"""))

            assertEquals("null", result.json())
            assertEquals(listOf("articles"), router.opened)
        }

    @Test
    fun `router_open 不认识的路由回 NOT_FOUND`() =
        runTest {
            val result = RouterHandler(FakeNativeRouter()).handle(params("""{"route":"settings"}"""))

            assertEquals("NOT_FOUND", result.rejectedCode())
        }

    @Test
    fun `router_open 缺 route 时连 port 都不调`() =
        runTest {
            val router = FakeNativeRouter()

            assertEquals("INVALID_PARAMS", RouterHandler(router).handle(null).rejectedCode())
            assertTrue(router.opened.isEmpty())
        }

    @Test
    fun `没有返回值的能力统一回 null`() =
        runTest {
            assertEquals("null", ToastHandler(FakeToaster()).handle(params("""{"text":"hi"}""")).json())
            assertEquals("null", PageCloseHandler(FakePageHost()).handle(null).json())
        }

    @Test
    fun `page_setTitle 不会顺手把页面关掉`() =
        runTest {
            // 两个 handler 共用一个 PageHost，所以「只做自己那件事」值得锁一下。
            val host = FakePageHost()

            PageSetTitleHandler(host).handle(params("""{"title":"标题"}"""))

            assertFalse(host.closed)
        }
}
