package com.heydearnono.hybrid.navigation

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heydearnono.hybrid.bridge.AppNativeRouter
import com.heydearnono.hybrid.core.webview.appAssetsUrl
import com.heydearnono.hybrid.feature.articles.ArticlesRoute
import com.heydearnono.hybrid.feature.web.WebPageRoute
import org.koin.compose.koinInject

internal const val ARTICLES_ROUTE = "articles"

/** 三端契约规定的探活路由（PROTOCOL §3）：一致性验收页要能在三端各成功跳一次。 */
internal const val PROBE_ROUTE = "probe"

internal const val WEB_ROUTE = "web?url={url}"
private const val WEB_ARG_URL = "url"

/** 带参路由的 URL 必须编码，否则 `https://...?a=b` 里的 `?` 和 `&` 会被 Nav 当成自己的分隔符。 */
internal fun webRoute(url: String): String = "web?url=${Uri.encode(url)}"

/**
 * JS 侧 `router.open` 能跳到的原生页面，就这张表。
 *
 * 表里的 key 是给 JS 的稳定名字，value 是导航图内部的路由——两者刻意分开，
 * 这样改导航图不会变成一次 JS 侧的破坏性变更。
 *
 * `probe` 是唯一一个名字被 PROTOCOL §3 直接规定的条目：一致性验收页要用同一份 JS
 * 在三端各跑一次 `router.open`，若白名单完全自定，验收页就测不出这个能力成功的样子。
 */
internal val NATIVE_ROUTE_TARGETS: Map<String, String> =
    mapOf(
        "articles" to ARTICLES_ROUTE,
        PROBE_ROUTE to PROBE_ROUTE,
    )

/** 装在 assets 里的 demo 页，也是起始页。 */
internal val DEMO_PAGE_URL: String = appAssetsUrl("demo/index.html")

/**
 * 全 App 的导航图。feature 模块只暴露自己的入口 Composable，路由常量集中在这里，
 * feature 之间不互相依赖。
 */
@Composable
fun BaseNavHost() {
    val navController = rememberNavController()
    val activity = LocalActivity.current
    val router = koinInject<AppNativeRouter>()

    // NavController 活在组合里，router 是单例。离开时必须卸下，否则 router 会攥着一个
    // 已经销毁的 NavController。
    DisposableEffect(navController) {
        router.navigate = { route -> navController.navigate(route) }
        onDispose { router.navigate = null }
    }

    NavHost(
        navController = navController,
        // 起始页是 web demo 页：它同时演示 bridge 双向通信和 web → native 跳转，
        // 几乎不用写新的原生 UI。原来的原生列表页样例完整保留在 ARTICLES_ROUTE。
        startDestination = webRoute(DEMO_PAGE_URL),
    ) {
        composable(ARTICLES_ROUTE) {
            ArticlesRoute()
        }

        composable(PROBE_ROUTE) {
            ProbeScreen()
        }

        composable(
            route = WEB_ROUTE,
            arguments = listOf(navArgument(WEB_ARG_URL) { type = NavType.StringType }),
        ) { entry ->
            WebPageRoute(
                url = entry.arguments?.getString(WEB_ARG_URL) ?: DEMO_PAGE_URL,
                // 栈里没有上一页时（比如起始页自己调 page.close），退出 Activity。
                // 「关闭当前页」在栈底就是「离开 App」，让它什么都不做才是意外行为。
                onClose = { if (!navController.popBackStack()) activity?.finish() },
            )
        }
    }
}
