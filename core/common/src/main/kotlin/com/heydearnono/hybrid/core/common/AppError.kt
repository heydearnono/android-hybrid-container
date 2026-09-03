package com.heydearnono.hybrid.core.common

/**
 * 领域层能理解的错误类型。
 *
 * 边界规则：网络/解析异常只允许出现在 `:core:network` 与 `:core:data` 内部，
 * 跨出 repository 之前必须翻译成 [AppError]。上层不认识 `IOException` / `HttpException`。
 */
sealed interface AppError {
    /** 连不上：断网、DNS、超时。 */
    data object Network : AppError

    /** 连上了但状态码不是 2xx。 */
    data class Http(
        val code: Int,
    ) : AppError

    /** 响应体和契约不一致。 */
    data object Serialization : AppError
}
