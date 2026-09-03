package com.heydearnono.hybrid.di

import org.koin.dsl.koinApplication
import kotlin.test.Test

/**
 * 只验证「整张图能加载」：Koin 在加载阶段就会因重复定义抛错，
 * 所以这个测试能挡住新增 feature 时把同一个类型注册两遍。
 *
 * 不在这里逐个 get()——ViewModel 的解析需要 ViewModelStoreOwner，那是设备上的事。
 * 具体依赖链的构造由 `:core:data` 的 DiGraphTest 覆盖。
 */
class AppModulesTest {
    @Test
    fun `全部模块能加载且没有重复定义`() {
        koinApplication {
            modules(appModules(loggingEnabled = false))
        }.koin.close()
    }
}
