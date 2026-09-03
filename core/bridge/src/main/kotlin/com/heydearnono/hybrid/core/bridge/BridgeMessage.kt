package com.heydearnono.hybrid.core.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JS → Native 的请求。
 *
 * 报文 DTO 全部 `internal`：协议长什么样是 bridge 的内部实现，上层只通过
 * [BridgeDispatcher] 和 [BridgeHandler] 打交道。改协议不该波及别的模块。
 */
@Serializable
internal data class BridgeRequest(
    /** null 表示单向通知：不回包，连错误也不回。 */
    val id: String? = null,
    val method: String,
    val params: JsonObject? = null,
)

/** Native → JS 的应答。[ok] 为真时看 [data]，为假时看 [error]。 */
@Serializable
internal data class BridgeResponse(
    val id: String,
    val ok: Boolean,
    val data: JsonElement? = null,
    val error: BridgeErrorPayload? = null,
)

@Serializable
internal data class BridgeErrorPayload(
    val code: String,
    val message: String,
)

/** Native → JS 的事件。没有 id，JS 只能订阅、无法应答。 */
@Serializable
internal data class BridgeEvent(
    val event: String,
    val data: JsonElement? = null,
)
