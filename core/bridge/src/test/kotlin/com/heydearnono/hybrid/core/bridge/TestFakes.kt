package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 手写 fake（ADR-0004）：记调用次数和最后一次参数，就够断言「handler 有没有被调到」。
 * 换成 mock 框架反而看不出接口变化。
 */
internal class FakeHandler(
    override val method: String,
    private val respond: suspend (JsonObject?) -> Outcome<JsonElement> = { Outcome.Success(JsonPrimitive("ok")) },
) : BridgeHandler {
    var calls: Int = 0
        private set

    var lastParams: JsonObject? = null
        private set

    override suspend fun handle(params: JsonObject?): Outcome<JsonElement> {
        calls++
        lastParams = params
        return respond(params)
    }
}

/** 把回包攒下来。runTest 是单线程的，普通 list 够用。 */
internal class RecordingReply : BridgeReply {
    val payloads: MutableList<String> = mutableListOf()

    override fun send(payload: String) {
        payloads += payload
    }
}
