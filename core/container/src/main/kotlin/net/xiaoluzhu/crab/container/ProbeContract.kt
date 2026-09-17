package net.xiaoluzhu.crab.container

/**
 * 探针契约：**取值全部来自 pro，端内不另定**。slug 拼写、顺序、固定标记三端一字不差，否则三份记录没法
 * 并排看，而「并排看」是 pro 要的唯一产出形式。
 *
 * 这里是端侧的唯一定义处。探针页（`.html` / `.js`）与 `scripts/probe.sh` 是另外两份拷贝——它们不在
 * 编译器、Spotless、lint 的覆盖范围里，只能靠 `ProbeContractAlignmentTest` 扫文本盯住。
 */
object ProbeContract {
    /** 页面把单条结果打到 console 的前缀；`WebChromeClient.onConsoleMessage` 原样转 logcat。 */
    const val LOG_PREFIX: String = "CRAB-PROBE"

    /**
     * 环境自报的前缀。**不是断言**，不参与那十六行：它打的是 UA 与 `typeof localStorage`，
     * 用来在差异表里填「加载后生效」那几格。前缀与断言分开，probe.sh 回读时不会把它当结果。
     */
    const val LOG_PREFIX_ENV: String = "CRAB-ENV"

    /** 拦截未命中时返回体里的固定标记：页面读到它就说明请求被容器接住了，没有落到网上。 */
    const val MARKER_INTERCEPTED: String = "INTERCEPTED"

    /** 承载目录之外那个文件的内容：页面**读到**它就是穿越成功，判 FAIL。 */
    const val MARKER_OUT_OF_BOUNDS: String = "OUT_OF_BOUNDS"

    /** `onJsPrompt` 人工要输入的内容，页面比对它判 `dialog`。 */
    const val PROMPT_INPUT: String = "CRAB"

    /**
     * 四个越界目标，取值来自 pro 的取值表，**端内不另定**。探针页的按钮按这四个地址跳，
     * `ProbeContractAlignmentTest` 盯着页面里的字面量与这里逐字相等。
     *
     * 域名同样落在 RFC 6761 保留的 `.invalid` 之下：闸门万一漏了，最坏也只是解析失败，
     * 不会真打到一台陌生服务器上。
     */
    const val TARGET_CROSS_ORIGIN: String = "https://out.crab.invalid/"

    const val TARGET_UNKNOWN_SCHEME: String = "crabx://probe"

    const val TARGET_MAILTO: String = "mailto:probe@crab.invalid"

    const val TARGET_TEL: String = "tel:+15555550100"

    /**
     * 十六条断言，**顺序即输出顺序**。`scripts/probe.sh` 按这个顺序打十六行
     * `<slug> PASS|FAIL|MANUAL`。
     */
    val SLUGS: List<String> =
        listOf(
            "origin",
            "storage",
            "subresource",
            "intercept",
            "escape",
            "escape-encoded",
            "inject-order",
            "inject-scope",
            "nav-same-origin",
            "nav-back",
            "nav-cross-origin",
            "nav-blank",
            "nav-system-scheme",
            "nav-unknown-scheme",
            "dialog",
            "permission",
        )
}
