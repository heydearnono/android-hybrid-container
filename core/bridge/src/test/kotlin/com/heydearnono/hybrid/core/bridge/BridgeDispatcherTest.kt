package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val ORIGIN = "https://appassets.androidplatform.net"

private fun wireCode(code: String) = "\"code\":\"$code\""

class BridgeDispatcherTest {
    private val echo = FakeHandler("echo") { params -> Outcome.Success(params ?: JsonPrimitive("no params")) }
    private val reply = RecordingReply()

    /** [authorized] 放的是「授权了但不一定注册了」的能力，用来把授权和注册两件事分开测。 */
    private fun dispatcher(
        handlers: List<BridgeHandler> = listOf(echo),
        authorized: Set<String> = setOf("echo", "ghost", "boom", "slow", "fast"),
    ) = BridgeDispatcher(
        registry = BridgeRegistry(handlers),
        policy = BridgePolicy(BridgeSecurityConfig(listOf(OriginRule(ORIGIN, authorized)))),
    )

    @Test
    fun `成功调用按 id 回包`() =
        runTest {
            dispatcher().dispatch(ORIGIN, """{"id":"7","method":"echo","params":{"k":"v"}}""", reply)

            assertEquals(listOf("""{"id":"7","ok":true,"data":{"k":"v"}}"""), reply.payloads)
            assertEquals(1, echo.calls)
        }

    @Test
    fun `未授权的 origin 拿不到能力是否存在的线索`() =
        runTest {
            val subject = dispatcher()

            subject.dispatch("https://evil.com", """{"id":"1","method":"echo"}""", reply)
            subject.dispatch("https://evil.com", """{"id":"2","method":"nonexistent"}""", reply)

            // 已注册的 echo 和根本不存在的 nonexistent 必须给出同一个错误码，否则未授权的
            // origin 能靠错误码差异把 native 有哪些能力枚举出来。
            assertEquals(2, reply.payloads.size)
            assertTrue(reply.payloads.all { wireCode("PERMISSION_DENIED") in it })
            assertEquals(0, echo.calls)
        }

    @Test
    fun `授权了但没注册回 METHOD_NOT_FOUND`() =
        runTest {
            dispatcher().dispatch(ORIGIN, """{"id":"1","method":"ghost"}""", reply)

            assertContains(reply.payloads.single(), wireCode("METHOD_NOT_FOUND"))
        }

    @Test
    fun `handler 抛异常被兜成 INTERNAL`() =
        runTest {
            val boom = FakeHandler("boom") { error("handler 自己炸了") }

            dispatcher(handlers = listOf(boom)).dispatch(ORIGIN, """{"id":"1","method":"boom"}""", reply)

            assertEquals(
                """{"id":"1","ok":false,"error":{"code":"INTERNAL","message":"handler 自己炸了"}}""",
                reply.payloads.single(),
            )
        }

    @Test
    fun `协程取消不会被兜成 INTERNAL`() =
        runTest {
            // 吞掉 CancellationException 会让上层的结构化并发失效，这条比错误码本身重要。
            val cancelled = FakeHandler("boom") { throw CancellationException("cancelled") }

            assertFailsWith<CancellationException> {
                dispatcher(handlers = listOf(cancelled)).dispatch(ORIGIN, """{"id":"1","method":"boom"}""", reply)
            }
            assertTrue(reply.payloads.isEmpty())
        }

    @Test
    fun `单向通知执行能力但不回包`() =
        runTest {
            dispatcher().dispatch(ORIGIN, """{"method":"echo","params":{"k":"v"}}""", reply)

            assertEquals(1, echo.calls)
            assertTrue(reply.payloads.isEmpty())
        }

    @Test
    fun `单向通知出错也不回包`() =
        runTest {
            val subject = dispatcher()

            subject.dispatch("https://evil.com", """{"method":"echo"}""", reply)
            subject.dispatch(ORIGIN, """{"method":"ghost"}""", reply)

            assertTrue(reply.payloads.isEmpty())
        }

    @Test
    fun `报文解不开但能抠出 id 时回 BAD_REQUEST`() =
        runTest {
            dispatcher().dispatch(ORIGIN, """{"id":"7"}""", reply)

            assertContains(reply.payloads.single(), """"id":"7","ok":false""")
            assertContains(reply.payloads.single(), wireCode("BAD_REQUEST"))
        }

    @Test
    fun `报文解不开且抠不出 id 时丢弃`() =
        runTest {
            dispatcher().dispatch(ORIGIN, "not json at all", reply)

            assertTrue(reply.payloads.isEmpty())
        }

    @Test
    fun `并发请求的回包不会串到别的 id 上`() =
        runTest {
            val slow =
                FakeHandler("slow") {
                    delay(100)
                    Outcome.Success(JsonPrimitive("slow"))
                }
            val fast = FakeHandler("fast") { Outcome.Success(JsonPrimitive("fast")) }
            val subject = dispatcher(handlers = listOf(slow, fast))

            coroutineScope {
                launch { subject.dispatch(ORIGIN, """{"id":"1","method":"slow"}""", reply) }
                launch { subject.dispatch(ORIGIN, """{"id":"2","method":"fast"}""", reply) }
            }

            // 快的先回，但每个回包仍然带着自己那条请求的 id。dispatcher 不持有任何跨调用状态，
            // 这条测试锁住的就是这个性质。
            assertEquals(
                listOf("""{"id":"2","ok":true,"data":"fast"}""", """{"id":"1","ok":true,"data":"slow"}"""),
                reply.payloads,
            )
        }

    @Test
    fun `事件编码后直接走回包通道`() =
        runTest {
            BridgeEventEmitter(reply).emit("page.resume")

            assertEquals(listOf("""{"event":"page.resume"}"""), reply.payloads)
        }
}
