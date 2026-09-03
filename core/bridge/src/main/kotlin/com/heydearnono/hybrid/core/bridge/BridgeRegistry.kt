package com.heydearnono.hybrid.core.bridge

/**
 * 能力注册表。构造时就把 method → handler 定死，运行期只读。
 */
class BridgeRegistry(
    handlers: List<BridgeHandler>,
) {
    private val byMethod: Map<String, BridgeHandler> =
        handlers.groupBy(BridgeHandler::method).mapValues { (method, duplicates) ->
            // 重复注册几乎总是两个模块各自注册了同一个能力，而「哪个赢」是随机的。
            // 构造期直接抛，DI 图测试会第一时间红，不给它跑到线上的机会。
            require(duplicates.size == 1) { "能力 $method 被注册了 ${duplicates.size} 次" }
            duplicates.single()
        }

    /** 已注册的能力名。DI 图测试用它断言「能力数量等于预期」，防止漏接线。 */
    val methods: Set<String> get() = byMethod.keys

    fun find(method: String): BridgeHandler? = byMethod[method]
}
