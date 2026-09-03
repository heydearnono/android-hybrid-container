package com.heydearnono.hybrid.bridge

import com.heydearnono.hybrid.core.bridge.BridgeSecurityConfig
import com.heydearnono.hybrid.core.bridge.OriginRule
import com.heydearnono.hybrid.core.webview.APP_ASSETS_ORIGIN

/**
 * 能力白名单：哪个 origin 能调哪些方法。
 *
 * 逐个列出而不是「注册了什么就允许什么」。列表和注册表可能对不上——这正是想要的：
 * 新增一个能力时，如果不来这里加一行，任何页面都调不到它。默认拒绝，不是默认允许。
 * `BridgeWiringTest` 锁住「这张表里的名字都真的存在」，反向的漏加则靠这条规则本身兜住。
 */
internal val DEMO_PAGE_METHODS: Set<String> =
    setOf(
        "device.info",
        "ui.toast",
        "page.close",
        "page.setTitle",
        "router.open",
        "storage.get",
        "storage.set",
        "storage.remove",
    )

/**
 * 首批只授权 assets 里的 demo 页。
 *
 * [allowAnyOrigin][BridgeSecurityConfig.allowAnyOrigin] 一直是 false，包括 debug——
 * 打开它等于放弃 bridge 的全部访问控制，而调试期的方便换不来这个代价。
 */
internal fun appBridgeSecurityConfig(): BridgeSecurityConfig =
    BridgeSecurityConfig(
        rules = listOf(OriginRule(origin = APP_ASSETS_ORIGIN, methods = DEMO_PAGE_METHODS)),
    )
