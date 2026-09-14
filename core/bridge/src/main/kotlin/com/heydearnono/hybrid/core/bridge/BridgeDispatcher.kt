package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.common.AppError
import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/** 回包通道。容器实现它，负责把字符串送回 JS。 */
fun interface BridgeReply {
    fun send(payload: String)
}

/**
 * `bridge.capabilities` 的方法名，不经过 [BridgeRegistry]——见 [BridgeDispatcher.execute] 的说明。
 *
 * 特意公开（而不是 internal）：调用方模块要把它塞进自己的 [BridgeSecurityConfig]（授权表），
 * 否则握手请求本身会先被 [BridgeErrorCode.PERMISSION_DENIED] 挡掉。
 */
const val CAPABILITIES_METHOD = "bridge.capabilities"

/**
 * 把一条 JS 报文分发到对应能力，并回包。
 *
 * 刻意不持有 CoroutineScope：生命周期归容器（`:core:webview`）管，这里只负责一次分发。
 * 于是这个类在 `runTest` 里能被完整测到，而它恰好是 bridge 最需要被测到的部分。
 */
class BridgeDispatcher(
    private val registry: BridgeRegistry,
    private val policy: BridgePolicy,
    json: Json = defaultBridgeJson(),
) {
    private val codec = BridgeCodec(json)

    /** 在途请求的 id，用来拒绝重复（三端契约 §1、§6.2）。同一个 dispatcher 实例内跨调用共享。 */
    private val inFlightIds = ConcurrentHashMap.newKeySet<String>()

    /**
     * @param origin 必须是 WebView 给出的 sourceOrigin。这是整条链路上唯一可信的调用方身份，
     *   容器不许自己编一个传进来。
     * @param reply 回包通道。
     */
    suspend fun dispatch(
        origin: String,
        raw: String,
        reply: BridgeReply,
    ) {
        val request =
            when (val decoded = codec.decodeRequest(raw)) {
                is Outcome.Success -> {
                    decoded.value
                }

                is Outcome.Failure -> {
                    // 报文没解开，id 只能从原始文本里猜。猜不到就没法回包，只能丢弃。
                    codec.peekId(raw)?.let { reply.send(encodeFailure(it, decoded.error)) }
                    return
                }
            }

        // id 已改为必填（§6.2），在途重复视为协议错误：不许静默覆盖前一个请求的回包通道。
        if (!inFlightIds.add(request.id)) {
            reply.send(encodeFailure(request.id, BridgeErrorCode.BAD_REQUEST.asAppError("重复的 id: ${request.id}")))
            return
        }
        try {
            val result = execute(origin, request)
            reply.send(
                when (result) {
                    is Outcome.Success ->
                        codec.encodeResponse(BridgeResponse(id = request.id, ok = true, data = result.value))

                    is Outcome.Failure -> encodeFailure(request.id, result.error)
                },
            )
        } finally {
            inFlightIds.remove(request.id)
        }
    }

    private suspend fun execute(
        origin: String,
        request: BridgeRequest,
    ): Outcome<JsonElement> {
        // bridge.capabilities 是新的握手对象（§6.4）：只报告当前 origin 已被授权的能力名，
        // 所以它需要 policy 和 registry 本身，不像别的能力那样只碰自己的 params——
        // 放进 BridgeRegistry 会让 handler 反过来依赖 dispatcher 手里的东西，顺序会绕回来。
        if (request.method == CAPABILITIES_METHOD) {
            return if (!policy.isAllowed(origin, CAPABILITIES_METHOD)) {
                Outcome.Failure(BridgeErrorCode.PERMISSION_DENIED.asAppError(CAPABILITIES_METHOD))
            } else {
                Outcome.Success(capabilitiesPayload(origin))
            }
        }

        // 先查授权、后查注册表。顺序反过来的话，未授权的 origin 能靠「收到的是
        // METHOD_NOT_FOUND 还是 PERMISSION_DENIED」把 native 有哪些能力枚举出来。
        if (!policy.isAllowed(origin, request.method)) {
            return Outcome.Failure(BridgeErrorCode.PERMISSION_DENIED.asAppError(request.method))
        }
        val handler =
            registry.find(request.method)
                ?: return Outcome.Failure(BridgeErrorCode.METHOD_NOT_FOUND.asAppError(request.method))
        return try {
            handler.handle(request.params)
        } catch (e: CancellationException) {
            // 协程取消不是 handler 的失败，吞掉它会让上层的结构化并发失效。
            throw e
        } catch (e: Exception) {
            // handler 是别人写的，一个没接住的异常不该把整个 bridge 打挂。
            Outcome.Failure(BridgeErrorCode.INTERNAL.asAppError(e.message ?: e::class.simpleName))
        }
    }

    /**
     * 只列当前 origin 已授权的方法名，且总带上 [CAPABILITIES_METHOD] 自己——
     * 它没在 [BridgeRegistry] 里注册，registry.methods 天然不包含它。
     */
    private fun capabilitiesPayload(origin: String): JsonElement {
        val methods =
            (registry.methods + CAPABILITIES_METHOD)
                .filter { policy.isAllowed(origin, it) }
                .sorted()
        return buildJsonObject {
            put("v", JsonPrimitive(BRIDGE_PROTOCOL_VERSION))
            put("methods", JsonArray(methods.map(::JsonPrimitive)))
        }
    }

    private fun encodeFailure(
        id: String,
        error: AppError,
    ): String = codec.encodeResponse(BridgeResponse(id = id, ok = false, error = error.toErrorPayload()))
}


/**
 * 往 JS 侧推事件。
 *
 * 和 [BridgeDispatcher] 分开是因为方向不同：事件是 native 主动发起的，没有 id、
 * 也没有应答，JS 侧只能订阅。
 */
class BridgeEventEmitter(
    private val reply: BridgeReply,
    json: Json = defaultBridgeJson(),
) {
    private val codec = BridgeCodec(json)

    fun emit(
        event: String,
        data: JsonElement? = null,
    ) {
        reply.send(codec.encodeEvent(BridgeEvent(event = event, data = data)))
    }
}
