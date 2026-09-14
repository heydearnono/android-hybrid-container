package com.heydearnono.hybrid.core.bridge.handler

import com.heydearnono.hybrid.core.bridge.BridgeHandler
import com.heydearnono.hybrid.core.bridge.booleanOr
import com.heydearnono.hybrid.core.bridge.invalidParams
import com.heydearnono.hybrid.core.bridge.port.DeviceInfoProvider
import com.heydearnono.hybrid.core.bridge.port.Toaster
import com.heydearnono.hybrid.core.bridge.requiredText
import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 没有返回值的能力统一回 `data: null`，而不是空对象——JS 侧 `await` 到的就是 `null`，
 * 不用去分辨「空对象」和「没结果」。
 */
internal val NoData: JsonElement = JsonNull

class DeviceInfoHandler(
    private val provider: DeviceInfoProvider,
) : BridgeHandler {
    override val method: String = "device.info"

    override suspend fun handle(params: JsonObject?): Outcome<JsonElement> {
        val info = provider.snapshot()
        // 报文字段在这里显式列一遍，不直接序列化 DeviceInfo：port 的字段名是内部事情，
        // 改它不该无声无息地改掉 JS 侧的契约。必须级字段在顶层，平台专有字段
        // （sdkInt、manufacturer）收进 extra——顶层混放会让 H5 写 info.sdkInt 在别的
        // 端上悄悄拿到 undefined，收进 extra 后 info.extra?.sdkInt 自带「可能没有」的语气。
        return Outcome.Success(
            buildJsonObject {
                // 固定写死，让 JS 侧将来能用同一份代码分辨 native 是哪一端。
                put("platform", "android")
                put("osVersion", info.osVersion)
                put("model", info.model)
                put("appVersionName", info.appVersionName)
                put("appVersionCode", info.appVersionCode)
                put("locale", info.locale)
                put(
                    "extra",
                    buildJsonObject {
                        put("sdkInt", info.sdkInt)
                        put("manufacturer", info.manufacturer)
                    },
                )
            },
        )
    }
}

class ToastHandler(
    private val toaster: Toaster,
) : BridgeHandler {
    override val method: String = "ui.toast"

    override suspend fun handle(params: JsonObject?): Outcome<JsonElement> {
        val text = params.requiredText("text") ?: return invalidParams("ui.toast 需要非空的 text")
        toaster.show(text = text, long = params.booleanOr("long", fallback = false))
        return Outcome.Success(NoData)
    }
}


