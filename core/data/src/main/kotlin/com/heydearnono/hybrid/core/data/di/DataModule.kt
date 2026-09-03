package com.heydearnono.hybrid.core.data.di

import com.heydearnono.hybrid.core.common.DefaultDispatcherProvider
import com.heydearnono.hybrid.core.common.DispatcherProvider
import com.heydearnono.hybrid.core.data.repository.DefaultArticleRepository
import com.heydearnono.hybrid.core.domain.repository.ArticleRepository
import com.heydearnono.hybrid.core.domain.usecase.GetArticlesUseCase
import org.koin.dsl.module

/**
 * 数据层装配。use case 也在这里声明，是为了让 `:core:domain` 保持零依赖——
 * 它不该知道 DI 框架的存在。
 */
fun dataModule() =
    module {
        single<DispatcherProvider> { DefaultDispatcherProvider() }
        single<ArticleRepository> { DefaultArticleRepository(get(), get()) }
        factory { GetArticlesUseCase(get()) }
    }
