package com.example.base.core.data.di

import com.example.base.core.common.DefaultDispatcherProvider
import com.example.base.core.common.DispatcherProvider
import com.example.base.core.data.repository.DefaultArticleRepository
import com.example.base.core.domain.repository.ArticleRepository
import com.example.base.core.domain.usecase.GetArticlesUseCase
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
