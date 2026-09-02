package com.example.base.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.base.feature.articles.ArticlesRoute

internal const val ARTICLES_ROUTE = "articles"

/**
 * 全 App 的导航图。feature 模块只暴露自己的入口 Composable，路由常量集中在这里，
 * feature 之间不互相依赖。
 */
@Composable
fun BaseNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ARTICLES_ROUTE,
    ) {
        composable(ARTICLES_ROUTE) {
            ArticlesRoute()
        }
    }
}
