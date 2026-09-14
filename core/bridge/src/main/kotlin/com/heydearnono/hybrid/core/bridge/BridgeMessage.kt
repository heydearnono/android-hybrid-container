package com.heydearnono.hybrid.core.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** 本端支持的契约大版本（三端契约 §1）。收到别的 `v` 一律 [BridgeErrorCode.UNSUPPORTED_VERSION]。 */
internal const val BRIDGE_PROTOCOL_VERSION: Int = 1

/**
 * JS → Native 的请求。
 *
 * 报文 DTO 全部 `internal`：协议长什么样是 bridge 的内部实现，上层只通过
 * [BridgeDispatcher] 和 [BridgeHandler] 打交道。改协议不该波及别的模块。
 */
@Serializable
internal data class BridgeRequest(
    val v: Int,
    /**
     * 三端契约把 `id` 改成必填（§6.2）：省略 id 曾经等价于「单向通知」，
     * 但那会让拼错 method、少传参数、origin 没授权这些错误全部静默——
     * 真的要 fire-and-forget，JS 侧不 await 就够了，协议层不该因此放弃报错能力。
     */
    val id: String,
    val method: String,
    val params: JsonObject? = null,
)

/** Native → JS 的应答。[ok] 为真时看 [data]，为假时看 [error]。 */
@Serializable
internal data class BridgeResponse(
    val v: Int = BRIDGE_PROTOCOL_VERSION,
    val id: String,
    val ok: Boolean,
    val data: JsonElement? = null,
    val error: BridgeErrorPayload? = null,
)

@Serializable
internal data class BridgeErrorPayload(
    val code: String,
    val message: String,
    /** 结构化错误详情，可选。`message` 是排查文案、不可依赖，[details] 才是给 JS 逻辑用的。 */
    val details: JsonObject? = null,
)

/** Native → JS 的事件。没有 id，JS 只能订阅、无法应答。 */
@Serializable
internal data class BridgeEvent(
    val event: String,
    val data: JsonElement? = null,
)

