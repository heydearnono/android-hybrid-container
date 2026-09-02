package com.example.base.feature.articles.di

import com.example.base.feature.articles.ArticlesViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun articlesModule() =
    module {
        // Koin 4 起 ViewModel DSL 在 org.koin.core.module.dsl，
        // 旧的 org.koin.androidx.viewmodel.dsl 已废弃。
        viewModelOf(::ArticlesViewModel)
    }
