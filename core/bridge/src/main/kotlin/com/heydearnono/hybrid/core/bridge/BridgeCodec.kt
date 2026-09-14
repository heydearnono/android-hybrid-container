package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 报文编解码。DTO 是 internal，所以它也是 internal——协议格式不出这个模块。 */
internal class BridgeCodec(
    private val json: Json,
) {
    fun decodeRequest(raw: String): Outcome<BridgeRequest> =
        try {
            val request = json.decodeFromString<BridgeRequest>(raw)
            if (request.v != BRIDGE_PROTOCOL_VERSION) {
                Outcome.Failure(BridgeErrorCode.UNSUPPORTED_VERSION.asAppError("v=${request.v}"))
            } else {
                Outcome.Success(request)
            }
        } catch (e: IllegalArgumentException) {
            // SerializationException 是 IllegalArgumentException 的子类，
            // 非法 JSON 和「结构合法但缺 v / id / method」都落在这一条里。
            Outcome.Failure(BridgeErrorCode.BAD_REQUEST.asAppError(e.message))
        }

    /**
     * 报文解不开时也要尽量回包，否则 JS 侧那个 Promise 会永远挂着。
     *
     * 只认顶层的字符串 `id`，抠不到就返回 null，由调用方决定放弃回包。
     * 这里绝不复用 [decodeRequest]——它已经失败了，才轮到这个函数。
     */
    fun peekId(raw: String): String? =
        try {
            ((json.parseToJsonElement(raw) as? JsonObject)?.get("id") as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
        } catch (_: IllegalArgumentException) {
            null
        }

    fun encodeResponse(response: BridgeResponse): String = json.encodeToString(response)

    fun encodeEvent(event: BridgeEvent): String = json.encodeToString(event)
}
