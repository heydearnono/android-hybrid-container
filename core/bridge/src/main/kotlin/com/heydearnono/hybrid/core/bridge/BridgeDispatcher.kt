package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.common.AppError
import com.heydearnono.hybrid.core.common.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** 回包通道。容器实现它，负责把字符串送回 JS。 */
fun interface BridgeReply {
    fun send(payload: String)
}

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

    /**
     * @param origin 必须是 WebView 给出的 sourceOrigin。这是整条链路上唯一可信的调用方身份，
     *   容器不许自己编一个传进来。
     * @param reply 回包通道。报文没带 id（单向通知）时一次都不会被调用。
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

        val result = execute(origin, request)

        // 单向通知：能力照常执行，但结果和错误都不回。这条要写进 JS SDK 的文档——
        // 想拿结果就必须带 id。
        val id = request.id ?: return
        reply.send(
            when (result) {
                is Outcome.Success -> codec.encodeResponse(BridgeResponse(id = id, ok = true, data = result.value))
                is Outcome.Failure -> encodeFailure(id, result.error)
            },
        )
    }

    private suspend fun execute(
        origin: String,
        request: BridgeRequest,
    ): Outcome<JsonElement> {
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
