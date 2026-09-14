package com.heydearnono.hybrid.core.bridge

import com.heydearnono.hybrid.core.common.AppError

/**
 * 发给 JS 的错误码。
 *
 * 这是个「对外契约」枚举：一个码发出去之后就会有页面在 `switch` 它，
 * 所以**改已有分支的 [wireCode] 等于破坏兼容**，只允许新增分支。
 *
 * bridge 内部一路都用这个强类型枚举，只在跨出边界（回包给 JS、或塞进
 * [AppError.Rejected]）时才退化成字符串。
 */
enum class BridgeErrorCode(
    val wireCode: String,
) {
    /** 报文根本没解开：不是合法 JSON，缺 `v` / `id` / `method`，或 `id` 在途重复。 */
    BAD_REQUEST("BAD_REQUEST"),

    /** `v` 不是本端支持的契约版本。三端 + H5 四方独立发版之后才需要这个码——单端不需要。 */
    UNSUPPORTED_VERSION("UNSUPPORTED_VERSION"),

    /** 这个 origin 没有该能力的授权。注意它永远优先于 [METHOD_NOT_FOUND]，见 [BridgeDispatcher]。 */
    PERMISSION_DENIED("PERMISSION_DENIED"),

    /** 没有注册这个能力。 */
    METHOD_NOT_FOUND("METHOD_NOT_FOUND"),

    /** 能力存在，但 `params` 不合法。 */
    INVALID_PARAMS("INVALID_PARAMS"),

    /** 目标不存在：读一个没写过的 key、打开一个不在白名单里的路由。 */
    NOT_FOUND("NOT_FOUND"),

    /** handler 抛了没接住的异常，或 native 侧出了自己的问题。 */
    INTERNAL("INTERNAL"),
    ;

    /** 跨出 bridge 边界。[detail] 只用于排查，不要让 JS 侧的逻辑依赖它的文案。 */
    fun asAppError(detail: String? = null): AppError = AppError.Rejected(wireCode, detail)
}

/**
 * [AppError] → 回包里的 error 对象。
 *
 * 这个 `when` 是穷举的，位置也是刻意的：将来 [AppError] 再长分支，编译会在这里红，
 * 逼着作者回答「这种错误发给 JS 应该长什么样」，而不是默默漏成一个兜底码。
 */
internal fun AppError.toErrorPayload(): BridgeErrorPayload =
    when (this) {
        is AppError.Rejected -> BridgeErrorPayload(code, detail ?: code)
        AppError.Network -> BridgeErrorPayload("NETWORK", "网络不可用")
        is AppError.Http -> BridgeErrorPayload("HTTP", "服务端返回 $code")
        AppError.Serialization -> BridgeErrorPayload("SERIALIZATION", "响应格式与预期不一致")
    }
