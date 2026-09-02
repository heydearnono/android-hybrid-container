package com.example.base.core.common

/**
 * 显式的成功/失败载体。
 *
 * 不用 `kotlin.Result`：它把失败统一成 `Throwable`，而我们要的是一个受限的、
 * 上层必须穷举处理的错误集合（[AppError]）。
 */
sealed interface Outcome<out T> {
    data class Success<T>(
        val value: T,
    ) : Outcome<T>

    data class Failure(
        val error: AppError,
    ) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

fun <T> Outcome<T>.valueOrNull(): T? =
    when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> null
    }

fun <T> Outcome<T>.errorOrNull(): AppError? =
    when (this) {
        is Outcome.Success -> null
        is Outcome.Failure -> error
    }
