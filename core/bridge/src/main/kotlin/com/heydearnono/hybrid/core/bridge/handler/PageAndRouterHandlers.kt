package com.heydearnono.hybrid.core.bridge.handler

import com.heydearnono.hybrid.core.bridge.BridgeErrorCode
import com.heydearnono.hybrid.core.bridge.BridgeHandler
import com.heydearnono.hybrid.core.bridge.invalidParams
import com.heydearnono.hybrid.core.bridge.port.NativeRouter
import com.heydearnono.hybrid.core.bridge.port.PageHost
import com.heydearnono.hybrid.core.bridge.requiredText
import com.heydearnono.hybrid.core.bridge.stringOrNull
import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class PageCloseHandler(
    private val host: PageHost,
) : BridgeHandler {
    override val method: String = "page.close"

    override suspend fun handle(params: JsonObject?): Outcome<JsonElement> {
        host.close()
        return Outcome.Success(NoData)
    }
}

class PageSetTitleHandler(
    private val host: PageHost,
) : BridgeHandler {
    override val method: String = "page.setTitle"

    override suspend fun handle(params: JsonObject?): Outcome<JsonElement> {
        // 空串是合法的：「清掉标题」是个正当请求。所以这里用 stringOrNull 而不是 requiredText。
        val title = params.stringOrNull("title") ?: return invalidParams("page.setTitle 需要 title")
        host.setTitle(title)
        return Outcome.Success(NoData)
    }
}

class RouterHandler(
    private val router: NativeRouter,
) : BridgeHandler {
    override val method: String = "router.open"

    override suspend fun handle(params: JsonObject?): Outcome<JsonElement> {
        val route = params.requiredText("route") ?: return invalidParams("router.open 需要非空的 route")
        // 首批只支持「白名单里的路由名」，不做 URL / scheme 匹配。现在定一套路由协议，
        // 将来做 native/web 统一路由时大概率要推翻，那时已经有页面在用了。
        return if (router.open(route)) {
            Outcome.Success(NoData)
        } else {
            Outcome.Failure(BridgeErrorCode.NOT_FOUND.asAppError(route))
        }
    }
}
