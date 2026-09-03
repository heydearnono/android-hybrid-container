package com.heydearnono.hybrid.core.data.di

import com.heydearnono.hybrid.core.common.DispatcherProvider
import com.heydearnono.hybrid.core.domain.repository.ArticleRepository
import com.heydearnono.hybrid.core.domain.usecase.GetArticlesUseCase
import com.heydearnono.hybrid.core.network.api.ArticleApi
import com.heydearnono.hybrid.core.network.di.networkModule
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * 依赖图的守卫：图接错了在 JVM 上就红，不用等装到设备上崩。
 *
 * 刻意逐个 get() 而不是只 `modules(...)`——单纯加载模块只登记定义，不会真的构造对象，
 * 少一个 `single<X>` 是发现不了的。
 */
class DiGraphTest {
    @Test
    fun `network 与 data 模块能装配出完整依赖链`() {
        val koin =
            koinApplication {
                modules(
                    networkModule(baseUrl = "https://example.com/", loggingEnabled = false),
                    dataModule(),
                )
            }.koin

        try {
            assertNotNull(koin.get<ArticleApi>())
            assertNotNull(koin.get<DispatcherProvider>())
            assertNotNull(koin.get<ArticleRepository>())
            assertNotNull(koin.get<GetArticlesUseCase>())
        } finally {
            koin.close()
        }
    }
}
