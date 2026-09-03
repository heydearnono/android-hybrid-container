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

    /**
     * 调用被拒绝，或参数不合法。
     *
     * [code] 是稳定的机器可读标识，会原样发给非 Kotlin 的调用方（比如 WebView 里的 JS）。
     * 之所以是字符串而不是枚举：产生它的一方（`:core:bridge`）内部仍然用枚举，
     * 只在跨出边界、接收方只认字符串时才退化成这里的 [code]。
     */
    data class Rejected(
        val code: String,
        val detail: String? = null,
    ) : AppError
}
