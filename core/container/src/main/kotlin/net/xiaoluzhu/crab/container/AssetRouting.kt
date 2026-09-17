package net.xiaoluzhu.crab.container

import java.io.ByteArrayOutputStream

/** [AssetRouting.resolve] 的结果。 */
sealed interface AssetRoute {
    /** 命中承载目录里的一个文件；[assetPath] 是 `assets/` 下的相对路径。 */
    data class Hit(
        val assetPath: String,
        val mimeType: String,
    ) : AssetRoute

    /** 路径想跑到承载目录之外（`..` 穿越、反斜杠、NUL）。 */
    data object OutOfBounds : AssetRoute

    /** 承载 origin 上没有这个路径：不在承载目录前缀下，或只指到目录。 */
    data object NotFound : AssetRoute
}

/**
 * URL 路径 → assets 相对路径的**全部判定**。放在纯 JVM 模块是刻意的：`:core:webview` 里断言不了任何
 * 东西（`android.webkit` 在单测里是 stub），所以路径这类「判错了就等于把 assets 目录漏出去」的逻辑
 * 一行都不许写在那边。
 *
 * 与 `WebViewAssetLoader` 的咬合（javap 读 androidx.webkit 1.17.0 的实现核过，不是凭记忆）：
 * - `PathMatcher` 只比 authority 与路径前缀，命中后把 **`Uri.getPath()`** 的剩余段交给
 *   `PathHandler.handle(String)`。`Uri.getPath()` 是**已经百分号解码过**的，所以 `%2e%2e%2f` 到这里
 *   已经是 `../`
 * - 注册的前缀必须以 `/` 开头且以 `/` 结尾（`PathMatcher` 构造函数直接抛）。本仓只注册 `/` 这一个前缀，
 *   于是承载 origin 上**任何**路径都会进到这里；`WebViewAssetLoader` 未命中会返回 null，而 null 是
 *   「交回 WebView 默认处理」——请求会真的打到网上去，那条路必须堵死
 *
 * 因此 [resolve] 的契约是：**承载 origin 上的一切都由它判**，非命中一律由调用方回 404，不允许落空。
 */
object AssetRouting {
    private const val MAX_DECODE_ROUNDS = 3

    /**
     * @param rawPath `PathHandler.handle` 拿到的路径（相对注册前缀 `/`，可能带前导斜杠）。
     */
    fun resolve(rawPath: String): AssetRoute {
        val decoded = decodeRepeatedly(rawPath) ?: return AssetRoute.NotFound

        // 反斜杠与 NUL：前者在某些文件系统上等价于路径分隔符，后者能截断 C 字符串，合法素材路径里都不该有。
        if (decoded.contains('\\') || decoded.any { it.code == 0 }) return AssetRoute.OutOfBounds

        val segments = ArrayList<String>()
        for (segment in decoded.split('/')) {
            when (segment) {
                "", "." -> Unit

                // 前导斜杠、`//`、`./` 都只是噪音
                ".." -> return AssetRoute.OutOfBounds

                // 不做「弹栈」化解：想穿越就是越界，不给它抵消的机会
                else -> segments.add(segment)
            }
        }

        // 第一段必须是承载目录，其余（含空路径、只指到目录）都不属于这个容器。
        if (segments.size < 2 || segments[0] != HostingOrigin.HOSTING_DIR) return AssetRoute.NotFound

        val assetPath = segments.joinToString("/")
        return AssetRoute.Hit(assetPath, mimeOf(assetPath))
    }

    /**
     * 按扩展名推 MIME。认不出的一律 `application/octet-stream`：让浏览器当二进制看，
     * 比猜成 `text/html` 安全（后者会把一段陌生内容当页面执行）。
     */
    fun mimeOf(path: String): String {
        val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return MIME_BY_EXTENSION[extension] ?: "application/octet-stream"
    }

    /**
     * 解码到不再变化。`Uri.getPath()` 已经解过一轮，这里再解是为了 `%252e%252e%252f` 这种双重编码——
     * 解一轮得到 `%2e%2e%2f`，看上去不含 `..`，放过去就漏了。代价是素材文件名里不能带字面量 `%`。
     *
     * 刻意不用 `URLDecoder.decode`：那是 form 编码，会把 `+` 变成空格。
     *
     * @return 编码坏掉（`%` 后不是两位十六进制）或解不完时返回 null，由调用方当坏请求处理。
     */
    private fun decodeRepeatedly(path: String): String? {
        var current = path
        repeat(MAX_DECODE_ROUNDS) {
            if (!current.contains('%')) return current
            val next = decodeOnce(current) ?: return null
            if (next == current) return current
            current = next
        }
        return if (current.contains('%')) null else current
    }

    private fun decodeOnce(path: String): String? {
        val bytes = ByteArrayOutputStream(path.length)
        var index = 0
        while (index < path.length) {
            val char = path[index]
            if (char == '%') {
                if (index + 2 >= path.length) return null
                val high = Character.digit(path[index + 1], 16)
                val low = Character.digit(path[index + 2], 16)
                if (high < 0 || low < 0) return null
                bytes.write(high shl 4 or low)
                index += 3
            } else {
                bytes.write(char.toString().toByteArray(Charsets.UTF_8))
                index += 1
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private val MIME_BY_EXTENSION: Map<String, String> =
        mapOf(
            "html" to "text/html",
            "htm" to "text/html",
            "js" to "text/javascript",
            "mjs" to "text/javascript",
            "css" to "text/css",
            "json" to "application/json",
            "txt" to "text/plain",
            "png" to "image/png",
            // 探针页那段循环音是「切后台媒体停播」唯一的观察面，MIME 认不出来会被当二进制、放不出声
            "wav" to "audio/wav",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "webp" to "image/webp",
            "svg" to "image/svg+xml",
            "ico" to "image/x-icon",
            "woff" to "font/woff",
            "woff2" to "font/woff2",
            "ttf" to "font/ttf",
        )
}
