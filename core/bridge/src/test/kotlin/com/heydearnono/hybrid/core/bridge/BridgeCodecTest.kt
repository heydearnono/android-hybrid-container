package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.common.AppError
import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 报文格式是对外契约，所以这里刻意断言完整的 JSON 字符串而不是逐字段比对——
 * 改动键名或键顺序都应该让测试红，然后由人决定要不要同步改 JS SDK。
 */
class BridgeCodecTest {
    private val codec = BridgeCodec(defaultBridgeJson())

    private fun decode(raw: String): BridgeRequest = (codec.decodeRequest(raw) as Outcome.Success).value

    private fun decodeErrorCode(raw: String): String =
        ((codec.decodeRequest(raw) as Outcome.Failure).error as AppError.Rejected).code

    @Test
    fun `合法请求解出 id method params`() {
        val request = decode("""{"id":"7","method":"storage.set","params":{"key":"k"}}""")

        assertEquals("7", request.id)
        assertEquals("storage.set", request.method)
        assertEquals(JsonPrimitive("k"), request.params?.get("key"))
    }

    @Test
    fun `id 省略时为 null 表示单向通知`() {
        assertNull(decode("""{"method":"ui.toast"}""").id)
    }

    @Test
    fun `JS 侧多传字段不影响解码`() {
        assertEquals("ui.toast", decode("""{"method":"ui.toast","sdkVersion":"9.9"}""").method)
    }

    @Test
    fun `缺 method 判为 BAD_REQUEST`() {
        assertEquals("BAD_REQUEST", decodeErrorCode("""{"id":"7"}"""))
    }

    @Test
    fun `非法 JSON 判为 BAD_REQUEST`() {
        assertEquals("BAD_REQUEST", decodeErrorCode("not json at all"))
    }

    @Test
    fun `peekId 能从解不开的报文里抠出 id`() {
        assertEquals("7", codec.peekId("""{"id":"7"}"""))
    }

    @Test
    fun `peekId 对非字符串 id 和非法 JSON 都返回 null`() {
        assertNull(codec.peekId("""{"id":7}"""))
        assertNull(codec.peekId("""{"id":null}"""))
        assertNull(codec.peekId("""{"id":"""))
        assertNull(codec.peekId("[1,2,3]"))
    }

    @Test
    fun `成功回包不带 error 键`() {
        val payload =
            codec.encodeResponse(
                BridgeResponse(id = "7", ok = true, data = buildJsonObject { put("v", JsonPrimitive(1)) }),
            )

        assertEquals("""{"id":"7","ok":true,"data":{"v":1}}""", payload)
    }

    @Test
    fun `失败回包带 code 与 message`() {
        val payload =
            codec.encodeResponse(
                BridgeResponse(
                    id = "7",
                    ok = false,
                    error = BridgeErrorCode.INVALID_PARAMS.asAppError("缺少 key").toErrorPayload(),
                ),
            )

        assertEquals("""{"id":"7","ok":false,"error":{"code":"INVALID_PARAMS","message":"缺少 key"}}""", payload)
    }

    @Test
    fun `事件编码没有 id`() {
        assertEquals("""{"event":"page.resume"}""", codec.encodeEvent(BridgeEvent(event = "page.resume")))
    }

    @Test
    fun `AppError 的每个分支都有对应的对外错误码`() {
        assertEquals("NETWORK", AppError.Network.toErrorPayload().code)
        assertEquals("HTTP", AppError.Http(500).toErrorPayload().code)
        assertEquals("SERIALIZATION", AppError.Serialization.toErrorPayload().code)
        assertEquals("PERMISSION_DENIED", AppError.Rejected("PERMISSION_DENIED").toErrorPayload().code)
        // detail 缺省时 message 退化成 code，不能让 JS 侧看到 "null"。
        assertEquals("PERMISSION_DENIED", AppError.Rejected("PERMISSION_DENIED").toErrorPayload().message)
    }
}
