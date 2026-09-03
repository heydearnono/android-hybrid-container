package com.heydearnono.hybrid.core.bridge.handler

import com.heydearnono.hybrid.core.bridge.BridgeHandler
import com.heydearnono.hybrid.core.bridge.invalidParams
import com.heydearnono.hybrid.core.bridge.port.KeyValueStore
import com.heydearnono.hybrid.core.bridge.requiredText
import com.heydearnono.hybrid.core.bridge.stringOrNull
import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MISSING_KEY = "storage 能力需要非空的 key"

class StorageGetHandler(
    private val store: KeyValueStore,
) : BridgeHandler {
    override val method: String = "storage.get"

    override suspend fun handle(params: JsonObject?): Outcome<JsonElement> {
        val key = params.requiredText("key") ?: return invalidParams(MISSING_KEY)
        // 读不到不算错误：localStorage 也是返回 null 而不是抛异常，JS 侧不该为一次
        // 正常的 miss 去写 try/catch。
        return Outcome.Success(buildJsonObject { put("value", store.get(key)) })
    }
}

class StorageSetHandler(
    private val store: KeyValueStore,
) : BridgeHandler {
    override val method: String = "storage.set"

    override suspend fun handle(params: JsonObject?): Outcome<JsonElement> {
        val key = params.requiredText("key") ?: return invalidParams(MISSING_KEY)
        // value 允许是空串，但不允许缺失——缺失更可能是调用方写错了字段名，
        // 静默存个空串会让这种错误很难查。
        val value = params.stringOrNull("value") ?: return invalidParams("storage.set 需要 value")
        store.set(key, value)
        return Outcome.Success(NoData)
    }
}

class StorageRemoveHandler(
    private val store: KeyValueStore,
) : BridgeHandler {
    override val method: String = "storage.remove"

    override suspend fun handle(params: JsonObject?): Outcome<JsonElement> {
        val key = params.requiredText("key") ?: return invalidParams(MISSING_KEY)
        // 删不存在的 key 不算错误，和 localStorage.removeItem 一致。
        store.remove(key)
        return Outcome.Success(NoData)
    }
}
