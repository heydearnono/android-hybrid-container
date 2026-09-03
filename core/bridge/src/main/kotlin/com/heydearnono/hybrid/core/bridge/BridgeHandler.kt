package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 一个 bridge 能力。
 *
 * 实现按「碰不碰 Android」拆成两半：参数校验、结果编码、错误映射留在这个模块
 * （纯 JVM，可以自测）；真正调 Android API 的部分抽成 port 接口，实现放
 * `:core:webview` / `:feature:web` / `:app`。这么切是为了把本环境验证不了的代码
 * 压到最薄、且没有分支。
 *
 * [handle] 不应该抛异常。抛了会被 [BridgeDispatcher] 兜成 `INTERNAL`，
 * 但那时 JS 侧拿到的信息已经没有意义了——预期内的失败要返回 [Outcome.Failure]。
 */
interface BridgeHandler {
    /** 稳定的能力名，形如 `storage.set`。同一个名字只允许注册一次。 */
    val method: String

    suspend fun handle(params: JsonObject?): Outcome<JsonElement>
}
