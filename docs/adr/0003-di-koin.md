# ADR-0003：DI 用 Koin，不用 Hilt

| | |
|---|---|
| 编号 | ADR-0003 |
| 日期 | 2026-09-02 |
| 状态 | 已采纳 |
| 影响范围 | 依赖 / 构建 / 测试 |

## 决策

依赖注入用 Koin 4.2.2。use case 与 repository 在 `:core:data` 的 `dataModule()` 里声明，ViewModel 在各 feature 的 module 里用 `viewModelOf(::X)`，`:app` 的 `appModules()` 负责汇总。

## 背景

需要一个 DI 方案，约束有三条：

1. `:core:*` 是纯 JVM 模块（[ADR-0002](0002-core-modules-pure-jvm.md)），DI 框架不能要求 Android 运行时才能装配
2. 依赖图必须能在 JVM 单测里验证（[ADR-0001](0001-verification-loop.md)）
3. 构建工具链是 AGP 9 + 内置 Kotlin，注解处理器的兼容性是未知项

## 候选方案

| 方案 | 优势 | 代价 | 关键风险 |
|---|---|---|---|
| A（选中）Koin | 纯 Kotlin，无 codegen；`koinApplication {}` 在普通 JVM 测试里就能装配整张图 | 图错了是运行期报错，不是编译期 | 漏注册一个类型要靠测试兜住 |
| B Hilt | 编译期校验依赖图，错了直接编译失败 | 需要 KSP/kapt + `com.android.library`；纯 JVM 模块用不了 Hilt 的 Android 组件 | AGP 9 内置 Kotlin 下 KSP 的接法未核实，可能直接卡住 |
| C 手写构造注入 | 零依赖、全编译期 | 装配代码随模块增长膨胀，ViewModel 的生命周期要自己接 | 没有 |

## 理由

Hilt 的核心优势（编译期校验）在这里会被 ADR-0002 抵消掉：一旦 core 模块必须改成 Android library 才能用 Hilt，就动摇了「大部分逻辑可自测」这个更重要的性质。而 Koin 的核心劣势（运行期才报错）可以用测试补偿——`DiGraphTest` 逐个 `get<T>()` 走了一遍真实构造，这个信号在 CI 上和编译期错误等价。

**注意不能只写 `modules(...)`**：加载模块只登记定义、不构造对象，少注册一个 `single` 也不会报。必须显式 `get<T>()`。

方案 C 在模块数少的时候确实可行，但基座的用途就是长期加 feature，装配代码的增长曲线不接受。

## 后果

- 接受了什么代价：依赖图错误在编译期抓不到，靠 `:core:data` 的 `DiGraphTest` 和 `:app` 的 `AppModulesTest` 兜住。新增可注入类型时必须同步补进这两个测试之一。
- 引入了什么依赖 / 锁定：`koin-core`（JVM 模块）、`koin-android`、`koin-androidx-compose`。ViewModel DSL 从 Koin 4 起在 `org.koin.core.module.dsl`，旧的 `org.koin.androidx.viewmodel.dsl` 已废弃。
- 什么条件下重新评估：装配开销出现在启动耗时的火焰图上时，或者 KSP 在这套工具链下被验证可用、且团队更看重编译期校验时。

## 参考

- `core/data/src/main/kotlin/.../di/DataModule.kt`、`app/src/main/kotlin/.../di/AppModules.kt`
- `core/data/src/test/kotlin/.../di/DiGraphTest.kt`
