package net.xiaoluzhu.crab.container

/**
 * 承载 origin 的**唯一**定义处。
 *
 * 域名字面量只允许出现在两处：这里，以及探针页的 `probe.js`（页面要拿它当期望值比对 `location.origin`）。
 * `HostingOriginSingleDefinitionTest` 扫源码树盯着「没有第三处」，`ProbeContractAlignmentTest` 盯着
 * 「探针页那份与这里逐字相等」。写成多份时改 origin 会漏掉一处，而漏掉的表现是脚本静默不注入或导航被
 * 误拦，两者都不报错。
 *
 * 三处从它派生，都不得另写字面量：
 * 1. `WebViewAssetLoader` 的域名
 * 2. `addDocumentStartJavaScript` 的 `allowedOriginRules`
 * 3. `shouldOverrideUrlLoading` 的放行判定
 *
 * 取值来自 pro 的取值表。域名落在 RFC 6761 保留的 `.invalid` 之下：拦截未命中的请求最坏只是解析失败，
 * 不会打到一台陌生服务器上。
 */
object HostingOrigin {
    const val SCHEME: String = "https"
    const val DOMAIN: String = "and.crab.invalid"
    const val ORIGIN: String = "$SCHEME://$DOMAIN"

    /** 承载目录：既是 assets 下的目录名，也是 URL 的第一段路径。 */
    const val HOSTING_DIR: String = "probe"

    const val ENTRY_URL: String = "$ORIGIN/$HOSTING_DIR/index.html"

    /** 承载目录里的第二个 HTML，M4 的 `nav-same-origin` / `nav-back` 用它。 */
    const val SECOND_PAGE_URL: String = "$ORIGIN/$HOSTING_DIR/second.html"

    /** `addDocumentStartJavaScript` 要的 origin 规则；注入范围从这里派生。 */
    val allowedOriginRules: Set<String> = setOf(ORIGIN)

    /**
     * 判 URL 是否落在承载 origin 上。**只认 `https://and.crab.invalid` 这一个 authority**：
     * 带端口、带用户信息、以它作后缀的域名（`and.crab.invalid.evil.com`）一律判否。
     * 宁可把自己的地址判成越界（表现是导航被拦、看得见），也不能把别人的判成自己（表现是注入的脚本
     * 落到不受控的 origin 上、看不见）。
     */
    fun isHostingOrigin(url: String): Boolean {
        val separator = url.indexOf("://")
        // 长度必须相等：只比前缀会让 `http://` 撞上 `https`（实测被单测抓到过）。
        if (separator != SCHEME.length) return false
        if (!url.regionMatches(0, SCHEME, 0, SCHEME.length, ignoreCase = true)) return false

        val authorityStart = separator + 3
        var authorityEnd = url.length
        for (index in authorityStart until url.length) {
            if (url[index] == '/' || url[index] == '?' || url[index] == '#') {
                authorityEnd = index
                break
            }
        }
        return authorityEnd - authorityStart == DOMAIN.length &&
            url.regionMatches(authorityStart, DOMAIN, 0, DOMAIN.length, ignoreCase = true)
    }
}
