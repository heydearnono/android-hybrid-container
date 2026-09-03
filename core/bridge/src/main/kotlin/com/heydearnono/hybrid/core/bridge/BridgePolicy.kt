package com.heydearnono.hybrid.core.bridge

/**
 * 一条授权：某个 origin 可以调哪些能力。
 *
 * [origin] 必须写成 `scheme://host` 或 `scheme://host:port`，匹配是**精确相等**。
 * 首批不支持 `*.example.com` 这类通配——子域匹配写错是一类经典漏洞
 * （`https://a.example.com.evil.com` 被当成 `a.example.com` 的子域放过），
 * 而这个仓库里需要授权的 origin 一只手数得完，多写几条就够。
 */
data class OriginRule(
    val origin: String,
    val methods: Set<String>,
)

/**
 * bridge 的安全配置，是 origin 授权的**唯一来源**。
 *
 * 它同时喂两处闸门：[BridgePolicy]（每次分发都校验）和 [allowedOriginRules]
 * （传给 `addWebMessageListener`，让 WebView 压根不把 bridge 对象注入给别的页面）。
 * 两处必须同源，否则会出现「注入层放进来了、分发层却拒绝」这种极难查的不一致。
 */
data class BridgeSecurityConfig(
    val rules: List<OriginRule>,
    /**
     * 只允许在 debug 构建里打开：跳过 origin 校验，任何来源都能调任何能力。
     * 打开它等于放弃 bridge 的全部访问控制，出现在 release 里就是事故。
     */
    val allowAnyOrigin: Boolean = false,
) {
    /**
     * 传给 `addWebMessageListener` 的 allowedOriginRules。
     *
     * 规则格式已对着 webkit 1.17.0 的源码核实：`SCHEME "://" [HOSTNAME_PATTERN [":" PORT]]`，
     * 不带结尾斜杠，省略端口时 https 匹配 443、http 匹配 80；[WILDCARD_ORIGIN_RULE] 就是 `*`。
     */
    val allowedOriginRules: Set<String>
        get() =
            if (allowAnyOrigin) {
                setOf(WILDCARD_ORIGIN_RULE)
            } else {
                rules.mapTo(mutableSetOf()) { normalizeOrigin(it.origin) }
            }

    companion object {
        const val WILDCARD_ORIGIN_RULE = "*"
    }
}

/** 分发时的授权校验。和 [BridgeSecurityConfig.allowedOriginRules] 读的是同一份配置。 */
class BridgePolicy(
    private val config: BridgeSecurityConfig,
) {
    private val methodsByOrigin: Map<String, Set<String>> =
        config.rules
            .groupBy { normalizeOrigin(it.origin) }
            .mapValues { (_, rules) -> rules.flatMapTo(mutableSetOf()) { it.methods } }

    /**
     * @param origin 必须是 WebView 给出的 sourceOrigin，容器不许自己编一个传进来。
     */
    fun isAllowed(
        origin: String,
        method: String,
    ): Boolean {
        if (config.allowAnyOrigin) return true
        return methodsByOrigin[normalizeOrigin(origin)]?.contains(method) == true
    }
}

/**
 * 只做两件事：去掉尾斜杠、转小写。scheme 和 host 本来就大小写不敏感，
 * 而 WebView 给出的 origin 可能带尾斜杠。
 *
 * 刻意**不**补默认端口：那意味着由这里决定 `https://a.com` 和 `https://a.com:443`
 * 谁等于谁，而这是规则作者该显式写清的事，藏在规范化里只会让人猜。
 */
internal fun normalizeOrigin(raw: String): String = raw.trimEnd('/').lowercase()
