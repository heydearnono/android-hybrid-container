package com.example.base.core.network.di

import com.example.base.core.network.DEFAULT_BASE_URL
import com.example.base.core.network.api.ArticleApi
import com.example.base.core.network.defaultJson
import com.example.base.core.network.okHttpClient
import com.example.base.core.network.retrofit
import org.koin.dsl.module
import retrofit2.Retrofit

/**
 * baseUrl 与日志开关作为参数传入，而不是在模块内部判断环境——
 * 这样测试里能指向 MockWebServer。
 */
fun networkModule(
    baseUrl: String = DEFAULT_BASE_URL,
    loggingEnabled: Boolean = false,
) = module {
    single { defaultJson() }
    single { okHttpClient(loggingEnabled) }
    single { retrofit(baseUrl, get(), get()) }
    single { get<Retrofit>().create(ArticleApi::class.java) }
}
