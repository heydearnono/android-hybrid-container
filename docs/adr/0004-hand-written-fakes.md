# ADR-0004：用手写 fake，不引 mock 框架

| | |
|---|---|
| 编号 | ADR-0004 |
| 日期 | 2026-09-02 |
| 状态 | 已采纳 |
| 影响范围 | 测试 |

## 决策

测试替身全部手写（`private class FakeArticleRepository : ArticleRepository`）。不引入 MockK / Mockito 一类 mock 框架。网络层的替身用 `mockwebserver3`——那是真实的 HTTP server，不是 mock。

## 背景

这个仓库全部由 AI 开发。AI 修改接口后，判断「哪里坏了」完全依赖测试给出的错误信息，而不同的替身方式给出的错误信息质量差别很大。

## 候选方案

| 方案 | 优势 | 代价 | 关键风险 |
|---|---|---|---|
| A（选中）手写 fake | 接口变了 fake 编译不过，错误直接指到那个类那一行 | 每个接口要写一个类，行为复杂时 fake 自己也会长 | fake 与真实实现行为漂移 |
| B MockK | 写起来短，能精确断言调用次数与参数 | 接口变了编译仍然通过，运行期才抛；错误栈在字节码代理里，指向不准 | AI 看到运行期失败容易误判成逻辑 bug，改错地方 |

## 理由

区别不在「哪个写起来快」，而在**失败信号的定位精度**。改了 `ArticleRepository.articles()` 的签名：手写 fake 直接给出 "class FakeArticleRepository is not abstract and does not implement member"，位置精确；MockK 会编译通过，然后在运行期抛一个跟接口变更看不出关系的异常。第二种情况下 AI 有很大概率去改业务代码而不是改测试。

「fake 行为漂移」这个风险是真的，控制手段是让 fake 保持极简：只存一个返回值和一个计数器，不写逻辑分支。一旦 fake 里开始出现 `if`，说明该考虑的是接口设计而不是测试。

## 后果

- 接受了什么代价：调用参数的细粒度断言要自己在 fake 里记（比如 `callCount`）。参数很多时会比 mock 啰嗦。
- 引入了什么依赖 / 锁定：测试依赖固定为 JUnit 4 + `kotlin-test-junit` + `kotlinx-coroutines-test` + Turbine，由 convention plugin 统一注入。**注意用 `kotlin-test-junit` 而不是 `kotlin-test`**：后者是多平台聚合坐标，靠 Gradle variant 属性选测试框架，AGP 内置 Kotlin 不设这个属性，会解析到拿不到 `kotlin.test.Test` 的变体（实测报 9 个 unresolved reference）。
- 什么条件下重新评估：出现需要验证复杂交互序列的场景（比如多次调用的顺序和参数组合），且手写 fake 已经长出分支逻辑时。

## 参考

- `build-logic/src/main/kotlin/com/example/base/buildlogic/Catalog.kt` 的 `commonTestDependencies`
- `core/domain/src/test/kotlin/.../GetArticlesUseCaseTest.kt`
