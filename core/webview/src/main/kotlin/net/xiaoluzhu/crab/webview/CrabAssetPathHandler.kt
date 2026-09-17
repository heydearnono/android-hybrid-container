package net.xiaoluzhu.crab.webview

import android.content.res.AssetManager
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import net.xiaoluzhu.crab.container.AssetRoute
import net.xiaoluzhu.crab.container.AssetRouting
import net.xiaoluzhu.crab.container.ProbeContract
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * 承载 origin 上**所有**请求的落点。
 *
 * 只在 `WebViewAssetLoader` 上注册 `/` 这一个前缀，路由全交给 [AssetRouting]（纯 JVM，有单测）。这样做
 * 的理由是 `WebViewAssetLoader` 未命中时返回 null，而 null 等于「交回 WebView 默认处理」——请求会真的
 * 发到网上去。注册一个吃掉全部路径的 handler 之后，承载 origin 上不存在「落空」这条路。
 *
 * 非命中一律回 **404 + 固定标记 `INTERCEPTED`**：状态码让页面能判，标记让页面能确认接住它的是容器
 * 而不是某台服务器。
 *
 * 这个类刻意不带单测：`WebResourceResponse` 在 JVM 单测里是 `android.jar` 的 stub，断言它的字段只会
 * 得到假绿。判定逻辑一行都不许挪进来。
 */
class CrabAssetPathHandler(
    private val assets: AssetManager,
) : WebViewAssetLoader.PathHandler {
    /** 返回类型收紧成非空：这个 handler 永不返回 null（见类注释）。 */
    override fun handle(path: String): WebResourceResponse =
        when (val route = AssetRouting.resolve(path)) {
            is AssetRoute.Hit -> openAsset(route)
            AssetRoute.OutOfBounds, AssetRoute.NotFound -> intercepted()
        }

    private fun openAsset(route: AssetRoute.Hit): WebResourceResponse =
        try {
            WebResourceResponse(
                route.mimeType,
                encodingFor(route.mimeType),
                200,
                "OK",
                NO_STORE_HEADERS,
                assets.open(route.assetPath),
            )
        } catch (error: IOException) {
            // 路由说该有、assets 里却没有：故意删掉入口文件那一遍走的就是这条路，回 404 让错误态接上。
            intercepted()
        }

    private fun intercepted(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            NO_STORE_HEADERS,
            ByteArrayInputStream(ProbeContract.MARKER_INTERCEPTED.toByteArray(Charsets.UTF_8)),
        )

    /** 二进制素材不报 charset：报了反而会让某些解码路径按文本处理。 */
    private fun encodingFor(mimeType: String): String? =
        if (mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "image/svg+xml") {
            "utf-8"
        } else {
            null
        }

    private companion object {
        /** 探针要反复跑（尤其是删文件那一遍），缓存住的旧响应会让结果不可复现。 */
        val NO_STORE_HEADERS: Map<String, String> = mapOf("Cache-Control" to "no-store")
    }
}
