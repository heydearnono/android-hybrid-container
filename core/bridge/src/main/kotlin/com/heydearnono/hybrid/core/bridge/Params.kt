package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * params 读取的公共部分。
 *
 * 抽出来不是为了省几行代码，而是因为**错误码是对外契约**：每个 handler 各写一遍
 * null 检查，迟早会出现有的回 `INVALID_PARAMS`、有的直接抛成 `INTERNAL`。
 */
internal fun JsonObject?.stringOrNull(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

/** 非空字符串参数。空串和空白串都算缺失——没有哪个能力需要一个空白的 key 或 text。 */
internal fun JsonObject?.requiredText(key: String): String? = stringOrNull(key)?.takeIf { it.isNotBlank() }

internal fun JsonObject?.booleanOr(
    key: String,
    fallback: Boolean,
): Boolean = (this?.get(key) as? JsonPrimitive)?.booleanOrNull ?: fallback

/** [message] 是给开发者排查用的，不要让 JS 侧的逻辑依赖它的文案。 */
internal fun invalidParams(message: String): Outcome<Nothing> =
    Outcome.Failure(BridgeErrorCode.INVALID_PARAMS.asAppError(message))
