package com.heydearnono.hybrid.core.bridge

import kotlinx.serialization.json.Json

/**
 * bridge 报文的 Json 配置。
 *
 * 和 `:core:network` 的 `defaultJson()` 刻意分成两份：那边面对的是后端契约，
 * 这边面对的是 WebView 里的 JS，两边的容错策略没有理由绑死在一起。
 */
fun defaultBridgeJson(): Json =
    Json {
        // JS 侧多传字段不该让整次调用失败。缺字段仍然报错——那是真的协议不一致。
        ignoreUnknownKeys = true
        // 回包里 data / error 为 null 时直接省掉这个键，JS 侧用 `in` 判断更自然。
        explicitNulls = false
        // BridgeResponse.v 带默认值（当前协议版本），默认情况下 kotlinx.serialization
        // 会把「等于默认值」的字段整个省掉——那会让回包在协议升级前缺 v，JS 侧没法
        // 靠它分辨「native 没升级」还是「这次刚好是版本 1」。v 必须每次都出现在报文里。
        encodeDefaults = true
    }

