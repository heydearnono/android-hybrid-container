package net.xiaoluzhu.crab.container

/**
 * 页面开口之前注入的那段脚本。
 *
 * 两条路都用**同一段**源码：`addDocumentStartJavaScript`（支持时）或拦截点改写入口 HTML（不支持时）。
 * **两条路不许叠加**——都跑一遍 `injected` 会变 2，`inject-order` 当场红，而那种红看起来像注入坏了。
 *
 * 脚本自己有三条讲究：
 * 1. 先判 `location.origin`：`addDocumentStartJavaScript` 的 origin 规则已经限定了范围，这里再判一次，
 *    是为了兜底那条路——改写 HTML 的方式没有 origin 概念
 * 2. `window.__CRAB__` 用 `||` 初始化：**已经存在就不重置**，否则重复注入会把计数抹平，反而看不出问题
 * 3. `injected` 只增不减：它是「注入跑了几次」的证据，探针页据此判 `inject-order`
 */
object DocumentStartScript {
    /** 入口 HTML 里的插入点。兜底注入把脚本插到这里，位置在页面自己的第一段脚本之前。 */
    const val MARKER: String = "<!--CRAB-INJECT-->"

    private const val HTML_MIME_TYPE: String = "text/html"

    val source: String =
        """
        (function () {
          if (location.origin !== '${HostingOrigin.ORIGIN}') { return; }
          var crab = window.__CRAB__ || { origin: '${HostingOrigin.ORIGIN}', injected: 0 };
          crab.injected = crab.injected + 1;
          window.__CRAB__ = crab;
        })();
        """.trimIndent()

    /**
     * 兜底路径要不要改写这个响应。**判断放在这里而不是拦截点**：拦截点在 `:core:webview`，那边没有单测。
     *
     * 只改 HTML：改写别的类型等于往素材里插一段脚本文本，`subresource` 会当场红，而那种红指不到原因。
     */
    fun appliesTo(mimeType: String): Boolean = mimeType == HTML_MIME_TYPE

    /**
     * 兜底路径：把同一段脚本内联进 HTML。
     *
     * 优先插在 [MARKER] 处（入口页把它放在自己第一段脚本之前）；页面没留标记时退到 `<head>` 之后，
     * 再没有 `<head>` 就插到最前面。**不会一个字都不插**——静默不注入的表现是 `inject-order` 红，
     * 而那种红指不到原因。
     */
    fun inlineInto(html: String): String {
        val tag = "<script>$source</script>"
        if (html.contains(MARKER)) return html.replace(MARKER, tag)

        val headStart = html.indexOf("<head", ignoreCase = true)
        val headEnd = if (headStart >= 0) html.indexOf('>', headStart) else -1
        return if (headEnd >= 0) {
            html.substring(0, headEnd + 1) + tag + html.substring(headEnd + 1)
        } else {
            tag + html
        }
    }
}
