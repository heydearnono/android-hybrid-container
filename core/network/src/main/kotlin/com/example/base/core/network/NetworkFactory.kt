package com.example.base.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** 默认后端地址。生产环境应由 `:app` 按 buildType 覆盖，不要在库里判断环境。 */
const val DEFAULT_BASE_URL: String = "https://jsonplaceholder.typicode.com/"

fun defaultJson(): Json =
    Json {
        // 后端加字段不应该让客户端崩。缺字段仍然报错——那是真的契约不一致。
        ignoreUnknownKeys = true
        explicitNulls = false
    }

fun okHttpClient(loggingEnabled: Boolean): OkHttpClient =
    OkHttpClient
        .Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            if (loggingEnabled) {
                // 只在 debug 打开：BODY 级别会把响应全文写进 logcat，release 打开等于泄漏。
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
                )
            }
        }.build()

fun retrofit(
    baseUrl: String,
    client: OkHttpClient,
    json: Json,
): Retrofit =
    Retrofit
        .Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
