package com.example.base.di

import com.example.base.core.data.di.dataModule
import com.example.base.core.network.di.networkModule
import com.example.base.feature.articles.di.articlesModule
import org.koin.core.module.Module

/**
 * 整个 App 的依赖图入口。新增 feature 就在这里加一行。
 *
 * `loggingEnabled` 由调用方按 buildType 传，库模块不自己判断环境。
 */
fun appModules(loggingEnabled: Boolean): List<Module> =
    listOf(
        networkModule(loggingEnabled = loggingEnabled),
        dataModule(),
        articlesModule(),
    )
