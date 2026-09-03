package com.heydearnono.hybrid.core.webview

import android.content.Context
import androidx.webkit.WebViewAssetLoader

/**
 * assets 目录对外呈现的 origin。
 *
 * 引入 [WebViewAssetLoader] 不是为了做离线包，是被 bridge 逼出来的：
 * `addWebMessageListener` 按 origin 授权，而 `file:///android_asset/...` 页面的
 * sourceOrigin 是字符串 `"null"`（webkit 1.17.0 的 `WebMessageListener` 文档写明了这点），
 * 没法写进白名单。把 assets 挂到一个真的 https origin 上，页面才有身份可授权。
 *
 * 域名取 [WebViewAssetLoader.DEFAULT_DOMAIN]，不自己写字面量——它和 loader 的匹配逻辑
 * 必须是同一个值，写死两遍就会有一天对不上。
 */
val APP_ASSETS_ORIGIN: String = "https://" + WebViewAssetLoader.DEFAULT_DOMAIN

/** assets 的挂载前缀。webkit 要求前后都带 `/`。 */
private const val ASSETS_PATH_PREFIX = "/assets/"

/** 某个 assets 文件的完整 URL。`path` 是相对 `src/main/assets/` 的路径。 */
fun appAssetsUrl(path: String): String = APP_ASSETS_ORIGIN + ASSETS_PATH_PREFIX + path.removePrefix("/")

/**
 * 只挂 assets，不挂 resources、更不挂内部存储。
 *
 * 不调 `setHttpAllowed(true)`：默认只走 https，和容器里 `MIXED_CONTENT_NEVER_ALLOW` 的口径一致。
 */
internal fun createAssetLoader(context: Context): WebViewAssetLoader =
    WebViewAssetLoader
        .Builder()
        .addPathHandler(ASSETS_PATH_PREFIX, WebViewAssetLoader.AssetsPathHandler(context))
        .build()
