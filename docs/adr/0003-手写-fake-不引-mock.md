# ADR-0003 · 手写 fake，不引 mock 框架

- 日期：2026-09-18
- 状态：已采纳

## 背景

容器的判定汇总在 `ContainerCoordinator`，它对外的四件事（打日志、交系统、重新加载、换 WebView）都在
`ContainerActions` 接口后面。测它必须有个替身。

本仓**全部由 AI 开发**，这一条直接影响选择：AI 改接口时不会替你想起来「哦还有个 mock 要跟着改」，
只有编译器会。

## 决定

测试替身一律手写（例：`ContainerCoordinatorTest` 里的 `private class RecordingActions : ContainerActions`，
把每次调用记进 `MutableList`），**不引 MockK / Mockito 一类框架**。

## 理由

- 接口加一个方法，手写 fake 立刻编译不过，错误指向准确；mock 是运行期生成的，同样的改动只会让某条
  测试在某个断言上莫名其妙地红，或者更糟——**继续绿**，因为 mock 对没配置的方法返回默认值
- 「记下调用序列再整体比一次」比逐个 `verify` 更容易看出问题。`assertEquals(listOf("CRAB-ERR render-gone 1"), actions.lines)`
  同时管住了「打了什么」和「打了几行」，而 `verify(times(1))` 只管后者。M4 有两处正是靠行数判的
  （同一次终止的重复回调不许再打、`nav-blank` 要两行）
- 代价（每个接口多一个几十行的类）在这个规模下可以忽略：接口只有一个，四个方法

## 后果

- 测试读起来是「做了这些动作 → 期望留下这些痕迹」，不需要先学一套 mock DSL
- fake 里那几个 `MutableList` 是共享状态，测试之间必须靠新实例隔离——本仓靠的是 JUnit 4 每个测试方法
  新建一次测试类实例（`private val actions = RecordingActions()` 写成属性即可）。改成 `companion object`
  持有会串味
- 想引 mock 框架，先说明它怎么在「接口变了」这件事上给出编译期错误
